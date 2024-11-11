/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
import lombok.Data;
import lombok.Getter;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-11-15 18:39
 **/
public class RingBufferTest {

    @Test
    public void testSequenceCursor() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        MessageEventFactory messageEventFactory = new MessageEventFactory();
        RingBuffer<Event> msgRingBuffer = RingBuffer.createSingleProducer(
            messageEventFactory, 8192, new BlockingWaitStrategy());
        RingBufferEventHandler eventHandler = new RingBufferEventHandler(msgRingBuffer, latch);
        SequenceBarrier sequenceBarrier = msgRingBuffer.newBarrier();

        BatchEventProcessor<Event> offerProcessor =
            new BatchEventProcessor<>(msgRingBuffer, sequenceBarrier, eventHandler);
        msgRingBuffer.addGatingSequences(offerProcessor.getSequence());

        ExecutorService offerExecutor = ThreadPoolUtil.createExecutorWithFixedNum(1, "applier");
        offerExecutor.submit(offerProcessor);

        for (int i = 1; i <= 100000; i++) {
            do {
                try {
                    long next = msgRingBuffer.tryNext();
                    Event data = msgRingBuffer.get(next);
                    data.setValue((long) i);
                    msgRingBuffer.publish(next);
                    break;
                } catch (InsufficientCapacityException e) {
                    Thread.sleep(100);
                }
            } while (true);
        }

        latch.await();
        Assert.assertNull(eventHandler.getThrowable());
    }

    @Data
    public static class Event {
        private Long value;
    }

    public static class MessageEventFactory implements EventFactory<Event> {

        @Override
        public Event newInstance() {
            return new Event();
        }
    }

    private static class RingBufferEventHandler implements EventHandler<Event>, LifecycleAware {

        RingBuffer<Event> msgRingBuffer;
        CountDownLatch latch;
        @Getter
        volatile Throwable throwable;

        public RingBufferEventHandler(RingBuffer<Event> msgRingBuffer, CountDownLatch latch) {
            this.msgRingBuffer = msgRingBuffer;
            this.latch = latch;
        }

        @Override
        public void onEvent(Event event, long sequence, boolean endOfBatch) {
            Long value = event.getValue();
            if (value == 100000) {
                try {
                    Assert.assertEquals(sequence, msgRingBuffer.getCursor());
                    System.out.println("sequence is " + sequence);
                    latch.countDown();
                } catch (Throwable t) {
                    throwable = t;
                }
            }
        }

        @Override
        public void onStart() {

        }

        @Override
        public void onShutdown() {

        }

    }
}
