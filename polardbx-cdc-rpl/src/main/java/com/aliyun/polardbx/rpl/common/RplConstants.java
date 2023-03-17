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
package com.aliyun.polardbx.rpl.common;

import com.aliyun.polardbx.binlog.ServerConfigUtil;

/**
 * @author shicai.xsc 2020/11/30 16:44
 * @since 5.0.0.0
 */
public class RplConstants {

    /**
     * canal 中，将binlog产生的时间写入option中
     */
    public static final String BINLOG_EVENT_OPTION_TIMESTAMP = "message_event_option_timestamp";

    /**
     * canal 中，将当前binlog position 写入option
     */
    public static final String BINLOG_EVENT_OPTION_POSITION = "message_event_option_offset";
    /**
     * 将source schema name 写入option
     */
    public static final String BINLOG_EVENT_OPTION_SOURCE_SCHEMA = "source_schema";
    /**
     * 将source table name 写入option
     */
    public static final String BINLOG_EVENT_OPTION_SOURCE_TABLE = "source_table";

    /**
     * canal ，事务tid
     */
    public static final String BINLOG_EVENT_OPTION_T_ID = "message_event_option_tid";

    public static final String LINE_SEPERATOR = System.getProperty("line.separator");

    public static final long LOG_MAX_LENGTH = 10000;

    public static final String SEMICOLON = ";";

    public static final String COMMA = ",";

    public static final String DOT = ".";

    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static final String TASK_ID = "taskId";
    public static final String TASK_NAME = "taskName";

    public static final String EXTRACTOR_DEFAULT_CHARSET = "utf8";

    public static final int DEFAULT_FETCH_BATCH_SIZE = 5000;

    /**
     * default java heap memory for task
     */
    public static final int DEFAULT_MEMORY_SIZE = 2048;

    /**
     * default java heap memory for full copy tasks
     */
    public static final int DEFAULT_MEMORY_SIZE_FOR_FULL_COPY = 3072;

    /**
     * extract 并行度 for full copy and full validation
     */
    public static final int PRODUCER_DEFAULT_PARALLEL_COUNT = 20;

    public static final int CONSUMER_DEFAULT_PARALLEL_COUNT = 64;

    /**
     * binlogExtractor按照时间回溯时，使用-2拼装回溯的位点
     */
    public final static String ROLLBACK_STRING = "-2";

    public final static int TASK_KEEP_ALIVE_INTERVAL_SECONDS = 300;

    public final static int HTTP_PORT = 10183;

    public final static String DOWNLOAD_URL = "http://%s:" + 10183 + "/rpl/download?file=%s";

    public final static String SUCCEED = "succeed";

    public final static String TRUE = "true";

    public final static String FALSE = "false";

    /**
     * Polar-x user name
     */
    public final static String POLARX_ROOT_USER = "polardbx_root";

    public final static String POLARX_SLAVE_USER = "polardbx_slave_inner";

    /**
     * Polar-x implicit ID
     */
    public final static String POLARX_IMPLICIT_ID = "_drds_implicit_id_";

    public final static String ASYNC_DDL = "PURE_ASYNC_DDL_MODE=TRUE";

    public final static String RDS_IMPLICIT_ID = "__#alibaba_rds_row_id#__";

    /**
     * Http call
     */
    public final static String CHECK_AND_RUN_TASKS = "checkAndRunLocalTasks";

    public final static String CHECK_CHANNEL_RUNNING = "checkChannelRunning";

    /**
     * Server-id
     */
    public final static long MY_POLARX_SERVER_ID = Long.valueOf(ServerConfigUtil.getGlobalVar("SERVER_ID").toString());

    public final static long SERVER_ID_NULL = 0;

    /**
     * RPC command params
     */
    public final static String MASTER_HOST = "MASTER_HOST";

    public final static String MASTER_USER = "MASTER_USER";

    public final static String MASTER_PASSWORD = "MASTER_PASSWORD";

    public final static String MASTER_PORT = "MASTER_PORT";

    public final static String MASTER_SERVER_ID = "MASTER_SERVER_ID";

    public final static String MASTER_LOG_FILE = "MASTER_LOG_FILE";

