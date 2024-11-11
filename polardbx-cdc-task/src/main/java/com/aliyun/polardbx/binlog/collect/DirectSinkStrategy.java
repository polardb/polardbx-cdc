/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.collect;

import com.aliyun.polardbx.binlog.collect.handle.HandleContext;
import com.aliyun.polardbx.binlog.collect.handle.TxnSinkStageHandler;
import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.collect.message.MessageEventExceptionHandler;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.CollectException;
import com.aliyun.polardbx.binlog.merge.HeartBeatWindow;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.transmit.Transmitter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * created by ziyang.lb
 **/
public class DirectSinkStrategy implements CollectStrategy {

    private static final Logger logger = LoggerFactory.getLogger(DirectSinkStrategy.class);

    private final Collector collector;
    private final Transmitter transmitter;
    private final boolean isMergeNoTsoXa;
    private final TaskType taskType;
    private final HandleContext handleContext;

    private RingBuffer<MessageEvent> disruptorMsgBuffer;
    private Storage storage;
    private ExecutorService txnSinkExecutor;
    private BatchEventProcessor<MessageEvent> txnSinkProcessor;
    private volatile boolean running;

    public DirectSinkStrategy(Collector collector, Transmitter transmitter, boolean isMergeNoTsoXa,
                              TaskType taskType) {
        this.collector = collector;
        this.transmitter = transmitter;
        this.isMergeNoTsoXa = isMergeNoTsoXa;
        this.taskType = taskType;
        this.handleContext = new HandleContext();
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;

        txnSinkExecutor = Executors
            .newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("collector-sink-%d").build());
        ExceptionHandler<Object> exceptionHandler = new MessageEventExceptionHandler();
        SequenceBarrier sinkSequenceBarrier = disruptorMsgBuffer.newBarrier();
        txnSinkProcessor = new BatchEventProcessor<>(disruptorMsgBuffer,
            sinkSequenceBarrier,
            new TxnSinkStageHandler(this, handleContext, storage, transmitter, isMergeNoTsoXa));
        txnSinkProcessor.setExceptionHandler(exceptionHandler);
        disruptorMsgBuffer.addGatingSequences(txnSinkProcessor.getSequence());

        txnSinkExecutor.submit(txnSinkProcessor);

        logger.info("Relay collect strategy started.");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        txnSinkProcessor.halt();
        try {
            txnSinkExecutor.shutdownNow();
            while (!txnSinkExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                if (txnSinkExecutor.isShutdown() || txnSinkExecutor.isTerminated()) {
                    break;
                }

                txnSinkExecutor.shutdownNow();
            }
        } catch (Throwable e) {
            // ignore
        }
        logger.info("Relay collect strategy stopped.");
    }

    @Override
    public void setCurrentHeartBeatWindow(HeartBeatWindow window) {
        this.handleContext.setCurrentHeartBeatWindow(window);
    }

    @Override
    public void setRingBuffer(RingBuffer<MessageEvent> buffer) {
        this.disruptorMsgBuffer = buffer;
    }

    @Override
    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Override
    public CollectException getException() {
        return this.handleContext.getException();
    }

    @Override
    public StrategyType getStrategyType() {
        return StrategyType.DirectSink;
    }

}
