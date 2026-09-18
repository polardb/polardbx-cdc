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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LockFreeQueue 无锁SPSC队列的单元测试
 */
public class LockFreeQueueTest {

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

    // ==================== 构造函数校验 ====================

    @Test(expected = IllegalArgumentException.class)
    public void testCapacityMustBePowerOfTwo_zero() {
        new LockFreeQueue<>(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCapacityMustBePowerOfTwo_negative() {
        new LockFreeQueue<>(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCapacityMustBePowerOfTwo_notPowerOfTwo() {
        new LockFreeQueue<>(3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCapacityMustBePowerOfTwo_six() {
        new LockFreeQueue<>(6);
    }

    @Test
    public void testValidCapacity() {
        // 不抛异常就是胜利
        new LockFreeQueue<>(1);
        new LockFreeQueue<>(2);
        new LockFreeQueue<>(4);
        new LockFreeQueue<>(8);
        new LockFreeQueue<>(1024);
    }

    // ==================== 基本读写 ====================

    @Test
    public void testPutAndTake() throws Exception {
        LockFreeQueue<String> queue = new LockFreeQueue<>(4);
        queue.put("hello");
        String result = queue.take(false);
        Assert.assertEquals("hello", result);
    }

    @Test
    public void testTakeNonBlockingReturnsNullOnEmpty() throws Exception {
        LockFreeQueue<String> queue = new LockFreeQueue<>(4);
        Assert.assertNull(queue.take(false));
    }

    @Test
    public void testFIFOOrdering() throws Exception {
        LockFreeQueue<Integer> queue = new LockFreeQueue<>(8);
        for (int i = 0; i < 5; i++) {
            queue.put(i);
        }
        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(Integer.valueOf(i), queue.take(false));
        }
        Assert.assertNull(queue.take(false));
    }

    @Test
    public void testFillToCapacity() throws Exception {
        int capacity = 8;
        LockFreeQueue<Integer> queue = new LockFreeQueue<>(capacity);
        for (int i = 0; i < capacity; i++) {
            queue.put(i);
        }
        Assert.assertEquals(capacity, queue.size());
        Assert.assertEquals(0, queue.remainingCapacity());
        for (int i = 0; i < capacity; i++) {
            Assert.assertEquals(Integer.valueOf(i), queue.take(false));
        }
    }

    // ==================== isEmpty / size / remainingCapacity ====================

    @Test
    public void testIsEmptyOnNewQueue() {
        LockFreeQueue<Object> queue = new LockFreeQueue<>(4);
        Assert.assertTrue(queue.isEmpty());
        Assert.assertEquals(0, queue.size());
        Assert.assertEquals(4, queue.remainingCapacity());
    }

    @Test
    public void testSizeAndRemainingCapacity() throws Exception {
        LockFreeQueue<String> queue = new LockFreeQueue<>(4);
        queue.put("a");
        Assert.assertFalse(queue.isEmpty());
        Assert.assertEquals(1, queue.size());
        Assert.assertEquals(3, queue.remainingCapacity());

        queue.put("b");
        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(2, queue.remainingCapacity());

        queue.take(false);
        Assert.assertEquals(1, queue.size());
        Assert.assertEquals(3, queue.remainingCapacity());

        queue.take(false);
        Assert.assertTrue(queue.isEmpty());
        Assert.assertEquals(0, queue.size());
        Assert.assertEquals(4, queue.remainingCapacity());
    }

    // ==================== 环形缓冲区回绕 ====================

    @Test
    public void testWrapAround() throws Exception {
        LockFreeQueue<Integer> queue = new LockFreeQueue<>(4);
        // 先填满再取空，多轮循环测试回绕
        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < 4; i++) {
                queue.put(round * 100 + i);
            }
            for (int i = 0; i < 4; i++) {
                Assert.assertEquals(Integer.valueOf(round * 100 + i), queue.take(false));
            }
            Assert.assertTrue(queue.isEmpty());
        }
    }

    // ==================== put with Function (slot复用) ====================

    @Test
    public void testPutWithFunction() throws Exception {
        LockFreeQueue<int[]> queue = new LockFreeQueue<>(4);
        // 第一次放入，旧槽位为null
        queue.put(old -> {
            Assert.assertNull(old);
            return new int[] {42};
        });
        int[] result = queue.take(false);
        Assert.assertNotNull(result);
        Assert.assertEquals(42, result[0]);
    }

    @Test
    public void testPutWithFunctionReusesSlot() throws Exception {
        LockFreeQueue<List<String>> queue = new LockFreeQueue<>(2);

        // 第一轮：放入并取出
        queue.put(old -> {
            List<String> list = new ArrayList<>();
            list.add("first");
            return list;
        });
        queue.take(false);

        // 绕一圈后，同一个slot有旧对象了
        // 注：此时slot上的引用还在buffer里，但take已经移动了readIndex
        // 再put到同一slot时，Function能拿到旧对象
        queue.put(old -> {
            // 旧对象可能非null（取决于实现是否清理slot），这里不做强假设
            List<String> list = (old != null) ? old : new ArrayList<>();
            list.clear();
            list.add("reused");
            return list;
        });
        List<String> result = queue.take(false);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("reused", result.get(0));
    }

    // ==================== take with checker ====================

    @Test
    public void testTakeWithChecker() throws Exception {
        LockFreeQueue<String> queue = new LockFreeQueue<>(4);
        queue.put("check-me");

        AtomicReference<String> checked = new AtomicReference<>();
        String result = queue.take(false, checked::set);
        Assert.assertEquals("check-me", result);
        Assert.assertEquals("check-me", checked.get());
    }

    @Test
    public void testTakeWithNullChecker() throws Exception {
        LockFreeQueue<String> queue = new LockFreeQueue<>(4);
        queue.put("no-check");
        String result = queue.take(false, null);
        Assert.assertEquals("no-check", result);
    }

    // ==================== timedTake ====================

    @Test
    public void testTimedTakeReturnsImmediatelyWhenDataAvailable() throws Exception {
        LockFreeQueue<String> queue = new LockFreeQueue<>(4);
        queue.put("fast");
        String result = queue.timedTake(1000);
        Assert.assertEquals("fast", result);
    }

    @Test
    public void testTimedTakeReturnsNullOnTimeout() throws Exception {
        LockFreeQueue<String> queue = new LockFreeQueue<>(4);
        long start = System.currentTimeMillis();
        String result = queue.timedTake(100);
        long elapsed = System.currentTimeMillis() - start;
        Assert.assertNull(result);
        // 至少等了差不多100ms（允许一定误差）
        Assert.assertTrue("elapsed: " + elapsed, elapsed >= 50);
    }

    @Test
    public void testTimedTakeWithZeroTimeoutActsAsNonBlocking() throws Exception {
        LockFreeQueue<String> queue = new LockFreeQueue<>(4);
        Assert.assertNull(queue.timedTake(0));
        Assert.assertNull(queue.timedTake(-1));
    }

    @Test
    public void testTimedTakeWithChecker() throws Exception {
        LockFreeQueue<Integer> queue = new LockFreeQueue<>(4);
        queue.put(99);
        AtomicInteger checkedValue = new AtomicInteger(-1);
        Integer result = queue.timedTake(1000, checkedValue::set);
        Assert.assertEquals(Integer.valueOf(99), result);
        Assert.assertEquals(99, checkedValue.get());
    }

    @Test
    public void testTimedTakeWakesUpWhenDataArrives() throws Exception {
        LockFreeQueue<String> queue = new LockFreeQueue<>(4);
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                result.set(queue.timedTake(5000));
                done.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        consumer.start();

        // 轮询确认consumer已进入等待状态
        awaitThreadState(consumer, 2000, Thread.State.WAITING, Thread.State.TIMED_WAITING);
        queue.put("delayed");

        Assert.assertTrue(done.await(3, TimeUnit.SECONDS));
        Assert.assertEquals("delayed", result.get());
    }

    // ==================== 多线程生产消费 ====================

    @Test
    public void testSingleProducerSingleConsumer() throws Exception {
        int count = 10000;
        LockFreeQueue<Integer> queue = new LockFreeQueue<>(64);
        List<Integer> consumed = new ArrayList<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        // 消费者线程
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < count; i++) {
                    Integer val = queue.take(true);
                    consumed.add(val);
                }
                done.countDown();
            } catch (Exception e) {
                error.set(e);
                done.countDown();
            }
        });
        consumer.start();

