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

import com.alibaba.fastjson.JSONObject;
import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddColumn;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropColumnItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAlterTableChangeColumn;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta.FieldMetaExt;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicDbTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicTableMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.PhyTableTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.SemiSnapshotInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.alibaba.polardbx.druid.sql.parser.SQLParserUtils.createSQLStatementParser;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_ROLLBACK_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_ROLLBACK_MODE_SUPPORT_INSTANT_CREATE_TABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_SEMI_SNAPSHOT_DELTA_CHANGE_CHECK_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_SEMI_SNAPSHOT_HOLDING_TIME;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_SEMI_SNAPSHOT_HOLDING_TIME_CHECK_INTERVAL;
import static com.aliyun.polardbx.binlog.cdc.meta.RollbackMode.SNAPSHOT_EXACTLY;
import static com.aliyun.polardbx.binlog.cdc.meta.RollbackMode.SNAPSHOT_SEMI;
import static com.aliyun.polardbx.binlog.cdc.meta.RollbackMode.SNAPSHOT_UNSAFE;
import static com.aliyun.polardbx.binlog.monitor.MonitorType.META_DATA_INCONSISTENT_WARNNIN;

/**
 * Created by ShuGuang & ziyang.lb
 */
@Slf4j
public class PolarDbXTableMetaManager {
    private static final Gson GSON = new GsonBuilder().create();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final String storageInstId;
    private final Map<String, Set<String>> deltaChangeMap;
    private final RollbackMode rollbackMode;

    private TopologyManager topologyManager;
    private PolarDbXLogicTableMeta polarDbXLogicTableMeta;
    private PolarDbXStorageTableMeta polarDbXStorageTableMeta;
    private ConsistencyChecker consistencyChecker;
    private ScheduledExecutorService cleaner;
    private long lastCheckAllDeltaTime;

    public PolarDbXTableMetaManager(String storageInstId) {
        this.storageInstId = storageInstId;
        this.deltaChangeMap = new HashMap<>();
        this.rollbackMode = getRollbackMode();
    }

    public void init() {
        if (initialized.compareAndSet(false, true)) {
            this.topologyManager = new TopologyManager();
            this.polarDbXLogicTableMeta = new PolarDbXLogicTableMeta(this.topologyManager);
            this.polarDbXLogicTableMeta.init(null);
            this.polarDbXStorageTableMeta = new PolarDbXStorageTableMeta(storageInstId,
                polarDbXLogicTableMeta, topologyManager);
            this.polarDbXStorageTableMeta.init(null);
            this.consistencyChecker = new ConsistencyChecker(topologyManager, polarDbXLogicTableMeta,
                polarDbXStorageTableMeta, this, storageInstId);
            this.startCleaner();
        }
    }

    public void destroy() {
        this.polarDbXStorageTableMeta.destory();
        this.polarDbXLogicTableMeta.destory();
        if (this.cleaner != null) {
            this.cleaner.shutdownNow();
        }
    }

    public TableMeta findPhyTable(String schema, String table) {
        TableMeta phy = polarDbXStorageTableMeta.find(schema, table);
        //进行一下补偿，如果表不存在，实时创建一下
        if (phy == null && supportInstantCreatTableWhenNotfound()) {
            LogicDbTopology logicTopology = getLogicSchema(schema, table);
            if (logicTopology != null && logicTopology.getLogicTableMetas() != null
                && logicTopology.getLogicTableMetas().size() == 1) {
                log.info("phy table meta is not found for {}:{}, will instantly try to create for compensation.",
                    schema, table);
                String logicSchema = logicTopology.getSchema();
                String logicTable = logicTopology.getLogicTableMetas().get(0).getTableName();
                TableMeta distinctPhyTableMeta = polarDbXLogicTableMeta.findDistinctPhy(logicSchema, logicTable);
                if (distinctPhyTableMeta == null) {
                    TableMeta logicTableMeta = polarDbXLogicTableMeta.find(logicSchema, logicTable);
                    String ddl = polarDbXLogicTableMeta.snapshot(logicSchema, logicTable);
                    createNotExistPhyTable(logicSchema, schema, logicTable, table, ddl);
                    return logicTableMeta;
                } else {
                    String ddl = polarDbXLogicTableMeta.distinctPhySnapshot(logicSchema, logicTable);
                    createNotExistPhyTable(logicSchema, schema, logicTable, table, ddl);
                    return distinctPhyTableMeta;
                }
            }
        }
        return phy;
    }

