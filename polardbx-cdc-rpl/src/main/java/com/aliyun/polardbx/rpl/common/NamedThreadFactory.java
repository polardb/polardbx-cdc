/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Actually this class should be provided by some com.aliyun.polardbx.common
 * utils.
 *
 * @author Moshan on 14-10-28.
 */
public class NamedThreadFactory implements ThreadFactory {

    final AtomicInteger threadNumber = new AtomicInteger();
    final String namePrefix;

    public NamedThreadFactory(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread result = new Thread(r, namePrefix + threadNumber.getAndIncrement());
        result.setDaemon(true);
        return result;
    }
}