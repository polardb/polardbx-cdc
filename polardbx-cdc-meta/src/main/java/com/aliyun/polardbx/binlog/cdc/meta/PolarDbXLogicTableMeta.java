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

package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.alibaba.polardbx.druid.sql.repository.Schema;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.jvm.JvmSnapshot;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.binlog.CommonUtils.escape;
import static com.aliyun.polardbx.binlog.util.FastSQLConstant.FEATURES;

/**
 * Created by ShuGuang,ziyang.lb
 */
public class PolarDbXLogicTableMeta extends MemoryTableMeta implements ICdcTableMeta {
    private static final Logger logger = LoggerFactory.getLogger(PolarDbXLogicTableMeta.class);
    private static final int PAGE_SIZE = 100;
    private static final Gson GSON = new GsonBuilder().create();

    private final TopologyManager topologyManager;
    private final MemoryTableMeta distinctPhyMeta = new MemoryTableMeta(logger);//保存逻辑表和物理表列序不一致的meta
    private final BinlogLogicMetaHistoryMapper binlogLogicMetaHistoryMapper = SpringContextHolder.getObject(
        BinlogLogicMetaHistoryMapper.class);

    public PolarDbXLogicTableMeta(TopologyManager topologyManager) {
        super(logger);
        this.topologyManager = topologyManager;
    }

    @Override
    public boolean init(final String destination) {
        return true;
    }

    public void applyBase(BinlogPosition position, LogicMetaTopology topology) {
        applySnapshotInternal(topology);
        DDLRecord record = DDLRecord.builder().schemaName("*").ddlSql(GSON.toJson(snapshot()))
            .metaInfo(GSON.toJson(topology)).build();
        applyToDb(position, record, MetaType.SNAPSHOT.getValue(), null);
    }

    public boolean apply(BinlogPosition position, DDLRecord record, String extra) {
        boolean result = false;
        if (checkBeforeApply(position.getRtso(), record.getSchemaName(), record.getTableName(), record.getDdlSql(),
            record.getId(), record.getJobId())) {
            apply(position, record.getSchemaName(), record.getDdlSql(), extra);

            //apply distinct phy meta
            if (record.getExtInfo() != null) {
                String createSql4PhyTable = record.getExtInfo().getCreateSql4PhyTable();
                if (StringUtils.isNotEmpty(createSql4PhyTable)) {
                    distinctPhyMeta.apply(position, record.getSchemaName(), StringUtils.lowerCase(createSql4PhyTable),
                        extra);
                }
            }

            //apply topology
            TopologyRecord r = GSON.fromJson(record.getMetaInfo(), TopologyRecord.class);
            tryRepair1(position.getRtso(), r, record);
            updateOrDropDistinctPhyMeta(position.getRtso(), record.getSchemaName(), record.getTableName(),
                record.getSqlKind(), record.getDdlSql(), record.getExtInfo());
            dropTopology(position.getRtso(), record.getSchemaName(), record.getTableName(), record.getDdlSql());
            topologyManager.apply(position.getRtso(), record.getSchemaName(), record.getTableName(), r);

            result = true;
        }

        applyToDb(position, record, MetaType.DDL.getValue(), extra);
        Printer.tryPrint(position, record.getSchemaName(), record.getTableName(), this);
        return result;
    }

    @Override
    public void applySnapshot(String snapshotTso) {
        // log before apply snapshot
        AtomicLong applyCount = new AtomicLong(0L);
        long startTime = System.currentTimeMillis();
        JvmSnapshot jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info("build logic meta snapshot started, current used memory -> young:{}, old:{}",
            jvmSnapshot.getYoungUsed(), jvmSnapshot.getOldUsed());

        // do apply
        destory();
        Optional<BinlogLogicMetaHistory> snapshot = binlogLogicMetaHistoryMapper.selectOne(s -> s
            .where(BinlogLogicMetaHistoryDynamicSqlSupport.tso, SqlBuilder.isEqualTo(snapshotTso)));
        snapshot.ifPresent(s -> {
            logger.warn("apply logic snapshot: [id={}, dbName={}, tso={}]", s.getId(), s.getDbName(), s.getTso());
            LogicMetaTopology topology = GSON.fromJson(s.getTopology(), LogicMetaTopology.class);
            applyCount.set(applySnapshotInternal(topology));
        });

        //log after apply snapshot
        long costTime = System.currentTimeMillis() - startTime;
        jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info("build logic meta snapshot finished, applyCount {}, cost time {}(ms), current used memory -> "
            + "young:{}, old:{}", costTime, applyCount.get(), jvmSnapshot.getYoungUsed(), jvmSnapshot.getOldUsed());
    }

