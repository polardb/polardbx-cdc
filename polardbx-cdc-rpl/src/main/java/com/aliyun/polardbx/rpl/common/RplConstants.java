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

import com.aliyun.polardbx.binlog.util.ServerConfigUtil;

/**
 * @author shicai.xsc 2020/11/30 16:44
 * @since 5.0.0.0
 */
public class RplConstants {

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
    public static final String ROLLBACK_STRING = "-2";

    /**
     * Polar-x implicit ID
     */
    public static final String POLARX_IMPLICIT_ID = "_drds_implicit_id_";

    public static final String ASYNC_DDL_HINTS = "/!TDDL:cmd_extra(PURE_ASYNC_DDL_MODE=TRUE)*/";

    public static final String RDS_IMPLICIT_ID = "__#alibaba_rds_row_id#__";

    /**
     * Http call
     */
    public static final String CHECK_AND_RUN_TASKS = "checkAndRunLocalTasks";

    public static final String CHECK_CHANNEL_RUNNING = "checkChannelRunning";

    /**
     * Server-id
     */
    public static final long MY_POLARX_SERVER_ID = Long.valueOf(ServerConfigUtil.getGlobalVar("SERVER_ID").toString());

    public static final long SERVER_ID_NULL = 0;

    /**
     * RPC command params
     */
    public static final String MASTER_HOST = "MASTER_HOST";

    public static final String MASTER_USER = "MASTER_USER";

    public static final String MASTER_PASSWORD = "MASTER_PASSWORD";

    public static final String MASTER_PORT = "MASTER_PORT";

    public static final String MASTER_INST_ID = "MASTER_INST_ID";

    public static final String MASTER_LOG_FILE = "MASTER_LOG_FILE";

    public static final String MASTER_LOG_POS = "MASTER_LOG_POS";

    public static final String MASTER_LOG_TIME_SECOND = "MASTER_LOG_TIME_SECOND";

    public static final String IGNORE_SERVER_IDS = "IGNORE_SERVER_IDS";

    public static final String SOURCE_HOST_TYPE = "SOURCE_HOST_TYPE";

    public static final String WRITE_TYPE = "WRITE_TYPE";

    public static final String COMPARE_ALL = "COMPARE_ALL";

    public static final String INSERT_ON_UPDATE_MISS = "INSERT_ON_UPDATE_MISS";

    public static final String CONFLICT_STRATEGY = "CONFLICT_STRATEGY";

    public static final String RDS = "RDS";
    public static final String POLARDBX = "POLARDBX";

    public static final String CHANNEL = "CHANNEL";

    public static final String SUB_CHANNEL = "SUB_CHANNEL";

    public static final String MODE = "MODE";

    public static final String IMAGE_MODE = "IMAGE";

    public static final String STREAM_GROUP = "STREAM_GROUP";

    public static final String WRITE_SERVER_ID = "WRITE_SERVER_ID";

    public static final String INCREMENTAL_MODE = "INCREMENTAL";

    public static final String FORCE_CHANGE = "FORCE_CHANGE";

    public static final String ENABLE_DYNAMIC_MASTER_HOST = "ENABLE_DYNAMIC_MASTER_HOST";

    public static final String ENABLE_SRC_LOGICAL_META_SNAPSHOT = "ENABLE_SRC_LOGICAL_META_SNAPSHOT";

    public static final String TRIGGER_DYNAMIC_MASTER_HOST = "TRIGGER_DYNAMIC_MASTER_HOST";

    public static final String TRIGGER_AUTO_POSITION = "TRIGGER_AUTO_POSITION";

    public static final String REPLICATE_DO_DB = "REPLICATE_DO_DB";

    public static final String REPLICATE_IGNORE_DB = "REPLICATE_IGNORE_DB";

    public static final String REPLICATE_DO_TABLE = "REPLICATE_DO_TABLE";

    public static final String REPLICATE_IGNORE_TABLE = "REPLICATE_IGNORE_TABLE";

    public static final String REPLICATE_WILD_DO_TABLE = "REPLICATE_WILD_DO_TABLE";

    public static final String REPLICATE_WILD_IGNORE_TABLE = "REPLICATE_WILD_IGNORE_TABLE";

    public static final String REPLICATE_REWRITE_DB = "REPLICATE_REWRITE_DB";

    public static final String REPLICATE_SKIP_TSO = "REPLICATE_SKIP_TSO";

    public static final String REPLICATE_SKIP_UNTIL_TSO = "REPLICATE_SKIP_UNTIL_TSO";

    public static final String REPLICATE_ENABLE_DDL = "REPLICATE_ENABLE_DDL";

    public static final String RUNNING = "RUNNING";

    // todo by jiyue 输出错误 并且最好带上tso
    public static final String LAST_ERROR = "LAST_ERROR";

    public static final String IS_ALL = "IS_ALL";

    public static final long INC_CATCH_UP_SECOND_THRESHOLD = 60;

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

    public static final String RPL_TASK_LEADER_LOCK_PREFIX = "RplTaskEngine_";

    public static final String RPL_FULL_VALID_DB = "dbName";

    public static final String RPL_FULL_VALID_TB = "tbName";

    public static final String RPL_FULL_VALID_MODE = "mode";
}
