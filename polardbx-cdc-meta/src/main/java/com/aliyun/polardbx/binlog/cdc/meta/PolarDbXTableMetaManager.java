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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAlterTableModifyColumn;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta.FieldMetaExt;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicBasicInfo;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicDbTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.LogicTableMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology.PhyTableTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistCleanPointDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistCleanPointMapper;
import com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistCleanPoint;
import com.aliyun.polardbx.binlog.domain.po.SemiSnapshotInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.util.DNStorageSqlExecutor;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_RECORD_IGNORED_DDL_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SEMI_SNAPSHOT_CHECK_DELTA_INTERVAL_SEC;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SEMI_SNAPSHOT_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_CACHE_COMPARE_RESULT_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_RETRIEVE_INSTANT_CREATE_TABLE_MODES;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.cdc.meta.RollbackMode.SNAPSHOT_EXACTLY;
import static com.aliyun.polardbx.binlog.cdc.meta.RollbackMode.SNAPSHOT_SEMI;
import static com.aliyun.polardbx.binlog.cdc.meta.RollbackMode.SNAPSHOT_UNSAFE;
import static com.aliyun.polardbx.binlog.monitor.MonitorType.META_DATA_INCONSISTENT_WARNNIN;
import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;
import static com.aliyun.polardbx.binlog.util.SQLUtils.buildCreateLikeSql;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isGreaterThanOrEqualTo;

/**
 * Created by ShuGuang & ziyang.lb
 */
@Slf4j
public class PolarDbXTableMetaManager {
    private static final String DN_SUPPORT_HIDDEN_PK_QUERY = "show global variables  like 'implicit_primary_key'";
    private static final String DN_VERSION_QUERY = "select version()";
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final String storageInstId;
    private final Map<String, Set<String>> deltaChangeMap;
    private final boolean enableCompareCache;
    private final Map<String, LogicTableMeta> compareCache;
    private final RollbackMode rollbackMode;
    private final boolean supportHiddenPk;
    private TopologyManager topologyManager;
    private PolarDbXLogicTableMeta polarDbXLogicTableMeta;
    private PolarDbXStorageTableMeta polarDbXStorageTableMeta;
    private ConsistencyChecker consistencyChecker;
    private long lastCheckAllDeltaTime;
    private long rollbackCostTime = -1L;
    private String lastApplyLogicTSO;
    private final String dnVersion;

    private String defaultDatabaseCharset;

    public PolarDbXTableMetaManager(String storageInstId) {
        this(storageInstId, () -> {
            DNStorageSqlExecutor executor = new DNStorageSqlExecutor(storageInstId);
            try {
                List<LinkedHashMap<String, Object>> result = executor.executeQuery(DN_SUPPORT_HIDDEN_PK_QUERY);
                return !result.isEmpty() && StringUtils.equalsIgnoreCase(
                    String.valueOf(result.get(0).get("Value")), "ON");
            } catch (SQLException ex) {
                throw new PolardbxException("check dn support hidden pk failed! " + storageInstId, ex);
            }
        }, () -> {
            try {
                DNStorageSqlExecutor executor = new DNStorageSqlExecutor(storageInstId);
                List<LinkedHashMap<String, Object>> result = executor.executeQuery(DN_VERSION_QUERY);
                if (!result.isEmpty()) {
                    String dnVersion = (String) result.get(0).get("version()");
                    log.info("dn version is : " + dnVersion);
                    return dnVersion.trim();
                } else {
                    return "5.7";
                }
            } catch (SQLException ex) {
                throw new PolardbxException("check dn support hidden pk failed! " + storageInstId, ex);
            }
        });
    }

    public PolarDbXTableMetaManager(String storageInstId,
                                    Supplier<Boolean> hiddenPkSupplier,
                                    Supplier<String> dnVersionSupplier) {
        this.storageInstId = storageInstId;
        this.deltaChangeMap = new HashMap<>();
        this.enableCompareCache = getBoolean(META_CACHE_COMPARE_RESULT_ENABLED);
        this.compareCache = new HashMap<>();
        this.rollbackMode = getRollbackMode();
        this.supportHiddenPk = hiddenPkSupplier.get();
        this.dnVersion = dnVersionSupplier.get();
    }

