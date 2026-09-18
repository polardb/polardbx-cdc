/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.metrics.RelayStreamMetrics;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RelayDataReaderBaseTest {

    private static final String VALID_TSO = "7000000000000000001000000000000000000";

    private StoreEngine mockStoreEngine;
    private LockingCleaner mockLockingCleaner;
    private RelayStreamMetrics metrics;

    @Before
    public void setUp() throws Exception {
        mockStoreEngine = mock(StoreEngine.class);
        mockLockingCleaner = new LockingCleaner();
        metrics = new RelayStreamMetrics(1);

        when(mockStoreEngine.getLockingCleaner()).thenReturn(mockLockingCleaner);
        when(mockStoreEngine.getOriginStartTso()).thenReturn(VALID_TSO);
        when(mockStoreEngine.getMaxCleanTso()).thenReturn("");
        doNothing().when(mockStoreEngine).close();
    }

    // ========== getData tests ==========

    @Test
    public void testGetData_EmptyList() {
        byte[] searchKey = RelayKeyUtil.buildRelayKey(VALID_TSO, "trace1", 0);
        RelayDataReaderBase reader = createReader(searchKey, new LinkedList<>());

        List<Pair<byte[], byte[]>> result = reader.getData(100, 1024);

        assertTrue("empty list should be returned as-is", result.isEmpty());
        assertEquals(0, metrics.getReadEventCount().get());
    }

    @Test
    public void testGetData_NonEmptyList_UpdatesMetrics() {
        String tso = VALID_TSO;
        byte[] key = RelayKeyUtil.buildRelayKey(tso, "trace1", 0);
        byte[] value = "test-data".getBytes();

        LinkedList<Pair<byte[], byte[]>> data = new LinkedList<>();
        data.add(Pair.of(key, value));

        RelayDataReaderBase reader = createReader(key, data);

        List<Pair<byte[], byte[]>> result = reader.getData(100, 1024);

        assertEquals(1, result.size());
        verify(mockStoreEngine).setMaxReadKey(key);
        assertEquals(1, metrics.getReadEventCount().get());
        assertTrue("readByteSize should be updated", metrics.getReadByteSize().get() > 0);
        assertTrue("readDelay should be set", metrics.getReadDelay().get() >= 0);
    }

    @Test
    public void testGetData_MultipleItems_UpdatesMetrics() {
        String tso = VALID_TSO;
        byte[] key1 = RelayKeyUtil.buildRelayKey(tso, "trace1", 0);
        byte[] key2 = RelayKeyUtil.buildRelayKey(tso, "trace2", 1);
        byte[] value1 = "data1".getBytes();
        byte[] value2 = "data2-longer".getBytes();

        LinkedList<Pair<byte[], byte[]>> data = new LinkedList<>();
        data.add(Pair.of(key1, value1));
        data.add(Pair.of(key2, value2));

        RelayDataReaderBase reader = createReader(key1, data);

        List<Pair<byte[], byte[]>> result = reader.getData(100, 1024);

        assertEquals(2, result.size());
        assertEquals(2, metrics.getReadEventCount().get());
        assertEquals(value1.length + value2.length, metrics.getReadByteSize().get());
    }

    @Test
    public void testGetData_NoEpsBpsCalculation_BeforeInterval() {
        String tso = VALID_TSO;
        byte[] key = RelayKeyUtil.buildRelayKey(tso, "trace1", 0);
        byte[] value = "data".getBytes();

        LinkedList<Pair<byte[], byte[]>> data = new LinkedList<>();
        data.add(Pair.of(key, value));

        RelayDataReaderBase reader = createReader(key, data);

        long epsBefore = metrics.getReadEps().get();
        long bpsBefore = metrics.getReadBps().get();

        reader.getData(100, 1024);

        assertEquals("readEps should not change when interval < 5000ms", epsBefore, metrics.getReadEps().get());
        assertEquals("readBps should not change when interval < 5000ms", bpsBefore, metrics.getReadBps().get());
    }

    @Test
    public void testGetData_CalculatesEpsBps_WhenIntervalExceeded() throws Exception {
        String tso = VALID_TSO;
        byte[] key = RelayKeyUtil.buildRelayKey(tso, "trace1", 0);
        // Use a large value to ensure byte delta is significant
        byte[] value = new byte[10000];

        // Create a list with many items to ensure event delta is large enough
        // for eps = items/interval*1000 to be > 0 with integer math
        LinkedList<Pair<byte[], byte[]>> data = new LinkedList<>();
        for (int i = 0; i < 100; i++) {
            data.add(Pair.of(key, value));
        }

        RelayDataReaderBase reader = createReader(key, data);

        // First call to establish baseline
        reader.getData(10000, 10 * 1024 * 1024);

        // Wait for the 5-second interval to pass
        Thread.sleep(5100);

        // Second call: interval >= 5000ms, EPS/BPS should be calculated
        reader.getData(10000, 10 * 1024 * 1024);

        assertTrue("readEps should be > 0 after interval exceeded, got " + metrics.getReadEps().get(),
            metrics.getReadEps().get() > 0);
        assertTrue("readBps should be > 0 after interval exceeded, got " + metrics.getReadBps().get(),
            metrics.getReadBps().get() > 0);
    }

    // ========== Constructor / checkValid tests ==========

    @Test
    public void testConstructor_ValidTso_Success() {
        byte[] searchKey = RelayKeyUtil.buildRelayKey(VALID_TSO, "trace1", 0);

        RelayDataReaderBase reader = createReader(searchKey, new LinkedList<>());
        assertTrue("reader should be created successfully with valid TSO", reader != null);
    }

    @Test
    public void testConstructor_RequestTsoGreaterThanOriginStartTso() {
        String laterTso = "8000000000000000001000000000000000000";
        when(mockStoreEngine.getOriginStartTso()).thenReturn(VALID_TSO);

        byte[] searchKey = RelayKeyUtil.buildRelayKey(laterTso, "trace1", 0);

        RelayDataReaderBase reader = createReader(searchKey, new LinkedList<>());
        assertTrue("reader should be created when requestTso > originStartTso", reader != null);
    }

    // ========== Helper methods ==========

    private RelayDataReaderBase createReader(byte[] searchFromKey,
                                             LinkedList<Pair<byte[], byte[]>> data) {
        return new RelayDataReaderBase(mockStoreEngine, metrics, searchFromKey) {
            @Override
            protected LinkedList<Pair<byte[], byte[]>> getDataInternal(int maxItemSize, long maxByteSize) {
                return data;
            }

            @Override
            public void close() {
                // no-op for testing
            }
        };
    }
}
