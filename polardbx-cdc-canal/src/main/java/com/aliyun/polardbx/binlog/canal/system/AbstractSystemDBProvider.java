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

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.aliyun.polardbx.binlog.InstructionType;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogParser;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;

public abstract class AbstractSystemDBProvider implements ISystemDBProvider {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSystemDBProvider.class);

    @Override
    public boolean isDrdsImplicitId(String colName) {
        return DRDS_IMPLICIT_ID.equalsIgnoreCase(SQLUtils.normalize(colName));
    }

    @Override
    public boolean isSys(String db) {
        return db.startsWith(LOGIC_SCHEMA);
    }

    @Override
    public boolean isMysql(String db) {
        return MYSQL_SCHEMA.equalsIgnoreCase(db);
    }

    @Override
    public boolean isMetaDb(String db) {
        return META_DB_SCHEMA.equalsIgnoreCase(db);
    }

    @Override
    public boolean isCdcSingleGroup(String groupName) {
        return CDC_SINGLE_GROUP_NAME.equalsIgnoreCase(groupName);
    }

    @Override
    public boolean isGlobalTxTable(String tableName) {
        return DRDS_GLOBAL_TX_LOG.equalsIgnoreCase(tableName);
    }

    @Override
    public boolean isPolarxGlobalTrxLogTable(String tableName) {
        return POLARX_GLOBAL_TRX_LOG.equalsIgnoreCase(tableName);
    }

    @Override
    public boolean isDrdsRedoLogTable(String tableName) {
        return DRDS_REDO_LOG.equalsIgnoreCase(tableName);
    }

    @Override
    public boolean isAndorDatabase(String database) {
        return "andor_qatest_polarx1".equalsIgnoreCase(database) || "andor_qatest_polarx2".equalsIgnoreCase(database);
    }

    @Override
    public InstructionCommand parseInstructionCommand(WriteRowsLogEvent wr) {
        BinlogParser parser = new BinlogParser();
        try {
            TableMeta instructionTableMeta = getInstructionTableMeta();
            parser.parse(instructionTableMeta, wr, "utf8");
            String type = (String) parser.getField(INSTRUCTION_FIELD_INSTRUCTION_TYPE);
            String instructionId = (String) parser.getField(INSTRUCTION_FIELD_INSTRUCTION_ID);
            String content = (String) parser.getField(INSTRUCTION_FIELD_INSTRUCTION_CONTENT);
            String clusterIdName = "cluster_id";
            InstructionType insType = InstructionType.valueOf(type);
            TableMeta.FieldMeta clusterIdField = instructionTableMeta.getFieldMetaByName(clusterIdName, true);
            if (insType == InstructionType.CdcStart &&
                !instructionId.contains(":") &&
                clusterIdField != null) {
                // 这里处理一下兼容性问题
                String clusterId = (String) parser.getField(clusterIdName);
                if (StringUtils.isNotBlank(clusterId) && !StringUtils.equals(clusterId, "0")) {
                    instructionId = clusterId + ":" + instructionId;
                    logger.warn("process compatibility instruction id: " + instructionId);
                }
            }
            return new InstructionCommand(instructionId, insType, content);
        } catch (UnsupportedEncodingException e) {
            throw new PolardbxException("parse cdc instruction error!", e);
        }
    }

    @Override
    public TxGlobalEvent parseTxGlobalEvent(WriteRowsLogEvent rowsLogEvent, String charset) {
        BinlogParser binlogParser = new BinlogParser();
        try {
            binlogParser.parse(getGlobalTxLogTableMeta(), rowsLogEvent, charset);
        } catch (Exception e) {
            throw new PolardbxException("parse tx global error!", e);
        }
        Long txGlobalTid = Long.valueOf(String.valueOf(binlogParser.getField(GLOBAL_TX_LOG_FIELD_TXID)));
        Long txGlobalTso = null;
        Serializable commitTs = binlogParser.getField(GLOBAL_TX_LOG_FIELD_COMMIT_TS);
        if (commitTs != null) {
            txGlobalTso = Long.valueOf((String) commitTs);
        }
        return new TxGlobalEvent(txGlobalTid, txGlobalTso);
    }
}
