/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.concurrent;

import lombok.Getter;

import java.util.Iterator;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * 批量任务容器，用于在生产者和消费者之间传递一批任务。
 * <p>
 * 线程安全协议：
 * 1. 线程-0（独占访问）：执行 reset、填充所有任务（put）、设置 completed=false
 * 2. 线程-1（独占访问）：消费所有任务（通过 getIterator）、设置 completed=true
 * <p>
 * 数组两端添加TASKS_PAD位的填充，避免内存相邻的数据引起CPU缓存行伪共享。
 *
 * @author yaozhili
 */
public class BatchTask<T> {
    /**
     * 数组两端的填充长度，用于避免缓存行伪共享
     */
    private static final int TASKS_PAD = 32;
    /**
     * 实际存储任务的数组，两端包含TASKS_PAD位的填充
     */
    private final T[] tasks;
    /**
     * 该批次的最大容量
     */
    @Getter
    private final int capacity;
    /**
     * 当前已填充的任务数量，使用Padding避免伪共享
     */
    private final Padding.PaddingLong size = new Padding.PaddingLong(0);
    /**
     * 标记该批次任务是否已被消费完成，初始为true表示可被重用
     */
    private final Padding.PaddingBoolean completed = new Padding.PaddingBoolean(true);
    /**
     * 等待此批次完成的线程引用，用于完成后的LockSupport.unpark通知
     */
    private final Padding.PaddingThread waitingThread = new Padding.PaddingThread(null);

    @SuppressWarnings("unchecked")
    public BatchTask(int capacity) {
        this.tasks = (T[]) new Object[capacity + 2 * TASKS_PAD];
        this.capacity = capacity;
    }

    /**
     * 重置任务计数，为下一批填充做准备（非线程安全）
     */
    public void reset() {
        this.size.value = 0;
    }

    /**
     * 向当前批次添加一个任务（非线程安全）
     */
    public void put(T task) {
        int currentIndex = (int) this.size.value;
        this.tasks[currentIndex + TASKS_PAD] = task;
        this.size.value = currentIndex + 1;
    }

    /**
     * 获取当前批次的任务迭代器（非线程安全）
     */
    public Iterator<T> getIterator() {
        return new Iterator<T>() {
            private int currentIndex = 0;

            @Override
            public boolean hasNext() {
                return currentIndex < size.value;
            }

            @Override
            public T next() {
                return tasks[currentIndex++ + TASKS_PAD];
            }
        };
    }

    /**
     * 使用给定的消费者逐个处理批次中的所有任务，处理完毕后标记为完成
     */
    public void handle(Consumer<T> singleTaskConsumer) {
        try {
            Iterator<T> iter = this.getIterator();
            while (iter.hasNext()) {
                singleTaskConsumer.accept(iter.next());
            }
        } finally {
            this.setCompleted(true);
        }
    }

    public boolean getCompleted() {
        return completed.value;
    }

    /**
     * 设置完成标志，若完成则唤醒等待的生产者线程。
     */
    public void setCompleted(boolean completed) {
        this.completed.value = completed;
        if (completed) {
            Thread waiting = this.waitingThread.value;
            if (null != waiting) {
                LockSupport.unpark(waiting);
            }
        }
    }

    public void setWaitingThread(Thread waitingThread) {
        this.waitingThread.value = waitingThread;
    }
}