    public void init() {
        if (initialized.compareAndSet(false, true)) {
            this.topologyManager = new TopologyManager();

            this.polarDbXLogicTableMeta = new PolarDbXLogicTableMeta(this.topologyManager, dnVersion);
            this.polarDbXLogicTableMeta.init(null);

            this.polarDbXStorageTableMeta = new PolarDbXStorageTableMeta(storageInstId,
                polarDbXLogicTableMeta, topologyManager, dnVersion);
            this.polarDbXStorageTableMeta.init(null);

            this.consistencyChecker = new ConsistencyChecker(topologyManager, polarDbXLogicTableMeta,
                this, storageInstId);
            this.registerToMetaMonitor();
        }
    }

    public void destroy() {
        this.polarDbXStorageTableMeta.destroy();
        this.polarDbXLogicTableMeta.destroy();
        this.unregisterToCleaner();
    }

    public void onStart(String defaultDatabaseCharset) {
        this.defaultDatabaseCharset = defaultDatabaseCharset;
        this.polarDbXStorageTableMeta.getRepository().setDefaultCharset(defaultDatabaseCharset);
    }

    public TableMeta findPhyTable(String schema, String table, boolean createIfNotExist) {
        TableMeta phy = findPhyTableInternal(schema, table);
        if (phy == null && supportInstantCreatTableWhenNotfound() && createIfNotExist) {
            instantCreatePhyTable(schema, table);
            phy = findPhyTableInternal(schema, table);
        }
        return phy;
    }

    private void instantCreatePhyTable(String schema, String table) {
        LogicBasicInfo logicBasicInfo = getLogicBasicInfo(schema, table);
        if (logicBasicInfo != null && StringUtils.isNotBlank(logicBasicInfo.getTableName())) {
            log.info("phy table meta is not found for {}:{}, will instantly try to create for compensation.",
                schema, table);
            String logicSchema = logicBasicInfo.getSchemaName();
            String logicTable = logicBasicInfo.getTableName();
            TableMeta distinctPhyTableMeta = polarDbXLogicTableMeta.findDistinctPhy(logicSchema, logicTable);
            if (distinctPhyTableMeta == null) {
                String ddl = polarDbXLogicTableMeta.snapshot(logicSchema, logicTable);
                createNotExistPhyTable(logicSchema, schema, logicTable, table, ddl);
            } else {
                String ddl = polarDbXLogicTableMeta.distinctPhySnapshot(logicSchema, logicTable);
                createNotExistPhyTable(logicSchema, schema, logicTable, table, ddl);
            }
        }
    }

    private TableMeta findPhyTableInternal(String schema, String table) {
        TableMeta phy = polarDbXStorageTableMeta.find(schema, table);
        if (phy != null && StringUtils.isBlank(phy.getCharset())) {
            // 如果没有charset，则从逻辑表中获取一下
            LogicBasicInfo logicBasicInfo = getLogicBasicInfo(schema, table);
            if (logicBasicInfo != null) {
                TableMeta logicTableMeta =
                    findLogicTable(logicBasicInfo.getSchemaName(), logicBasicInfo.getTableName());
                if (logicTableMeta != null) {
                    phy.setCharset(logicTableMeta.getCharset());
                }
            }
        }
        return phy;
    }

    private void registerToMetaMonitor() {
        MetaMonitor.getInstance().register(storageInstId, this);
    }

    private void unregisterToCleaner() {
        MetaMonitor.getInstance().unregister(storageInstId);
    }

    private void createNotExistPhyTable(String logicSchema, String phySchema, String logicTable, String phyTable,
                                        String ddl) {
        String createSql = buildCreateLikeSql(phyTable, logicSchema, logicTable);
        polarDbXStorageTableMeta.apply(logicSchema, ddl);
        polarDbXStorageTableMeta.apply(phySchema, createSql);
        polarDbXStorageTableMeta.apply(logicSchema, "drop database `" + logicSchema + "`");
    }

    public TableMeta findLogicTable(String schema, String table) {
        TableMeta logicTableMeta = polarDbXLogicTableMeta.find(schema, table);
        if (logicTableMeta != null && StringUtils.isBlank(logicTableMeta.getCharset())) {
            logicTableMeta.setCharset(defaultDatabaseCharset);
        }
        return logicTableMeta;
    }

