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
package com.aliyun.polardbx.binlog.canal.system;

import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;

public interface ISystemDBProvider {
    String DDL_RECORD_FIELD_DDL_ID = "ID";
    String DDL_RECORD_FIELD_JOB_ID = "JOB_ID";
    String DDL_RECORD_FIELD_SQL_KIND = "SQL_KIND";
    String DDL_RECORD_FIELD_DDL_SQL = "DDL_SQL";
    String DDL_RECORD_FIELD_SCHEMA_NAME = "SCHEMA_NAME";
    String DDL_RECORD_FIELD_TABLE_NAME = "TABLE_NAME";
    String DDL_RECORD_FIELD_META_INFO = "META_INFO";
    String DDL_RECORD_FIELD_VISIBILITY = "VISIBILITY";
    String DDL_RECORD_EXT = "EXT";
    String INSTRUCTION_FIELD_INSTRUCTION_TYPE = "INSTRUCTION_TYPE";
    String INSTRUCTION_FIELD_INSTRUCTION_CONTENT = "INSTRUCTION_CONTENT";
    String INSTRUCTION_FIELD_INSTRUCTION_ID = "INSTRUCTION_ID";
    String INSTRUCTION_FIELD_CLUSTER_ID = "CLUSTER_ID";
    String DDL_RECORD_FIELD_EXT = "EXT";
    String LOGIC_SCHEMA = "__cdc__";

    /*
     * cdc虽然是系统表，但不会进行注册操作，因为其需要通过逻辑层的ddl功能进行创建，如果注册的话，create sql会被DDL模块忽略掉，导致无法创建
     */
    String DRDS_CDC_DDL_RECORD = "__cdc_ddl_record__";
    String DRDS_CDC_INSTRUCTION = "__cdc_instruction__";
    String DRDS_CDC_HEARTBEAT = "__cdc_heartbeat__";
    String DRDS_GLOBAL_TX_LOG = "__drds_global_tx_log";
    String POLARX_GLOBAL_TRX_LOG = "polarx_global_trx_log";
    String DRDS_REDO_LOG = "__drds_redo_log";
    String GLOBAL_TX_LOG_FIELD_COMMIT_TS = "COMMIT_TS";
    String GLOBAL_TX_LOG_FIELD_TYPE = "TYPE";
    String GLOBAL_TX_LOG_FIELD_TXID = "TXID";
    String SINGLE_KEY_WORLD = "__single";
    String CDC_SINGLE_GROUP_NAME = "__CDC___SINGLE_GROUP";

    String DRDS_IMPLICIT_ID = "_drds_implicit_id_";
    String AUTO_LOCAL_INDEX_PREFIX = "_local_";

    String CREATE_DRDS_GLOBAL_TX_LOG =
        String.format("CREATE TABLE `%s` (\n" + "  `TXID` bigint(20) NOT NULL,\n"
            + "  `START_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
            + "  `TYPE` enum('TCC','XA','BED','TSO','HLC') NOT NULL,\n"
            + "  `STATE` enum('PREPARE','COMMIT','ROLLBACK','SUCCEED','ABORTED') NOT NULL,\n"
            + "  `RETRIES` int(11) NOT NULL DEFAULT '0',\n"
            + "  `COMMIT_TS` bigint(20) DEFAULT NULL,\n"
            + "  `PARTICIPANTS` blob,\n"
            + "  `TIMEOUT` timestamp NULL DEFAULT NULL,\n"
            + "  `SERVER_ADDR` varchar(21) NOT NULL,\n"
            + "  `CONTEXT` text NOT NULL,\n" + "  `ERROR` text,\n"
            + "  PRIMARY KEY (`TXID`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8", DRDS_GLOBAL_TX_LOG);

    boolean ddlRecordTable(String db, String table);

    boolean instructionTable(String db, String table);

    boolean heartbeatTable(String db, String phyTable);

    TableMeta getDdlTableMeta();

    TableMeta getInstructionTableMeta();

    TableMeta getHeartbeatTableMeta();

    TableMeta getGlobalTxLogTableMeta();

    InstructionCommand parseInstructionCommand(WriteRowsLogEvent wr);

    TxGlobalEvent parseTxGlobalEvent(WriteRowsLogEvent rowsLogEvent, String charset);

    boolean isDrdsImplicitId(String colName);

    boolean isSys(String db);

    boolean isCdcSingleGroup(String groupName);

    boolean isGlobalTxTable(String tableName);

    boolean isPolarxGlobalTrxLogTable(String tableName);

    boolean isDrdsRedoLogTable(String tableName);
}
