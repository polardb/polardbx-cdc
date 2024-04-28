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
package com.aliyun.polardbx.binlog.dumper.dump.constants;

/**
 * @author yudong
 * @since 2023/12/20 17:18
 **/
public class DumpUserVariableName {

    /**
     * binlog dump客户端在连接到server之后可以通过 SET @master_binlog_checksum 设置这个变量
     * 该变量控制server发送的fake rotate event是否带checksum
     * 其他类型的binlog event是否带checksum由server本身决定(m_event_checksum_alg)
     * heartbeat event是否带checksum应该和m_event_checksum_alg保持一致
     */
    public static final String MASTER_BINLOG_CHECKSUM = "master_binlog_checksum";

    /**
     * A heartbeat is needed before waiting for more events, if some events are skipped.
     * This is needed so that the slave can increase master_log_pos correctly.
     * Or if waiting for new events.
     */
    public static final String MASTER_HEARTBEAT_PERIOD = "master_heartbeat_period";
}
