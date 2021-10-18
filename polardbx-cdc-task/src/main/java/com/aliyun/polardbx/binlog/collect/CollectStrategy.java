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
