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
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicDbTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.PhyTableTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import com.aliyun.polardbx.binlog.jvm.JvmSnapshot;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.alibaba.polardbx.druid.sql.parser.SQLParserUtils.createSQLStatementParser;

/**
 * Created by Shuguang & ziyang.lb
 */
public class PolarDbXStorageTableMeta extends MemoryTableMeta implements ICdcTableMeta {
    private static final Logger logger = LoggerFactory.getLogger(PolarDbXStorageTableMeta.class);
    private static final int PAGE_SIZE = 200;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final String storageInstId;
    private final PolarDbXLogicTableMeta polarDbXLogicTableMeta;
    private final TopologyManager topologyManager;
    private final BinlogPhyDdlHistoryMapper binlogPhyDdlHistoryMapper = SpringContextHolder.getObject(
        BinlogPhyDdlHistoryMapper.class);

    public PolarDbXStorageTableMeta(String storageInstId, PolarDbXLogicTableMeta polarDbXLogicTableMeta,
                                    TopologyManager topologyManager) {
        super(logger);
        this.storageInstId = storageInstId;
        this.polarDbXLogicTableMeta = polarDbXLogicTableMeta;
        this.topologyManager = topologyManager;
    }

    @Override
    public boolean init(final String destination) {
        return true;
    }

    public void applyBase(BinlogPosition position) {
        applySnapshot(position.getRtso());
    }

    @Override
    public void applySnapshot(String snapshotTso) {
        // log before apply snapshot
        long applyCount = 0;
        long startTime = System.currentTimeMillis();
        JvmSnapshot jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info("build physical meta snapshot started, current used memory -> young:{}, old:{}",
            jvmSnapshot.getYoungUsed(), jvmSnapshot.getOldUsed());

        // 获取Logic Snapshot
        Map<String, String> snapshot = polarDbXLogicTableMeta.snapshot();
        snapshot.forEach((k, v) -> super.apply(null, k, v, null));
        //用于订正逻辑表和物理表结构不一致的情况
        Map<String, String> snapshotToFix = polarDbXLogicTableMeta.distinctPhySnapshot();
        if (snapshotToFix != null && !snapshotToFix.isEmpty()) {
            snapshotToFix.forEach((k, v) -> super.apply(null, k, v, null));
        } else {
            logger.info("All logical and physical tables is compatible for snapshot tso:{}...", snapshotTso);
        }

        // 根据逻辑MetaSnapshot构建物理
        List<PhyTableTopology> phyTables = topologyManager.getPhyTables(storageInstId);
        for (PhyTableTopology phyTable : phyTables) {
            final List<String> tables = phyTable.getPhyTables();
            if (tables != null) {
                for (String table : tables) {
                    LogicDbTopology logicSchema = topologyManager.getLogicSchema(phyTable.getSchema(), table);
                    checkSchema(logicSchema, phyTable, table);
                    String tableName = logicSchema.getLogicTableMetas().get(0).getTableName();
                    String createTableSql = "create table `" + CommonUtils.escape(table) + "` like `" +
                        CommonUtils.escape(logicSchema.getSchema()) + "`.`" + CommonUtils.escape(tableName) + "`";
                    super.apply(null, phyTable.getSchema(), createTableSql, null);
                    applyCount++;

                    if (logger.isDebugEnabled()) {
                        logger.debug("apply from logic table, phy:{}.{}, logic:{}.{} [{}] ...", phyTable.getSchema(),
                            table, logicSchema.getSchema(), tableName, createTableSql);
                    }
                }
            }
        }
        //drop 逻辑库
        snapshot.forEach((k, v) -> super.apply(null, k, "drop database `" + k + "`", null));

        //log after apply snapshot
        long costTime = System.currentTimeMillis() - startTime;
        jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info("build physical meta snapshot finished, applyCount {}, cost time {}(ms), current used memory -> "
            + "young:{}, old:{}", costTime, applyCount, jvmSnapshot.getYoungUsed(), jvmSnapshot.getOldUsed());
    }

    @Override
    public void applyHistory(String snapshotTso, String rollbackTso) {
        // log before apply
        long applyCount = 0;
        long startTime = System.currentTimeMillis();
        JvmSnapshot jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info("apply phy ddl history started, current used memory -> young:{}, old:{}",
            jvmSnapshot.getYoungUsed(), jvmSnapshot.getOldUsed());

        // apply history
        while (true) {
            final String snapshotTsoCondition = snapshotTso;
            List<BinlogPhyDdlHistory> ddlHistories = binlogPhyDdlHistoryMapper.select(
                s -> s.where(BinlogPhyDdlHistoryDynamicSqlSupport.storageInstId, SqlBuilder.isEqualTo(storageInstId))
                    .and(BinlogPhyDdlHistoryDynamicSqlSupport.tso, SqlBuilder.isGreaterThan(snapshotTsoCondition))
                    .and(BinlogPhyDdlHistoryDynamicSqlSupport.tso, SqlBuilder.isLessThanOrEqualTo(rollbackTso))
                    .orderBy(BinlogPhyDdlHistoryDynamicSqlSupport.tso).limit(PAGE_SIZE)
            );
            for (BinlogPhyDdlHistory ddlHistory : ddlHistories) {
                toLowerCase(ddlHistory);
                BinlogPosition position = new BinlogPosition(null, ddlHistory.getTso());
                super.apply(position, ddlHistory.getDbName(), ddlHistory.getDdl(), ddlHistory.getExtra());
                if (logger.isDebugEnabled()) {
                    logger.debug("apply one physical phy ddl: [id={}, dbName={}, tso={}]", ddlHistory.getId(),
                        ddlHistory.getDbName(), ddlHistory.getTso());
                }
                tryPrint(position, ddlHistory.getDbName(), ddlHistory.getDdl());
            }

            applyCount += ddlHistories.size();
            if (ddlHistories.size() == PAGE_SIZE) {
                snapshotTso = ddlHistories.get(PAGE_SIZE - 1).getTso();
            } else {
                break;
            }
        }

        //log after apply
        long costTime = System.currentTimeMillis() - startTime;
        jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info(
            "apply phy ddl history finished, snapshot tso {}, rollback tso {}, cost time {}(ms), applyCount {}, "
                + "current used memory -> young:{}, old:{}", snapshotTso, rollbackTso, costTime, applyCount,
            jvmSnapshot.getYoungUsed(), jvmSnapshot.getOldUsed());
    }

