/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit;

import com.aliyun.polardbx.binlog.collect.message.MessageEvent;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;

/**
 * Created by ziyang.lb
 **/
public interface Transmitter {

    void start();

    void stop();

    void transmit(MessageEvent messageEvent);

    boolean checkTSO(String startTSO, TxnOutputStream<DumpReply> outputStream, boolean keepWaiting)
        throws InterruptedException;

    void dump(String startTSO, TxnOutputStream<DumpReply> outputStream) throws InterruptedException;
}