    public final static String MASTER_LOG_POS = "MASTER_LOG_POS";

    public final static String IGNORE_SERVER_IDS = "IGNORE_SERVER_IDS";

    public final static String SOURCE_HOST_TYPE = "SOURCE_HOST_TYPE";

    public final static String CHANNEL = "CHANNEL";

    public final static String MODE = "MODE";

    public final static String IMAGE_MODE = "IMAGE";
    public final static String INCREMENTAL_MODE = "INCREMENTAL";

    public final static String REPLICATE_DO_DB = "REPLICATE_DO_DB";

    public final static String REPLICATE_IGNORE_DB = "REPLICATE_IGNORE_DB";

    public final static String REPLICATE_DO_TABLE = "REPLICATE_DO_TABLE";

    public final static String REPLICATE_IGNORE_TABLE = "REPLICATE_IGNORE_TABLE";

    public final static String REPLICATE_WILD_DO_TABLE = "REPLICATE_WILD_DO_TABLE";

    public final static String REPLICATE_WILD_IGNORE_TABLE = "REPLICATE_WILD_IGNORE_TABLE";

    public final static String REPLICATE_REWRITE_DB = "REPLICATE_REWRITE_DB";

    public final static String RUNNING = "RUNNING";

    public final static String LAST_ERROR = "LAST_ERROR";

    public final static String SKIP_COUNTER = "SKIP_COUNTER";

    public final static String REPLICATE_IGNORE_SERVER_IDS = "REPLICATE_IGNORE_SERVER_IDS";

    public final static String IS_ALL = "IS_ALL";

    public static final String NULL_COLUMN = "##NULL##";

    public static final String CHECK_RESULT_SEPERATOR = "#$#";
    public static final String KEY_GROUP_SEPERATOR = "#&#";

    public static final int WORKER_KEEP_ALIVE_SECONDS = 120;

    public static final long INC_CATCH_UP_SECOND_THRESHOLD = 30;

    public static final int FALLBACK_INTERVAL_SECONDS = 60;

    public static final int FAILURE = 0;
    public static final int SUCCESS = 1;

    public static final int FAILURE_CODE = 500;
    public static final int SUCCESS_CODE = 200;

    public static final long ERROR_FSMID = -1;

    public static final int FINISH_PERCENTAGE = 100;

    public static final int NOT_FINISH = 0;
    public static final int FINISH = 1;

    public static final int LOG_NO_COMMIT = 0;
    public static final int LOG_END_COMMIT = 1;
    public static final int LOG_ALL_COMMIT = 2;

    public static final int KEY_TRACE_IDX = 2;
    public static final int KEY_DRDS_IDX = 0;
    public static final String DRDS_HINT_PREFIX = "DRDS";
    /**
     * 精确匹配 or 模糊匹配
     */
    public static final String BINLOG_EVENT_OPTION_SQL_CALLBACK_TYPE = "message_event_option_sql_callback_type";

    public static final String BINLOG_EVENT_OPTION_SQL_QUERY_LOG = "message_event_option_sql_query_log";

    public static final String BINLOG_EVENT_OPTION_SHOULD_STOP = "message_event_option_should_stop";

    public static final String TRANSACTION_END_COMMENT = "\n-- Transaction end;\n";

    public static final String FLASH_BACK_ROOT_PATH = "SQL_FLASH_BACK";

    public static final String FLASH_BACK_RESULT_DIR = FLASH_BACK_ROOT_PATH + "/{0}/";

    public static final String FLASH_BACK_PARTIAL_PREFIX = FLASH_BACK_ROOT_PATH + "/{0}/result-{1}";

    public static final String FLASH_BACK_PARTIAL_RESULT_FILE = FLASH_BACK_PARTIAL_PREFIX + "-{2}.sql";

    public static final String FLASH_BACK_COMBINE_RESULT_FILE = FLASH_BACK_ROOT_PATH + "/{0}/result.sql";

    public static final int INSERT_MODE_SIMPLE_INSERT_OR_DELETE = 1;

    public static final int INSERT_MODE_INSERT_IGNORE = 2;

    public static final int INSERT_MODE_REPLACE = 3;
}
