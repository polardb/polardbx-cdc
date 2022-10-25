/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_CHECK_CONSISTENCY_AFTER_EACH_APPLY;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_CHECK_FASTSQL_FORMAT_RESULT;
import static com.aliyun.polardbx.binlog.util.FastSQLConstant.FEATURES;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class ConsistencyChecker {

    private static final Gson GSON = new GsonBuilder().create();
    private static final JdbcTemplate polarxTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
    private static final String cdcPhyTableName = getCdcPhyTableName();

    private final TopologyManager topologyManager;
    private final PolarDbXLogicTableMeta polarDbXLogicTableMeta;
    private final PolarDbXStorageTableMeta polarDbXStorageTableMeta;
    private final PolarDbXTableMetaManager polarDbXTableMetaManager;
    private final String storageInstId;

    public ConsistencyChecker(TopologyManager topologyManager, PolarDbXLogicTableMeta polarDbXLogicTableMeta,
                              PolarDbXStorageTableMeta polarDbXStorageTableMeta,
                              PolarDbXTableMetaManager polarDbXTableMetaManager, String storageInstId) {
        this.topologyManager = topologyManager;
        this.polarDbXLogicTableMeta = polarDbXLogicTableMeta;
        this.polarDbXStorageTableMeta = polarDbXStorageTableMeta;
        this.polarDbXTableMetaManager = polarDbXTableMetaManager;
        this.storageInstId = storageInstId;
    }

    private static String getCdcPhyTableName() {
        List<Map<String, Object>> list = polarxTemplate.queryForList("show topology from __cdc__.__cdc_ddl_record__");
        return list.get(0).get("TABLE_NAME").toString();
    }

    private static String renameTo(String sql) {
        if (StringUtils.isNotBlank(sql)) {
            SQLStatementParser parser =
                SQLParserUtils.createSQLStatementParser(sql, DbType.mysql, FEATURES);
            SQLStatement stmt = parser.parseStatementList().get(0);

            if (stmt instanceof MySqlRenameTableStatement) {
                MySqlRenameTableStatement renameTableStatement = (MySqlRenameTableStatement) stmt;
                for (MySqlRenameTableStatement.Item item : renameTableStatement.getItems()) {
                    //CN只支持一次Rename一张表，直接返回即可
                    return SQLUtils.normalize(item.getTo().getSimpleName());
                }
            }
        }
        return "";
    }

    public void checkLogicAndPhysicalConsistency(String tso, DDLRecord record) {
        boolean needCheckConsistency = DynamicApplicationConfig.getBoolean(META_CHECK_CONSISTENCY_AFTER_EACH_APPLY);
        if (needCheckConsistency) {
            TopologyRecord r = GSON.fromJson(record.getMetaInfo(), TopologyRecord.class);
            if ("DROP_DATABASE".equals(record.getSqlKind())) {
                checkForDropDatabase(tso, record);
            } else if ("DROP_TABLE".equals(record.getSqlKind())) {
                checkForDropTable(tso, record);
            } else if ("RENAME_TABLE".equals(record.getSqlKind())) {
                String tableName = renameTo(record.getDdlSql());
                compareForOneLogicTable(tso, record.getSchemaName(), tableName, r != null);
            } else if (StringUtils.isNotEmpty(record.getTableName())) {
                //针对online modify ddl，打标之后，物理表和逻辑表的结构会出现不一致，但最终是一致的(打标之后物理表会有清理动作)，此处跳过判断
                if (isAlterWithOMC(record.getDdlSql())) {
                    log.info("skip consistency check for alter ddl sql with omc");
                    return;
                }
                compareForOneLogicTable(tso, record.getSchemaName(), record.getTableName(), r != null);
            }

            boolean checkFastsql = DynamicApplicationConfig.getBoolean(META_CHECK_FASTSQL_FORMAT_RESULT);

            String reformatDDL = "";
            try {
                List<SQLStatement> statements =
                    SQLUtils.parseStatements(record.getDdlSql(), DbType.mysql, FastSQLConstant.FEATURES);
                reformatDDL = statements.get(0).toString();
                SQLUtils.parseStatements(reformatDDL, DbType.mysql, FastSQLConstant.FEATURES);
            } catch (Exception e) {
                log.error("org ddl : " + record.getDdlSql());
                log.error("after reformat ddl : " + reformatDDL);
                log.error("reformat parse DDL failed  : ", e);
                if (checkFastsql) {
                    throw new PolardbxException(e);
                }
            }
        }
    }

    public void checkTopologyConsistencyWithOrigin(String tso, DDLRecord ddlRecord) {
        //校验topology的一致性
        boolean needCheckConsistency = DynamicApplicationConfig.getBoolean(META_CHECK_CONSISTENCY_AFTER_EACH_APPLY);
        if (!needCheckConsistency) {
            return;
        }

        if (CreateDropTableWithExistFilter.shouldIgnore(ddlRecord.getDdlSql(), ddlRecord.getId(),
            ddlRecord.getJobId())) {
            return;
        }

        String metaStr = polarxTemplate.queryForObject("/!+TDDL:node(0)*/select meta_info from __cdc___000000." +
            cdcPhyTableName + " where id = " + ddlRecord.getId(), String.class);

        if (StringUtils.isNotBlank(metaStr)) {
            TopologyRecord r = GSON.fromJson(metaStr, TopologyRecord.class);
            if (r == null) {
                return;
            }

            if (r.getLogicTableMeta() != null) {
                String renameTo = renameTo(ddlRecord.getDdlSql());
                Pair<LogicMetaTopology.LogicDbTopology, LogicMetaTopology.LogicTableMetaTopology> pair =
                    topologyManager.getTopology(ddlRecord.getSchemaName(),
                        StringUtils.isNotBlank(renameTo) ? renameTo : ddlRecord.getTableName());
                LogicMetaTopology.LogicTableMetaTopology srcTopology = r.getLogicTableMeta();
                LogicMetaTopology.LogicTableMetaTopology destTopology = pair.getValue();

                boolean result = false;
                if (destTopology != null) {
                    result =
                        StringUtils.equalsIgnoreCase(srcTopology.getTableName(), destTopology.getTableName()) &&
                            (srcTopology.getTableType() == destTopology.getTableType()) &&
                            comparePhyTableTopology(srcTopology.getPhySchemas(), destTopology.getPhySchemas());
                }

                if (!result) {
                    throw new PolardbxException(
                        String.format("check table topology failed, tso is %s, metaStr is %s, origin is %s, "
                                + "dest is %s.", tso, metaStr, JSONObject.toJSONString(srcTopology),
                            JSONObject.toJSONString(destTopology)));
                }
            } else if (r.getLogicDbMeta() != null) {
                LogicMetaTopology.LogicDbTopology srcTopology = r.getLogicDbMeta();
                LogicMetaTopology.LogicDbTopology destTopology = getTopologyBySchema(ddlRecord.getSchemaName());
                boolean result = StringUtils.equalsIgnoreCase(srcTopology.getSchema(), destTopology.getSchema()) &&
                    comparePhyDbTopology(srcTopology.getPhySchemas(), destTopology.getPhySchemas());
                if (!result) {
                    throw new PolardbxException(
                        String.format("check db topology failed, tso is %s, metaStr is %s, origin is %s, dest is %s.",
                            tso, metaStr, JSONObject.toJSONString(srcTopology), JSONObject.toJSONString(destTopology)));
                }
            }
        }
    }

    private void checkForDropDatabase(String tso, DDLRecord record) {
        LogicMetaTopology.LogicDbTopology logicDbTopology = topologyManager.getTopology(record.getSchemaName());
        if (logicDbTopology != null) {
            String message = String.format("check consistency failed, schema has been dropped but topology "
                + "still there, schema is %s, tso is %s", record.getSchemaName(), tso);
            throw new PolardbxException(message);
        }
    }

    private void checkForDropTable(String tso, DDLRecord record) {
        Pair<LogicMetaTopology.LogicDbTopology, LogicMetaTopology.LogicTableMetaTopology> pair =
            topologyManager.getTopology(record.getSchemaName(), record.getTableName());
        if (pair.getRight() != null) {
            String message = String.format("check consistency failed, table has been dropped but topology "
                    + "still there, schema is %s, table is %s, tso is %s", record.getSchemaName(), record.getTableName(),
                tso);
            throw new PolardbxException(message);
        }
    }

    private void compareForOneLogicTable(String tso, String logicSchema, String logicTable,
                                         boolean createPhyIfNotExist) {
        Pair<LogicMetaTopology.LogicDbTopology, LogicMetaTopology.LogicTableMetaTopology> pair =
            topologyManager.getTopology(logicSchema, logicTable);
        LogicMetaTopology.LogicTableMetaTopology logicTableMetaTopology = pair.getRight();
        if (logicTableMetaTopology == null) {
            throw new PolardbxException(
                String.format("logic table meta topology should not be null, logicSchema %s, logicTable %s ,tso %s.",
                    logicSchema, logicTable, tso));
        }

        if (logicTableMetaTopology.getPhySchemas() != null) {
            for (LogicMetaTopology.PhyTableTopology phyTableTopology : logicTableMetaTopology.getPhySchemas()) {
                if (storageInstId.equals(phyTableTopology.getStorageInstId())) {
                    for (String table : phyTableTopology.getPhyTables()) {
                        compareLogicWithPhysicalTable(tso, logicSchema, logicTable,
                            phyTableTopology.getSchema(), table, createPhyIfNotExist);
                    }
                }
            }
        }
    }

    private boolean isAlterWithOMC(String sql) {
        return StringUtils.contains(sql.toLowerCase(), "ALGORITHM=OMC".toLowerCase());
    }

    private void compareLogicWithPhysicalTable(String tso, String logicSchemaName, String logicTableName,
                                               String phySchemaName, String phyTableName, boolean createPhyIfNotExist) {
        //get table meta
        TableMeta logicDimTableMeta = polarDbXLogicTableMeta.findDistinctPhy(logicSchemaName, logicTableName);
        if (logicDimTableMeta == null) {
            logicDimTableMeta = polarDbXLogicTableMeta.find(logicSchemaName, logicTableName);
        }
        TableMeta phyDimTableMeta =
            createPhyIfNotExist ? polarDbXTableMetaManager.findPhyTable(phySchemaName, phyTableName) :
                polarDbXStorageTableMeta.find(phySchemaName, phyTableName);

        // check meta if null
        if (logicDimTableMeta == null) {
            String message = String.format("check consistency failed, can`t find logic table meta %s:%s, with tso %s.",
                logicSchemaName, logicTableName, tso);
            throw new PolardbxException(message);
        }
        if (phyDimTableMeta == null) {
            String message = String.format("check consistency failed, can`t find phy table meta %s:%s, with tso %s.",
                phySchemaName, phyTableName, tso);
            throw new PolardbxException(message);
        }

        //compare table meta
        List<String> logicDimColumns = logicDimTableMeta.getFields().stream()
            .map(f -> SQLUtils.normalize(f.getColumnName().toLowerCase())).collect(Collectors.toList());
        List<String> phyDimColumns = phyDimTableMeta.getFields().stream()
            .map(f -> SQLUtils.normalize(f.getColumnName().toLowerCase())).collect(Collectors.toList());
        boolean result = logicDimColumns.equals(phyDimColumns);
        if (!result) {
            String message = String.format(
                "check consistency failed, logic and phy table meta is not consistent, logicSchema %s,"
                    + " logicTable %s , phySchema %s, phyTable %s,logicColumns %s, phy Columns %s, tso %s.",
                logicSchemaName, logicTableName, phySchemaName, phyTableName, logicDimColumns, phyDimColumns, tso);
            throw new PolardbxException(message);
        }
    }

    private boolean comparePhyDbTopology(List<LogicMetaTopology.PhyDbTopology> src,
                                         List<LogicMetaTopology.PhyDbTopology> dest) {
        if (src.size() != dest.size()) {
            return false;
        }

        boolean result = true;
        for (int i = 0; i < src.size(); i++) {
            LogicMetaTopology.PhyDbTopology t1 = src.get(i);
            LogicMetaTopology.PhyDbTopology t2 = dest.get(i);
            result &= StringUtils.equalsIgnoreCase(t1.getSchema(), t2.getSchema()) && StringUtils
                .equalsIgnoreCase(t1.getGroup(), t2.getGroup()) && StringUtils
                .equalsIgnoreCase(t1.getStorageInstId(), t2.getStorageInstId());
        }
        return result;
    }

    private LogicMetaTopology.LogicDbTopology getTopologyBySchema(String schemaName) {
        LogicMetaTopology.LogicDbTopology topology = topologyManager.getTopology(schemaName);

        LogicMetaTopology.LogicDbTopology result = new LogicMetaTopology.LogicDbTopology();
        result.setSchema(topology.getSchema());
        result.setCharset(topology.getCharset());
        result.setPhySchemas(topology.getPhySchemas());
        return result;
    }

    private boolean comparePhyTableTopology(List<LogicMetaTopology.PhyTableTopology> src,
                                            List<LogicMetaTopology.PhyTableTopology> dest) {
        if (src.size() != dest.size()) {
            return false;
        }

        boolean result = true;
        for (int i = 0; i < src.size(); i++) {
            LogicMetaTopology.PhyTableTopology t1 = src.get(i);
            LogicMetaTopology.PhyTableTopology t2 = dest.get(i);
            result &= StringUtils.equalsIgnoreCase(t1.getSchema(), t2.getSchema()) && StringUtils
                .equalsIgnoreCase(t1.getGroup(), t2.getGroup()) && StringUtils
                .equalsIgnoreCase(t1.getStorageInstId(), t2.getStorageInstId());
            result &= (t1.getPhyTables().size() == t2.getPhyTables().size());
            if (!result) {
                return result;
            }
            for (int j = 0; j < t1.getPhyTables().size(); j++) {
                String p1 = t1.getPhyTables().get(j);
                String p2 = t2.getPhyTables().get(j);
                result &= StringUtils.equalsIgnoreCase(p1, p2);
            }
        }
        return result;
    }
}
