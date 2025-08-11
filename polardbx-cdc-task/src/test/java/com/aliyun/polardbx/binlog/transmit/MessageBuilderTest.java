/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit;

import com.aliyun.polardbx.binlog.protocol.TxnBegin;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Iterator;

import static org.mockito.Mockito.when;

public class MessageBuilderTest extends BaseTest {

    @Test
    public void testBuildTxnBegin() {
        TxnToken token = TxnToken.newBuilder().setTso("888888").build();
        TxnBegin txnBegin = MessageBuilder.buildTxnBegin(token, false);
        Assert.assertNotEquals("888888", txnBegin.getTxnToken().getTso());
        Assert.assertEquals("888888", txnBegin.getTxnMergedToken().getTso());

        txnBegin = MessageBuilder.buildTxnBegin(token, true);
        Assert.assertEquals("888888", txnBegin.getTxnToken().getTso());
        Assert.assertNotEquals("888888", txnBegin.getTxnMergedToken().getTso());
    }

    @Test
    public void testBuildTxnMessage() {
        TxnToken token = TxnToken.newBuilder().setTso("888888").build();
        TxnBuffer txnBuffer = Mockito.mock(TxnBuffer.class);
        Iterator iterator = Mockito.mock(Iterator.class);
        when(txnBuffer.parallelRestoreIterator()).thenReturn(iterator);

        TxnMessage txnMessage = MessageBuilder.buildTxnMessage(token, txnBuffer, false);
        Assert.assertNotEquals("888888", txnMessage.getTxnBegin().getTxnToken().getTso());
        Assert.assertEquals("888888", txnMessage.getTxnBegin().getTxnMergedToken().getTso());

        txnMessage = MessageBuilder.buildTxnMessage(token, txnBuffer, true);
        Assert.assertEquals("888888", txnMessage.getTxnBegin().getTxnToken().getTso());
        Assert.assertNotEquals("888888", txnMessage.getTxnBegin().getTxnMergedToken().getTso());
    }
}
