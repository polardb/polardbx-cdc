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

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.TableMetaTSDB;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class DynamicSystemDBProvider extends AbstractSystemDBProvider {

    private static final Logger logger = LoggerFactory.getLogger(DynamicSystemDBProvider.class);

    private static final String SHOW_CREATE_TABLE = "show create table `%s`";
    private static final String SHOW_TOPOLOGY = "show topology from `%s`";
    @Resource(name = "polarxJdbcTemplate")
    private JdbcTemplate template;

    private MemoryTableMeta memoryTableMeta;
    private String physicalHeartbeatTable;
    private String physicalInstructionTable;
    private String physicalDdlTable;

    private volatile boolean isInit = false;

    @Override
    public boolean ddlRecordTable(String db, String table) {
        checkState();
        return db.startsWith(LOGIC_SCHEMA) && StringUtils.equalsIgnoreCase(table, physicalDdlTable) && !db
            .endsWith(SINGLE_KEY_WORLD);
    }

    @Override
    public boolean instructionTable(String db, String table) {
        checkState();
        return db.startsWith(LOGIC_SCHEMA) && StringUtils.equalsIgnoreCase(table, physicalInstructionTable) && !db
            .endsWith(SINGLE_KEY_WORLD);
    }

    @Override
    public boolean heartbeatTable(String db, String phyTable) {
        checkState();
        return db.startsWith(LOGIC_SCHEMA) && StringUtils.equalsIgnoreCase(phyTable, physicalHeartbeatTable) && !db
            .endsWith(SINGLE_KEY_WORLD);
    }

    private void initTableMeta(String table) {
        template.query(String.format(SHOW_CREATE_TABLE, table), rs -> {
            String ddlSql = rs.getString("Create Table");
            logger.info("init system table : " + table + " : " + ddlSql);
            memoryTableMeta.apply(TableMetaTSDB.INIT_POSITION, LOGIC_SCHEMA, ddlSql, null);
        });
    }

    private String queryPhysicalTable(String table) {
        return template.query(String.format(SHOW_TOPOLOGY, table), rs -> {
            if (rs.next()) {
                return rs.getString("TABLE_NAME");
            }
            return null;
        });
    }

    private void postInit() {
        memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(TableMetaTSDB.INIT_POSITION, LOGIC_SCHEMA, CREATE_DRDS_GLOBAL_TX_LOG, null);
        initTableMeta(DRDS_CDC_DDL_RECORD);
        initTableMeta(DRDS_CDC_INSTRUCTION);
        initTableMeta(DRDS_CDC_HEARTBEAT);
        physicalHeartbeatTable = queryPhysicalTable(DRDS_CDC_HEARTBEAT);
        logger.info("logic : " + DRDS_CDC_HEARTBEAT + "  , physical: " + physicalHeartbeatTable);
        physicalDdlTable = queryPhysicalTable(DRDS_CDC_DDL_RECORD);
        logger.info("logic : " + DRDS_CDC_DDL_RECORD + "  , physical: " + physicalDdlTable);
        physicalInstructionTable = queryPhysicalTable(DRDS_CDC_INSTRUCTION);
        logger.info("logic : " + DRDS_CDC_INSTRUCTION + "  , physical: " + physicalInstructionTable);
    }

    private void checkState() {
        if (isInit) {
            return;
        }
        synchronized (this) {
            if (isInit) {
                return;
            }
            postInit();
            isInit = true;
        }
    }

    @Override
    public TableMeta getDdlTableMeta() {
        checkState();
        return memoryTableMeta.find(LOGIC_SCHEMA, DRDS_CDC_DDL_RECORD);
    }

    @Override
    public TableMeta getInstructionTableMeta() {
        checkState();
        return memoryTableMeta.find(LOGIC_SCHEMA, DRDS_CDC_INSTRUCTION);
    }

    @Override
    public TableMeta getHeartbeatTableMeta() {
        checkState();
        return memoryTableMeta.find(LOGIC_SCHEMA, DRDS_CDC_HEARTBEAT);
    }

    @Override
    public TableMeta getGlobalTxLogTableMeta() {
        checkState();
        return memoryTableMeta.find(LOGIC_SCHEMA, DRDS_GLOBAL_TX_LOG);
    }

}
