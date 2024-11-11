/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.collect;

import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.error.CollectException;
import com.aliyun.polardbx.binlog.merge.HeartBeatWindowAware;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.lmax.disruptor.RingBuffer;

/**
 *
 **/
public interface CollectStrategy extends HeartBeatWindowAware {

    /**
     * 启动
     */
    void start();

    /**
     * 停止，释放资源
     */
    void stop();

    /**
     * 注入缓冲区
     *
     * @param buffer ring buffer
     */
    void setRingBuffer(RingBuffer<MessageEvent> buffer);

    /**
     * 注入存储组件
     */
    void setStorage(Storage storage);

    /**
     * 策略运行期间如果有异常，通过该方法暴露异常
     *
     * @return 有异常返回异常对象，没有异常返回null
     */
    CollectException getException();

    /**
     * 获取策略类型
     */
    StrategyType getStrategyType();
}
