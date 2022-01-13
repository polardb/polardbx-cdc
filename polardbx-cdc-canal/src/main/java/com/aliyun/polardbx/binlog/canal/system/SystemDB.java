/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.canal.system;

import com.aliyun.polardbx.binlog.canal.binlog.BinlogParser;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.ConsoleTableMetaTSDB;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.error.PolardbxException;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;

public class SystemDB {

    public static final String DDL_RECORD_FIELD_SQL_KIND = "SQL_KIND";
    public static final String DDL_RECORD_FIELD_DDL_SQL = "DDL_SQL";
    public static final String DDL_RECORD_FIELD_SCHEMA_NAME = "SCHEMA_NAME";
    public static final String DDL_RECORD_FIELD_TABLE_NAME = "TABLE_NAME";
    public static final String DDL_RECORD_FIELD_META_INFO = "META_INFO";
    public static final String DDL_RECORD_FIELD_VISIBILITY = "VISIBILITY";
    public static final String DDL_RECORD_EXT = "EXT";

    public static final String INSTRUCTION_FIELD_INSTRUCTION_TYPE = "INSTRUCTION_TYPE";
    public static final String INSTRUCTION_FIELD_INSTRUCTION_CONTENT = "INSTRUCTION_CONTENT";
    public static final String INSTRUCTION_FIELD_INSTRUCTION_ID = "INSTRUCTION_ID";
    public static final String DDL_RECORD_FIELD_EXT = "EXT";
    public static final String LOGIC_SCHEMA = "__cdc__";
    // cdc虽然是系统表，但不会进行注册操作，因为其需要通过逻辑层的ddl功能进行创建，如果注册的话，create sql会被DDL模块忽略掉，导致无法创建
    public static final String DRDS_CDC_DDL_RECORD = "__cdc_ddl_record__";
    public static final String DRDS_CDC_INSTRUCTION = "__cdc_instruction__";
    public static final String DRDS_CDC_HEARTBEAT = "__cdc_heartbeat__";
    public static final String DRDS_GLOBAL_TX_LOG = "__drds_global_tx_log";
    public static final String DRDS_REDO_LOG = "__drds_redo_log";
    public static final String GLOBAL_TX_LOG_FIELD_COMMIT_TS = "COMMIT_TS";
    public static final String GLOBAL_TX_LOG_FIELD_TYPE = "TYPE";
    public static final String GLOBAL_TX_LOG_FIELD_TXID = "TXID";

    public static final String SINGLE_KEY_WORLD = "__single";
    public static final String CDC_SINGLE_GROUP_NAME = "__CDC___SINGLE_GROUP";

    /**
     * DRDS隐藏主键
     */
    public static final String DRDS_IMPLICIT_ID = "_drds_implicit_id_";

