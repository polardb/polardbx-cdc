/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.collect.message.MessageEventExceptionHandler;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileGenerator;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_PARALLEL_BUILD_MAX_SLOT_PAYLOAD_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_PARALLEL_BUILD_MAX_SLOT_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_PARALLEL_BUILD_WITH_BATCH;
import static io.grpc.internal.GrpcUtil.getThreadFactory;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class ParallelWriter {
    private static final int MAX_FULL_TIMES = 10;

    private final int ringBufferSize;
    private final int eventBuilderParallelism;
    private final HandleContext handleContext;
    private final AtomicLong eventTokenSeq;
    private final AtomicBoolean running;
    private final boolean dryRun;
    private final int dryRunMode;
    private final boolean useBatch;
    private final int maxSlotSize;
    private final int maxSlotPayloadSize;
    private final StreamMetrics metrics;
    private final String threadName;

    private RingBuffer<EventData> disruptorMsgBuffer;
    private ExecutorService eventBuildExecutor;
    private ExecutorService eventSinkExecutor;
    private WorkerPool<EventData> eventBuildWorkerPool;
    private BatchEventProcessor<EventData> eventSinkStage;
    private BatchEventToken currentBatchEventToken;

    public ParallelWriter(LogFileGenerator logFileGenerator, int ringBufferSize, int eventBuilderParallelism,
                          StreamMetrics metrics, boolean dryRun, int dryRunMode, String threadName) {
        this.ringBufferSize = ringBufferSize;
        this.eventBuilderParallelism = eventBuilderParallelism;
        this.dryRun = dryRun;
        this.dryRunMode = dryRunMode;
        this.eventTokenSeq = new AtomicLong(0);
        this.running = new AtomicBoolean(false);
        this.handleContext = new HandleContext();
        this.handleContext.setLogFileGenerator(logFileGenerator);
        this.handleContext.setRunning(running);
        this.useBatch = DynamicApplicationConfig.getBoolean(BINLOG_PARALLEL_BUILD_WITH_BATCH);
        this.maxSlotSize = DynamicApplicationConfig.getInt(BINLOG_PARALLEL_BUILD_MAX_SLOT_SIZE);
        this.maxSlotPayloadSize = DynamicApplicationConfig.getInt(BINLOG_PARALLEL_BUILD_MAX_SLOT_PAYLOAD_SIZE);
        this.metrics = metrics;
        this.threadName = threadName;
    }

    public void push(SingleEventToken eventToken) {
        if (dryRun && dryRunMode == 1) {
            return;
        }

        EventToken tokenToPush;
        if (useBatch) {
            if (currentBatchEventToken == null) {
                currentBatchEventToken = new BatchEventToken();
            }

            if (currentBatchEventToken.hasCapacity(eventToken, maxSlotSize, maxSlotPayloadSize) &&
                eventToken.getType() != SingleEventToken.Type.HEARTBEAT) {
                currentBatchEventToken.addToken(eventToken);
                return;
            } else {
                tokenToPush = currentBatchEventToken;
                currentBatchEventToken = new BatchEventToken();
                currentBatchEventToken.addToken(eventToken);
            }
        } else {
            tokenToPush = eventToken;
        }

        doPush(tokenToPush);
    }

    private void doPush(EventToken eventToken) {
        eventToken.setSequence(eventTokenSeq.incrementAndGet());
        int fullTimes = 0;
        do {
            if (handleContext.getException() != null) {
                throw handleContext.getException();
            }
            try {
                long next = disruptorMsgBuffer.tryNext();
                EventData data = disruptorMsgBuffer.get(next);
                data.setEventToken(eventToken);
                disruptorMsgBuffer.publish(next);
                break;
            } catch (InsufficientCapacityException e) {
                applyWait(++fullTimes);
            }
        } while (running.get());

        metrics.setWriteQueueSize(
            disruptorMsgBuffer.getBufferSize() - disruptorMsgBuffer.remainingCapacity());
    }

    public void await() {
        if (useBatch && currentBatchEventToken != null && !currentBatchEventToken.getTokens().isEmpty()) {
            doPush(currentBatchEventToken);
            currentBatchEventToken = null;
        }

        if (eventTokenSeq.get() == 0L) {
            log.info("event token seq is zero, no need to loop await.");
            return;
        }

        while (true) {
            if (handleContext.getException() != null) {
                throw handleContext.getException();
            }
            if (eventTokenSeq.get() == handleContext.getLatestSinkSequence()) {
                break;
            }
            LockSupport.parkNanos(1000 * 1000);
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            // init
            this.disruptorMsgBuffer = RingBuffer
                .createSingleProducer(new EventDataFactory(maxSlotSize), ringBufferSize,
                    new BlockingWaitStrategy());
            this.eventBuildExecutor = Executors.newFixedThreadPool(eventBuilderParallelism,
                getThreadFactory(threadName + "-event-builder" + "-%d", false));
            this.eventSinkExecutor = Executors.newSingleThreadExecutor(
                getThreadFactory(threadName + "-event-sink" + "-%d", false));

            // stage 1
            ExceptionHandler<Object> exceptionHandler = new MessageEventExceptionHandler();
            SequenceBarrier eventBuilderSequenceBarrier = disruptorMsgBuffer.newBarrier();
            WorkHandler<EventData>[] workHandlers = new EventDataBuildHandler[eventBuilderParallelism];
            for (int i = 0; i < eventBuilderParallelism; i++) {
                workHandlers[i] = new EventDataBuildHandler(handleContext);
            }
            eventBuildWorkerPool = new WorkerPool<>(disruptorMsgBuffer,
                eventBuilderSequenceBarrier,
                exceptionHandler,
                workHandlers);
            Sequence[] sequence = eventBuildWorkerPool.getWorkerSequences();
            disruptorMsgBuffer.addGatingSequences(sequence);

            // stage 2
            SequenceBarrier sinkSequenceBarrier = disruptorMsgBuffer.newBarrier(sequence);
            eventSinkStage = new BatchEventProcessor<>(disruptorMsgBuffer,
                sinkSequenceBarrier, new EventDataSinkHandler(handleContext, dryRun, dryRunMode));
            eventSinkStage.setExceptionHandler(exceptionHandler);
            disruptorMsgBuffer.addGatingSequences(eventSinkStage.getSequence());

            // start
            eventSinkExecutor.submit(eventSinkStage);
            eventBuildWorkerPool.start(eventBuildExecutor);
        }

    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            eventBuildWorkerPool.halt();
            eventSinkStage.halt();
            try {
                eventBuildExecutor.shutdownNow();
                while (!eventBuildExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    if (eventBuildExecutor.isShutdown() || eventBuildExecutor.isTerminated()) {
                        break;
                    }
                    eventBuildExecutor.shutdownNow();
                }
            } catch (Throwable e) {
                // ignore
            }

            try {
                eventSinkExecutor.shutdownNow();
                while (!eventSinkExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    if (eventSinkExecutor.isShutdown() || eventSinkExecutor.isTerminated()) {
                        break;
                    }
                    eventSinkExecutor.shutdownNow();
                }
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    private void applyWait(int fullTimes) {
        int newFullTimes = Math.min(MAX_FULL_TIMES, fullTimes);
        if (fullTimes <= 3) {
            // 3次以内
            Thread.yield();
        } else {
            // 超过3次，最多只sleep 1ms
            LockSupport.parkNanos(100 * 1000L * newFullTimes);
        }
    }

}
