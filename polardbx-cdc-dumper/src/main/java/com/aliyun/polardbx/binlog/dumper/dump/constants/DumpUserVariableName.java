/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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

    /**
     * 用来区分是 Columnar 还是其它 binlog dump 请求
     * 后续如有需要可以添加新的 enum 类型，并新增对应的处理逻辑
     */
    public static final String CLIENT_TYPE = "client_type";
}