    private static final SystemDB instance = new SystemDB();
    private final TableMeta ddlTableMeta;
    private final TableMeta instructionTableMeta;
    private final TableMeta heartbeatTableMeta;
    private final TableMeta globalTxLogTableMeta;
    /**
     * 需注意"SCHEMA_NAME列"和"TABLE_NAME列"的长度不能小于meta db中"db_info表"和"tables表"中对应列的长度
     */
    String CREATE_CDC_DDL_RECORD_TABLE = String.format(
        "CREATE TABLE IF NOT EXISTS `%s` (\n" + "  `ID` BIGINT(20) NOT NULL auto_increment,\n"
            + "  `JOB_ID` BIGINT(20)  DEFAULT NULL,\n" + "  `SQL_KIND` VARCHAR(50) NOT NULL,\n"
            + "  `SCHEMA_NAME` VARCHAR(200) NOT NULL,\n" + "  `TABLE_NAME`  VARCHAR(200) DEFAULT NULL,\n"
            + "  `GMT_CREATED` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" + "  `DDL_SQL`  TEXT NOT NULL,\n"
            + "  `META_INFO` TEXT DEFAULT NULL,\n" + "  `VISIBILITY` BIGINT(10) NOT NULL,\n"
            + "  `EXT` TEXT DEFAULT NULL,\n" + "  PRIMARY KEY (`ID`)\n" + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n",
        DRDS_CDC_DDL_RECORD);
    /**
     * CDC通用指令表
     */
    String CREATE_CDC_INSTRUCTION_TABLE = String.format(
        "CREATE TABLE IF NOT EXISTS `%s` (\n" + "  `ID` BIGINT(20) NOT NULL auto_increment,\n"
            + "  `INSTRUCTION_TYPE` VARCHAR(50) NOT NULL,\n" + "  `INSTRUCTION_CONTENT` MEDIUMTEXT NOT NULL,\n"
            + "  `GMT_CREATED` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
            + "  `INSTRUCTION_ID` VARCHAR(50) NOT NULL,\n" + "  PRIMARY KEY (`ID`),\n"
            + "  UNIQUE KEY `uk_instruction_id_type` (`INSTRUCTION_TYPE`,`INSTRUCTION_ID`) \n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 BROADCAST\n", DRDS_CDC_INSTRUCTION);
    String CREATE_CDC_HEARTBEAT_TABLE = String.format(
        "CREATE TABLE IF NOT EXISTS `%s` (`id` bigint(20) auto_increment primary key , sname varchar(20) ,gmt_modified timestamp) broadcast",
        DRDS_CDC_HEARTBEAT);
    String CREATE_DRDS_GLOBAL_TX_LOG = String.format("CREATE TABLE `%s` (\n" + "  `TXID` bigint(20) NOT NULL,\n"
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

    private SystemDB() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null);
        memoryTableMeta.apply(ConsoleTableMetaTSDB.INIT_POSITION, LOGIC_SCHEMA, CREATE_CDC_DDL_RECORD_TABLE, null);
        memoryTableMeta.apply(ConsoleTableMetaTSDB.INIT_POSITION, LOGIC_SCHEMA, CREATE_CDC_INSTRUCTION_TABLE, null);
        memoryTableMeta.apply(ConsoleTableMetaTSDB.INIT_POSITION, LOGIC_SCHEMA, CREATE_DRDS_GLOBAL_TX_LOG, null);
        memoryTableMeta.apply(ConsoleTableMetaTSDB.INIT_POSITION, LOGIC_SCHEMA, CREATE_CDC_HEARTBEAT_TABLE, null);
        ddlTableMeta = memoryTableMeta.find(LOGIC_SCHEMA, DRDS_CDC_DDL_RECORD);
        instructionTableMeta = memoryTableMeta.find(LOGIC_SCHEMA, DRDS_CDC_INSTRUCTION);
        heartbeatTableMeta = memoryTableMeta.find(LOGIC_SCHEMA, DRDS_CDC_HEARTBEAT);
        globalTxLogTableMeta = memoryTableMeta.find(LOGIC_SCHEMA, DRDS_GLOBAL_TX_LOG);
    }

    public static boolean isDrdsImplicitId(String colName) {
        return DRDS_IMPLICIT_ID.equalsIgnoreCase(colName);
    }

    public static SystemDB getInstance() {
        return instance;
    }

    public static boolean isHeartbeat(String db, String phyTable) {
        return db.startsWith(LOGIC_SCHEMA) && phyTable.startsWith(DRDS_CDC_HEARTBEAT) && !db.endsWith(SINGLE_KEY_WORLD);
    }

    public static boolean isSys(String db) {
        return db.startsWith(LOGIC_SCHEMA);
    }

    public static boolean isInstruction(String db, String table) {
        return db.startsWith(LOGIC_SCHEMA) && table.startsWith(DRDS_CDC_INSTRUCTION) && !db.endsWith(SINGLE_KEY_WORLD);
    }

    public static boolean isLogicDDL(String db, String table) {
        return db.startsWith(LOGIC_SCHEMA) && table.startsWith(DRDS_CDC_DDL_RECORD) && !db.endsWith(SINGLE_KEY_WORLD);
    }

    public static boolean acceptSysEvent(String db, String table) {
        return (db.startsWith(LOGIC_SCHEMA) && !db.endsWith(SINGLE_KEY_WORLD)) && (
            table.startsWith(DRDS_CDC_INSTRUCTION) || table.startsWith(DRDS_CDC_DDL_RECORD) || table.startsWith(
                DRDS_CDC_HEARTBEAT));
    }

    public static boolean isCdcSingleGroup(String groupName) {
        return CDC_SINGLE_GROUP_NAME.equalsIgnoreCase(groupName);
    }

    public static boolean isGlobalTxTable(String tableName) {
        return DRDS_GLOBAL_TX_LOG.equalsIgnoreCase(tableName);
    }

    public TableMeta getDdlTableMeta() {
        return ddlTableMeta;
    }

    public TableMeta getInstructionTableMeta() {
        return instructionTableMeta;
    }

    public TableMeta getHeartbeatTableMeta() {
        return heartbeatTableMeta;
    }

    public TableMeta getGlobalTxLogTableMeta() {
        return globalTxLogTableMeta;
    }

    public InstructionCommand parseInstructionCommand(WriteRowsLogEvent wr) {
        TableMapLogEvent tm = wr.getTable();
        BinlogParser parser = new BinlogParser();
        try {
            parser.parse(instructionTableMeta, wr, "utf8");
            String type = (String) parser.getField(INSTRUCTION_FIELD_INSTRUCTION_TYPE);
            String instructionId = (String) parser.getField(INSTRUCTION_FIELD_INSTRUCTION_ID);
            String content = (String) parser.getField(SystemDB.INSTRUCTION_FIELD_INSTRUCTION_CONTENT);
            InstructionType insType = InstructionType.valueOf(type);
            InstructionCommand command = new InstructionCommand(instructionId, insType, content);
            return command;
        } catch (UnsupportedEncodingException e) {
            throw new PolardbxException("parse cdc instruction error!", e);
        }
    }

    public TxGlobalEvent parseTxGlobalEvent(WriteRowsLogEvent rowsLogEvent, String charset) {
        BinlogParser binlogParser = new BinlogParser();
        try {
            binlogParser.parse(SystemDB.getInstance().getGlobalTxLogTableMeta(), rowsLogEvent, charset);
        } catch (Exception e) {
            throw new PolardbxException("parse tx global error!", e);
        }
        Long txGlobalTid = Long.valueOf(String.valueOf(binlogParser.getField(SystemDB.GLOBAL_TX_LOG_FIELD_TXID)));
        Long txGlobalTso = null;
        Serializable commitTs = binlogParser.getField(SystemDB.GLOBAL_TX_LOG_FIELD_COMMIT_TS);
        if (commitTs != null) {
            txGlobalTso = Long.valueOf((String) commitTs);
        }
        return new TxGlobalEvent(txGlobalTid, txGlobalTso);
    }
}
