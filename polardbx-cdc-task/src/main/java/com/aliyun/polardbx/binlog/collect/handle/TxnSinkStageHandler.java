/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.collect.handle;

import com.aliyun.polardbx.binlog.collect.CollectStrategy;
import com.aliyun.polardbx.binlog.collect.StrategyType;
import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.error.CollectException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.transmit.Transmitter;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;

/**
 * created by ziyang.lb
 **/
public class TxnSinkStageHandler implements EventHandler<MessageEvent>, LifecycleAware {

    private final CollectStrategy collectStrategy;
    private final HandleContext handleContext;
    private final Storage storage;
    private final Transmitter transmitter;
    private final boolean isMergeNoTsoXa;
    private TxnToken lastToken;

    public TxnSinkStageHandler(CollectStrategy collectStrategy, HandleContext handleContext, Storage storage,
                               Transmitter transmitter, boolean isMergeNoTsoXa) {
        this.collectStrategy = collectStrategy;
        this.handleContext = handleContext;
        this.storage = storage;
        this.transmitter = transmitter;
        this.isMergeNoTsoXa = isMergeNoTsoXa;
    }

    @Override
    public void onEvent(MessageEvent event, long sequence, boolean endOfBatch) {
        try {
            TxnToken txnToken = event.getToken();

            if (collectStrategy.getStrategyType() == StrategyType.MergeAndSink
                && (txnToken.getXaTxn() && (txnToken.getTsoTransaction() || isMergeNoTsoXa))
                && !event.isMerged()) {
                throw new PolardbxException("find not-merged event in sink handler.");
            }

            if (lastToken != null) {
                if (txnToken.getTso().compareTo(lastToken.getTso()) < 0) {
                    throw new PolardbxException(
                        "detected disorderly tso，current token is:\r\n" + txnToken + ",last token is:\r\n" + lastToken);
                }
            }

            transmitter.transmit(event.copy());
            lastToken = txnToken;
            event.clear();
        } catch (Throwable t) {
            CollectException exception = new CollectException("error occurred when do txn sink.", t);
            handleContext.setException(exception);
            throw exception;
        }
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onShutdown() {

    }
}