    public LogicTableMeta compare(String schema, String table, int columnCount) {
        String cacheKey = schema + ":" + table + ":" + columnCount;
        if (enableCompareCache) {
            LogicTableMeta cacheValue = compareCache.get(cacheKey);
            if (cacheValue != null) {
                return cacheValue;
            }
        }

        TableMeta phy = findPhyTable(schema, table, true);
        Preconditions.checkNotNull(phy, "TableMeta is not found for physical table " + schema + "." + table);

        LogicBasicInfo logicTopology = getLogicBasicInfo(schema, table);
        Preconditions.checkArgument(logicTopology != null && StringUtils.isNotBlank(logicTopology.getTableName()),
            "Logic TableMeta is not found for physical table " + schema + "." + table + " , found result is "
                + logicTopology);

        TableMeta logic =
            findLogicTable(logicTopology.getSchemaName(), logicTopology.getTableName());
        Preconditions.checkNotNull(logic, "phyTable [" + schema + "." + table + "], logic tableMeta["
            + logicTopology.getSchemaName() + "." + logicTopology.getTableName()
            + "] should not be null!");
        boolean hasRdsHiddenPK = false;
        boolean forceRebuild =
            getBoolean(ConfigKeys.TASK_REFORMAT_EVENT_FORCE_ENABLED);
        if (phy.getFields().size() != columnCount) {
            // ddl 中的列和binlog中数量对不上，可能有隐藏主键
            String key = "`" + escape(schema) + "`.`" + escape(table) + "`";
            String errorMsg = String.format("find row data column len [%s] not equal to table meta column len [%s], "
                    + " and test rds hidden pk failed! table name : %s, phy table meta : %s", columnCount,
                phy.getFields().size(), key, phy);
            boolean ignoreError =
                getBoolean(ConfigKeys.TASK_REFORMAT_IGNORE_MISMATCHED_COLUMN_ERROR);

            // 此处改为常量，
            if (supportHiddenPk && phy.getPrimaryFields().isEmpty()) {
                hasRdsHiddenPK = true;
            }

            if (!ignoreError && !hasRdsHiddenPK) {
                log.error(errorMsg);
                throw new PolardbxException(errorMsg);
            }
            if (ignoreError) {
                forceRebuild = true;
            }

        }

        final List<String> columnNames = phy.getFields().stream().map(FieldMeta::getColumnName).collect(
            Collectors.toList());
        LogicTableMeta meta = new LogicTableMeta();
        meta.setLogicSchema(logic.getSchema());
        meta.setLogicTable(logic.getTable());
        meta.setPhySchema(schema);
        meta.setPhyTable(table);
        meta.setCompatible(phy.getFields().size() == logic.getFields().size());
        FieldMeta hiddenPK = null;
        int logicIndex = 0;
        for (int i = 0; i < logic.getFields().size(); i++) {
            FieldMeta fieldMeta = logic.getFields().get(i);
            final int x = columnNames.indexOf(fieldMeta.getColumnName());
            if (x != logicIndex) {
                meta.setCompatible(false);
            }
            if (fieldMeta.isKey()) {
                meta.addPk(new FieldMetaExt(fieldMeta, -1, x));
            }
            // 隐藏主键忽略掉
            if (SystemDB.isDrdsImplicitId(fieldMeta.getColumnName())) {
                meta.setCompatible(false);
                hiddenPK = fieldMeta;
                continue;
            }

            FieldMetaExt destFieldMeta = new FieldMetaExt(fieldMeta, logicIndex++, x);
            if (x != -1) {
                FieldMeta phyField = phy.getFields().get(x);
                String defaultLogicCharset = logic.getCharset();
                String defaultPhyCharset = phy.getCharset();
                if (getBoolean(ConfigKeys.TASK_REFORMAT_COLUMN_TYPE_ENABLED)
                    && !columnTypeMatch(fieldMeta, phyField, defaultLogicCharset, defaultPhyCharset)) {
                    destFieldMeta.setTypeNotMatch();
                    destFieldMeta.setPhyFieldMeta(phyField);
                    meta.setCompatible(false);
                }
            }
            meta.add(destFieldMeta);
        }
        // 如果有隐藏主键，直接放到最后
        if (hiddenPK != null && getBoolean(ConfigKeys.TASK_REFORMAT_ATTACH_DRDS_HIDDEN_PK_ENABLED)) {
            final int x = columnNames.indexOf(hiddenPK.getColumnName());
            meta.add(new FieldMetaExt(hiddenPK, logicIndex, x));
        }

        if (hasRdsHiddenPK) {
            meta.setCompatible(false);
        }

        if (forceRebuild) {
            meta.setCompatible(false);
            log.warn("force rebuild meta is not compatible, return meta {}, logic TableMeta {}, phy TableMeta {}", meta,
                logic, phy);
        }

        //对于含有隐藏主键的表，不输出日志，避免日志膨胀
        if (!meta.isCompatible() && !hasRdsHiddenPK && hiddenPK == null) {
            log.warn("meta is not compatible, return meta {}, logic TableMeta {}, phy TableMeta {}", meta, logic, phy);
        }

        if (enableCompareCache) {
            compareCache.computeIfAbsent(cacheKey, k -> meta);
        }
        return meta;
    }

