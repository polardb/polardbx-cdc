/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.concurrent;

import java.util.Iterator;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * 基于LockFreeQueue的批量任务队列。
 * <p>
 * 工作流程：
 * 1. 生产者调用 put() 获取一个BatchTask容器，向其中填充任务
 * 2. 生产者将填充好的BatchTask提交给消费者线程池处理
 * 3. 消费者调用 take() 获取已完成的BatchTask，逐个消费其中的任务
 * <p>
 * 用于列存CDC客户端中将binlog事件批量分发给行解析线程池，并保证输出顺序。
 *
 * @author yaozhili
 */
public class BatchTaskQueue<T> {
    private final LockFreeQueue<BatchTask<T>> queue;

    public BatchTaskQueue(int queueSize) {
        this.queue = new LockFreeQueue<>(queueSize);
    }

    /**
     * 从队列中取出一个BatchTask，等待其处理完成后，再逐个消费其中的任务。
     * 该方法会阻塞直到有可用的已完成BatchTask。
     * 通过按插入顺序消费，保证了事件的全局有序性。
     */
    public BatchTask<T> take(Consumer<T> taskConsumer)
        throws InterruptedException {
        return queue.take(true, (task) -> {
            while (!task.getCompleted()) {
                task.setWaitingThread(Thread.currentThread());
                // check again before parking
                if (!task.getCompleted()) {
                    LockSupport.park();
                    if (Thread.interrupted()) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("interrupt when checking if task is completed");
                    }
                }
                task.setWaitingThread(null);
            }
            // task is completed, consume all one by one
            Iterator<T> iter = task.getIterator();
            while (iter.hasNext()) {
                taskConsumer.accept(iter.next());
            }
        });
    }

    /**
     * 从队列中插入一个BatchTask容器，并返回该BatchTask用于填充任务。
     * 如果队列已满，会阻塞等待直到有空位。
     * 返回的BatchTask已被重置并标记为未完成状态。
     */
    public BatchTask<T> put(int batchSize) throws InterruptedException {
        return queue.put(
            b -> {
                if (null == b || b.getCapacity() != batchSize) {
                    b = new BatchTask<>(batchSize);
                }
                b.reset();
                b.setCompleted(false);
                return b;
            });
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
