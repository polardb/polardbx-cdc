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
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.Data;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PERSIST_ENABLED;

/**
 * created by ziyang.lb
 **/
@Data
public class PersistConfig {
    //持久化相关
    private boolean forcePersist = false;
    private boolean supportPersist = DynamicApplicationConfig.getBoolean(RPL_PERSIST_ENABLED);
    private long transPersistCheckIntervalMs = 1000;
    private double transPersistMemoryThreshold = 0.85;
    private long transPersistRangeMaxItemSize = 10000;
    private long transPersistRangeMaxByteSize = 10 * 1024 * 1024;
    private long eventPersistThreshold = 1024 * 1024;
}
