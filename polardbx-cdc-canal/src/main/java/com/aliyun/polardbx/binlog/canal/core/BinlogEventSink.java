/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.canal.core;

import java.util.List;

import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;

/**
 * 数据解析之后的回调机制
 *
 * @author agapple 2017年7月19日 下午2:13:43
 * @since 3.2.5
 */
public interface BinlogEventSink {

    /**
     * 提交数据
     */
    boolean sink(List<MySQLDBMSEvent> events);

    /**
     * 通知异常
     */
    boolean sink(Throwable e);
}
