/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.collect;

import com.aliyun.polardbx.binlog.collect.handle.HandleContext;
import com.aliyun.polardbx.binlog.collect.handle.TxnShuffleStageHandler;
import com.aliyun.polardbx.binlog.collect.handle.TxnSinkStageHandler;
import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.collect.message.MessageEventExceptionHandler;
import com.aliyun.polardbx.binlog.error.CollectException;
import com.aliyun.polardbx.binlog.merge.HeartBeatWindow;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.transmit.Transmitter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 *
 **/
public class CollectStrategyFinal implements CollectStrategy {

    private static final Logger logger = LoggerFactory.getLogger(CollectStrategyFinal.class);

    private final Collector collector;
    private final Transmitter transmitter;
    private final HandleContext handleContext;
    private final boolean isMergeNoTsoXa;
    private final int txnShuffleThreadCount = 4;

    private RingBuffer<MessageEvent> disruptorMsgBuffer;
    private Storage storage;
    private ExecutorService txnShuffleExecutor;
    private ExecutorService txnSinkExecutor;
    private WorkerPool<MessageEvent> txnShuffleWorkerPool;
    private BatchEventProcessor<MessageEvent> txnSinkStage;
    private volatile boolean running;

    public CollectStrategyFinal(Collector collector, Transmitter transmitter, boolean isMergeNoTsoXa) {
        this.collector = collector;
        this.transmitter = transmitter;
        this.isMergeNoTsoXa = isMergeNoTsoXa;
        this.handleContext = new HandleContext();
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;

        this.txnShuffleExecutor = Executors.newFixedThreadPool(txnShuffleThreadCount,
            new ThreadFactoryBuilder().setNameFormat("collector-shuffle-%d").build());

        this.txnSinkExecutor = Executors
            .newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("collector-sink-%d").build());

        // stage 1
        ExceptionHandler<Object> exceptionHandler = new MessageEventExceptionHandler();
        SequenceBarrier txnShuffleSequenceBarrier = disruptorMsgBuffer.newBarrier();
        WorkHandler<MessageEvent>[] workHandlers = new TxnShuffleStageHandler[txnShuffleThreadCount];
        for (int i = 0; i < txnShuffleThreadCount; i++) {
            workHandlers[i] = new TxnShuffleStageHandler(handleContext, storage, isMergeNoTsoXa);
        }
        txnShuffleWorkerPool = new WorkerPool<>(disruptorMsgBuffer,
            txnShuffleSequenceBarrier,
            exceptionHandler,
            workHandlers);
        Sequence[] sequence = txnShuffleWorkerPool.getWorkerSequences();
        disruptorMsgBuffer.addGatingSequences(sequence);

        // stage 2
        SequenceBarrier sinkSequenceBarrier = disruptorMsgBuffer.newBarrier(sequence);
        txnSinkStage = new BatchEventProcessor<>(disruptorMsgBuffer,
            sinkSequenceBarrier,
            new TxnSinkStageHandler(this, handleContext, storage, transmitter, isMergeNoTsoXa));
        txnSinkStage.setExceptionHandler(exceptionHandler);
        disruptorMsgBuffer.addGatingSequences(txnSinkStage.getSequence());

        // start
        txnSinkExecutor.submit(txnSinkStage);
        txnShuffleWorkerPool.start(txnShuffleExecutor);

        logger.info("Final collect strategy started.");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        txnShuffleWorkerPool.halt();
        txnSinkStage.halt();
        try {
            txnShuffleExecutor.shutdownNow();
            while (!txnShuffleExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                if (txnShuffleExecutor.isShutdown() || txnShuffleExecutor.isTerminated()) {
                    break;
                }

                txnShuffleExecutor.shutdownNow();
            }
        } catch (Throwable e) {
            // ignore
        }

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
        logger.info("Final collect strategy stopped.");
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
        return handleContext.getException();
    }

    @Override
    public StrategyType getStrategyType() {
        return StrategyType.Final;
    }
}
