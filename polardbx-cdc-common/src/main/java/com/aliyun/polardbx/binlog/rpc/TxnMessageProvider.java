/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.protocol.DumpReply;

/**
 * Created by ziyang.lb
 **/
public interface TxnMessageProvider {

    /**
     * 检查指定的tso是否存在
     */
    boolean checkTSO(String startTSO, TxnOutputStream<DumpReply> outputStream, boolean keepWaiting)
        throws InterruptedException;

    /**
     * 从指定的tso开始，消费binlog
     */
    void dump(String startTSO, TxnOutputStream<DumpReply> outputStream) throws InterruptedException;

    /**
     * 根据指定的tso，对provider进行重启
     */
    void restart(String startTSO);
}
