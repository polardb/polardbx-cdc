/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.stress;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metrics.MetricsManager;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.rpc.TxnMessageProvider;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcServer;
import com.aliyun.polardbx.binlog.storage.AlreadyExistException;
import com.aliyun.polardbx.binlog.storage.LogEventStorage;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnBufferItem;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.aliyun.polardbx.binlog.transmit.ChunkMode;
import com.aliyun.polardbx.binlog.transmit.LogEventTransmitter;
import com.aliyun.polardbx.binlog.transmit.Transmitter;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;

/**
 * Created by ziyang.lb
 **/
public class FromTransmitterStressSimulator extends BaseStressSimulator {

    private static final String DEFAULT_EVENT_SIZE = "1024";
    private static final String DEFAULT_REUSE_TXNITEMS = "false";
    private static final String DEFAULT_USE_RANDOM_SIZE = "false";
    private static final String DEFAULT_MESSAGE_ITEM_SIZE = "30";

    private static final List<String> ALL_PARTIES =
        Lists.newArrayList("111111111111111", "222222222222222", "333333333333333", "444444444444444");
    private static final byte[] FORMAT_DESC_DATA = new byte[] {
        -103, -107, 113, 95, 15, 1, 0, 0, 0, -71, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 4, 0, 53, 46, 54, 46, 50, 57, 45, 84, 68, 68, 76, 45, 53,
        46, 52, 46, 54, 45, 83, 78, 65, 80, 83, 72, 79, 84, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 19, 56, 13, 0, 8, 0, 18, 0, 4, 4, 4, 4, 18, 0, 0, -95,
        0, 4, 26, 8, 0, 0, 0, 8, 8, 8, 2, 0, 0, 0, 10, 10, 10, 42, 42,
        0, 18, 52, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 10, 0, 0, 0, 0, 1, 60, -27, 100, 74};
    private static List<TxnBufferItem> TxnBufferItems;

    //sh stress.sh TransmitterSimulator "stress.trans.messageItemSize=10 stress.trans.reuseTxnBufferItems=false"
    public static void main(String[] args) throws InterruptedException, IOException {
        if (args.length > 0 && StringUtils.isNotBlank(args[0])) {
            handleArgs(args[0]);
        }
        boolean reuseTxnBufferItems =
            Boolean.parseBoolean(getValue("stress.trans.reuseTxnBufferItems", DEFAULT_REUSE_TXNITEMS));
        int eventSize = Integer.parseInt(getValue("stress.trans.eventSize", DEFAULT_EVENT_SIZE));
        boolean useRandomSize = Boolean.parseBoolean(getValue("stress.trans.useRandomSize", DEFAULT_USE_RANDOM_SIZE));
        int messageItemSize = Integer.parseInt(getValue("stress.trans.messageItemSize", DEFAULT_MESSAGE_ITEM_SIZE));

        TxnBufferItems = buildTxnBufferItems(eventSize, useRandomSize);

        final MetricsManager metricsManager = new MetricsManager();
        final Storage storage = new LogEventStorage(null);
        final Transmitter transmitter = new LogEventTransmitter(TaskType.Final, 8192, storage, ChunkMode.MEMSIZE,
            messageItemSize, 1073741824, false, "");
        final TxnStreamRpcServer rpcServer = new TxnStreamRpcServer(9999, new TxnMessageProvider() {

            @Override
            public boolean checkTSO(String startTSO, TxnOutputStream outputStream, boolean keepWaiting) {
                return true;
            }

            @Override
            public void dump(String startTSO, TxnOutputStream outputStream) throws InterruptedException {
                transmitter.dump(startTSO, outputStream);
            }

            @Override
            public void restart(String startTSO) {
            }
        });

        metricsManager.start();
        storage.start();
        transmitter.start();
        rpcServer.start();

        new Thread(() -> {
            TxnToken formatDesc = TxnToken.newBuilder()
                .setTso("0000000000000000000.01601362878_000000_178094002")
                .setType(TxnType.FORMAT_DESC)
                .setPayload(ByteString.copyFrom(FORMAT_DESC_DATA))
                .build();

            MessageEvent messageEvent = new MessageEvent();
            messageEvent.setToken(formatDesc);
            transmitter.transmit(messageEvent);

            while (true) {
                TxnToken txnToken = buildToken();
                try {
                    TxnBuffer txnBuffer = storage.create(new TxnKey(txnToken.getTxnId(), txnToken.getPartitionId()));
                    if (reuseTxnBufferItems) {
                        txnBuffer.push(TxnBufferItems);
                    } else {
                        txnBuffer.push(buildTxnBufferItems(eventSize, useRandomSize));
                    }
                    txnBuffer.markComplete();
                } catch (AlreadyExistException e) {
                    throw new PolardbxException("error", e);
                }
                MessageEvent messageEvent1 = new MessageEvent();
                messageEvent.setToken(txnToken);
                transmitter.transmit(messageEvent1);
            }
        }).start();

        rpcServer.blockUntilShutdown();
    }

    private static TxnToken buildToken() {
        long localTso = CommonUtils.nextLocalTso();
        long xid = System.nanoTime();
        String virtualTso = CommonUtils.generateTSO(localTso, String.valueOf(xid), "111111");

        return TxnToken.newBuilder()
            .setSchema("test")
            .setTso(virtualTso)
            .setType(TxnType.DML)
            .addAllAllParties(ALL_PARTIES)
            .setTxnId(xid)
            .setXaTxn(true)
            .setTsoTransaction(true)
            .setTxnSize(8)
            .setPartitionId(ALL_PARTIES.get((int) (localTso % 4)))
            .build();
    }

    private static List<TxnBufferItem> buildTxnBufferItems(int eventSize, boolean useRandom) {
        return Lists.newArrayList(
            TxnBufferItem.builder()
                .eventType(LogEvent.TABLE_MAP_EVENT)
                .payload(new byte[getEventSize(eventSize, useRandom)])
                .build(),
            TxnBufferItem.builder()
                .eventType(LogEvent.WRITE_ROWS_EVENT)
                .payload(new byte[getEventSize(eventSize, useRandom)])
                .build(),
            TxnBufferItem.builder()
                .eventType(LogEvent.TABLE_MAP_EVENT)
                .payload(new byte[getEventSize(eventSize, useRandom)])
                .build(),
            TxnBufferItem.builder()
                .eventType(LogEvent.WRITE_ROWS_EVENT)
                .payload(new byte[getEventSize(eventSize, useRandom)])
                .build(),
            TxnBufferItem.builder()
                .eventType(LogEvent.TABLE_MAP_EVENT)
                .payload(new byte[getEventSize(eventSize, useRandom)])
                .build(),
            TxnBufferItem.builder()
                .eventType(LogEvent.WRITE_ROWS_EVENT)
                .payload(new byte[getEventSize(eventSize, useRandom)])
                .build(),
            TxnBufferItem.builder()
                .eventType(LogEvent.TABLE_MAP_EVENT)
                .payload(new byte[getEventSize(eventSize, useRandom)])
                .build(),
            TxnBufferItem.builder()
                .eventType(LogEvent.WRITE_ROWS_EVENT)
                .payload(new byte[getEventSize(eventSize, useRandom)])
                .build());
    }

    private static int getEventSize(int eventSize, boolean useRandom) {
        return useRandom ? getRandomEventSize() : eventSize;
    }
}
