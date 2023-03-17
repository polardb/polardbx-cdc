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