    private long applySnapshotInternal(LogicMetaTopology topology) {
        AtomicLong applyCount = new AtomicLong(0L);
        topology.getLogicDbMetas().forEach(s -> {
            String schema = s.getSchema();
            s.getLogicTableMetas().forEach(t -> {
                String createSql = t.getCreateSql();
                apply(null, schema, createSql, null);
                if (StringUtils.isNotEmpty(t.getCreateSql4Phy())) {
                    distinctPhyMeta.apply(null, schema, t.getCreateSql4Phy(), null);
                }
                applyCount.incrementAndGet();
            });
        });
        topologyManager.setTopology(topology);
        return applyCount.get();
    }

    @Override
    public void applyHistory(String snapshotTso, String rollbackTso) {
        // log before apply
        long startTime = System.currentTimeMillis();
        long applyCount = 0;
        JvmSnapshot jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info("apply logic ddl history started, current used memory -> young:{}, old:{}",
            jvmSnapshot.getYoungUsed(), jvmSnapshot.getOldUsed());

        //apply history
        while (true) {
            final String snapshotTsoCondition = snapshotTso;
            List<BinlogLogicMetaHistory> histories = binlogLogicMetaHistoryMapper.select(s -> s
                .where(BinlogLogicMetaHistoryDynamicSqlSupport.tso, SqlBuilder.isGreaterThan(snapshotTsoCondition))
                .and(BinlogLogicMetaHistoryDynamicSqlSupport.tso, SqlBuilder.isLessThanOrEqualTo(rollbackTso))
                .and(BinlogLogicMetaHistoryDynamicSqlSupport.type, SqlBuilder.isEqualTo(MetaType.DDL.getValue()))
                .orderBy(BinlogLogicMetaHistoryDynamicSqlSupport.tso).limit(PAGE_SIZE)
            );
            histories.forEach(h -> {
                toLowerCase(h);
                BinlogPosition position = new BinlogPosition(null, h.getTso());
                if (checkBeforeApply(h.getTso(), h.getDbName(), h.getTableName(), h.getDdl(), h.getDdlRecordId(),
                    h.getDdlJobId())) {
                    super.apply(position, h.getDbName(), h.getDdl(), null);

                    // apply create sql for distinct phy meta
                    DDLExtInfo extInfo = parseExtInfo(h.getExtInfo());
                    if (extInfo != null && StringUtils.isNotEmpty(extInfo.getCreateSql4PhyTable())) {
                        distinctPhyMeta.apply(position, h.getDbName(), extInfo.getCreateSql4PhyTable(), null);
                    }

                    // apply topology
                    if (StringUtils.isNotEmpty(h.getTopology())) {
                        TopologyRecord topologyRecord = GSON.fromJson(h.getTopology(), TopologyRecord.class);
                        topologyManager.apply(h.getTso(), h.getDbName(), h.getTableName(), topologyRecord);
                    }

                    // try update distinct phy meta
                    updateOrDropDistinctPhyMeta(h.getTso(), h.getDbName(), h.getTableName(), h.getSqlKind(),
                        h.getDdl(), extInfo);

                    //try drop topology
                    dropTopology(h.getTso(), h.getDbName(), h.getTableName(), h.getDdl());

                    if (logger.isDebugEnabled()) {
                        logger.debug("apply one history logic ddl : [id={}, dbName={}, tableName={}, tso={}]",
                            h.getId(), h.getDbName(), h.getTableName(), h.getTso());
                    }
                }

                Printer.tryPrint(position, h.getDbName(), h.getTableName(), this);
            });

            applyCount += histories.size();
            if (histories.size() == PAGE_SIZE) {
                snapshotTso = histories.get(PAGE_SIZE - 1).getTso();
            } else {
                break;
            }
        }

        //log after apply
        long costTime = System.currentTimeMillis() - startTime;
        jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info("apply logic ddl history finished, snapshot tso {}, rollback tso {}, cost time {}(ms),"
                + " applyCount {}" + ", current used memory -> young:{}, old:{}", snapshotTso, rollbackTso, costTime,
            applyCount, jvmSnapshot.getYoungUsed(), jvmSnapshot.getOldUsed());
    }