    private boolean columnTypeMatch(FieldMeta logicField, FieldMeta phyField, String logicCharset, String phyCharset) {
        if (StringUtils.isNotBlank(logicField.getCharset())) {
            logicCharset = logicField.getCharset();
        }
        if (StringUtils.isNotBlank(phyField.getCharset())) {
            phyCharset = phyField.getCharset();
        }
        return StringUtils.equalsIgnoreCase(logicField.getColumnType(), phyField.getColumnType()) &&
            StringUtils.equalsIgnoreCase(logicCharset, phyCharset);
    }

    public void applyBase(BinlogPosition position, LogicMetaTopology topology, String cmdId) {
        this.compareCache.clear();
        this.polarDbXLogicTableMeta.applyBase(position, topology, cmdId);
        this.polarDbXStorageTableMeta.applyBase(position);
    }

    public void applyLogic(BinlogPosition position, DDLRecord record, String cmdId) {
        this.compareCache.clear();
        if (isIgnoreApply(position, record)) {
            if (getBoolean(META_BUILD_RECORD_IGNORED_DDL_ENABLED)) {
                polarDbXLogicTableMeta.applyToDb(position, record, MetaType.DDL.getValue(), cmdId, false);
            }
            return;
        }

        boolean result = this.polarDbXLogicTableMeta.apply(position, record, cmdId);
        //只有发生了Actual Apply Operation，才进行后续处理
        if (result) {
            if (StringUtils.isBlank(record.getDdlSql())) {
                return;
            }
            this.processSnapshotSemi(position, record);
            //对拓扑和表结构进行一致性对比，正常情况下，每个表执行完一个逻辑DDL后，都应该是一个一致的状态，如果不一致说明出现了问题
            this.consistencyChecker.checkTopologyConsistencyWithOrigin(position.getRtso(), record);
            this.consistencyChecker.checkLogicAndPhysicalConsistency(position.getRtso(), record);
        }
        lastApplyLogicTSO = position.getRtso();
    }

    public void applyPhysical(BinlogPosition position, String schema, String ddl, String extra) {
        this.compareCache.clear();
        this.polarDbXStorageTableMeta.apply(position, schema, ddl, extra);
        this.updateDeltaChangeByPhysicalDdl(position.getRtso(), schema, ddl);
    }

    public void rollback(BinlogPosition position) {
        this.compareCache.clear();
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
        rollbackCostTime = sw.elapsed(TimeUnit.MILLISECONDS);
        log.warn("successfully rollback to tso:{}, cost {}", position.getRtso(), sw);
    }

    public Map<String, String> snapshot() {
        log.info("Logic: {}", polarDbXLogicTableMeta.snapshot());
        log.info("Storage: {}", polarDbXStorageTableMeta.snapshot());
        throw new RuntimeException("not support for PolarDbXTableMetaManager");
    }

    public Set<String> findIndexes(String schema, String table) {
        return polarDbXLogicTableMeta.find(schema, table).getIndexes().keySet();
    }

    /**
     * 从存储中获取小于等于rollback tso的最新一次Snapshot的位点
     */
    protected String getLatestSnapshotTso(String rollbackTso) {
        JdbcTemplate metaJdbcTemplate = getObject("metaJdbcTemplate");
        return metaJdbcTemplate.queryForObject(
            "select max(tso) tso from binlog_logic_meta_history where tso <= '" + rollbackTso +
                "' and type = " + MetaType.SNAPSHOT.getValue(), String.class);
    }

