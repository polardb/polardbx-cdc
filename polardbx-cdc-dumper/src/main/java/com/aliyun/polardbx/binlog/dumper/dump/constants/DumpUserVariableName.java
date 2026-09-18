/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
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

    /**
     * 用于和show processlist进行关联
     */
    public static final String TRACE_ID = "trace_id";
    public static final String PROCESS_ID = "id";
    /**
     * 下游数据库的server_id,用于双向回环复制场景中对符合server_id的事件进行过滤
     */
    public static final String IGNORE_SERVER_IDS = "ignore_server_ids";
    /**
     * 控制 server_id 过滤时是否同时过滤 DDL 事件
     */
    public static final String SERVER_ID_FILTER_DDL = "server_id_filter_ddl";
    /**
     * 需要过滤的表名
     */
    public static final String TABLE_IGNORE = "table_ignore";
    /**
     * 允许的表名
     */
    public static final String TABLE_ALLOW = "table_allow";
    /**
     * 是否需要过滤由归档表产生的删除事件
     */
    public static final String ARCHIVE_IGNORE = "archive_ignore";
    /**
     * 是否需要过滤ROWS_QUERY_EVENT
     */
    public static final String ROWS_QUERY_IGNORE = "rows_query_ignore";
    /**
     * 是否使用flag的方式过滤事件
     */
    public static final String IGNORE_BY_FLAG = "ignore_by_flag";

    /**
     * 发起binlog dump请求的用户
     */
    public static final String USER = "user";

    public static final String INST_ID = "inst_id";
}
