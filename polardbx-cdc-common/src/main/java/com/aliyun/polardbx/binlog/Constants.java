/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

public abstract class Constants {
    public static final String MDC_STREAM_SEQ = "stream_seq";
    public static final String MDC_THREAD_LOGGER_KEY = "thread_logger";
    public static final String MDC_THREAD_LOGGER_VALUE_BINLOG_BACKUP = "binlog_backup";
    public static final String MDC_THREAD_LOGGER_VALUE_BINLOG_CLEAN = "binlog_clean";
    public static final String MDC_THREAD_LOGGER_VALUE_BINLOG_DUMP = "binlog_dump";
    public static final String MDC_THREAD_LOGGER_VALUE_BINLOG_SYNC = "binlog_sync";
    public static final String MDC_RPL_FULL_VALID_TASK_ID_KEY = "fullValidTaskId";

    public static final String RELAY_DATA_FORCE_CLEAN_FLAG = "force_clean_flag";
}