    /**
     * 从存储中获取小于等于rollback tso的最新一次的位点
     */
    protected String getLatestLogicDDLTso(String rollbackTso) {
        JdbcTemplate metaJdbcTemplate = getObject("metaJdbcTemplate");
        return metaJdbcTemplate.queryForObject(
            "select max(tso) tso from binlog_logic_meta_history where tso <= '" + rollbackTso + "' +"
                + "and type = " + MetaType.DDL.getValue(), String.class);
    }

    private void processSnapshotSemi(BinlogPosition position, DDLRecord record) {
        boolean enableSemi = getBoolean(META_BUILD_SEMI_SNAPSHOT_ENABLED);
        if (rollbackMode == SNAPSHOT_SEMI || enableSemi) {
            this.updateDeltaChangeByLogicDdl(position.getRtso(), record);
            this.tryUpdateSemiSnapshotPosition(position.getRtso());
        }
    }

    private void checkSafetyOfSnapshotTso(String snapshotTso) {
        BinlogPhyDdlHistCleanPointMapper cleanPointMapper = getObject(BinlogPhyDdlHistCleanPointMapper.class);
        List<BinlogPhyDdlHistCleanPoint> list = cleanPointMapper.select(
            s -> s.where(BinlogPhyDdlHistCleanPointDynamicSqlSupport.storageInstId, isEqualTo(storageInstId))
                .and(BinlogPhyDdlHistCleanPointDynamicSqlSupport.tso, isGreaterThanOrEqualTo(snapshotTso)));
        if (!list.isEmpty()) {
            throw new PolardbxException(String.format("can`t rollback in SNAPSHOT_EXACTLY mode with snapshot tso %s ,"
                    + " because there exists clean point tso %s which is greater than snapshot tso!", snapshotTso,
                list.get(0).getTso()));
        }
    }

    /**
     * 获取回滚模式
     */
    private RollbackMode getRollbackMode() {
        RollbackMode mode = RollbackModeUtil.getRollbackMode();
        log.info("random selected rollback mode is " + mode);
        return mode;
    }

    /**
     * 在不出现bug的情况下，只有SNAPSHOT_SEMI 和 SNAPSHOT_UNSAFE才有必要
     */
    private boolean supportInstantCreatTableWhenNotfound() {
        String configStr = DynamicApplicationConfig.getString(META_RETRIEVE_INSTANT_CREATE_TABLE_MODES);
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
        checkSafetyOfSnapshotTso(snapshotTso);

        polarDbXLogicTableMeta.applySnapshot(snapshotTso);
        polarDbXStorageTableMeta.applySnapshot(snapshotTso);
        polarDbXLogicTableMeta.applyHistory(snapshotTso, position.getRtso());
        polarDbXStorageTableMeta.applyHistory(snapshotTso, position.getRtso());
    }

    public int buildSnapshot(BinlogPosition position, String topology, String cmdId) {
        JSONArray array = JSON.parseObject(topology).getJSONArray("logicDbMetas");
        JSONObject ddlObj = new JSONObject();
        for (int i = 0; i < array.size(); i++) {
            JSONObject object = array.getJSONObject(i);
            JSONArray subArray = object.getJSONArray("logicTableMetas");
            String scheamName = object.getString("schema");
            StringBuilder sb = new StringBuilder();
            String createSql = null;
            for (int j = 0; j < subArray.size(); i++) {
                JSONObject createSqlObj = subArray.getJSONObject(j);
                createSql = createSqlObj.getString("createSql");
                sb.append(createSql);
                if (createSql != null) {
                    break;
                }
            }
            log.warn(scheamName + " : " + createSql);
        }
        DDLRecord ddlRecord = DDLRecord.builder().schemaName("*").ddlSql("").metaInfo(topology).build();
        log.warn("build snapshot for : " + JSON.toJSONString(ddlRecord));
        return polarDbXLogicTableMeta.applyToDb(position, ddlRecord, MetaType.SNAPSHOT.getValue(), cmdId, true);
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

    Map<String, Set<String>> initDeltaChangeMap(String tso) {
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
        return inconsistencyTables;
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
            TopologyRecord r = JSONObject.parseObject(record.getMetaInfo(), TopologyRecord.class);
            updateDeltaChangeForOneLogicTable(tso, record.getSchemaName(), getRenameTo(record.getDdlSql()),
                r != null, true);
        } else if (StringUtils.isNotEmpty(record.getTableName())) {
            TopologyRecord r = JSONObject.parseObject(record.getMetaInfo(), TopologyRecord.class);
            updateDeltaChangeForOneLogicTable(tso, record.getSchemaName(), record.getTableName(),
                r != null, true);
        }

        doPeriodCheck(tso);
    }

