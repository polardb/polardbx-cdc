/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.concurrent;

import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 无锁的单生产者-单消费者(SPSC)队列。
 * <p>
 * 设计要点：
 * - 容量必须为2的幂，使用位运算代替取模操作
 * - 数组两端添加BUFFER_PAD填充，避免缓存行伪共享
 * - writeIndex/readIndex使用Padding包装，确保在不同缓存行上
 * - 通过LockSupport.park/unpark实现生产者和消费者的互相等待/唤醒
 *
 * @author yaozhili
 */
public class LockFreeQueue<T> {
    /**
     * 数组两端的填充长度，避免相邻数据的缓存行伪共享
     */
    private static final int BUFFER_PAD = 32;

    /**
     * 队列容量，必须为2的幂
     */
    private final int capacity;
    /**
     * 用于快速取模的位掩码，等于 capacity - 1
     */
    private final int mask;

    /**
     * 环形缓冲区数组，两端包含BUFFER_PAD填充
     */
    private final T[] buffer;

    /**
     * 生产者的写入索引，单调递增
     */
    private final Padding.PaddingLong writeIndex;
    /**
     * 消费者的读取索引，单调递增
     */
    private final Padding.PaddingLong readIndex;

    /**
     * 消费者线程引用，用于生产者唤醒消费者
     */
    private final Padding.PaddingThread consumerThread;
    /**
     * 生产者线程引用，用于消费者唤醒生产者
     */
    private final Padding.PaddingThread producerThread;

    @SuppressWarnings("unchecked")
    public LockFreeQueue(int capacity) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be a power of 2, but is: " + capacity);
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.buffer = (T[]) new Object[capacity + 2 * BUFFER_PAD];
        this.writeIndex = new Padding.PaddingLong(0);
        this.readIndex = new Padding.PaddingLong(0);
        this.consumerThread = new Padding.PaddingThread(null);
        this.producerThread = new Padding.PaddingThread(null);
    }

    public T put(T item) throws InterruptedException {
        return put(ignored -> item);
    }

    /**
     * 向队列中放入一个元素，支持通过函数复用已有的槽位对象，避免每次分配新对象。
     * 如果队列已满，生产者线程会被park阻塞，直到消费者消费后唤醒。
     *
     * @param itemPublisher 接受当前槽位的旧对象，返回要放入的新对象（可复用旧对象）
     */
    public T put(Function<T, T> itemPublisher) throws InterruptedException {
        long currentWrite = writeIndex.value;
        while (true) {
            if (currentWrite - readIndex.value < capacity) {
                break;
            } else {
                // queue is full, wait
                producerThread.value = Thread.currentThread();
                // check again after setting waiting thread
                if (currentWrite - readIndex.value >= capacity) {
                    LockSupport.park();
                }
                producerThread.value = null;
                if (Thread.interrupted()) {
                    throw new InterruptedException("Producer thread has been interrupted");
                }
            }
        }

        // publish this item
        T item = itemPublisher.apply(buffer[BUFFER_PAD + (int) (currentWrite & mask)]);
        buffer[BUFFER_PAD + (int) (currentWrite & mask)] = item;
        writeIndex.value = currentWrite + 1;

        // consumer may be waiting, notify it
        Thread t = consumerThread.value;
        if (t != null) {
            LockSupport.unpark(t);
        }

        return item;
    }

    public T take(boolean blockWait) throws InterruptedException {
        return take(blockWait, null);
    }

    /**
     * 从队列中取出一个元素，支持在取出前对元素进行检查。
     * 在检查完成前，该元素仍占据队列槽位，不会被生产者覆盖。
     * 例如，checker 可实现为轮询当前任务是否完成，只有完成后才把任务取出队列。
     *
     * @param blockWait 是否阻塞等待，false时队列为空直接返回null
     * @param checker 取出前的检查回调，可用于等待元素满足某些条件
     * @return 队列元素，非阻塞模式下队列为空时返回null
     */
    public T take(boolean blockWait, Consumer<T> checker) throws InterruptedException {
        long currentRead = readIndex.value;
        while (true) {
            if (writeIndex.value > currentRead) {
                break;
            } else {
                // 队列为空，等待
                if (blockWait) {
                    consumerThread.value = Thread.currentThread();
                    // 设置等待线程后再次检查，防止错过唤醒
                    if (writeIndex.value <= currentRead) {
                        LockSupport.park();
                    }
                    consumerThread.value = null;
                    if (Thread.interrupted()) {
                        throw new InterruptedException("Consumer thread has been interrupted");
                    }
                } else {
                    return null;
                }
            }
        }
        return consumeAndNotify(currentRead, checker);
    }

    /**
     * 带超时的阻塞取出。队列为空时等待，超过指定毫秒后仍无数据则返回null。
     *
     * @param timeoutMillis 最大等待时间（毫秒），<=0 时等同于非阻塞 take(false)
     * @param checker 取出前的检查回调，可为null
     * @return 队列元素，超时返回null
     */
    public T timedTake(long timeoutMillis, Consumer<T> checker) throws InterruptedException {
        if (timeoutMillis <= 0) {
            return take(false, checker);
        }
        long currentRead = readIndex.value;
        long deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (true) {
            if (writeIndex.value > currentRead) {
                break;
            } else {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    return null;
                }
                consumerThread.value = Thread.currentThread();
                // 设置等待线程后再次检查，防止错过唤醒
                if (writeIndex.value <= currentRead) {
                    LockSupport.parkNanos(remainingNanos);
                }
                consumerThread.value = null;
                if (Thread.interrupted()) {
                    throw new InterruptedException("Consumer thread has been interrupted");
                }
            }
        }
        return consumeAndNotify(currentRead, checker);
    }

    /**
     * 消费队列头部元素并唤醒可能等待的生产者。
     * take 和 timedTake 共用的后半段逻辑。
     */
    private T consumeAndNotify(long currentRead, Consumer<T> checker) {
        T value = buffer[BUFFER_PAD + ((int) (currentRead & mask))];
        if (null != checker) {
            checker.accept(value);
        }
        readIndex.value = currentRead + 1;

        // 生产者可能在等待空位，唤醒它
        Thread t = producerThread.value;
        if (t != null) {
            LockSupport.unpark(t);
        }
        return value;
    }

    public T timedTake(long timeoutMillis) throws InterruptedException {
        return timedTake(timeoutMillis, null);
    }

    public boolean isEmpty() {
        return writeIndex.value == readIndex.value;
    }

    /**
     * 返回当前队列中的元素数量（近似值，因为读写索引可能在不同时刻被读取）
     */
    public long size() {
        return writeIndex.value - readIndex.value;
    }

    /**
     * 返回当前队列剩余的元素数量（近似值，因为读写索引可能在不同时刻被读取）
     */
    public long remainingCapacity() {
        return capacity - ((int) size());
    }
}