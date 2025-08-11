/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;

/**
 * Created by ziyang.lb
 **/
public interface TxnMessageProvider {
    /**
     * 从指定的tso开始，消费binlog
     */
    void dump(DumpRequest request, TxnOutputStream<DumpReply> outputStream) throws InterruptedException;
}
