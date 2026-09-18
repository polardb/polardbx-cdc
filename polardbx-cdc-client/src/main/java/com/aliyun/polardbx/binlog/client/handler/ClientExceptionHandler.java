/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.handler;

import com.aliyun.polardbx.binlog.client.ClientHealthChecker;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Setter;

/**
 * 客户端异常处理器，用于在多线程解析管道中传递异常。
 * <p>
 * 工作原理：异步处理线程（如batch线程、parser线程池）捕获到异常后，
 * 通过 setThrowable() 设置异常；主解析循环在每次迭代时调用 check() 检测并抛出异常。
 */
@Setter
public class ClientExceptionHandler implements ClientHealthChecker {
    /**
     * 异步线程中发生的异常，为null表示没有异常
     */
    private Throwable throwable;

    /**
     * 检查是否有异步异常，若有则包装为PolardbxException抛出。
     * 主解析循环中会周期性调用此方法。
     */
    @Override
    public void check() {
        if (throwable != null) {
            throw new PolardbxException(throwable);
        }
    }
}
