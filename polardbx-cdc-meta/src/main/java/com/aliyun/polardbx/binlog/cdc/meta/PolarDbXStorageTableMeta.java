/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.repository.CdcSchemaStoreProvider;
import com.aliyun.polardbx.binlog.cdc.topology.LogicBasicInfo;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.PhyTableTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.jvm.JvmSnapshot;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.service.BinlogPhyDdlHistoryService;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.alibaba.polardbx.druid.sql.SQLUtils.normalizeNoTrim;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_IGNORE_APPLY_ERROR;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_CACHE_TABLE_MEAT_EXPIRE_TIME_MINUTES;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_CACHE_TABLE_META_MAX_SIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.util.SQLUtils.buildCreateLikeSql;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * Created by Shuguang & ziyang.lb
 */
public class PolarDbXStorageTableMeta extends MemoryTableMeta implements ICdcTableMeta {
    private static final Logger logger = LoggerFactory.getLogger(PolarDbXStorageTableMeta.class);
    private static final int PAGE_SIZE = 200;

    private final BinlogPhyDdlHistoryService phyDdlHistoryService =
        SpringContextHolder.getObject(BinlogPhyDdlHistoryService.class);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final String storageInstId;
    private final PolarDbXLogicTableMeta polarDbXLogicTableMeta;
    private final TopologyManager topologyManager;
    private final String dnVersion;
    private String maxTsoWithInit = "";
    private long applySnapshotCostTime = -1;
    private long applyHistoryCostTime = -1;
    private long queryDdlHistoryCostTime = -1;
    private long queryDdlHistoryCount = -1;

    public PolarDbXStorageTableMeta(String storageInstId, PolarDbXLogicTableMeta polarDbXLogicTableMeta,
                                    TopologyManager topologyManager, String dnVersion) {
        super(logger, CdcSchemaStoreProvider.getInstance(),
            DynamicApplicationConfig.getInt(META_CACHE_TABLE_META_MAX_SIZE),
            DynamicApplicationConfig.getInt(META_CACHE_TABLE_MEAT_EXPIRE_TIME_MINUTES),
            DynamicApplicationConfig.getBoolean(META_BUILD_IGNORE_APPLY_ERROR));
        this.storageInstId = storageInstId;
        this.polarDbXLogicTableMeta = polarDbXLogicTableMeta;
        this.topologyManager = topologyManager;
        this.dnVersion = dnVersion;
        boolean isMySQL8 = StringUtils.startsWith(dnVersion, "8");
        setMySql8(isMySQL8);
    }

    @Override
    public boolean init(final String destination) {
        this.maxTsoWithInit = phyDdlHistoryService.getMaxTso(getString(CLUSTER_ID), storageInstId);
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

        RandomNameConverterMapping nameConverter = new RandomNameConverterMapping();

        // 获取Logic Snapshot
        Map<String, String> snapshot = polarDbXLogicTableMeta.snapshot();
        snapshot.forEach((k, v) -> {
            super.apply(null, nameConverter.convert(k), v, null);
        });
        //用于订正逻辑表和物理表结构不一致的情况
        Map<String, String> snapshotToFix = polarDbXLogicTableMeta.distinctPhySnapshot();
        if (snapshotToFix != null && !snapshotToFix.isEmpty()) {
            snapshotToFix.forEach((k, v) -> {
                super.apply(null, nameConverter.convert(k), v, null);
            });
        } else {
            logger.info("All logical and physical tables is compatible for snapshot tso:{}...", snapshotTso);
        }

        // 根据逻辑MetaSnapshot构建物理
        topologyManager.initPhyLogicMapping(storageInstId);
        List<PhyTableTopology> phyTables =
            topologyManager.getPhyTables(storageInstId, Sets.newHashSet(), Sets.newHashSet());
        for (PhyTableTopology phyTable : phyTables) {
            final List<String> tables = phyTable.getPhyTables();
            if (tables != null) {
                for (String table : tables) {
                    LogicBasicInfo logicBasicInfo = topologyManager.getLogicBasicInfo(phyTable.getSchema(), table);
                    checkLogicBasicInfo(logicBasicInfo, phyTable, table);
                    String schemeName = logicBasicInfo.getSchemaName();
                    String tableName = logicBasicInfo.getTableName();
                    String createTableSql = buildCreateLikeSql(table, nameConverter.convert(schemeName), tableName);
                    super.apply(null, phyTable.getSchema(), createTableSql, null);
                    applyCount++;

                    if (logger.isDebugEnabled()) {
                        logger.debug("apply from logic table, phy:{}.{}, logic:{}.{} [{}] ...", phyTable.getSchema(),
                            table, schemeName, tableName, createTableSql);
                    }
                }
            }
        }
        //drop 逻辑库
        snapshot.forEach((k, v) -> {
            String newSchema = nameConverter.convert(k);
            super.apply(null, newSchema, "drop database `" + newSchema + "`", null);
        });

        //log after apply snapshot
        long costTime = System.currentTimeMillis() - startTime;
        applySnapshotCostTime += costTime;
        jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info("build physical meta snapshot finished, applyCount {}, cost time {}(ms), current used memory -> "
            + "young:{}, old:{}", costTime, applyCount, jvmSnapshot.getYoungUsed(), jvmSnapshot.getOldUsed());
    }