    private void startCleaner() {
        if (rollbackMode == SNAPSHOT_SEMI) {
            long checkInterval = DynamicApplicationConfig.getLong(META_SEMI_SNAPSHOT_HOLDING_TIME_CHECK_INTERVAL);
            this.cleaner = Executors.newSingleThreadScheduledExecutor((r) -> {
                Thread t = new Thread(r, "semi-snapshot-cleaner");
                t.setDaemon(true);
                return t;
            });
            cleaner.scheduleAtFixedRate(() -> {
                try {
                    cleanExpiredSemiSnapshot();
                } catch (Exception e) {
                    log.error("clean semi snapshot error!", e);
                }
            }, 0, checkInterval, TimeUnit.MINUTES);
        }
    }

    private void cleanExpiredSemiSnapshot() {
        int holdingTime = DynamicApplicationConfig.getInt(META_SEMI_SNAPSHOT_HOLDING_TIME);
        Date expireTime = DateTime.now().minusHours(holdingTime).toDate();
        SemiSnapshotInfoMapper mapper = SpringContextHolder.getObject(SemiSnapshotInfoMapper.class);

        List<SemiSnapshotInfo> list = mapper
            .select(s -> s.where(SemiSnapshotInfoDynamicSqlSupport.storageInstId, SqlBuilder.isEqualTo(storageInstId))
                .and(SemiSnapshotInfoDynamicSqlSupport.gmtCreated, SqlBuilder.isLessThanOrEqualTo(expireTime))
                .orderBy(SemiSnapshotInfoDynamicSqlSupport.tso.descending())
                .limit(1));
        if (!list.isEmpty()) {
            int count = mapper.delete(
                s -> s.where(SemiSnapshotInfoDynamicSqlSupport.storageInstId, SqlBuilder.isEqualTo(storageInstId))
                    .and(SemiSnapshotInfoDynamicSqlSupport.tso, SqlBuilder.isLessThan(list.get(0).getTso())));
            log.info("successfully deleted expired semi snapshot records which tso is less than {}, delete count: {}. ",
                list.get(0).getTso(), count);
        }
    }

    private void createNotExistPhyTable(String logicSchema, String phySchema, String logicTable, String phyTable,
                                        String ddl) {
        String createSql = "create table `" + CommonUtils.escape(phyTable) + "` like `" +
            CommonUtils.escape(logicSchema) + "`.`" + CommonUtils.escape(logicTable) + "`";
        polarDbXStorageTableMeta.apply(logicSchema, ddl);
        polarDbXStorageTableMeta.apply(phySchema, createSql);
        polarDbXStorageTableMeta.apply(logicSchema, "drop database `" + logicSchema + "`");
    }

    public TableMeta findLogicTable(String schema, String table) {
        return polarDbXLogicTableMeta.find(schema, table);
    }

    public LogicTableMeta compare(String schema, String table) {
        TableMeta phy = findPhyTable(schema, table);
        Preconditions.checkNotNull(phy, "phyTable " + schema + "." + table + "'s tableMeta should not be null!");

        LogicDbTopology logicTopology = getLogicSchema(schema, table);
        Preconditions.checkArgument(logicTopology != null && logicTopology.getLogicTableMetas() != null
            && logicTopology.getLogicTableMetas().size() == 1, "can not find logic meta " + logicTopology);

        TableMeta logic =
            findLogicTable(logicTopology.getSchema(), logicTopology.getLogicTableMetas().get(0).getTableName());
        Preconditions.checkNotNull(logic, "phyTable [" + schema + "." + table + "], logic tableMeta["
            + logicTopology.getSchema() + "." + logicTopology.getLogicTableMetas().get(0).getTableName()
            + "] should not be null!");

        final List<String> columnNames = phy.getFields().stream().map(FieldMeta::getColumnName).collect(
            Collectors.toList());
        LogicTableMeta meta = new LogicTableMeta();
        meta.setLogicSchema(logic.getSchema());
        meta.setLogicTable(logic.getTable());
        meta.setPhySchema(schema);
        meta.setPhyTable(table);
        meta.setCompatible(phy.getFields().size() == logic.getFields().size());
        FieldMeta hiddenPK = null;
        if (!meta.isCompatible()) {
            log.warn("meta is not compatible {} {}", phy, logic);
        }
        int logicIndex = 0;
        for (int i = 0; i < logic.getFields().size(); i++) {
            FieldMeta fieldMeta = logic.getFields().get(i);
            final int x = columnNames.indexOf(fieldMeta.getColumnName());
            if (x != i) {
                meta.setCompatible(false);
            }
            // 隐藏主键忽略掉
            if (SystemDB.isDrdsImplicitId(fieldMeta.getColumnName())) {
                meta.setCompatible(false);
                hiddenPK = fieldMeta;
                continue;
            }
            meta.add(new FieldMetaExt(fieldMeta, logicIndex++, x));
        }
        if (!meta.isCompatible()) {
            log.warn("meta is not compatible {}", meta);
        }
        // 如果有隐藏主键，直接放到最后
        if (hiddenPK != null && DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DRDS_HIDDEN_PK_SUPPORT)) {
            final int x = columnNames.indexOf(hiddenPK.getColumnName());
            meta.add(new FieldMetaExt(hiddenPK, logicIndex, x));
        }

