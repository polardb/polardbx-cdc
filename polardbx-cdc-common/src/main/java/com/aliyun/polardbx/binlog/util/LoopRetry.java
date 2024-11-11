/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yudong
 */
public abstract class LoopRetry {

    private final IntervalStrategy intervalStrategy;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public LoopRetry(IntervalStrategy intervalStrategy) {
        this.intervalStrategy = intervalStrategy;
    }

    /**
     * 实现重试逻辑，重试成功返回true，将会退出重试机制
     *
     * @return 本次执行是否成功
     */
    public abstract boolean retry();

    /**
     * 是否已经在执行重试逻辑
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * 重试几次
     *
     * @return 重试几次之后成功还是失败
     */
    public boolean loop(AtomicInteger times) {
        isRunning.set(true);
        while (times.getAndDecrement() > 0) {
            if (this.retry()) {
                isRunning.set(false);
                return true;
            }
            intervalStrategy.interval();
        }
        isRunning.set(false);
        return false;
    }

    static void doSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    public interface IntervalStrategy {

        /**
         * 重试间隔做什么
         */
        void interval();
    }

    /**
     * 重试间隔时sleep
     */
    public static class SleepIntervalStrategy implements IntervalStrategy {

        private final long sleepMs;

        public SleepIntervalStrategy(long sleepMs) {
            this.sleepMs = sleepMs;
        }

        @Override
        public void interval() {
            doSleep(sleepMs);
        }
    }

    /**
     * 重试间隔成倍递增策略
     */
    public static class IncreaseSleepIntervalStrategy implements IntervalStrategy {

        private final long sleepMs;
        private final AtomicInteger times;

        public IncreaseSleepIntervalStrategy(long sleepMs, AtomicInteger times) {
            this.sleepMs = sleepMs;
            this.times = times;
        }

        @Override
        public void interval() {
            doSleep(times.get() * sleepMs);
        }
    }

    /**
     * 重试间隔什么也不做
     */
    public static class NoneIntervalStrategy implements IntervalStrategy {

        @Override
        public void interval() {
        }
    }
}
