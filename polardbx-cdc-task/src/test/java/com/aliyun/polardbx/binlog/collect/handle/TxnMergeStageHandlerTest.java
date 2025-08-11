/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.collect.handle;

import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Iterator;

import static org.mockito.Mockito.when;

public class TxnMergeStageHandlerTest extends BaseTest {

    @Test
    public void testTryBuildTxnMessageObject() {
        TxnToken token = TxnToken.newBuilder().build();
        TxnBuffer txnBuffer = Mockito.mock(TxnBuffer.class);
        MessageEvent messageEvent = Mockito.mock(MessageEvent.class);

        when(messageEvent.getToken()).thenReturn(token);
        when(messageEvent.getTxnBuffers()).thenReturn(Lists.newArrayList(txnBuffer));
        when(txnBuffer.parallelRestoreIterator()).thenReturn(Mockito.mock(Iterator.class));

        HandleContext handleContext = new HandleContext();
        TxnMergeStageHandler txnMergeStageHandler = new TxnMergeStageHandler(handleContext, null, false, true, false);
        TxnMessage message = txnMergeStageHandler.tryBuildTxnMessageObject(messageEvent);
        Assert.assertNotNull(message);

        when(messageEvent.getMemSize()).thenReturn(1000000L);
        message = txnMergeStageHandler.tryBuildTxnMessageObject(messageEvent);
        Assert.assertNull(message);
    }

    @Test
    public void testTryBuildTxnMessageBytes() {
        TxnToken token = TxnToken.newBuilder().build();
        TxnBuffer txnBuffer = Mockito.mock(TxnBuffer.class);
        MessageEvent messageEvent = Mockito.mock(MessageEvent.class);

        when(messageEvent.getToken()).thenReturn(token);
        when(messageEvent.getTxnBuffers()).thenReturn(Lists.newArrayList(txnBuffer));
        when(txnBuffer.parallelRestoreIterator()).thenReturn(Mockito.mock(Iterator.class));

        HandleContext handleContext = new HandleContext();
        TxnMergeStageHandler txnMergeStageHandler = new TxnMergeStageHandler(handleContext, null, false, true, false);
        ByteString message = txnMergeStageHandler.tryBuildTxnMessageBytes(messageEvent);
        Assert.assertNotNull(message);

        when(messageEvent.getMemSize()).thenReturn(1000000L);
        message = txnMergeStageHandler.tryBuildTxnMessageBytes(messageEvent);
        Assert.assertNull(message);
    }
}
