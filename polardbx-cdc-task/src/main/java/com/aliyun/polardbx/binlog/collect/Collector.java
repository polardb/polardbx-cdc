/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.collect;

import com.aliyun.polardbx.binlog.merge.HeartBeatWindowAware;
import com.aliyun.polardbx.binlog.protocol.TxnToken;

/**
 *
 **/
public interface Collector extends HeartBeatWindowAware {

    /**
     * 启动collector
     */
    void start();

    /**
     * 停止collector，释放资源
     */
    void stop();

    /**
     * 将TxnToken压入Collector的缓冲区，如果缓冲区已满则进行阻塞等待
     *
     * @param token 事务令牌
     */
    void push(TxnToken token);

    /**
     * 获取正在排队的事务令牌个数
     *
     * @return 令牌个数
     */
    long getQueuedSize();

}
