/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolUtil {

    private static final int MAX_POOL_SIZE = 2048;
    private static final long ALIVE_TIME = 5 * 1000L;
    private static final int QUEUE_SIZE = 2048;

    public static ThreadPoolExecutor createExecutorWithFixedNum(int coreSize, String name) {
        return new ThreadPoolExecutor(
            coreSize,
            coreSize,
            ALIVE_TIME,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new NamedThreadFactory(name));
    }

    public static ThreadPoolExecutor createExecutor(int minSize, int maxSize, String name) {
        return new ThreadPoolExecutor(
            minSize,
            maxSize,
            ALIVE_TIME,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new NamedThreadFactory(name));
    }

}





