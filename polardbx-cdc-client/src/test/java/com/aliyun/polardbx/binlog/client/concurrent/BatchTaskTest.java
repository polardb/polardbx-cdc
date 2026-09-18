/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.concurrent;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * BatchTask 批量任务容器的单元测试
 */
public class BatchTaskTest {

    @Test
    public void testConstructorAndCapacity() {
        BatchTask<String> bt = new BatchTask<>(16);
        Assert.assertEquals(16, bt.getCapacity());
    }

    @Test
    public void testPutAndIterator() {
        BatchTask<Integer> bt = new BatchTask<>(8);
        bt.reset();
        for (int i = 0; i < 5; i++) {
            bt.put(i);
        }
        Iterator<Integer> iter = bt.getIterator();
        List<Integer> result = new ArrayList<>();
        while (iter.hasNext()) {
            result.add(iter.next());
        }
        Assert.assertEquals(5, result.size());
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(Integer.valueOf(i), result.get(i));
        }
    }

    @Test
    public void testResetClearsSize() {
        BatchTask<String> bt = new BatchTask<>(4);
        bt.reset();
        bt.put("a");
        bt.put("b");
        // reset后iterator应该拿不到任何东西
        bt.reset();
        Iterator<String> iter = bt.getIterator();
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void testResetAndRefill() {
        BatchTask<String> bt = new BatchTask<>(4);
        bt.reset();
        bt.put("old1");
        bt.put("old2");
        bt.reset();
        bt.put("new1");
        Iterator<String> iter = bt.getIterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals("new1", iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void testEmptyIterator() {
        BatchTask<Object> bt = new BatchTask<>(4);
        bt.reset();
        Iterator<Object> iter = bt.getIterator();
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void testFillToCapacity() {
        int capacity = 32;
        BatchTask<Integer> bt = new BatchTask<>(capacity);
        bt.reset();
        for (int i = 0; i < capacity; i++) {
            bt.put(i);
        }
        Iterator<Integer> iter = bt.getIterator();
        int count = 0;
        while (iter.hasNext()) {
            Assert.assertEquals(Integer.valueOf(count), iter.next());
            count++;
        }
        Assert.assertEquals(capacity, count);
    }

    @Test
    public void testCompletedDefaultTrue() {
        BatchTask<Object> bt = new BatchTask<>(4);
        Assert.assertTrue(bt.getCompleted());
    }

    @Test
    public void testSetCompletedFalseAndTrue() {
        BatchTask<Object> bt = new BatchTask<>(4);
        bt.setCompleted(false);
        Assert.assertFalse(bt.getCompleted());
        bt.setCompleted(true);
        Assert.assertTrue(bt.getCompleted());
    }

    @Test
    public void testHandleConsumesAllAndSetsCompleted() {
        BatchTask<Integer> bt = new BatchTask<>(8);
        bt.reset();
        bt.put(10);
        bt.put(20);
        bt.put(30);
        bt.setCompleted(false);

        List<Integer> consumed = new ArrayList<>();
        bt.handle(consumed::add);

        Assert.assertEquals(3, consumed.size());
        Assert.assertEquals(Integer.valueOf(10), consumed.get(0));
        Assert.assertEquals(Integer.valueOf(20), consumed.get(1));
        Assert.assertEquals(Integer.valueOf(30), consumed.get(2));
        Assert.assertTrue(bt.getCompleted());
    }

    @Test
    public void testHandleSetsCompletedEvenOnException() {
        BatchTask<Integer> bt = new BatchTask<>(4);
        bt.reset();
        bt.put(1);
        bt.setCompleted(false);

        try {
            bt.handle(item -> {
                throw new RuntimeException("boom");
            });
            Assert.fail("should have thrown");
        } catch (RuntimeException e) {
            Assert.assertEquals("boom", e.getMessage());
        }
        // handle的finally块保证completed被设为true
        Assert.assertTrue(bt.getCompleted());
    }

    /**
     * 轮询等待线程进入指定状态，替代不靠谱的Thread.sleep
     */
    private void awaitThreadState(Thread thread, long timeoutMs, Thread.State... expectedStates)
        throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Thread.State state = thread.getState();
            for (Thread.State expected : expectedStates) {
                if (state == expected) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        Assert.fail("Thread did not reach expected state within " + timeoutMs
            + "ms, current: " + thread.getState());
    }

    @Test
    public void testSetCompletedUnparksWaitingThread() throws Exception {
        BatchTask<Object> bt = new BatchTask<>(4);
        bt.setCompleted(false);

        AtomicBoolean unparked = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);

        Thread waiter = new Thread(() -> {
            bt.setWaitingThread(Thread.currentThread());
            started.countDown();
            // 循环park，防止虚假唤醒
            while (!bt.getCompleted()) {
                LockSupport.park();
            }
            unparked.set(true);
        });
        waiter.start();

        Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
        // 轮询确认waiter已经进入WAITING状态
        awaitThreadState(waiter, 2000, Thread.State.WAITING);
        Assert.assertFalse(unparked.get());

        // setCompleted(true)应该unpark等待线程
        bt.setCompleted(true);
        waiter.join(2000);
        Assert.assertTrue(unparked.get());
    }

    @Test
    public void testSetWaitingThread() {
        BatchTask<Object> bt = new BatchTask<>(4);
        bt.setWaitingThread(Thread.currentThread());
        // 没有直接getter，但不抛异常就行，主要验证setCompleted会unpark
        bt.setWaitingThread(null);
    }

    @Test
    public void testHandleWithEmptyBatch() {
        BatchTask<String> bt = new BatchTask<>(4);
        bt.reset();
        bt.setCompleted(false);
        List<String> consumed = new ArrayList<>();
        bt.handle(consumed::add);
        Assert.assertTrue(consumed.isEmpty());
        Assert.assertTrue(bt.getCompleted());
    }

    @Test
    public void testProducerConsumerFlow() throws Exception {
        BatchTask<Integer> bt = new BatchTask<>(16);
        bt.reset();
        bt.setCompleted(false);

        // 生产者填充任务
        for (int i = 0; i < 10; i++) {
            bt.put(i);
        }

        // 消费者在另一个线程handle
        List<Integer> consumed = new ArrayList<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            try {
                bt.handle(consumed::add);
            } catch (Exception e) {
                error.set(e);
            }
        });
        consumer.start();
        consumer.join(2000);

        Assert.assertNull(error.get());
        Assert.assertEquals(10, consumed.size());
        Assert.assertTrue(bt.getCompleted());
    }
}