    private void doPeriodCheck(String tso) {
        // 定时检测所有的deltaChange,将已经一致的表进行清理，比如
        // 1. ddl任务发生过rollback的场景，物理表先加列，然后删列，都会触发delta change data的变化，由于没有最后的打标，需要定时check
        // 2. 或者一些变态场景，绕过ddl引擎，手动修改了物理表结构，导致和logic不一致，也需要定时check
        long checkInterval = DynamicApplicationConfig.getLong(META_BUILD_SEMI_SNAPSHOT_CHECK_DELTA_INTERVAL_SEC);
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
        SQLStatement sqlStatement = parseSQLStatement(phyDdl);

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
                    || item instanceof MySqlAlterTableChangeColumn || item instanceof MySqlAlterTableModifyColumn) {
                    recordDeltaChangeByPhysicalChange(tso, phySchema, phyTableName);
                    break;
                }
            }
        }
    }

    private String getRenameTo(String ddl) {
        SQLStatement sqlStatement = parseSQLStatement(ddl);

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
            String logicSchemaName = topologyManager.getLogicSchema(phySchema);
            // 如果拓扑中保存的phySchema对应的storageInstId和当前的storageInstId不匹配，则不进行记录
            // 什么情况下会出现不匹配？比如执行move database命令时，把physical_db_1从dn1 move到 dn2，
            // 然后清理dn1上的physical_db_1，此时dn1会受到drop database命令，需要忽略
            if (logicSchemaName != null && checkStorageInstId(tso, phySchema)) {
                addToDeltaChangMap(logicSchemaName, null);
            }
        } else {
            LogicBasicInfo logicBasicInfo = topologyManager.getLogicBasicInfo(phySchema, phyTable);
            if (logicBasicInfo == null || StringUtils.isBlank(logicBasicInfo.getTableName())) {
                return;
            }
            if (checkStorageInstId(tso, phySchema)) {
                addToDeltaChangMap(logicBasicInfo.getSchemaName().toLowerCase(),
                    logicBasicInfo.getTableName().toLowerCase());
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
        if (log.isDebugEnabled()) {
            log.debug("prepare to compare logic with physical, {}:{}:{}:{}:{}:{}", tso, logicSchemaName, logicTableName,
                phySchemaName, phyTableName, createPhyIfNotExist);
        }

        if (MetaFilter.isDbInApplyBlackList(logicSchemaName)) {
            return true;
        }
        if (MetaFilter.isTableInApplyBlackList(logicSchemaName + "." + logicTableName)) {
            return true;
        }

        // get table meta
        // 先从distinctPhyMeta查，如果没查到，说明物理表和逻辑表的列序是一致的，如果查到了，在进行对比的时候必须以此为准
        TableMeta logicDimTableMeta = polarDbXLogicTableMeta.findDistinctPhy(logicSchemaName, logicTableName);
        if (logicDimTableMeta == null) {
            logicDimTableMeta = polarDbXLogicTableMeta.find(logicSchemaName, logicTableName);
        }
        TableMeta phyDimTableMeta = findPhyTable(phySchemaName, phyTableName, createPhyIfNotExist);

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
        List<Triple<String, String, String>> logicDimColumns = logicDimTableMeta.getFields().stream()
            .map(f -> Triple.of(SQLUtils.normalize(f.getColumnName().toLowerCase()), f.getColumnType().toLowerCase(),
                StringUtils.lowerCase(f.getCharset())))
            .collect(Collectors.toList());
        List<Triple<String, String, String>> phyDimColumns = phyDimTableMeta.getFields().stream()
            .map(f -> Triple.of(SQLUtils.normalize(f.getColumnName().toLowerCase()), f.getColumnType().toLowerCase(),
                StringUtils.lowerCase(f.getCharset())))
            .collect(Collectors.toList());
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
                SemiSnapshotInfoMapper mapper = getObject(SemiSnapshotInfoMapper.class);
                SemiSnapshotInfo info = new SemiSnapshotInfo();
                info.setTso(tso);
                info.setStorageInstId(storageInstId);
                mapper.insertSelective(info);
            } catch (DuplicateKeyException e) {
                if (log.isDebugEnabled()) {
                    log.debug("semi snapshot point has existed for tso " + tso);
                }
            }
        } else {
            log.info("it is not a consistent semi snapshot point for tso {}, deltaChangData is {}.",
                tso, JSONObject.toJSONString(deltaChangeMap));
        }
    }

    private String getSuitableSemiSnapshotTso(String snapshotTso, String rollbackTso) {
        JdbcTemplate metaJdbcTemplate = getObject("metaJdbcTemplate");
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
            if (MetaFilter.isDbInApplyBlackList(logicSchema.getSchema())) {
                continue;
            }
            for (LogicTableMetaTopology tableMetaTopology : logicSchema.getLogicTableMetas()) {
                String fullTableName = schema + "." + tableMetaTopology.getTableName();
                if (MetaFilter.isTableInApplyBlackList(fullTableName)) {
                    continue;
                }
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
                JdbcTemplate polarxTemplate = getObject("polarxJdbcTemplate");
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

    private boolean isIgnoreApply(BinlogPosition position, DDLRecord record) {
        if (!StringUtils.isBlank(lastApplyLogicTSO) && lastApplyLogicTSO.compareTo(position.getRtso()) >= 0) {
            log.warn("logic ddl apply is ignored by duplicate record, with tso {}, record detail is {}.",
                position.getRtso(), position.getRtso());
            return true;
        }

        if (MetaFilter.isDbInApplyBlackList(record.getSchemaName())) {
            log.warn("logic ddl apply is ignored by database blacklist, with tso {}, record detail is {}.",
                position.getRtso(), record);
            return true;
        }

        String fullTableName = record.getSchemaName() + "." + record.getTableName();
        if (MetaFilter.isTableInApplyBlackList(fullTableName)) {
            log.warn("logic ddl apply is ignored by table blacklist, with tso {}, record detail is {}.",
                position.getRtso(), record);
            return true;
        }

        if (MetaFilter.isTsoInApplyBlackList(position.getRtso())) {
            log.warn("logic ddl apply is ignored by tso blacklist, with tso {}, record detail is {}.",
                position.getRtso(), record);
            return true;
        }

        if (!MetaFilter.isSupportApply(record)) {
            log.warn("logic ddl apply is ignored by sql-kind blacklist, with tso {}, record detail is {}.",
                position.getRtso(), record);
            return true;
        }

        return false;
    }

    /**
     * 通过物理库获取逻辑库信息
     */
    public String getLogicSchema(String phySchema) {
        return topologyManager.getLogicSchema(phySchema);
    }
    //------------------------------------------拓扑相关---------------------------------------

    /**
     * 通过物理库表获取逻辑库表信息
     */
    public LogicBasicInfo getLogicBasicInfo(String phySchema, String phyTable) {
        return topologyManager.getLogicBasicInfo(phySchema, phyTable);
    }

    /**
     * 获取存储实例id下面的所有物理库表信息
     */
    public List<PhyTableTopology> getPhyTables(String storageInstId, Set<String> excludeLogicDbs,
                                               Set<String> excludeLogicTables) {
        return topologyManager.getPhyTables(storageInstId, excludeLogicDbs, excludeLogicTables);
    }

    public Pair<LogicDbTopology, LogicTableMetaTopology> getTopology(String logicSchema, String logicTable) {
        return topologyManager.getTopology(logicSchema, logicTable);
    }

    public LogicMetaTopology getTopology() {
        return topologyManager.getTopology();
    }

    public PolarDbXLogicTableMeta getPolarDbXLogicTableMeta() {
        return polarDbXLogicTableMeta;
    }

    public PolarDbXStorageTableMeta getPolarDbXStorageTableMeta() {
        return polarDbXStorageTableMeta;
    }

    public long getRollbackCostTime() {
        return rollbackCostTime;
    }

    ConsistencyChecker getConsistencyChecker() {
        return consistencyChecker;
    }

    Map<String, Set<String>> getDeltaChangeMap() {
        return deltaChangeMap;
    }
}
