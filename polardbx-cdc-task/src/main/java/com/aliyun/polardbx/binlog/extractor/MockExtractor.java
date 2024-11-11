/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnBufferItem;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.aliyun.polardbx.binlog.stress.BaseStressSimulator;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ziyang.lb
 **/
public class MockExtractor implements Extractor {
    private static final Logger logger = LoggerFactory.getLogger(MockExtractor.class);
    private static final long BASE_TSO;
    private static final byte[] FORMAT_DESC_DATA = new byte[] {
        -103, -107, 113, 95, 15, 1, 0, 0, 0, -71, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 4, 0, 53, 46, 54, 46, 50, 57, 45, 84, 68, 68, 76,
        45, 53, 46, 52, 46, 54, 45, 83, 78, 65, 80, 83, 72, 79, 84,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 19, 56, 13, 0, 8, 0, 18, 0, 4, 4, 4,
        4, 18, 0, 0, -95, 0, 4, 26, 8, 0, 0, 0, 8, 8, 8, 2, 0, 0, 0,
        10, 10, 10, 42, 42, 0, 18, 52, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0, 0, 1, 60,
        -27, 100, 74};

    static {
        BASE_TSO = CommonUtils.nextLocalTso();
    }

    public long TSO_SEED = BASE_TSO;
    public long TXNID_SEED = 6716612767337602880L;

    private final int txnType;
    private final int dmlCount;
    private final int eventSize;
    private final boolean useBuffer;
    private final String partitionId;
    private final MergeSource mergeSource;
    private final Storage storage;
    private ExecutorService executor;

    public MockExtractor(int txnType, int dmlCount, int eventSize, boolean useBuffer, String partitionId,
                         MergeSource mergeSource,
                         Storage storage) {
        this.txnType = txnType;
        this.dmlCount = dmlCount;
        this.eventSize = eventSize;
        this.useBuffer = useBuffer;
        this.partitionId = partitionId;
        this.mergeSource = mergeSource;
        this.storage = storage;
    }

    @Override
    public void start(String startTSO) {
        executor =
            Executors.newFixedThreadPool(1, new ThreadFactoryBuilder().setNameFormat("mock-extractor-%d").build());
        executor.execute(() -> {
            try {
                mergeSource.push(TxnToken.newBuilder()
                    .setTso("0000000000000000000.01601362878_000000_178094002")
                    .setType(TxnType.FORMAT_DESC)
                    .setPayload(ByteString.copyFrom(FORMAT_DESC_DATA))
                    .build(), false);

                long tso;
                long txnId;

                // send heartbeat
                tso = ++TSO_SEED;
                txnId = ++TXNID_SEED;
                mergeSource.push(TxnToken.newBuilder()
                    .setTso(CommonUtils.generateTSO(tso, String.valueOf(txnId), partitionId))
                    .setTxnId(txnId)
                    .setPartitionId(partitionId)
                    .setType(TxnType.META_HEARTBEAT)
                    .setXaTxn(true)
                    .setTsoTransaction(true)
                    .setTxnSize(0)
                    .build(), false);

                // send heartbeat
                tso = ++TSO_SEED;
                txnId = ++TXNID_SEED;
                mergeSource.push(TxnToken.newBuilder()
                    .setTso(CommonUtils.generateTSO(tso, String.valueOf(txnId), partitionId))
                    .setTxnId(txnId)
                    .setPartitionId(partitionId)
                    .setType(TxnType.META_HEARTBEAT)
                    .setXaTxn(true)
                    .setTsoTransaction(true)
                    .setTxnSize(0)
                    .build(), false);

                // send dml data
                while (true) {
                    int traceId = 1111;
                    tso = ++TSO_SEED;
                    txnId = ++TXNID_SEED;

                    boolean isXA;
                    if (txnType == 0) {
                        isXA = (tso % 3 == 2 || tso % 3 == 1);//2/3 2PC,1/3 1PC
                    } else if (txnType == 1) {
                        isXA = false;
                    } else {
                        isXA = true;
                    }

                    if (useBuffer) {
                        TxnBuffer buffer = storage.create(new TxnKey(txnId, partitionId));
                        for (int i = 0; i < dmlCount; i++) {
                            buffer.push(TxnBufferItem.builder()
                                .traceId(String.valueOf(++traceId))
                                .eventType(LogEvent.TABLE_MAP_EVENT)
                                .payload(new byte[getEventSize()])
                                .build());
                            buffer.push(TxnBufferItem.builder()
                                .traceId(String.valueOf(++traceId))
                                .eventType(LogEvent.WRITE_ROWS_EVENT)
                                .payload(new byte[getEventSize()])
                                .build());
                        }
                    }
                    mergeSource.push(TxnToken.newBuilder()
                        .setTso(CommonUtils.generateTSO(tso, String.valueOf(txnId), partitionId))
                        .setTxnId(txnId)
                        .setPartitionId(partitionId)
                        .setType(TxnType.DML)
                        .setXaTxn(isXA)
                        .setTsoTransaction(isXA)
                        .setTxnSize(4)
                        .build(), useBuffer);
                }
            } catch (Exception e) {
                logger.error("error occurred.", e);
            }
        });
    }

    @Override
    public void stop() {
        executor.shutdownNow();
    }

    private int getEventSize() {
        if (eventSize != 0) {
            return eventSize;
        } else {
            return BaseStressSimulator.getRandomEventSize();
        }
    }
}