    @Override
    public boolean apply(BinlogPosition position, String schema, String ddl, String extra) {
        // 首先记录到内存结构
        lock.writeLock().lock();
        try {
            if (super.apply(position, schema, ddl, extra)) {
                // 同步每次变更给远程做历史记录，只记录ddl，不记录快照
                applyHistoryToDb(position, schema, ddl, extra);
                tryPrint(position, schema, ddl);
                return true;
            } else {
                throw new RuntimeException("apply to memory is failed");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean apply(String schema, String ddl) {
        return super.apply(null, schema, ddl, null);
    }

    private void checkSchema(LogicDbTopology logicSchema, PhyTableTopology phyTable, String table) {
        Preconditions.checkNotNull(logicSchema,
            "phyTable " + phyTable.getSchema() + "." + table + "'s logicSchema should not be null!");
        Preconditions.checkNotNull(logicSchema.getLogicTableMetas(),
            "phyTable " + phyTable.getSchema() + "." + table + "'s logicTables should not be null!");
        Preconditions.checkNotNull(logicSchema.getLogicTableMetas().get(0),
            "phyTable " + phyTable.getSchema() + "." + table + "'s logicTable should not be null!");
    }

    private void applyHistoryToDb(BinlogPosition position, String schema, String ddl, String extra) {
        try {
            binlogPhyDdlHistoryMapper.insert(BinlogPhyDdlHistory.builder().storageInstId(storageInstId)
                .binlogFile(position.getFileName())
                .tso(position.getRtso())
                .dbName(schema)
                .ddl(ddl)
                .extra(extra).build());
        } catch (DuplicateKeyException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("already applyHistoryToDB, ignore this time, position is : {}, schema is {}, tso is {},"
                    + " extra is {}", position, schema, position.getRtso(), extra);
            }
        }
    }

    private void toLowerCase(BinlogPhyDdlHistory phyDdlHistory) {
        phyDdlHistory.setDbName(StringUtils.lowerCase(phyDdlHistory.getDbName()));
        phyDdlHistory.setDdl(StringUtils.lowerCase(phyDdlHistory.getDdl()));
    }

    private void tryPrint(BinlogPosition position, String schema, String ddl) {
        if (Printer.isSupportPrint()) {
            String tableName = parseTableName(ddl);
            if (StringUtils.isNotBlank(tableName)) {
                Printer.tryPrint(position, schema, tableName, this);
            }
        }
    }

    private String parseTableName(String ddl) {
        try {
            if (StringUtils.isBlank(ddl)) {
                return "";
            }

            SQLStatementParser parser = createSQLStatementParser(ddl, DbType.mysql, FastSQLConstant.FEATURES);
            List<SQLStatement> statementList = parser.parseStatementList();
            SQLStatement sqlStatement = statementList.get(0);

            if (sqlStatement instanceof SQLCreateTableStatement) {
                SQLCreateTableStatement sqlCreateTableStatement = (SQLCreateTableStatement) sqlStatement;
                return SQLUtils.normalize(sqlCreateTableStatement.getTableName());
            } else if (sqlStatement instanceof SQLDropTableStatement) {
                SQLDropTableStatement sqlDropTableStatement = (SQLDropTableStatement) sqlStatement;
                for (SQLExprTableSource tableSource : sqlDropTableStatement.getTableSources()) {
                    //CN只支持一次drop一张表，直接返回即可
                    return tableSource.getTableName(true);
                }
            } else if (sqlStatement instanceof MySqlRenameTableStatement) {
                MySqlRenameTableStatement renameTableStatement = (MySqlRenameTableStatement) sqlStatement;
                for (MySqlRenameTableStatement.Item item : renameTableStatement.getItems()) {
                    //CN只支持一次Rename一张表，直接返回即可
                    return SQLUtils.normalize(item.getName().getSimpleName());
                }
            } else if (sqlStatement instanceof SQLAlterTableStatement) {
                SQLAlterTableStatement sqlAlterTableStatement = (SQLAlterTableStatement) sqlStatement;
                return SQLUtils.normalize(sqlAlterTableStatement.getTableName());
            }
        } catch (Throwable t) {
            logger.error("parse table from ddl sql failed.", t);
        }
        return "";
    }
}
