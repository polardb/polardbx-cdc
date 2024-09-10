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
package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_CHECK_CONSISTENCY_ENABLED;
import static com.aliyun.polardbx.binlog.cdc.meta.MetaFilter.isSupportApply;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class ConsistencyChecker {
    public interface OriginMetaSupplier {
        String get(Long ddlRecordId);
    }

    private final TopologyManager topologyManager;
    private final PolarDbXLogicTableMeta polarDbXLogicTableMeta;
    private final PolarDbXTableMetaManager polarDbXTableMetaManager;
    private final String storageInstId;
    private final AtomicLong checkCount;
    private OriginMetaSupplier originMetaSupplier;

    public ConsistencyChecker(TopologyManager topologyManager, PolarDbXLogicTableMeta polarDbXLogicTableMeta,
                              PolarDbXTableMetaManager polarDbXTableMetaManager, String storageInstId) {
        this.topologyManager = topologyManager;
        this.polarDbXLogicTableMeta = polarDbXLogicTableMeta;
        this.polarDbXTableMetaManager = polarDbXTableMetaManager;
        this.storageInstId = storageInstId;
        this.checkCount = new AtomicLong();
        this.originMetaSupplier = i -> {
            final JdbcTemplate polarxTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
            List<Map<String, Object>> list =
                polarxTemplate.queryForList("show topology from __cdc__.__cdc_ddl_record__");
            String cdcPhyTableName = list.get(0).get("TABLE_NAME").toString();
            return polarxTemplate.queryForObject("/!+TDDL:node(0)*/select meta_info from __cdc___000000." +
                cdcPhyTableName + " where id = " + i, String.class);
        };
    }

    private static String renameTo(String sql) {
        if (StringUtils.isNotBlank(sql)) {
            SQLStatement stmt = parseSQLStatement(sql);

            if (stmt instanceof MySqlRenameTableStatement) {
                MySqlRenameTableStatement renameTableStatement = (MySqlRenameTableStatement) stmt;
                for (MySqlRenameTableStatement.Item item : renameTableStatement.getItems()) {
                    //CN只支持一次Rename一张表，直接返回即可
                    return SQLUtils.normalizeNoTrim(item.getTo().getSimpleName());
                }
            }
        }
        return "";
    }

    public void checkLogicAndPhysicalConsistency(String tso, DDLRecord record) {
        boolean needCheckConsistency = DynamicApplicationConfig.getBoolean(META_BUILD_CHECK_CONSISTENCY_ENABLED);
        if (needCheckConsistency && isSupportApply(record)) {
            TopologyRecord r = JSONObject.parseObject(record.getMetaInfo(), TopologyRecord.class);
            if ("DROP_DATABASE".equals(record.getSqlKind())) {
                checkForDropDatabase(tso, record);
            } else if ("DROP_TABLE".equals(record.getSqlKind())) {
                checkForDropTable(tso, record);
            } else if ("RENAME_TABLE".equals(record.getSqlKind())) {
                String tableName = renameTo(record.getDdlSql());
                compareForOneLogicTable(tso, record.getSchemaName(), tableName, r != null);
            } else if (StringUtils.isNotEmpty(record.getTableName())) {
                //针对online modify ddl，打标之后，物理表和逻辑表的结构会出现不一致，但最终是一致的(打标之后物理表会有清理动作)，此处跳过判断
                if (isAlterWithOMC(record)) {
                    log.info("skip consistency check for alter ddl sql with omc");
                    return;
                }
                compareForOneLogicTable(tso, record.getSchemaName(), record.getTableName(), r != null);
            }

            String reformatDDL = "";
            try {
                SQLStatement statements = parseSQLStatement(record.getDdlSql());
                reformatDDL = statements.toString();
                parseSQLStatement(reformatDDL);
            } catch (Exception e) {
                log.error("org ddl : " + record.getDdlSql());
                log.error("after reformat ddl : " + reformatDDL);
                log.error("reformat parse DDL failed  : ", e);
                throw new PolardbxException(e);
            }
        }

        checkCount.incrementAndGet();
    }

    public void checkTopologyConsistencyWithOrigin(String tso, DDLRecord ddlRecord) {
        //校验topology的一致性
        boolean needCheckConsistency = DynamicApplicationConfig.getBoolean(META_BUILD_CHECK_CONSISTENCY_ENABLED);
        if (!needCheckConsistency) {
            return;
        }

        if (!isSupportApply(ddlRecord)) {
            return;
        }

        if (CreateDropTableWithExistFilter.shouldIgnore(ddlRecord.getDdlSql(), ddlRecord.getId(),
            ddlRecord.getJobId(), ddlRecord.getExtInfo())) {
            return;
        }

        String metaStr = originMetaSupplier.get(ddlRecord.getId());
        if (StringUtils.isNotBlank(metaStr)) {
            TopologyRecord r = JSONObject.parseObject(metaStr, TopologyRecord.class);
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

        checkCount.incrementAndGet();
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

    private boolean isAlterWithOMC(DDLRecord record) {
        boolean result = StringUtils.contains(record.getDdlSql().toLowerCase(), "ALGORITHM=OMC".toLowerCase()) ||
            (record.getExtInfo() != null && record.getExtInfo().getUseOMC() != null && record.getExtInfo().getUseOMC());
        if (result) {
            log.info("meet a ddl sql with OMC " + JSONObject.toJSONString(record));
        }
        return result;
    }

    private void compareLogicWithPhysicalTable(String tso, String logicSchemaName, String logicTableName,
                                               String phySchemaName, String phyTableName, boolean createPhyIfNotExist) {
        //get table meta
        TableMeta logicDimTableMeta = polarDbXLogicTableMeta.find(logicSchemaName, logicTableName);
        TableMeta phyDimTableMeta = polarDbXTableMetaManager.findPhyTable(
            phySchemaName, phyTableName, createPhyIfNotExist);
        TableMeta distinctPhyDimTableMeta = polarDbXLogicTableMeta.findDistinctPhy(logicSchemaName, logicTableName);

        // compare table meta
        boolean result1 = compareOne(logicDimTableMeta, phyDimTableMeta, logicSchemaName, logicTableName, tso,
            phySchemaName, phyTableName, 0);
        boolean result2 = distinctPhyDimTableMeta != null &&
            compareOne(distinctPhyDimTableMeta, phyDimTableMeta, logicSchemaName, logicTableName, tso,
                phySchemaName, phyTableName, 1);

        if (distinctPhyDimTableMeta != null && !result2) {
            String message = String.format(
                "check consistency failed, distinct physical table meta and phy table meta is not consistent, "
                    + "logicSchema %s, logicTable %s , phySchema %s, phyTable %s,logicColumns %s, phy Columns %s, "
                    + "tso %s.", logicSchemaName, logicTableName, phySchemaName, phyTableName,
                parseColumns(distinctPhyDimTableMeta), parseColumns(phyDimTableMeta), tso);
            throw new PolardbxException(message);
        }

        if (!result1 && !result2) {
            String message = String.format(
                "check consistency failed, logic and phy table meta is not consistent, logicSchema %s,"
                    + " logicTable %s , phySchema %s, phyTable %s,logicColumns %s, phy Columns %s, tso %s.",
                logicSchemaName, logicTableName, phySchemaName, phyTableName, parseColumns(logicDimTableMeta),
                parseColumns(phyDimTableMeta), tso);
            throw new PolardbxException(message);
        }
    }

    private boolean compareOne(
        TableMeta logicDimTableMeta, TableMeta phyDimTableMeta, String logicSchemaName,
        String logicTableName, String tso, String phySchemaName, String phyTableName, int mode) {
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
        if (!StringUtils.equalsAnyIgnoreCase(logicDimTableMeta.getCharset(), phyDimTableMeta.getCharset())) {
            String message =
                String.format(
                    "check consistency failed, logic charset %s[%s] not equals physical charset %s[%s], with tso %s.",
                    logicDimTableMeta.getTable(), logicDimTableMeta.getCharset(), phyDimTableMeta.getTable(),
                    phyDimTableMeta.getCharset(), tso);
            throw new PolardbxException(message);
        }
        List<Triple<String, String, String>> logicDimColumns = parseColumns(logicDimTableMeta);
        List<Triple<String, String, String>> phyDimColumns = parseColumns(phyDimTableMeta);
        boolean result = logicDimColumns.equals(phyDimColumns);
        if (!result & mode == 0) {
            // 允许出现逻辑表和物理表列序不一致，如逻辑sql为: alter table add column xxx after yyy.
            // 也允许出现物理表比逻辑表多列，如OMC、生成列等场景，会先进行DDL打标，再清理物理表的相关列
            // 但不允许物理表比逻辑表少列，也不允许逻辑表和物理表列类型不一致

            Map<String, String> phyColumnMap =
                phyDimColumns.stream()
                    .collect(Collectors.toMap(Triple::getLeft,
                        triple -> triple.getMiddle()
                            + triple.getRight()));
            logicDimColumns.forEach(p -> {
                if (!(phyColumnMap.containsKey(p.getLeft()) && (p.getMiddle() + p.getRight()).equals(
                    phyColumnMap.get(p.getLeft())))) {
                    String message = String.format(
                        "check consistency failed, logic table meta and phy table meta is not consistent, logicSchema %s, "
                            + "logicTable %s , phySchema %s, phyTable %s, logicColumns %s, phy Columns %s, tso %s.",
                        logicSchemaName, logicTableName, phySchemaName, phyTableName,
                        parseColumns(logicDimTableMeta), parseColumns(phyDimTableMeta), tso);
                    throw new PolardbxException(message);
                }
            });
        }
        return result;
    }

    private List<Triple<String, String, String>> parseColumns(TableMeta tableMeta) {
        return tableMeta.getFields().stream()
            .map(f -> Triple.of(SQLUtils.normalize(f.getColumnName().toLowerCase()), f.getColumnType().toLowerCase(),
                StringUtils.lowerCase(f.getCharset())))
            .collect(Collectors.toList());
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

    public void setOriginMetaSupplier(
        OriginMetaSupplier originMetaSupplier) {
        this.originMetaSupplier = originMetaSupplier;
    }

    public OriginMetaSupplier getOriginMetaSupplier() {
        return originMetaSupplier;
    }

    public AtomicLong getCheckCount() {
        return checkCount;
    }
}
