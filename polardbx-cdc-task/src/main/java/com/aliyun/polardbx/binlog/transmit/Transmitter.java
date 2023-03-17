/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
