/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.protocol.TxnMessage;

import java.util.List;

/**
 * Created by ziyang.lb
 **/
public interface TxnMessageReceiver {

    /**
     * 收到TxnMessage时进行回调
     *
     * @param messages 事务消息
     */
    void onReceived(List<TxnMessage> messages) throws InterruptedException;
}
