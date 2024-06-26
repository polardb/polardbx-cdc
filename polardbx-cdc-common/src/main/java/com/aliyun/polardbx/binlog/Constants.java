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
