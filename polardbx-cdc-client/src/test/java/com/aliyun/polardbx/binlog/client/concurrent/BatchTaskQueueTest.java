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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BatchTaskQueue 批量任务队列的单元测试
 */
public class BatchTaskQueueTest {

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
    public void testIsEmptyOnNew() {
        BatchTaskQueue<String> queue = new BatchTaskQueue<>(4);
        Assert.assertTrue(queue.isEmpty());
    }

    @Test
    public void testPutReturnsBatchTask() throws Exception {
        BatchTaskQueue<String> queue = new BatchTaskQueue<>(4);
        BatchTask<String> bt = queue.put(8);
        Assert.assertNotNull(bt);
        Assert.assertEquals(8, bt.getCapacity());
        Assert.assertFalse(bt.getCompleted()); // put后标记为未完成
        Assert.assertFalse(queue.isEmpty());
    }

    @Test
    public void testBasicPutFillAndTake() throws Exception {
        BatchTaskQueue<Integer> queue = new BatchTaskQueue<>(4);

        // 生产者获取一个batch，填充任务并标记完成
        BatchTask<Integer> bt = queue.put(4);
        bt.put(10);
        bt.put(20);
        bt.put(30);
        bt.setCompleted(true);

        // 消费者take并消费
        List<Integer> consumed = new ArrayList<>();
        queue.take(consumed::add);

        Assert.assertEquals(3, consumed.size());
        Assert.assertEquals(Integer.valueOf(10), consumed.get(0));
        Assert.assertEquals(Integer.valueOf(20), consumed.get(1));
        Assert.assertEquals(Integer.valueOf(30), consumed.get(2));
    }

    @Test
    public void testTakeWaitsForCompletion() throws Exception {
        BatchTaskQueue<String> queue = new BatchTaskQueue<>(4);

        BatchTask<String> bt = queue.put(4);
        bt.put("task1");
        bt.put("task2");
        // 注意：还没setCompleted(true)

        List<String> consumed = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch takeDone = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();

        Thread consumer = new Thread(() -> {
            try {
                queue.take(consumed::add);
                takeDone.countDown();
            } catch (Exception e) {
                error.set(e);
                takeDone.countDown();
            }
        });
        consumer.start();

        // 轮询确认consumer已进入WAITING状态
        awaitThreadState(consumer, 2000, Thread.State.WAITING, Thread.State.TIMED_WAITING);
        Assert.assertTrue(consumed.isEmpty());
        Assert.assertEquals(1, takeDone.getCount());

        // 标记完成，consumer应该被唤醒
        bt.setCompleted(true);
        Assert.assertTrue(takeDone.await(3, TimeUnit.SECONDS));
        Assert.assertNull(error.get());
        Assert.assertEquals(2, consumed.size());
        Assert.assertEquals("task1", consumed.get(0));
        Assert.assertEquals("task2", consumed.get(1));
    }

    @Test
    public void testMultipleBatchesInOrder() throws Exception {
        BatchTaskQueue<Integer> queue = new BatchTaskQueue<>(4);

        // 放入3个batch，每个batch有不同的数据
        for (int batch = 0; batch < 3; batch++) {
            BatchTask<Integer> bt = queue.put(4);
            bt.put(batch * 100 + 1);
            bt.put(batch * 100 + 2);
            bt.setCompleted(true);
        }

        // 按顺序消费
        List<Integer> allConsumed = new ArrayList<>();
        for (int batch = 0; batch < 3; batch++) {
            queue.take(allConsumed::add);
        }

        Assert.assertEquals(6, allConsumed.size());
        Assert.assertEquals(Integer.valueOf(1), allConsumed.get(0));
        Assert.assertEquals(Integer.valueOf(2), allConsumed.get(1));
        Assert.assertEquals(Integer.valueOf(101), allConsumed.get(2));
        Assert.assertEquals(Integer.valueOf(102), allConsumed.get(3));
        Assert.assertEquals(Integer.valueOf(201), allConsumed.get(4));
        Assert.assertEquals(Integer.valueOf(202), allConsumed.get(5));
    }