    /**
     * 快照备份到存储, 这里只需要备份变动的table
     */
    private void applyToDb(BinlogPosition position, DDLRecord record, byte type, String extra) {
        if (position == null) {
            return;
        }
        try {
            BinlogLogicMetaHistory history = BinlogLogicMetaHistory.builder()
                .tso(position.getRtso())
                .dbName(record.getSchemaName())
                .tableName(record.getTableName())
                .sqlKind(record.getSqlKind())
                .ddl(record.getDdlSql())
                .topology(record.getMetaInfo()).type(type)
                .extInfo(record.getExtInfo() != null ? GSON.toJson(record.getExtInfo()) : null)
                .ddlRecordId(record.getId())
                .ddlJobId(record.getJobId())
                .build();
            binlogLogicMetaHistoryMapper.insert(history);
        } catch (DuplicateKeyException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("ddl record already applied, ignore this time, record tso is " + position.getRtso());
            }
        }
    }

    public Map<String, String> distinctPhySnapshot() {
        Collection<Schema> schemas = distinctPhyMeta.getRepository().getSchemas();
        schemas.forEach(schema -> {
            logger.warn("to be replaced phySchema:{}, tables:{}", schema.getCatalog(), schema.showTables());
        });
        return distinctPhyMeta.snapshot();
    }

    public TableMeta findDistinctPhy(String schema, String table) {
        return distinctPhyMeta.find(schema, table);
    }

    public String distinctPhySnapshot(String schema, String table) {
        return distinctPhyMeta.snapshot(schema, table);
    }

    /**
     * 兼容性方法，主要为了兼容很老之前的一个内核版本，在Rename场景下，Topology中记录的tablename不是Rename后的名字，而是rename前的名字
     */
    private void tryRepair1(String tso, TopologyRecord r, DDLRecord record) {
        if (r != null && StringUtils.isNotEmpty(record.getDdlSql())) {
            SQLStatementParser parser = SQLParserUtils.createSQLStatementParser(record.getDdlSql(), DbType.mysql,
                FEATURES);
            SQLStatement stmt = parser.parseStatementList().get(0);
            if (stmt instanceof MySqlRenameTableStatement) {
                String renameTo = ((MySqlRenameTableStatement) stmt).getItems().get(0).getTo().getSimpleName();
                if (r.getLogicTableMeta() != null) {
                    renameTo = SQLUtils.normalize(renameTo);
                    r.getLogicTableMeta().setTableName(renameTo);
                    record.setMetaInfo(GSON.toJson(r));
                }
            }
        }
    }

    private void dropTopology(String tso, String schema, String tableName, String ddl) {
        SQLStatementParser parser = SQLParserUtils.createSQLStatementParser(ddl, DbType.mysql, FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);
        if (stmt instanceof SQLDropDatabaseStatement) {
            String databaseName = ((SQLDropDatabaseStatement) stmt).getDatabaseName();
            databaseName = SQLUtils.normalize(databaseName);
            Preconditions.checkArgument(StringUtils.equalsIgnoreCase(databaseName, schema),
                "drop database record should be coincident DDL(" + databaseName + "), History(" + schema + ")");
            topologyManager.removeTopology(tso, schema.toLowerCase(), null);
        }
        if (stmt instanceof SQLDropTableStatement) {
            for (SQLExprTableSource tableSource : ((SQLDropTableStatement) stmt).getTableSources()) {
                String tn = tableSource.getTableName(true);
                Preconditions.checkArgument(StringUtils.equalsIgnoreCase(tn, tableName),
                    "drop table record should be coincident DDL(" + tn + "), History(" + tableName + ")");
                topologyManager.removeTopology(tso, schema.toLowerCase(), tableName.toLowerCase());
            }
        }
    }

    private boolean checkBeforeApply(String tso, String schema, String tableName, String ddl, Long ddlRecordId,
                                     Long ddlJobId) {
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(ddl, DbType.mysql, FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);

        boolean result = true;
        if (stmt instanceof SQLCreateDatabaseStatement) {
            tryRemovePreviousMeta((SQLCreateDatabaseStatement) stmt, schema, tso);
            SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) stmt;
            result = !createDatabaseStatement.isIfNotExists()
                || (topologyManager.getTopology(schema) == null && !isSchemaExists(schema));
        } else if (stmt instanceof MySqlCreateTableStatement) {
            // fix https://aone.alibaba-inc.com/issue/38023203
            // fix https://aone.alibaba-inc.com/issue/39665786
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) stmt;
            boolean isIfNotExists = createTableStatement.isIfNotExists();
            if (isIfNotExists) {
                if (ddlRecordId != null) {
                    result = ddlJobId != null;
                } else {
                    result = find(schema, tableName) == null;
                }
            } else {
                result = true;
            }
            //result = !isIfNotExists || find(schema, tableName) == null;
        } else if (stmt instanceof SQLDropTableStatement) {
            SQLDropTableStatement dropTableStatement = (SQLDropTableStatement) stmt;
            boolean isIfExists = dropTableStatement.isIfExists();
            if (isIfExists) {
                if (ddlRecordId != null) {
                    result = ddlJobId != null;
                } else {
                    result = true;
                }
            } else {
                result = true;
            }
        }

        if (!result) {
            logger.warn("ignore logic ddl sql， with tso {}, schema {}, tableName {}.", tso, schema, tableName);
        }
        return result;
    }

    private void updateOrDropDistinctPhyMeta(String tso, String schema, String tableName, String sqlKind, String ddlSql,
                                             DDLExtInfo extInfo) {
        if (distinctPhyMeta.find(schema, tableName) != null) {
            if (StringUtils.equals(sqlKind, "DROP_DATABASE") || StringUtils.equals(sqlKind, "DROP_TABLE")
                || StringUtils.equals(sqlKind, "RENAME_TABLE")) {
                distinctPhyMeta.apply(new BinlogPosition(null, tso), schema, ddlSql, null);
            } else if (StringUtils.equals(sqlKind, "ALTER_TABLE") && (extInfo == null || StringUtils
                .isEmpty(extInfo.getCreateSql4PhyTable()))) {
                //如果是一个普通的ALTER SQL，还是要在distinct phy meta执行的，否则会取到不一致的数据
                distinctPhyMeta.apply(new BinlogPosition(null, tso), schema, ddlSql, null);
            }
        }
    }

    private void tryRemovePreviousMeta(SQLCreateDatabaseStatement createDatabaseStatement, String schema, String tso) {
        // 如果是create database，将元数据尝试进行一下清理，正常不应该有元数据的，但是不排除意外情况
        // 比如：polarx内核针对drop database未接入ddl引擎，sql执行和cdc打标无法保证原子性
        // 但对于含有if not exist的sql来说，无法判断当前create database操作是否是有效操作，所以不予处理
        if (!createDatabaseStatement.isIfNotExists()) {
            String databaseName = createDatabaseStatement.getDatabaseName();
            databaseName = SQLUtils.normalize(databaseName);
            Preconditions.checkArgument(StringUtils.equalsIgnoreCase(databaseName, schema),
                "create database record should be coincident DDL(" + databaseName + "), History(" + schema + ")");
            super.apply(null, schema, "drop database if exists `" + escape(databaseName) + "`", null);
            topologyManager.removeTopology(tso, schema.toLowerCase(), null);

            logger.warn("remove previous meta for newly create database sql, tso :{}, sql :{} ", tso,
                createDatabaseStatement.toUnformattedString());
        }
    }

    private void toLowerCase(BinlogLogicMetaHistory logicMetaHistory) {
        logicMetaHistory.setDbName(StringUtils.lowerCase(logicMetaHistory.getDbName()));
        logicMetaHistory.setTableName(StringUtils.lowerCase(logicMetaHistory.getTableName()));
        logicMetaHistory.setDdl(StringUtils.lowerCase(logicMetaHistory.getDdl()));
    }

    private DDLExtInfo parseExtInfo(String str) {
        DDLExtInfo extInfo = null;
        if (StringUtils.isNotEmpty(str)) {
            extInfo = GSON.fromJson(str, DDLExtInfo.class);
            if (extInfo != null && StringUtils.isNotBlank(extInfo.getCreateSql4PhyTable())) {
                extInfo.setCreateSql4PhyTable(StringUtils.lowerCase(extInfo.getCreateSql4PhyTable()));
            }
        }
        return extInfo;
    }
}
