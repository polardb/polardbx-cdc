/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.collect;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.collect.handle.HandleContext;
import com.aliyun.polardbx.binlog.collect.handle.TxnMergeStageHandler;
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
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_COLLECTOR_MERGE_STAGE_PARALLELISM;

/**
 * created by ziyang.lb
 **/
public class CollectStrategyFinal implements CollectStrategy {

    private static final Logger logger = LoggerFactory.getLogger(CollectStrategyFinal.class);

    private final Collector collector;
    private final Transmitter transmitter;
    private final HandleContext handleContext;
    private final boolean isMergeNoTsoXa;
    private final TaskType taskType;

    private RingBuffer<MessageEvent> disruptorMsgBuffer;
    private Storage storage;
    private ExecutorService txnMergeExecutor;
    private ExecutorService txnSinkExecutor;
    private WorkerPool<MessageEvent> txnMergeWorkerPool;
    private BatchEventProcessor<MessageEvent> txnSinkStage;
    private volatile boolean running;

    public CollectStrategyFinal(Collector collector, Transmitter transmitter, boolean isMergeNoTsoXa,
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

        int txnMergeThreadCount = DynamicApplicationConfig.getInt(TASK_COLLECTOR_MERGE_STAGE_PARALLELISM);
        this.txnMergeExecutor = Executors.newFixedThreadPool(txnMergeThreadCount,
            new ThreadFactoryBuilder().setNameFormat("collector-merge-%d").build());

        this.txnSinkExecutor = Executors
            .newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("collector-sink-%d").build());

        // stage 1
        ExceptionHandler<Object> exceptionHandler = new MessageEventExceptionHandler();
        SequenceBarrier txnMergeSequenceBarrier = disruptorMsgBuffer.newBarrier();
        WorkHandler<MessageEvent>[] workHandlers = new TxnMergeStageHandler[txnMergeThreadCount];
        for (int i = 0; i < txnMergeThreadCount; i++) {
            workHandlers[i] = new TxnMergeStageHandler(handleContext, storage, isMergeNoTsoXa, taskType);
        }
        txnMergeWorkerPool = new WorkerPool<>(disruptorMsgBuffer,
            txnMergeSequenceBarrier,
            exceptionHandler,
            workHandlers);
        Sequence[] sequence = txnMergeWorkerPool.getWorkerSequences();
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
        txnMergeWorkerPool.start(txnMergeExecutor);

        logger.info("Final collect strategy started.");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        txnMergeWorkerPool.halt();
        txnSinkStage.halt();
        try {
            txnMergeExecutor.shutdownNow();
            while (!txnMergeExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                if (txnMergeExecutor.isShutdown() || txnMergeExecutor.isTerminated()) {
                    break;
                }

                txnMergeExecutor.shutdownNow();
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