    @Override
    public void applyHistory(String snapshotTso, String rollbackTso) {
        // log before apply
        final String snapshotTsoInput = snapshotTso;
        long applyCount = 0;
        long startTime = System.currentTimeMillis();
        JvmSnapshot jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info("apply phy ddl history started, current used memory -> young:{}, old:{}",
            jvmSnapshot.getYoungUsed(), jvmSnapshot.getOldUsed());

        // apply history
        while (true) {
            final String snapshotTsoCondition = snapshotTso;
            long queryStartTime = System.currentTimeMillis();
            List<BinlogPhyDdlHistory> ddlHistories = phyDdlHistoryService.getPhyDdlHistoryForRollback(storageInstId,
                snapshotTsoCondition, rollbackTso, getString(CLUSTER_ID), PAGE_SIZE);
            queryDdlHistoryCostTime += (System.currentTimeMillis() - queryStartTime);
            queryDdlHistoryCount += ddlHistories.size();

            for (BinlogPhyDdlHistory ddlHistory : ddlHistories) {
                toLowerCase(ddlHistory);
                BinlogPosition position = new BinlogPosition(null, ddlHistory.getTso());
                String ddl = ddlHistory.getDdl();
                if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EXTRACT_REMOVE_HINTS_IN_DDL_SQL)) {
                    ddl = com.aliyun.polardbx.binlog.canal.core.ddl.SQLUtils.removeDDLHints(ddlHistory.getDdl());
                }
                super.apply(position, ddlHistory.getDbName(), ddl, ddlHistory.getExtra());
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
        applyHistoryCostTime += costTime;
        jvmSnapshot = JvmUtils.buildJvmSnapshot();
        logger.info(
            "apply phy ddl history finished, snapshot tso {}, rollback tso {}, cost time {}(ms), applyCount {}, "
                + "current used memory -> young:{}, old:{}", snapshotTsoInput, rollbackTso, costTime, applyCount,
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

    private void checkLogicBasicInfo(LogicBasicInfo logicBasicInfo, PhyTableTopology phyTable, String table) {
        Preconditions.checkNotNull(logicBasicInfo,
            "phyTable " + phyTable.getSchema() + "." + table + "'s logicBasicInfo should not be null!");
        Preconditions.checkNotNull(logicBasicInfo.getSchemaName(),
            "phyTable " + phyTable.getSchema() + "." + table + "'s logicSchemaName should not be null!");
        Preconditions.checkNotNull(logicBasicInfo.getTableName(),
            "phyTable " + phyTable.getSchema() + "." + table + "'s logicTableName should not be null!");
    }

    private void applyHistoryToDb(BinlogPosition position, String schema, String ddl, String extra) {
        if (position.getRtso().compareTo(maxTsoWithInit) <= 0) {
            return;
        }
        phyDdlHistoryService.insetSelectiveIgnore(BinlogPhyDdlHistory.builder().storageInstId(storageInstId)
                .binlogFile(position.getFileName())
                .tso(position.getRtso())
                .dbName(schema)
                .ddl(ddl)
                .clusterId(getString(ConfigKeys.CLUSTER_ID))
                .extra(extra).build(),
            String.format("already applyHistoryToDB, ignore this time, position is : %s, schema is %s, tso is %s,"
                + " extra is %s", position, schema, position.getRtso(), extra));
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

            SQLStatement sqlStatement = parseSQLStatement(ddl);
            if (sqlStatement instanceof SQLCreateTableStatement) {
                SQLCreateTableStatement sqlCreateTableStatement = (SQLCreateTableStatement) sqlStatement;
                return SQLUtils.normalizeNoTrim(sqlCreateTableStatement.getTableName());
            } else if (sqlStatement instanceof SQLDropTableStatement) {
                SQLDropTableStatement sqlDropTableStatement = (SQLDropTableStatement) sqlStatement;
                for (SQLExprTableSource tableSource : sqlDropTableStatement.getTableSources()) {
                    //CN只支持一次drop一张表，直接返回即可
                    return normalizeNoTrim(tableSource.getTableName());
                }
            } else if (sqlStatement instanceof MySqlRenameTableStatement) {
                MySqlRenameTableStatement renameTableStatement = (MySqlRenameTableStatement) sqlStatement;
                for (MySqlRenameTableStatement.Item item : renameTableStatement.getItems()) {
                    //CN只支持一次Rename一张表，直接返回即可
                    return SQLUtils.normalizeNoTrim(item.getName().getSimpleName());
                }
            } else if (sqlStatement instanceof SQLAlterTableStatement) {
                SQLAlterTableStatement sqlAlterTableStatement = (SQLAlterTableStatement) sqlStatement;
                return SQLUtils.normalizeNoTrim(sqlAlterTableStatement.getTableName());
            }
        } catch (Throwable t) {
            logger.error("parse table from ddl sql failed.", t);
        }
        return "";
    }

    public long getApplySnapshotCostTime() {
        return applySnapshotCostTime;
    }

    public long getApplyHistoryCostTime() {
        return applyHistoryCostTime;
    }

    public long getQueryDdlHistoryCostTime() {
        return queryDdlHistoryCostTime;
    }

    public long getQueryDdlHistoryCount() {
        return queryDdlHistoryCount;
    }

    static class RandomNameConverterMapping {
        private static final int RANDOM_DB_COUNT = 20;

        private static final AtomicLong sequenceGenerator = new AtomicLong(0);
        private static final String RANDOM_PREFIX = "_cdc_tmp_";
        private final LoadingCache<String, String> cache =
            CacheBuilder.newBuilder().build(new CacheLoader<String, String>() {
                @Override
                public String load(String key) throws Exception {
                    return RANDOM_PREFIX + sequenceGenerator.incrementAndGet() + "_" + StringUtils.lowerCase(
                        RandomStringUtils.randomAlphabetic(RANDOM_DB_COUNT));
                }
            });

        public String convert(String db) {
            try {
                return cache.get(db);
            } catch (ExecutionException e) {
                throw new PolardbxException(e);
            }
        }

    }

}