        // 生产者（当前线程）
        for (int i = 0; i < count; i++) {
            queue.put(i);
        }

        Assert.assertTrue(done.await(10, TimeUnit.SECONDS));
        Assert.assertNull(error.get());
        Assert.assertEquals(count, consumed.size());
        // 验证顺序
        for (int i = 0; i < count; i++) {
            Assert.assertEquals(Integer.valueOf(i), consumed.get(i));
        }
    }

    // ==================== 中断处理 ====================

    @Test
    public void testProducerInterrupted() throws Exception {
        LockFreeQueue<Integer> queue = new LockFreeQueue<>(2);
        // 填满队列
        queue.put(1);
        queue.put(2);

        AtomicReference<Exception> error = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            try {
                started.countDown();
                queue.put(3); // 队列满了，会park
                Assert.fail("should have been interrupted");
            } catch (InterruptedException e) {
                error.set(e);
            }
        });
        producer.start();
        Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
        awaitThreadState(producer, 2000, Thread.State.WAITING, Thread.State.TIMED_WAITING);
        producer.interrupt();
        producer.join(2000);
        Assert.assertNotNull(error.get());
        Assert.assertTrue(error.get() instanceof InterruptedException);
    }

    @Test
    public void testConsumerInterrupted() throws Exception {
        LockFreeQueue<Integer> queue = new LockFreeQueue<>(4);
        AtomicReference<Exception> error = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                started.countDown();
                queue.take(true); // 队列空，会park
                Assert.fail("should have been interrupted");
            } catch (InterruptedException e) {
                error.set(e);
            }
        });
        consumer.start();
        Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
        awaitThreadState(consumer, 2000, Thread.State.WAITING, Thread.State.TIMED_WAITING);
        consumer.interrupt();
        consumer.join(2000);
        Assert.assertNotNull(error.get());
        Assert.assertTrue(error.get() instanceof InterruptedException);
    }

    @Test
    public void testTimedTakeInterrupted() throws Exception {
        LockFreeQueue<Integer> queue = new LockFreeQueue<>(4);
        AtomicReference<Exception> error = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                started.countDown();
                queue.timedTake(10000);
                Assert.fail("should have been interrupted");
            } catch (InterruptedException e) {
                error.set(e);
            }
        });
        consumer.start();
        Assert.assertTrue(started.await(2, TimeUnit.SECONDS));
        awaitThreadState(consumer, 2000, Thread.State.WAITING, Thread.State.TIMED_WAITING);
        consumer.interrupt();
        consumer.join(2000);
        Assert.assertNotNull(error.get());
        Assert.assertTrue(error.get() instanceof InterruptedException);
    }

    // ==================== 队列满后消费唤醒生产者 ====================

    @Test
    public void testProducerUnblockedByConsumer() throws Exception {
        LockFreeQueue<Integer> queue = new LockFreeQueue<>(2);
        queue.put(1);
        queue.put(2);
        // 队列已满

        AtomicReference<Integer> putResult = new AtomicReference<>();
        CountDownLatch producerDone = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            try {
                putResult.set(queue.put(3)); // 会阻塞
                producerDone.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();

        awaitThreadState(producer, 2000, Thread.State.WAITING, Thread.State.TIMED_WAITING);
        // 消费一个，释放一个slot，唤醒producer
        Integer first = queue.take(false);
        Assert.assertEquals(Integer.valueOf(1), first);

        Assert.assertTrue(producerDone.await(3, TimeUnit.SECONDS));
        Assert.assertEquals(Integer.valueOf(3), putResult.get());
    }

    // ==================== 容量为1的边界情况 ====================

    @Test
    public void testCapacityOne() throws Exception {
        LockFreeQueue<String> queue = new LockFreeQueue<>(1);
        Assert.assertTrue(queue.isEmpty());
        Assert.assertEquals(1, queue.remainingCapacity());

        queue.put("only");
        Assert.assertEquals(1, queue.size());
        Assert.assertEquals(0, queue.remainingCapacity());

        Assert.assertEquals("only", queue.take(false));
        Assert.assertTrue(queue.isEmpty());
    }
}
