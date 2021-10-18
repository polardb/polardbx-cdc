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

import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.collect.message.MessageEventFactory;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.merge.HeartBeatWindow;
import com.aliyun.polardbx.binlog.metrics.MergeMetrics;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.transmit.Transmitter;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by ziyang.lb
 **/
public class LogEventCollector implements Collector {

    private static final Logger logger = LoggerFactory.getLogger(LogEventCollector.class);
    private static final int MAX_FULL_TIMES = 10;

    private final int ringBufferSize;
    private final TaskType taskType;
    private final Storage storage;
    private final Transmitter transmitter;
    private final boolean isMergeNoTsoXa;
    private AtomicLong eventsPushBlockingTime;
    private RingBuffer<MessageEvent> disruptorMsgBuffer;
    private CollectStrategy collectStrategy;
    private volatile boolean running;

    public LogEventCollector(Storage storage, Transmitter transmitter, int ringBufferSize, TaskType taskType,
                             boolean isMergeNoTsoXa) {
        this.storage = storage;
        this.transmitter = transmitter;
        this.ringBufferSize = ringBufferSize;
        this.taskType = taskType;
        this.isMergeNoTsoXa = isMergeNoTsoXa;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;

        this.eventsPushBlockingTime = new AtomicLong(0L);
        this.disruptorMsgBuffer = RingBuffer
            .createSingleProducer(new MessageEventFactory(), ringBufferSize, new BlockingWaitStrategy());
        if (taskType == TaskType.Relay) {
            this.collectStrategy = new CollectStrategyRelay(this, transmitter, isMergeNoTsoXa);
        } else {
            this.collectStrategy = new CollectStrategyFinal(this, transmitter, isMergeNoTsoXa);
        }
        this.collectStrategy.setRingBuffer(disruptorMsgBuffer);
        this.collectStrategy.setStorage(storage);
        this.collectStrategy.start();

        logger.info("Log event collector started.");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        this.collectStrategy.stop();
        logger.info("Log event collector stopped.");
    }

    @Override
    public void push(TxnToken token) {
        publish(token);
    }

    @Override
    public long getQueuedSize() {
        return disruptorMsgBuffer.getBufferSize() - disruptorMsgBuffer.remainingCapacity();
    }

    @Override
    public void setCurrentHeartBeatWindow(HeartBeatWindow window) {
        this.collectStrategy.setCurrentHeartBeatWindow(window);
    }

    private void publish(TxnToken token) {
        boolean interupted;
        long blockingStart = 0L;
        int fullTimes = 0;
        do {
            /**
             * 由于改为processor仅终止自身stage而不是stop，那么需要由incident标识coprocessor是否正常工作。
             * 让dump线程能够及时感知
             */
            if (collectStrategy.getException() != null) {
                throw collectStrategy.getException();
            }
            try {
                long next = disruptorMsgBuffer.tryNext();
                MessageEvent data = disruptorMsgBuffer.get(next);
                data.setToken(token);
                disruptorMsgBuffer.publish(next);
                if (fullTimes > 0) {
                    recordBlockingTime(System.nanoTime() - blockingStart);
                }
                MergeMetrics.get().setRingBufferQueuedSize(getQueuedSize());
                break;
            } catch (InsufficientCapacityException e) {
                if (fullTimes == 0) {
                    blockingStart = System.nanoTime();
                }

                applyWait(++fullTimes);
                interupted = Thread.interrupted();
                if (fullTimes % 1000 == 0) {
                    long nextStart = System.nanoTime();
                    recordBlockingTime(nextStart - blockingStart);
                    blockingStart = nextStart;
                }
            }
        } while (!interupted);
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

    private void recordBlockingTime(long nanoTime) {
        MergeMetrics.get().setTotalPushToCollectorBlockTime(eventsPushBlockingTime.addAndGet(nanoTime));
    }
}
