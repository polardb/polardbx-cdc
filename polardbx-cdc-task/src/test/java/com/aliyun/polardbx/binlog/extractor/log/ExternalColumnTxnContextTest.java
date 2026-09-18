/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import com.aliyun.polardbx.binlog.storage.Repository;
import com.aliyun.polardbx.binlog.storage.Storage;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ExternalColumnTxnContextTest {

    private static final long SLOT_ADDR = 0x8000000000000403L;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private MockedStatic<DynamicApplicationConfig> mockedConfig;
    private long memoryLimitBytes;
    private long maxTxnBytes;
    private int maxEntries;

    @Before
    public void before() {
        memoryLimitBytes = 1024;
        maxTxnBytes = 4096;
        maxEntries = 16;
        mockedConfig = Mockito.mockStatic(DynamicApplicationConfig.class);
        mockedConfig.when(() -> DynamicApplicationConfig.getLong(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MEMORY_LIMIT_BYTES)).thenAnswer(i -> memoryLimitBytes);
        mockedConfig.when(() -> DynamicApplicationConfig.getLong(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MAX_TXN_BYTES)).thenAnswer(i -> maxTxnBytes);
        mockedConfig.when(() -> DynamicApplicationConfig.getInt(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MAX_ENTRIES)).thenAnswer(i -> maxEntries);
    }

    @After
    public void after() {
        mockedConfig.close();
    }

    @Test
    public void testBlobRefCanonicalDecodeAndRawValidation() throws Exception {
        byte[] raw = "external-column-raw".getBytes("UTF-8");
        String encoded = blobRef(7, SLOT_ADDR, raw);
        ExternalColumnBlobRef blobRef = ExternalColumnBlobRef.decode(encoded);

        Assert.assertEquals(7, blobRef.getSeqId());
        Assert.assertEquals(SLOT_ADDR, blobRef.getSlotAddr());
        blobRef.validateRaw(raw);

        assertFailure("text length", () -> ExternalColumnBlobRef.decode(null));
        assertFailure("text length", () -> ExternalColumnBlobRef.decode(encoded.substring(1)));
        assertFailure("non-canonical", () -> ExternalColumnBlobRef.decode('A' + encoded.substring(1)));
        assertFailure("non-canonical", () -> ExternalColumnBlobRef.decode('g' + encoded.substring(1)));

        byte[] binary = decodeHex(encoded);
        binary[0] = 3;
        String unsupportedVersion = encodeHex(binary);
        assertUnsupportedVersion(3, () -> ExternalColumnBlobRef.decode(unsupportedVersion));
        assertUnsupportedVersion(3, () -> ExternalColumnBlobRef.decode(unsupportedVersion + "00"));
        assertFailure("V2 text length", () -> ExternalColumnBlobRef.decode(encoded.substring(0, 64)));
        assertFailure("non-canonical", () -> ExternalColumnBlobRef.decode(unsupportedVersion + "gg"));

        binary = decodeHex(encoded);
        putInt(binary, 1, -1);
        String invalidSeqId = encodeHex(binary);
        assertFailure("negative", () -> ExternalColumnBlobRef.decode(invalidSeqId));

        binary = decodeHex(encoded);
        putLong(binary, 5, 0x8100000000000403L);
        String opaqueAddress = encodeHex(binary);
        Assert.assertEquals(0x8100000000000403L,
            ExternalColumnBlobRef.decode(opaqueAddress).getSlotAddr());

        binary = decodeHex(encoded);
        putLong(binary, 5, 0L);
        String zeroAddress = encodeHex(binary);
        assertFailure("zero", () -> ExternalColumnBlobRef.decode(zeroAddress));

        binary = decodeHex(encoded);
        putInt(binary, 13, 256 * 1024 * 1024 + 1);
        String oversizedRaw = encodeHex(binary);
        assertFailure("raw size", () -> ExternalColumnBlobRef.decode(oversizedRaw));

        assertFailure("size mismatch", () -> blobRef.validateRaw(null));
        assertFailure("size mismatch", () -> blobRef.validateRaw(Arrays.copyOf(raw, raw.length - 1)));
        byte[] corrupted = raw.clone();
        corrupted[0] ^= 1;
        assertFailure("MD5 mismatch", () -> blobRef.validateRaw(corrupted));
    }

    @Test
    public void testInMemoryRestoreDuplicateAndRelease() throws Exception {
        RepoFixture fixture = newRepoFixture();
        ExternalColumnTxnContext context = new ExternalColumnTxnContext(fixture.storage);
        byte[] raw = "hello".getBytes("UTF-8");
        ExternalColumnBlobRef blobRef = ExternalColumnBlobRef.decode(blobRef(2, SLOT_ADDR, raw));

        context.put(2, SLOT_ADDR, 10, raw);
        Assert.assertEquals(1, context.size());
        Assert.assertEquals(raw.length, context.getRawBytes());
        Assert.assertEquals(raw.length, context.getMemoryBytes());
        Assert.assertArrayEquals(raw, context.resolve(blobRef));

        context.put(2, SLOT_ADDR, 10, raw.clone());
        Assert.assertEquals(1, context.size());
        assertFailure("conflicting duplicate", () -> context.put(2, SLOT_ADDR, 11, raw));
        assertFailure("conflicting duplicate", () -> context.put(2, SLOT_ADDR, 10, new byte[] {1}));
        assertFailure("must not be null", () -> context.put(3, SLOT_ADDR + 1, 10, null));

        byte[] otherRaw = new byte[] {9};
        ExternalColumnBlobRef missing = ExternalColumnBlobRef.decode(blobRef(3, SLOT_ADDR + 1, otherRaw));
        assertFailure("missing", () -> context.resolve(missing));

        context.release();
        context.release();
        Assert.assertEquals(0, context.size());
        Assert.assertEquals(0, context.getRawBytes());
        Assert.assertEquals(0, context.getMemoryBytes());
        assertFailure("already released", () -> context.resolve(blobRef));
        assertFailure("already released", () -> context.put(2, SLOT_ADDR, 10, raw));
    }

    @Test
    public void testRepositorySpillResolveAndCleanup() throws Exception {
        memoryLimitBytes = 0;
        RepoFixture fixture = newRepoFixture();
        ExternalColumnTxnContext context = new ExternalColumnTxnContext(fixture.storage);
        byte[] raw = "spilled-value".getBytes("UTF-8");
        ExternalColumnBlobRef blobRef = ExternalColumnBlobRef.decode(blobRef(4, SLOT_ADDR, raw));

        context.put(4, SLOT_ADDR, 20, raw);
        Assert.assertEquals(1, fixture.persisted.size());
        Assert.assertEquals(0, context.getMemoryBytes());
        byte[] persisted = fixture.persisted.values().iterator().next();
        Assert.assertEquals(raw.length + 1, persisted.length);
        Assert.assertEquals(0, persisted[0]);
        Assert.assertArrayEquals(raw, context.resolve(blobRef));

        context.put(4, SLOT_ADDR, 20, raw.clone());
        context.release();
        Assert.assertTrue(fixture.persisted.isEmpty());
        verify(fixture.repoUnit).delete(any(byte[].class));
    }

    @Test
    public void testLimitsAndRepositoryFailures() throws Exception {
        RepoFixture fixture = newRepoFixture();

        memoryLimitBytes = -1;
        assertFailure("invalid", () -> new ExternalColumnTxnContext(fixture.storage));
        memoryLimitBytes = 1;
        maxTxnBytes = 0;
        assertFailure("invalid", () -> new ExternalColumnTxnContext(fixture.storage));
        maxTxnBytes = 4;
        maxEntries = 0;
        assertFailure("invalid", () -> new ExternalColumnTxnContext(fixture.storage));

        maxEntries = 1;
        ExternalColumnTxnContext entryLimited = new ExternalColumnTxnContext(fixture.storage);
        entryLimited.put(1, SLOT_ADDR, 1, new byte[] {1});
        assertFailure("entry limit", () -> entryLimited.put(2, SLOT_ADDR + 1, 1, new byte[] {2}));

        maxEntries = 2;
        maxTxnBytes = 2;
        ExternalColumnTxnContext byteLimited = new ExternalColumnTxnContext(fixture.storage);
        byteLimited.put(1, SLOT_ADDR, 1, new byte[] {1, 2});
        assertFailure("byte limit", () -> byteLimited.put(2, SLOT_ADDR + 1, 1, new byte[] {3}));

        memoryLimitBytes = 0;
        maxTxnBytes = 10;
        RepoFixture putFailure = newRepoFixture();
        doThrow(new IllegalStateException("put failed")).when(putFailure.repoUnit)
            .put(any(byte[].class), any(byte[].class));
        ExternalColumnTxnContext putFailureContext = new ExternalColumnTxnContext(putFailure.storage);
        assertFailure("persist", () -> putFailureContext.put(1, SLOT_ADDR, 1, new byte[] {1}));

        RepoFixture getFailure = newRepoFixture();
        ExternalColumnTxnContext getFailureContext = new ExternalColumnTxnContext(getFailure.storage);
        byte[] raw = new byte[] {1};
        ExternalColumnBlobRef blobRef = ExternalColumnBlobRef.decode(blobRef(1, SLOT_ADDR, raw));
        getFailureContext.put(1, SLOT_ADDR, 1, raw);
        doThrow(new IllegalStateException("get failed")).when(getFailure.repoUnit).get(any(byte[].class));
        assertFailure("load", () -> getFailureContext.resolve(blobRef));

        RepoFixture cleanupFailure = newRepoFixture();
        ExternalColumnTxnContext cleanupFailureContext = new ExternalColumnTxnContext(cleanupFailure.storage);
        cleanupFailureContext.put(1, SLOT_ADDR, 1, new byte[] {1});
        cleanupFailureContext.put(2, SLOT_ADDR + 1, 1, new byte[] {2});
        doThrow(new IllegalStateException("delete failed")).when(cleanupFailure.repoUnit).delete(any(byte[].class));
        cleanupFailureContext.release();
        Assert.assertEquals(0, cleanupFailureContext.size());
        assertFailure("already released", () -> cleanupFailureContext.resolve(blobRef));
    }

    private static RepoFixture newRepoFixture() throws Exception {
        Storage storage = mock(Storage.class);
        Repository repository = mock(Repository.class);
        RepoUnit repoUnit = mock(RepoUnit.class);
        Map<ByteArrayKey, byte[]> persisted = new HashMap<>();
        when(storage.getRepository()).thenReturn(repository);
        when(repository.selectUnit(anyLong())).thenReturn(repoUnit);
        doAnswer(invocation -> {
            persisted.put(new ByteArrayKey(invocation.getArgument(0)), invocation.getArgument(1));
            return null;
        }).when(repoUnit).put(any(byte[].class), any(byte[].class));
        when(repoUnit.get(any(byte[].class))).thenAnswer(invocation ->
            persisted.get(new ByteArrayKey(invocation.getArgument(0))));
        doAnswer(invocation -> {
            persisted.remove(new ByteArrayKey(invocation.getArgument(0)));
            return null;
        }).when(repoUnit).delete(any(byte[].class));
        return new RepoFixture(storage, repoUnit, persisted);
    }

    private static String blobRef(int seqId, long slotAddr, byte[] raw) throws Exception {
        byte[] binary = new byte[33];
        binary[0] = 2;
        putInt(binary, 1, seqId);
        putLong(binary, 5, slotAddr);
        putInt(binary, 13, raw.length);
        System.arraycopy(MessageDigest.getInstance("MD5").digest(raw), 0, binary, 17, 16);
        return encodeHex(binary);
    }

    private static String encodeHex(byte[] value) {
        char[] result = new char[value.length * 2];
        for (int i = 0; i < value.length; i++) {
            result[i * 2] = HEX[value[i] >>> 4 & 0xF];
            result[i * 2 + 1] = HEX[value[i] & 0xF];
        }
        return new String(result);
    }

    private static byte[] decodeHex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static void putInt(byte[] target, int offset, int value) {
        ByteBuffer.wrap(target, offset, Integer.BYTES).putInt(value);
    }

    private static void putLong(byte[] target, int offset, long value) {
        ByteBuffer.wrap(target, offset, Long.BYTES).putLong(value);
    }

    private static void assertFailure(String messageFragment, ThrowingRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("expected PolardbxException containing: " + messageFragment);
        } catch (PolardbxException e) {
            Assert.assertTrue("unexpected message: " + e.getMessage(), e.getMessage().contains(messageFragment));
        } catch (Exception e) {
            throw new AssertionError("unexpected exception", e);
        }
    }

    private static void assertUnsupportedVersion(int version, ThrowingRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("expected unsupported BlobRef version " + version);
        } catch (UnsupportedExternalColumnBlobRefVersionException e) {
            Assert.assertEquals(version, e.getObservedVersion());
            Assert.assertEquals(2, e.getSupportedVersion());
        } catch (Exception e) {
            throw new AssertionError("unexpected exception", e);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class RepoFixture {
        private final Storage storage;
        private final RepoUnit repoUnit;
        private final Map<ByteArrayKey, byte[]> persisted;

        private RepoFixture(Storage storage, RepoUnit repoUnit, Map<ByteArrayKey, byte[]> persisted) {
            this.storage = storage;
            this.repoUnit = repoUnit;
            this.persisted = persisted;
        }
    }

    private static class ByteArrayKey {
        private final byte[] value;

        private ByteArrayKey(byte[] value) {
            this.value = value.clone();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ByteArrayKey && Arrays.equals(value, ((ByteArrayKey) obj).value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }
}
