/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit;

import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.LogEventStorage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * @author zm
 */
public class LogEventTransmitterTest extends BaseTest {
    LogEventStorage storage = Mockito.mock(LogEventStorage.class);
    private final LogEventTransmitter logEventTransmitter =
        new LogEventTransmitter(false, 100, storage, ChunkMode.ITEMSIZE, 100, 100, false, "00000");

    @SneakyThrows
    @Test
    public void testSendChunk() {
        try (MockedStatic<MessageBuilder> mockedMessageBuilder = Mockito.mockStatic(MessageBuilder.class)) {
            TxnMessage txnMessage = Mockito.mock(TxnMessage.class);
            mockedMessageBuilder.when(
                    () -> MessageBuilder.buildTxnMessage(Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(txnMessage);
            TxnBuffer txnBuffer = Mockito.mock(TxnBuffer.class);
            when(txnBuffer.parallelRestoreIterator()).thenReturn(Collections.emptyIterator());
            List<TxnBuffer> txnBuffers = new ArrayList<>();
            txnBuffers.add(txnBuffer);
            TxnToken txnToken = TxnToken.newBuilder().build();

            MessageEvent e1 = new MessageEvent();
            MessageEvent e2 = new MessageEvent();

            e1.setMemSize(100);
            e1.setTxnBuffers(txnBuffers);
            e1.setToken(txnToken);

            e2.setMemSize(100);
            logEventTransmitter.checkIfFlushChunk(e1, false);
            logEventTransmitter.checkIfFlushChunk(e2, false);

            Assert.assertNotNull(logEventTransmitter.pollFromDumpingQueue());
        }
    }
}
