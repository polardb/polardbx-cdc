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
import com.aliyun.polardbx.binlog.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Branch-transaction-local staging sidecar. Raw data stays outside the main TxnBuffer and spills to its repository.
 */
public class ExternalColumnTxnContext {

    private static final Logger logger = LoggerFactory.getLogger(ExternalColumnTxnContext.class);
    private static final byte[] REPOSITORY_KEY_PREFIX = "EXT_COLUMN_STAGING_".getBytes(StandardCharsets.US_ASCII);
    private static final AtomicLong CONTEXT_SEQUENCE = new AtomicLong(0);

    private final Map<AddressKey, StagingValue> values = new HashMap<>();
    private final RepoUnit repoUnit;
    private final long contextId;
    private final long memoryLimitBytes;
    private final long maxTxnBytes;
    private final int maxEntries;
    private long rawBytes;
    private long memoryBytes;
    private boolean released;

    public ExternalColumnTxnContext(Storage storage) {
        this.contextId = CONTEXT_SEQUENCE.incrementAndGet();
        this.repoUnit = storage.getRepository().selectUnit(contextId);
        this.memoryLimitBytes = DynamicApplicationConfig.getLong(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MEMORY_LIMIT_BYTES);
        this.maxTxnBytes = DynamicApplicationConfig.getLong(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MAX_TXN_BYTES);
        this.maxEntries = DynamicApplicationConfig.getInt(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MAX_ENTRIES);
        if (memoryLimitBytes < 0 || maxTxnBytes <= 0 || maxEntries <= 0) {
            throw new PolardbxException("invalid external-column transaction context limits");
        }
    }

    void put(int seqId, long slotAddr, long tableId, byte[] raw) {
        ensureActive();
        if (raw == null) {
            throw new PolardbxException("external-column staging data must not be null");
        }
        AddressKey address = new AddressKey(seqId, slotAddr);
        StagingValue previous = values.get(address);
        if (previous != null) {
            byte[] previousRaw = load(previous);
            if (previous.tableId != tableId || !Arrays.equals(previousRaw, raw)) {
                throw new PolardbxException("conflicting duplicate external-column staging row for seqId " + seqId
                    + ", slotAddr " + Long.toUnsignedString(slotAddr));
            }
            return;
        }

        if (values.size() >= maxEntries) {
            throw new PolardbxException("external-column staging entry limit exceeded: " + maxEntries);
        }
        if (rawBytes > maxTxnBytes - raw.length) {
            throw new PolardbxException("external-column staging transaction byte limit exceeded: " + maxTxnBytes);
        }

        StagingValue value;
        if (memoryBytes <= memoryLimitBytes - raw.length) {
            // RowsLogBuffer returns a dedicated byte array for the field. Ownership is transferred to this context.
            value = StagingValue.inMemory(tableId, raw);
            memoryBytes += raw.length;
        } else {
            byte[] repositoryKey = buildRepositoryKey(address);
            byte[] repositoryValue = new byte[raw.length + 1];
            System.arraycopy(raw, 0, repositoryValue, 1, raw.length);
            try {
                repoUnit.put(repositoryKey, repositoryValue);
            } catch (Exception e) {
                throw new PolardbxException("persist external-column staging sidecar failed", e);
            }
            value = StagingValue.spilled(tableId, repositoryKey);
        }
        values.put(address, value);
        rawBytes += raw.length;
    }

    public byte[] resolve(ExternalColumnBlobRef blobRef) {
        ensureActive();
        AddressKey address = new AddressKey(blobRef.getSeqId(), blobRef.getSlotAddr());
        StagingValue value = values.get(address);
        if (value == null) {
            throw new PolardbxException("missing external-column staging data for seqId " + blobRef.getSeqId()
                + ", slotAddr " + Long.toUnsignedString(blobRef.getSlotAddr()));
        }
        byte[] raw = load(value);
        blobRef.validateRaw(raw);
        return raw;
    }

    public int size() {
        return values.size();
    }

    public long getRawBytes() {
        return rawBytes;
    }

    public long getMemoryBytes() {
        return memoryBytes;
    }

    public void release() {
        if (released) {
            return;
        }
        int cleanupFailureCount = 0;
        AddressKey firstFailedAddress = null;
        Exception firstFailure = null;
        for (Map.Entry<AddressKey, StagingValue> entry : values.entrySet()) {
            StagingValue value = entry.getValue();
            if (value.repositoryKey != null) {
                try {
                    repoUnit.delete(value.repositoryKey);
                } catch (Exception e) {
                    cleanupFailureCount++;
                    if (firstFailure == null) {
                        firstFailure = e;
                        firstFailedAddress = entry.getKey();
                    }
                }
            }
        }
        values.clear();
        rawBytes = 0;
        memoryBytes = 0;
        released = true;
        if (firstFailure != null) {
            logger.error("failed to cleanup {} external-column staging sidecars for context {}, first address "
                    + "seqId {}, slotAddr {}, ignored to keep the binlog pipeline moving",
                cleanupFailureCount, contextId, firstFailedAddress.seqId,
                Long.toUnsignedString(firstFailedAddress.slotAddr), firstFailure);
        }
    }

    private byte[] load(StagingValue value) {
        if (value.raw != null) {
            return value.raw;
        }
        try {
            byte[] persisted = repoUnit.get(value.repositoryKey);
            return Arrays.copyOfRange(persisted, 1, persisted.length);
        } catch (Exception e) {
            throw new PolardbxException("load external-column staging sidecar failed", e);
        }
    }

    private byte[] buildRepositoryKey(AddressKey address) {
        ByteBuffer buffer = ByteBuffer.allocate(REPOSITORY_KEY_PREFIX.length + Long.BYTES + Integer.BYTES + Long.BYTES);
        buffer.put(REPOSITORY_KEY_PREFIX);
        buffer.putLong(contextId);
        buffer.putInt(address.seqId);
        buffer.putLong(address.slotAddr);
        return buffer.array();
    }

    private void ensureActive() {
        if (released) {
            throw new PolardbxException("external-column transaction context is already released");
        }
    }

    private static class AddressKey {
        private final int seqId;
        private final long slotAddr;

        private AddressKey(int seqId, long slotAddr) {
            this.seqId = seqId;
            this.slotAddr = slotAddr;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof AddressKey)) {
                return false;
            }
            AddressKey other = (AddressKey) obj;
            return seqId == other.seqId && slotAddr == other.slotAddr;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(seqId);
            return 31 * result + Long.hashCode(slotAddr);
        }
    }

    private static class StagingValue {
        private final long tableId;
        private final byte[] raw;
        private final byte[] repositoryKey;

        private StagingValue(long tableId, byte[] raw, byte[] repositoryKey) {
            this.tableId = tableId;
            this.raw = raw;
            this.repositoryKey = repositoryKey;
        }

        private static StagingValue inMemory(long tableId, byte[] raw) {
            return new StagingValue(tableId, raw, null);
        }

        private static StagingValue spilled(long tableId, byte[] repositoryKey) {
            return new StagingValue(tableId, null, repositoryKey);
        }
    }
}