        return meta;
    }

    public void applyBase(BinlogPosition position, LogicMetaTopology topology) {
        this.polarDbXLogicTableMeta.applyBase(position, topology);
        this.polarDbXStorageTableMeta.applyBase(position);
    }

    public void applyLogic(BinlogPosition position, DDLRecord record, String extra) {
        if (StringUtils.isNotEmpty(extra)) {
            record.setExtInfo(GSON.fromJson(extra, DDLExtInfo.class));
        }
        boolean result = this.polarDbXLogicTableMeta.apply(position, record, extra);
        //只有发生了Actual Apply Operation，才进行后续处理
        if (result) {
            this.processSnapshotSemi(position, record);
            //对拓扑和表结构进行一致性对比，正常情况下，每个表执行完一个逻辑DDL后，都应该是一个一致的状态，如果不一致说明出现了问题
            this.consistencyChecker.checkTopologyConsistencyWithOrigin(position.getRtso(), record);
            this.consistencyChecker.checkLogicAndPhysicalConsistency(position.getRtso(), record);
        }
    }

    public void applyPhysical(BinlogPosition position, String schema, String ddl, String extra) {
        this.polarDbXStorageTableMeta.apply(position, schema, ddl, extra);
        this.updateDeltaChangeByPhysicalDdl(position.getRtso(), schema, ddl);
    }

    public void rollback(BinlogPosition position) {
        Stopwatch sw = Stopwatch.createStarted();

        if (rollbackMode == SNAPSHOT_EXACTLY) {
            rollbackInSnapshotExactlyMode(position);
        } else if (rollbackMode == SNAPSHOT_SEMI) {
            rollbackInSnapshotSemiMode(position);
        } else if (rollbackMode == SNAPSHOT_UNSAFE) {
            rollbackInSnapshotUnSafeMode(position);
        } else {
            throw new PolardbxException("invalid rollback mode " + rollbackMode);
        }
        sw.stop();
        log.warn("successfully rollback to tso:{}, cost {}", position.getRtso(), sw);
    }

    public Map<String, String> snapshot() {
        log.info("Logic: {}", polarDbXLogicTableMeta.snapshot());
        log.info("Storage: {}", polarDbXStorageTableMeta.snapshot());
        throw new RuntimeException("not support for PolarDbXTableMetaManager");
    }

    public Set<String> findIndexes(String schema, String table) {
        return polarDbXLogicTableMeta.findIndexes(schema, table);
    }

    /**
     * 从存储中获取小于等于rollback tso的最新一次Snapshot的位点
     */
    protected String getLatestSnapshotTso(String rollbackTso) {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        return metaJdbcTemplate.queryForObject(
            "select max(tso) tso from binlog_logic_meta_history where tso <= '" + rollbackTso +
                "' and type = " + MetaType.SNAPSHOT.getValue(), String.class);
    }

    /**
     * 从存储中获取小于等于rollback tso的最新一次的位点
     */
    protected String getLatestLogicDDLTso(String rollbackTso) {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        return metaJdbcTemplate.queryForObject(
            "select max(tso) tso from binlog_logic_meta_history where tso <= '" + rollbackTso + "' +"
                + "and type = " + MetaType.DDL.getValue(), String.class);
    }

    private void processSnapshotSemi(BinlogPosition position, DDLRecord record) {
        if (rollbackMode == SNAPSHOT_SEMI) {
            this.updateDeltaChangeByLogicDdl(position.getRtso(), record);
            this.tryUpdateSemiSnapshotPosition(position.getRtso());
        }
    }

    /**
     * 获取回滚模式
     */
    private RollbackMode getRollbackMode() {
        String phyMetaBuildModeStr = DynamicApplicationConfig.getString(META_ROLLBACK_MODE);
        RollbackMode mode = RollbackMode.valueOf(phyMetaBuildModeStr);
        if (mode == RollbackMode.RANDOM) {
            List<RollbackMode> list = Lists.newArrayList(SNAPSHOT_SEMI, SNAPSHOT_EXACTLY);
            Collections.shuffle(list);
            mode = list.get(0);
            log.info("random selected rollback mode is " + mode);
        }
        return mode;
    }

    /**
     * 在不出现bug的情况下，只有SNAPSHOT_SEMI 和 SNAPSHOT_UNSAFE才有必要
     */
    private boolean supportInstantCreatTableWhenNotfound() {
        String configStr = DynamicApplicationConfig.getString(META_ROLLBACK_MODE_SUPPORT_INSTANT_CREATE_TABLE);
        if (StringUtils.isNotBlank(configStr)) {
            String[] configArray = StringUtils.split(configStr, ",");
            for (String s : configArray) {
                if (rollbackMode.name().equals(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void rollbackInSnapshotExactlyMode(BinlogPosition position) {
        String snapshotTso = getLatestSnapshotTso(position.getRtso());
        polarDbXLogicTableMeta.applySnapshot(snapshotTso);
        polarDbXStorageTableMeta.applySnapshot(snapshotTso);
        polarDbXLogicTableMeta.applyHistory(snapshotTso, position.getRtso());
        polarDbXStorageTableMeta.applyHistory(snapshotTso, position.getRtso());
    }

    private void rollbackInSnapshotSemiMode(BinlogPosition position) {
        String snapshotTso = getLatestSnapshotTso(position.getRtso());
        String semiSnapshotTso = getSuitableSemiSnapshotTso(snapshotTso, position.getRtso());
        if (StringUtils.isBlank(semiSnapshotTso)) {
            log.info("semi snapshot is not found between {} and {}.", snapshotTso, position.getRtso());
            rollbackInSnapshotExactlyMode(position);
        } else {
            log.info("found semi snapshot {} between {} and {}.", semiSnapshotTso, snapshotTso, position.getRtso());
            polarDbXLogicTableMeta.applySnapshot(snapshotTso);
            polarDbXLogicTableMeta.applyHistory(snapshotTso, semiSnapshotTso);
            polarDbXStorageTableMeta.applySnapshot(snapshotTso);
            polarDbXLogicTableMeta.applyHistory(semiSnapshotTso, position.getRtso());
            polarDbXStorageTableMeta.applyHistory(semiSnapshotTso, position.getRtso());
            initDeltaChangeMap(position.getRtso());
        }
    }

    private void rollbackInSnapshotUnSafeMode(BinlogPosition position) {
        String snapshotTso = getLatestSnapshotTso(position.getRtso());
        polarDbXLogicTableMeta.applySnapshot(snapshotTso);
        polarDbXLogicTableMeta.applyHistory(snapshotTso, position.getRtso());
        polarDbXStorageTableMeta.applySnapshot(snapshotTso);
        polarDbXStorageTableMeta.applyHistory(getLatestLogicDDLTso(position.getRtso()), position.getRtso());
    }

    private void initDeltaChangeMap(String tso) {
        Stopwatch sw = Stopwatch.createStarted();

        long logicDbCount = 0;
        long logicTableCount = 0;
        long phyTableCount = 0;
        Map<String, Set<String>> inconsistencyTables = new HashMap<>();

        List<LogicDbTopology> logicDbTopologies = topologyManager.getTopology().getLogicDbMetas();
        for (LogicDbTopology logicDbTopology : logicDbTopologies) {
            final List<LogicTableMetaTopology> logicTableMetas = logicDbTopology.getLogicTableMetas();
            if (logicTableMetas == null || logicTableMetas.isEmpty()) {
                continue;
            }
            for (LogicTableMetaTopology tableMetaTopology : logicTableMetas) {
                List<PhyTableTopology> phyTableTopologies = tableMetaTopology.getPhySchemas();
                if (phyTableTopologies == null || phyTableTopologies.isEmpty()) {
                    continue;
                }
                for (PhyTableTopology phyTableTopology : phyTableTopologies) {
                    if (!storageInstId.equals(phyTableTopology.getStorageInstId())) {
                        continue;
                    }
                    for (String phyTable : phyTableTopology.getPhyTables()) {
                        boolean result = compareLogicWithPhysical(tso, logicDbTopology.getSchema(),
                            tableMetaTopology.getTableName(), phyTableTopology.getSchema(), phyTable, true);
                        if (!result) {
                            inconsistencyTables.computeIfAbsent(tableMetaTopology.getTableName(), k -> new HashSet<>());
                            inconsistencyTables.get(tableMetaTopology.getTableName()).add(phyTable);
                        }
                        phyTableCount++;
                    }
                }
                logicTableCount++;
            }
            logicDbCount++;
        }

        log.warn("successfully initialized delta change map, cost {}, checked logic db count {}, checked logic table "
                + "count {}, checked phy table count {}, inconsistency Tables {}.", sw, logicDbCount, logicTableCount,
            phyTableCount, JSONObject.toJSONString(inconsistencyTables));
    }

    private void updateDeltaChangeByLogicDdl(String tso, DDLRecord record) {
        if (deltaChangeMap.isEmpty()) {
            return;
        }
        if ("DROP_DATABASE".equals(record.getSqlKind())) {
            removeFromDeltaChangeMap(record.getSchemaName().toLowerCase());
        } else if ("DROP_TABLE".equals(record.getSqlKind())) {
            removeFromDeltaChangeMap(record.getSchemaName(), record.getTableName());
        } else if ("RENAME_TABLE".equals(record.getSqlKind())) {
            removeFromDeltaChangeMap(record.getSchemaName(), record.getTableName());
            TopologyRecord r = GSON.fromJson(record.getMetaInfo(), TopologyRecord.class);
            updateDeltaChangeForOneLogicTable(tso, record.getSchemaName(), getRenameTo(record.getDdlSql()),
                r != null, true);
        } else if (StringUtils.isNotEmpty(record.getTableName())) {
            TopologyRecord r = GSON.fromJson(record.getMetaInfo(), TopologyRecord.class);
            updateDeltaChangeForOneLogicTable(tso, record.getSchemaName(), record.getTableName(),
                r != null, true);
        }

        doPeriodCheck(tso);
    }

    private void doPeriodCheck(String tso) {
        // 定时检测所有的deltaChange,将已经一致的表进行清理，比如
        // 1. ddl任务发生过rollback的场景，物理表先加列，然后删列，都会触发delta change data的变化，由于没有最后的打标，需要定时check
        // 2. 或者一些变态场景，绕过ddl引擎，手动修改了物理表结构，导致和logic不一致，也需要定时check
        long checkInterval = DynamicApplicationConfig.getLong(META_SEMI_SNAPSHOT_DELTA_CHANGE_CHECK_INTERVAL);
        if (System.currentTimeMillis() - lastCheckAllDeltaTime > checkInterval * 1000) {
            Map<String, Set<String>> toRemoveData = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : deltaChangeMap.entrySet()) {
                for (String logicTable : entry.getValue()) {
                    boolean flag = updateDeltaChangeForOneLogicTable(tso, entry.getKey(), logicTable, false, false);
                    if (flag) {
                        toRemoveData.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
                        toRemoveData.get(entry.getKey()).add(logicTable);
                    }
                }
            }
            for (Map.Entry<String, Set<String>> entry : toRemoveData.entrySet()) {
                for (String logicTable : entry.getValue()) {
                    removeFromDeltaChangeMap(entry.getKey(), logicTable);
                }
            }

            log.info("latest delta change data after checking is " + JSONObject.toJSONString(deltaChangeMap));
            checkConsistencyBetweenTopologyAndLogicSchema();
            tryTriggerAlarm();
            lastCheckAllDeltaTime = System.currentTimeMillis();
        }
    }

    private boolean updateDeltaChangeForOneLogicTable(String tso, String logicSchema, String logicTable,
                                                      boolean createPhyIfNotExist,
                                                      boolean directRemoveIfHasRecoverConsistent) {
        Pair<LogicDbTopology, LogicTableMetaTopology> pair = topologyManager.getTopology(logicSchema, logicTable);
        LogicTableMetaTopology logicTableMetaTopology = pair.getRight();
        if (logicTableMetaTopology == null) {
            throw new PolardbxException(
                String.format("logic table meta topology should not be null, logicSchema %s, logicTable %s ,tso %s.",
                    logicSchema, logicTable, tso));
        }
        if (logicTableMetaTopology.getPhySchemas() != null) {
            boolean flag = true;
            for (PhyTableTopology phyTableTopology : logicTableMetaTopology.getPhySchemas()) {
                if (storageInstId.equals(phyTableTopology.getStorageInstId())) {
                    for (String table : phyTableTopology.getPhyTables()) {
                        flag &= compareLogicWithPhysical(tso, logicSchema, logicTable,
                            phyTableTopology.getSchema(), table, createPhyIfNotExist);
                    }
                }
            }
            if (flag && directRemoveIfHasRecoverConsistent) {
                removeFromDeltaChangeMap(logicSchema, logicTable);
            }
            return flag;
        }
        return true;
    }

    private void updateDeltaChangeByPhysicalDdl(String tso, String phySchema, String phyDdl) {
        SQLStatementParser parser = createSQLStatementParser(phyDdl, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> statementList = parser.parseStatementList();
        SQLStatement sqlStatement = statementList.get(0);

        if (sqlStatement instanceof SQLDropTableStatement) {
            SQLDropTableStatement sqlDropTableStatement = (SQLDropTableStatement) sqlStatement;
            for (SQLExprTableSource tableSource : sqlDropTableStatement.getTableSources()) {
                String phyTableName = tableSource.getTableName(true);
                recordDeltaChangeByPhysicalChange(tso, phySchema, phyTableName);
            }
        } else if (sqlStatement instanceof SQLDropDatabaseStatement) {
            String databaseName = ((SQLDropDatabaseStatement) sqlStatement).getDatabaseName();
            databaseName = SQLUtils.normalize(databaseName);
            recordDeltaChangeByPhysicalChange(tso, databaseName, null);
        } else if (sqlStatement instanceof MySqlRenameTableStatement) {
            MySqlRenameTableStatement renameTableStatement = (MySqlRenameTableStatement) sqlStatement;
            for (MySqlRenameTableStatement.Item item : renameTableStatement.getItems()) {
                String tableName = SQLUtils.normalize(item.getName().getSimpleName());
                recordDeltaChangeByPhysicalChange(tso, phySchema, tableName);
            }
        } else if (sqlStatement instanceof SQLAlterTableStatement) {
            SQLAlterTableStatement sqlAlterTableStatement = (SQLAlterTableStatement) sqlStatement;
            String phyTableName = SQLUtils.normalize(sqlAlterTableStatement.getTableName());
            for (SQLAlterTableItem item : sqlAlterTableStatement.getItems()) {
                if (item instanceof SQLAlterTableAddColumn || item instanceof SQLAlterTableDropColumnItem
                    || item instanceof MySqlAlterTableChangeColumn) {
                    recordDeltaChangeByPhysicalChange(tso, phySchema, phyTableName);
                    break;
                }
            }
        }
    }

    private String getRenameTo(String ddl) {
        SQLStatementParser parser = createSQLStatementParser(ddl, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> statementList = parser.parseStatementList();
        SQLStatement sqlStatement = statementList.get(0);

        if (sqlStatement instanceof MySqlRenameTableStatement) {
            MySqlRenameTableStatement renameTableStatement = (MySqlRenameTableStatement) sqlStatement;
            for (MySqlRenameTableStatement.Item item : renameTableStatement.getItems()) {
                return SQLUtils.normalize(item.getTo().getSimpleName());
            }
        }
        throw new PolardbxException("not a rename ddl sql :" + ddl);
    }

    private void recordDeltaChangeByPhysicalChange(String tso, String phySchema, String phyTable) {
        if (StringUtils.isBlank(phyTable)) {
            LogicDbTopology logicDbTopology = topologyManager.getLogicSchema(phySchema);
            // 如果拓扑中保存的phySchema对应的storageInstId和当前的storageInstId不匹配，则不进行记录
            // 什么情况下会出现不匹配？比如执行move database命令时，把physical_db_1从dn1 move到 dn2，
            // 然后清理dn1上的physical_db_1，此时dn1会受到drop database命令，需要忽略
            if (logicDbTopology != null && checkStorageInstId(tso, phySchema)) {
                addToDeltaChangMap(logicDbTopology.getSchema(), null);
            }
        } else {
            LogicDbTopology logicTopology = topologyManager.getLogicSchema(phySchema, phyTable);
            if (logicTopology == null || logicTopology.getLogicTableMetas() == null
                || logicTopology.getLogicTableMetas().get(0) == null) {
                return;
            }
            if (checkStorageInstId(tso, phySchema)) {
                addToDeltaChangMap(logicTopology.getSchema().toLowerCase(),
                    logicTopology.getLogicTableMetas().get(0).getTableName().toLowerCase());
            }
        }
        log.info("record delta change by physical change , with tso {}.", tso);
    }

    private boolean checkStorageInstId(String tso, String phySchema) {
        String storageInstIdInTopology = topologyManager.getStorageInstIdByPhySchema(phySchema);
        if (!storageInstId.equals(storageInstIdInTopology)) {
            log.info("receive a physical sql whose schema existing in topology but its storageInstId in topology is "
                    + "different with storageInstId in current meta manager, tso is {}, physical schema is {}, "
                    + "storageInstId in topology is {}, storageInstId in current meta manager is {}. ", tso,
                phySchema, storageInstIdInTopology, storageInstId);
            return false;
        } else {
            return true;
        }
    }

    private void addToDeltaChangMap(String logicSchema, String logicTable) {
        deltaChangeMap.computeIfAbsent(logicSchema.toLowerCase(), k -> new HashSet<>());
        if (StringUtils.isNotBlank(logicTable)) {
            deltaChangeMap.get(logicSchema.toLowerCase()).add(logicTable.toLowerCase());
        }
    }

    private void removeFromDeltaChangeMap(String logicSchema) {
        deltaChangeMap.remove(logicSchema.toLowerCase());
    }

    private void removeFromDeltaChangeMap(String logicSchema, String logicTable) {
        if (deltaChangeMap.containsKey(logicSchema.toLowerCase())) {
            Set<String> deltaTables = deltaChangeMap.get(logicSchema.toLowerCase());
            deltaTables.remove(logicTable.toLowerCase());
            if (deltaTables.isEmpty()) {
                deltaChangeMap.remove(logicSchema.toLowerCase());
            }
        }
    }

    private boolean compareLogicWithPhysical(String tso, String logicSchemaName, String logicTableName,
                                             String phySchemaName, String phyTableName, boolean createPhyIfNotExist) {
        // get table meta
        // 先从distinctPhyMeta查，如果没查到，说明物理表和逻辑表的列序是一致的，如果查到了，在进行对比的时候必须以此为准
        TableMeta logicDimTableMeta = polarDbXLogicTableMeta.findDistinctPhy(logicSchemaName, logicTableName);
        if (logicDimTableMeta == null) {
            logicDimTableMeta = polarDbXLogicTableMeta.find(logicSchemaName, logicTableName);
        }
        TableMeta phyDimTableMeta = createPhyIfNotExist ? findPhyTable(phySchemaName, phyTableName) :
            polarDbXStorageTableMeta.find(phySchemaName, phyTableName);

        // check meta if null
        if (logicDimTableMeta == null) {
            String message = String.format("compare failed, can`t find logic table meta %s:%s, with tso %s.",
                logicSchemaName, logicTableName, tso);
            throw new PolardbxException(message);
        }
        if (phyDimTableMeta == null) {
            addToDeltaChangMap(logicSchemaName, logicTableName);
            log.info("can`t find physical table meta, will record it to deltaChangeMap, phySchema {}, phyTable {}, "
                + "tso {}.", phySchemaName, phyTableName, tso);
            return false;
        }

        //compare table meta
        List<String> logicDimColumns = logicDimTableMeta.getFields().stream()
            .map(f -> SQLUtils.normalize(f.getColumnName().toLowerCase())).collect(Collectors.toList());
        List<String> phyDimColumns = phyDimTableMeta.getFields().stream()
            .map(f -> SQLUtils.normalize(f.getColumnName().toLowerCase())).collect(Collectors.toList());
        boolean result = logicDimColumns.equals(phyDimColumns);
        if (!result) {
            addToDeltaChangMap(logicSchemaName, logicTableName);
            log.warn("logic and phy meta is not consistent, will record it to deltaChangeMap, logicSchema {},"
                    + " logicTable {}, phySchema {}, phyTable {},logicColumns {}, phy Columns {}, tso {}.",
                logicSchemaName, logicTableName, phySchemaName, phyTableName, logicDimColumns, phyDimColumns, tso);
            return false;
        }

        return true;
    }

    private void tryUpdateSemiSnapshotPosition(String tso) {
        if (deltaChangeMap.isEmpty()) {
            try {
                SemiSnapshotInfoMapper mapper = SpringContextHolder.getObject(SemiSnapshotInfoMapper.class);
                SemiSnapshotInfo info = new SemiSnapshotInfo();
                info.setTso(tso);
                info.setStorageInstId(storageInstId);
                mapper.insert(info);
            } catch (DuplicateKeyException e) {
                if (log.isDebugEnabled()) {
                    log.debug("semi snapshot point has existed for tso " + tso);
                }
            }
        } else {
            log.info("it is not a consistent semi snapshot point for tso {}, deltChangData is {}.",
                tso, JSONObject.toJSONString(deltaChangeMap));
        }
    }

    private String getSuitableSemiSnapshotTso(String snapshotTso, String rollbackTso) {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        return metaJdbcTemplate.queryForObject(
            "select max(tso) tso from binlog_semi_snapshot where tso > '" + snapshotTso +
                "' and tso <= '" + rollbackTso + "' and storage_inst_id = '" + storageInstId + "'", String.class);
    }

    private void checkConsistencyBetweenTopologyAndLogicSchema() {
        //看一下拓扑中的逻辑表是否都存在，fastsql之前出现过bug，创建一个和表名同名的索引，索引会把表覆盖掉，这里做一个校验
        List<LogicDbTopology> logicDbTopologies = topologyManager.getTopology().getLogicDbMetas();
        for (LogicDbTopology logicSchema : logicDbTopologies) {
            String schema = logicSchema.getSchema();
            if (logicSchema.getLogicTableMetas() == null || logicSchema.getLogicTableMetas().isEmpty()) {
                continue;
            }
            for (LogicTableMetaTopology tableMetaTopology : logicSchema.getLogicTableMetas()) {
                TableMeta tableMeta = polarDbXLogicTableMeta.find(schema, tableMetaTopology.getTableName());
                if (tableMeta == null) {
                    throw new PolardbxException(String.format("checking consistency failed, logic table is not found,"
                        + " %s:%s.", schema, tableMetaTopology.getTableName()));
                }
            }
        }
    }

    private void tryTriggerAlarm() {
        try {
            if (!deltaChangeMap.isEmpty()) {
                JdbcTemplate polarxTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
                List<Map<String, Object>> list = polarxTemplate.queryForList("show ddl");
                //如果ddl引擎中已经没有任务了，但是还有delta change data，可能出现了bug，触发报警
                if (list.isEmpty()) {
                    MonitorManager.getInstance()
                        .triggerAlarm(META_DATA_INCONSISTENT_WARNNIN, JSONObject.toJSONString(deltaChangeMap));
                }
            }
        } catch (Throwable t) {
            log.error("send alarm error!", t);
        }
    }
    //------------------------------------------拓扑相关---------------------------------------

    /**
     * 通过物理库获取逻辑库信息
     */
    public LogicDbTopology getLogicSchema(String phySchema) {
        return topologyManager.getLogicSchema(phySchema);
    }

    /**
     * 通过物理库表获取逻辑库表信息
     */
    public LogicDbTopology getLogicSchema(String phySchema, String phyTable) {
        return topologyManager.getLogicSchema(phySchema, phyTable);
    }

    /**
     * 获取存储实例id下面的所有物理库表信息
     */
    public List<PhyTableTopology> getPhyTables(String storageInstId) {
        return topologyManager.getPhyTables(storageInstId);
    }

    public Pair<LogicDbTopology, LogicTableMetaTopology> getTopology(String logicSchema, String logicTable) {
        return topologyManager.getTopology(logicSchema, logicTable);
    }

    public LogicMetaTopology getTopology() {
        return topologyManager.getTopology();
    }

}
