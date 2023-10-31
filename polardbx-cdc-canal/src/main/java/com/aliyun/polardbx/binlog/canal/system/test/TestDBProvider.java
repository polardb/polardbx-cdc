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
package com.aliyun.polardbx.binlog.canal.system.test;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.system.AbstractSystemDBProvider;
import com.aliyun.polardbx.binlog.testing.spring.TestComponent;

@TestComponent
public class TestDBProvider extends AbstractSystemDBProvider {

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
    private final TableMeta ddlTableMeta;
    private final TableMeta instructionTableMeta;
    private final TableMeta heartbeatTableMeta;
    private final TableMeta globalTxLogTableMeta;

    public TestDBProvider() {
        MemoryTableMeta memoryTableMeta =
            new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, LOGIC_SCHEMA, CREATE_CDC_DDL_RECORD_TABLE, null);
        memoryTableMeta.apply(null, LOGIC_SCHEMA, CREATE_CDC_INSTRUCTION_TABLE, null);
        memoryTableMeta.apply(null, LOGIC_SCHEMA, CREATE_DRDS_GLOBAL_TX_LOG, null);
        memoryTableMeta.apply(null, LOGIC_SCHEMA, CREATE_CDC_HEARTBEAT_TABLE, null);
        ddlTableMeta = memoryTableMeta.find(LOGIC_SCHEMA, DRDS_CDC_DDL_RECORD);
        instructionTableMeta = memoryTableMeta.find(LOGIC_SCHEMA, DRDS_CDC_INSTRUCTION);
        heartbeatTableMeta = memoryTableMeta.find(LOGIC_SCHEMA, DRDS_CDC_HEARTBEAT);
        globalTxLogTableMeta = memoryTableMeta.find(LOGIC_SCHEMA, DRDS_GLOBAL_TX_LOG);
    }

    @Override
    public boolean ddlRecordTable(String db, String table) {
        return db.startsWith(LOGIC_SCHEMA) && table.startsWith(DRDS_CDC_DDL_RECORD) && !db.endsWith(SINGLE_KEY_WORLD);
    }

    @Override
    public boolean instructionTable(String db, String table) {
        return db.startsWith(LOGIC_SCHEMA) && table.startsWith(DRDS_CDC_INSTRUCTION) && !db.endsWith(SINGLE_KEY_WORLD);
    }

    @Override
    public boolean heartbeatTable(String db, String phyTable) {
        return db.startsWith(LOGIC_SCHEMA) && phyTable.startsWith(DRDS_CDC_HEARTBEAT) && !db.endsWith(SINGLE_KEY_WORLD);
    }

    @Override
    public TableMeta getDdlTableMeta() {
        return ddlTableMeta;
    }

    @Override
    public TableMeta getInstructionTableMeta() {
        return instructionTableMeta;
    }

    @Override
    public TableMeta getHeartbeatTableMeta() {
        return heartbeatTableMeta;
    }

    @Override
    public TableMeta getGlobalTxLogTableMeta() {
        return globalTxLogTableMeta;
    }
}