    @Test
    public void testProducerConsumerWithThreadPool() throws Exception {
        int totalBatches = 50;
        int batchSize = 8;
        BatchTaskQueue<Integer> queue = new BatchTaskQueue<>(8);
        ExecutorService executor = Executors.newFixedThreadPool(4);

        List<Integer> consumed = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Exception> producerError = new AtomicReference<>();
        AtomicReference<Exception> consumerError = new AtomicReference<>();
        CountDownLatch producerDone = new CountDownLatch(1);
        CountDownLatch consumerDone = new CountDownLatch(1);

        // 生产者线程：创建batch，填充任务，提交给线程池处理
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < totalBatches; i++) {
                    BatchTask<Integer> bt = queue.put(batchSize);
                    for (int j = 0; j < batchSize; j++) {
                        bt.put(i * batchSize + j);
                    }
                    // 模拟线程池处理：直接在executor中标记完成
                    final BatchTask<Integer> task = bt;
                    executor.submit(() -> task.setCompleted(true));
                }
                producerDone.countDown();
            } catch (Exception e) {
                producerError.set(e);
                producerDone.countDown();
            }
        });

        // 消费者线程：按顺序取出batch并消费
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < totalBatches; i++) {
                    queue.take(consumed::add);
                }
                consumerDone.countDown();
            } catch (Exception e) {
                consumerError.set(e);
                consumerDone.countDown();
            }
        });

        producer.start();
        consumer.start();

        Assert.assertTrue(producerDone.await(10, TimeUnit.SECONDS));
        Assert.assertTrue(consumerDone.await(10, TimeUnit.SECONDS));
        Assert.assertNull(producerError.get());
        Assert.assertNull(consumerError.get());

        // 验证所有数据都被消费了
        Assert.assertEquals(totalBatches * batchSize, consumed.size());

        // 验证全局有序：每个batch内部有序，batch之间有序
        for (int i = 0; i < consumed.size(); i++) {
            Assert.assertEquals(Integer.valueOf(i), consumed.get(i));
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testPutWithDifferentBatchSizes() throws Exception {
        BatchTaskQueue<String> queue = new BatchTaskQueue<>(4);

        // 第一次用batchSize=4
        BatchTask<String> bt1 = queue.put(4);
        Assert.assertEquals(4, bt1.getCapacity());
        bt1.put("a");
        bt1.setCompleted(true);

        List<String> consumed = new ArrayList<>();
        queue.take(consumed::add);
        Assert.assertEquals(1, consumed.size());
        Assert.assertEquals("a", consumed.get(0));
    }

    @Test
    public void testEmptyBatch() throws Exception {
        BatchTaskQueue<String> queue = new BatchTaskQueue<>(4);

        // 放入一个空的batch（不put任何task）
        BatchTask<String> bt = queue.put(4);
        bt.setCompleted(true);

        List<String> consumed = new ArrayList<>();
        queue.take(consumed::add);
        Assert.assertTrue(consumed.isEmpty());
    }

    @Test
    public void testQueueIsEmptyAfterAllConsumed() throws Exception {
        BatchTaskQueue<Integer> queue = new BatchTaskQueue<>(4);
        BatchTask<Integer> bt = queue.put(4);
        bt.put(1);
        bt.setCompleted(true);

        Assert.assertFalse(queue.isEmpty());

        List<Integer> consumed = new ArrayList<>();
        queue.take(consumed::add);

        Assert.assertTrue(queue.isEmpty());
    }

    @Test
    public void testBatchTaskReuse() throws Exception {
        // 队列大小为2，放入并消费多轮，测试batch对象复用
        BatchTaskQueue<Integer> queue = new BatchTaskQueue<>(2);

        for (int round = 0; round < 10; round++) {
            BatchTask<Integer> bt = queue.put(4);
            bt.put(round);
            bt.setCompleted(true);

            List<Integer> consumed = new ArrayList<>();
            queue.take(consumed::add);
            Assert.assertEquals(1, consumed.size());
            Assert.assertEquals(Integer.valueOf(round), consumed.get(0));
        }
    }

    @Test
    public void testHighThroughputOrdering() throws Exception {
        int totalBatches = 200;
        int itemsPerBatch = 16;
        BatchTaskQueue<Integer> queue = new BatchTaskQueue<>(16);

        List<Integer> consumed = new ArrayList<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        // 消费者
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < totalBatches; i++) {
                    queue.take(consumed::add);
                }
                done.countDown();
            } catch (Exception e) {
                error.set(e);
                done.countDown();
            }
        });
        consumer.start();

        // 生产者
        for (int i = 0; i < totalBatches; i++) {
            BatchTask<Integer> bt = queue.put(itemsPerBatch);
            for (int j = 0; j < itemsPerBatch; j++) {
                bt.put(i * itemsPerBatch + j);
            }
            bt.setCompleted(true);
        }

        Assert.assertTrue(done.await(10, TimeUnit.SECONDS));
        Assert.assertNull(error.get());
        Assert.assertEquals(totalBatches * itemsPerBatch, consumed.size());
        // 验证严格有序
        for (int i = 0; i < consumed.size(); i++) {
            Assert.assertEquals("Mismatch at index " + i, Integer.valueOf(i), consumed.get(i));
        }
    }
}
