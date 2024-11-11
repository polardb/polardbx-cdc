/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

/**
 * 定时器
 *
 * @author yudong
 * @since 2023/12/20 18:48
 **/
public class Timer {

    // 定时时间，单位：毫秒
    private final long period;

    private long start;

    public Timer(long period) {
        this.period = period;
        start = System.currentTimeMillis();
    }

    public boolean isTimeout() {
        long now = System.currentTimeMillis();
        if (now - start > period) {
            start = now;
            return true;
        }
        return false;
    }

    public void reset() {
        start = System.currentTimeMillis();
    }
}
