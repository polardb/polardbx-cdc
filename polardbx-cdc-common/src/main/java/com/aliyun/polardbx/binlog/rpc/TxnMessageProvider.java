/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
