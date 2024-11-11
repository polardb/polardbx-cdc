/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterFunctionStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterSequenceStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAnalyzeTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCallStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateFunctionStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateJavaFunctionStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateSequenceStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropFunctionStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropJavaFunctionStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropSequenceStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTruncateStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MysqlForeignKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.dao.RplDdlMapperExt;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.domain.po.RplDdlSub;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.alibaba.polardbx.druid.sql.SQLUtils.normalize;
import static com.alibaba.polardbx.druid.sql.SQLUtils.normalizeNoTrim;
import static com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement.Type.SHADOW;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PARALLEL_SCHEMA_CHANNEL_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PARALLEL_SCHEMA_CHANNEL_PARALLELISM;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PARALLEL_TABLE_APPLY_ENABLED;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.canal.LogEventUtil.SYNC_POINT_PROCEDURE_NAME;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;
import static com.aliyun.polardbx.rpl.applier.ParallelSchemaApplier.DependencyCheckResult.OBJ_TYPE_FUNCTION;
import static com.aliyun.polardbx.rpl.applier.ParallelSchemaApplier.DependencyCheckResult.OBJ_TYPE_SEQ;
import static com.aliyun.polardbx.rpl.applier.ParallelSchemaApplier.DependencyCheckResult.OBJ_TYPE_TABLE;
import static com.aliyun.polardbx.rpl.common.LogUtil.getSkipDdlLogger;
import static com.aliyun.polardbx.rpl.pipeline.SerialPipeline.shouldSkip;

/**
 * description: 目前，仅限在实验室环境使用parallel schema模式
 * author: ziyang.lb
 * create: 2023-11-15 10:45
 **/
@Slf4j
public class ParallelSchemaApplier {
    private static final long FLUSH_METRICS_INTERVAL_MILLS = 30000;
    private static final int MAX_PARALLELISM_UNDER_ONE_SCHEMA = 32;//足够用了，不要进行任何调整，会触发兼容性问题

    private final ExecutorService parallelSchemaExecutorService;
    private final ExecutorService parallelTableExecutorService;
    private final ConcurrentHashMap<String, String> maxDdlTsoCheckpointMap;
    private final ConcurrentHashMap<String, AtomicBoolean> schemaFirstDdlFlags;
    private final ConcurrentHashMap<String, AtomicBoolean> tableFirstDdlFlags;
    private final ConcurrentHashMap<String, SchemaChannel> schemaChannels;
    private final Semaphore schemaChannelSemaphore;
    private final AtomicReference<Throwable> schemaChannelError;
    private final Statistic statistic;
    private String lastFlushedPosition;
    private long lastCheckFlushTime = System.currentTimeMillis();
    private long batchId;

    public ParallelSchemaApplier() {
        this.parallelSchemaExecutorService = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("parallel-schema-ddl-executor-%d").build());
        this.parallelTableExecutorService = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("parallel-table-ddl-executor-%d").build());

        this.maxDdlTsoCheckpointMap = new ConcurrentHashMap<>();
        this.schemaFirstDdlFlags = new ConcurrentHashMap<>();
        this.tableFirstDdlFlags = new ConcurrentHashMap<>();

        this.schemaChannels = new ConcurrentHashMap<>();
        this.schemaChannelSemaphore = new Semaphore(getInt(RPL_PARALLEL_SCHEMA_CHANNEL_PARALLELISM));
        this.schemaChannelError = new AtomicReference<>();
        this.statistic = new Statistic();
        this.initMaxDdlTsoCheckPoint();
    }

    public void stop() {
        if (parallelSchemaExecutorService != null) {
            parallelSchemaExecutorService.shutdownNow();
        }
        if (parallelTableExecutorService != null) {
            parallelTableExecutorService.shutdownNow();
        }
        if (schemaChannels != null) {
            schemaChannels.values().forEach(s -> {
                try {
                    s.close(true);
                } catch (Throwable t) {
                }
            });
        }
        log.info("parallel schema applier stopped.");
    }

    public void parallelApply(List<DBMSEvent> eventBatch) {
        Map<String, List<DBMSEvent>> groupedEvents = new HashMap<>();
        long totalEventCount = 0;
        long ddlEventCount = 0;
        String firstPosition = "";
        String lastPosition = "";

        for (DBMSEvent e : eventBatch) {
            String schemaName;
            if (e instanceof DefaultRowChange) {
                DefaultRowChange rowChange = (DefaultRowChange) e;
                schemaName = rowChange.getSchema();
            } else if (e instanceof DefaultQueryLog) {
                DefaultQueryLog queryLog = (DefaultQueryLog) e;
                schemaName = queryLog.getSchema();
            } else {
                lastPosition = e.getPosition();
                continue;
            }

            if (StringUtils.isBlank(schemaName)) {
                throw new PolardbxException("schema name can`t be null or empty " + e);
            } else {
                schemaName = schemaName.toLowerCase();
            }

            // 部分ddl是实例级的，不支持并行
            if (e instanceof DefaultQueryLog) {
                DefaultQueryLog queryLog = (DefaultQueryLog) e;
                String originalDdlSql = DdlApplyHelper.getOriginSql(queryLog.getQuery());

                if (StringUtils.equalsAnyIgnoreCase(schemaName, "cdc_token_db")
                    || isCrossDatabase(originalDdlSql, schemaName) || isSyncPoint(originalDdlSql, schemaName)) {
                    log.warn("meet a serial executing ddl sql {}, with schema {} ", originalDdlSql, schemaName);

                    // flush previous events
                    parallelApplyInternal(groupedEvents, totalEventCount, ddlEventCount,
                        firstPosition, lastPosition, true);
                    groupedEvents.clear();
                    totalEventCount = 0;
                    ddlEventCount = 0;

                    // apply this ddl separately
                    serialApplyInternal(schemaName, e, originalDdlSql);
                    continue;
                }
                ddlEventCount++;
            }

            if (DynamicApplicationConfig.getBoolean(RPL_PARALLEL_SCHEMA_CHANNEL_ENABLED) && !groupedEvents.isEmpty()
                && !groupedEvents.containsKey(schemaName)) {
                // flush previous schema events
                parallelApplyInternal(groupedEvents, totalEventCount, ddlEventCount,
                    firstPosition, lastPosition, false);
                groupedEvents.clear();
                totalEventCount = 0;
                ddlEventCount = 0;
            }

            // 按Schema进行分组
            if (groupedEvents.isEmpty()) {
                firstPosition = e.getPosition();
            }
            List<DBMSEvent> list = groupedEvents.computeIfAbsent(schemaName, k -> new ArrayList<>());
            list.add(e);
            lastPosition = e.getPosition();
            totalEventCount++;
        }

        parallelApplyInternal(groupedEvents, totalEventCount, ddlEventCount, firstPosition, lastPosition, false);
        tryFlushMetrics();
    }

    protected void initMaxDdlTsoCheckPoint() {
        RplDdlMapperExt rplDdlMapperExt = SpringContextHolder.getObject(RplDdlMapperExt.class);
        List<RplDdl> mainCheckPointList = rplDdlMapperExt.getCheckpointTsoListForDdlMain(
            TaskContext.getInstance().getStateMachineId(), TaskContext.getInstance().getTaskId());
        List<RplDdlSub> subCheckPointList = rplDdlMapperExt.getCheckpointTsoListForDdlSub(
            TaskContext.getInstance().getStateMachineId(), TaskContext.getInstance().getTaskId());
        mainCheckPointList.forEach(p -> {
            String key = buildCheckPointKey(p.getSchemaName(), p.getParallelSeq());
            this.maxDdlTsoCheckpointMap.put(key, p.getDdlTso());
        });
        subCheckPointList.forEach(p -> {
            String key = buildCheckPointKey(p.getSchemaName(), p.getParallelSeq());
            String tso = this.maxDdlTsoCheckpointMap.computeIfAbsent(key, k -> p.getDdlTso());
            if (StringUtils.compare(p.getDdlTso(), tso) > 0) {
                this.maxDdlTsoCheckpointMap.put(key, p.getDdlTso());
            }
        });
        log.info("max ddl tso checkpoint map is initialized , {} ", JSONObject.toJSONString(maxDdlTsoCheckpointMap));
    }

    private String buildCheckPointKey(String schemaName, long parallelSeq) {
        return schemaName.toLowerCase() + "." + parallelSeq;
    }

    boolean isCrossDatabase(String sql, String baseSchemaName) {
        if (StringUtils.containsIgnoreCase(sql, "create") && StringUtils.containsIgnoreCase(sql, "like")) {
            SQLStatement statement = SQLUtils.parseSQLStatement(sql);
            if (statement instanceof MySqlCreateTableStatement) {
                MySqlCreateTableStatement mySqlCreateTableStatement = (MySqlCreateTableStatement) statement;
                SQLExprTableSource likeTableSource = mySqlCreateTableStatement.getLike();
                SQLExprTableSource targetTableSource = mySqlCreateTableStatement.getTableSource();
                String likeSchema = parseSchemaName(likeTableSource, baseSchemaName);
                String targetSchema = parseSchemaName(targetTableSource, baseSchemaName);
                boolean result = !StringUtils.equalsIgnoreCase(likeSchema, targetSchema);
                if (result) {
                    log.warn("meet a crossing database sql: {}", sql);
                } else {
                    log.info("skip none-crossing database sql for create like, {}:{}:{}:{}",
                        baseSchemaName, targetSchema, likeSchema, sql);
                }
                return result;
            }
        }
        return false;
    }

    boolean isSyncPoint(String sql, String schema) {
        if (StringUtils.equalsIgnoreCase(schema, "polardbx") && StringUtils.startsWithIgnoreCase(sql, "call")) {
            SQLStatement stmt = SQLUtils.parseSQLStatement(sql);
            if (stmt instanceof SQLCallStatement) {
                if (StringUtils.equalsIgnoreCase(SYNC_POINT_PROCEDURE_NAME,
                    ((SQLCallStatement) stmt).getProcedureName().getSimpleName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String parseSchemaName(SQLExprTableSource tableSource, String defaultSchema) {
        if (tableSource != null && tableSource.getExpr() != null && tableSource.getExpr() instanceof SQLPropertyExpr) {
            SQLPropertyExpr sqlPropertyExpr = (SQLPropertyExpr) tableSource.getExpr();
            SQLIdentifierExpr owner = (SQLIdentifierExpr) sqlPropertyExpr.getOwner();
            return normalizeNoTrim(owner.getName());
        }
        return defaultSchema;
    }

    private void recordAndFlushPosition(String position, boolean isFlush) {
        if (StringUtils.isBlank(position)) {
            return;
        }
        StatisticalProxy.getInstance().recordPosition(position);
        if (isFlush) {
            StatisticalProxy.getInstance().flushPosition();
        }
        lastFlushedPosition = position;
        log.info("successfully recording and flushing position: " + lastFlushedPosition);
    }

    private void serialApplyInternal(String schemaName, DBMSEvent event, String originalDdlSql) {
        log.warn("start to apply events in serial mode separately, with position {}, ddl sql {}.",
            event.getPosition(), originalDdlSql);
        SchemaExecutor executor = new SchemaExecutor(schemaName, Lists.newArrayList(event), null);
        SchemaStatistic schemaStatistic = executor.call();
        statistic.addEffectCostTime(schemaStatistic.getSchemaName(), schemaStatistic.getTotalCostTime());
        recordAndFlushPosition(event.getPosition(), true);
    }

    private void parallelApplyInternal(Map<String, List<DBMSEvent>> groupedEvents, long totalEventCount,
                                       long ddlEventCount, String firstPosition, String lastPosition,
                                       boolean waitAllComplete) {
        batchId++;
        if (DynamicApplicationConfig.getBoolean(RPL_PARALLEL_SCHEMA_CHANNEL_ENABLED)) {
            parallelApplyWithChannel(groupedEvents, totalEventCount, ddlEventCount,
                firstPosition, lastPosition, waitAllComplete);
        } else {
            parallelApplyWithBlock(groupedEvents, totalEventCount, ddlEventCount, lastPosition);
        }
    }

    private void parallelApplyWithBlock(Map<String, List<DBMSEvent>> groupedEvents, long totalEventCount,
                                        long ddlEventCount, String lastPosition) {
        if (groupedEvents.isEmpty()) {
            recordAndFlushPosition(lastPosition, false);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("start to apply events in parallel schema block mode, batch group count is {}, "
                    + "batch event count is {}," + " ddl count is {}, schema list is {}.",
                groupedEvents.size(), totalEventCount, ddlEventCount, groupedEvents.keySet());
        }

        // submit
        Map<String, Future<SchemaStatistic>> futures = new HashMap<>();
        groupedEvents.forEach(
            (key, value) -> futures.put(key,
                parallelSchemaExecutorService.submit(new SchemaExecutor(key, value, null))));

        // wait
        Set<String> failedSchemas = new HashSet<>();
        SchemaStatistic maxStatistic = null;
        for (Map.Entry<String, Future<SchemaStatistic>> entry : futures.entrySet()) {
            try {
                SchemaStatistic schemaStatistic = entry.getValue().get();
                if (maxStatistic == null || schemaStatistic.getTotalCostTime() > maxStatistic.getTotalCostTime()) {
                    maxStatistic = schemaStatistic;
                }
            } catch (Throwable t) {
                log.error("apply events error for schema {}.", entry.getKey(), t);
                failedSchemas.add(entry.getKey());
            }
        }
        if (maxStatistic != null) {
            statistic.addEffectCostTime(maxStatistic.schemaName, maxStatistic.getTotalCostTime());
        }

        // retry once
        // 有些情况下的失败，可能是由于不同schema之间有依赖(主要是ddl)，这里尽最大努力进行一下重试
        initMaxDdlTsoCheckPoint();
        failedSchemas.forEach(s -> {
            log.warn("retry to apply events for schema {}", s);
            SchemaExecutor executor = new SchemaExecutor(s, groupedEvents.get(s), null);
            SchemaStatistic schemaStatistic = executor.call();
            statistic.addEffectCostTime(schemaStatistic.getSchemaName(), schemaStatistic.getTotalCostTime());
        });

        recordAndFlushPosition(lastPosition, true);
    }

    @SneakyThrows
    private void parallelApplyWithChannel(Map<String, List<DBMSEvent>> groupedEvents, long totalEventCount,
                                          long ddlEventCount, String firstPosition, String lastPosition,
                                          boolean waitAllCompleteBeforeApply) {
        if (groupedEvents.isEmpty() && schemaChannels.isEmpty()) {
            recordAndFlushPosition(lastPosition, false);
            return;
        }

        if (groupedEvents.size() > 1) {
            throw new PolardbxException(
                "submitted events should belongs to only one schema, but actual is " + groupedEvents.keySet());
        }

        if (log.isDebugEnabled()) {
            log.debug("start to apply events in parallel schema channel mode, batch id {},batch group count is {}, "
                    + "batch event count is {}," + " ddl count is {}, schema list is {}, first position {}, "
                    + "last position {}," + " wait all YN {}.",
                batchId, groupedEvents.size(), totalEventCount, ddlEventCount, groupedEvents.keySet(),
                firstPosition, lastPosition, waitAllCompleteBeforeApply);
        }

        tryFlushMinPosition();

        Iterator<Map.Entry<String, List<DBMSEvent>>> iterator = groupedEvents.entrySet().iterator();
        while (iterator.hasNext()) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (schemaChannelError.get() != null) {
                throw schemaChannelError.get();
            }

            double oldUsedRatio = JvmUtils.getOldUsedRatio();
            int totalRemaining = schemaChannels.values().stream().mapToInt(SchemaChannel::remaining).sum();
            if (oldUsedRatio < 0.7 || totalRemaining < 524288) {
                Map.Entry<String, List<DBMSEvent>> entry = iterator.next();
                SchemaChannel schemaChannel = schemaChannels.computeIfAbsent(entry.getKey(), SchemaChannel::new);
                schemaChannel.events.add(entry.getValue());
                if (log.isDebugEnabled()) {
                    log.debug("group events is put to buffer, with batch id " + batchId);
                }
            } else {
                Thread.sleep(10);
                tryFlushMinPosition();
            }
        }

        if (waitAllCompleteBeforeApply) {
            while (true) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                if (schemaChannelError.get() != null) {
                    throw schemaChannelError.get();
                }

                Optional<SchemaChannel> optional =
                    schemaChannels.values().stream().filter(c -> !c.events.isEmpty()).findAny();
                if (!optional.isPresent()) {
                    schemaChannels.values().forEach(c -> c.close(false));
                    schemaChannels.clear();
                    break;
                } else {
                    Thread.sleep(10);
                    tryFlushMinPosition();
                }
            }
        }

        tryFlushMinPosition();
    }

    private void tryFlushMinPosition() {
        if (System.currentTimeMillis() - lastCheckFlushTime < 10000) {
            return;
        }

        List<Triple<String, String, Integer>> snapshot = schemaChannels.values().stream()
            .map(s -> Triple.of(s.getSchemaName(), s.getPosition(), s.remaining())).collect(Collectors.toList());

        Optional<Triple<String, String, Integer>> minOptional = snapshot.stream()
            .min((o1, o2) -> StringUtils.compare(o1.getMiddle(), o2.getMiddle()));
        Optional<Triple<String, String, Integer>> maxOptional = snapshot.stream()
            .max((o1, o2) -> StringUtils.compare(o1.getMiddle(), o2.getMiddle()));
        Pair<String, String> minMaxPair = Pair.of(
            minOptional.map(triple -> triple.getLeft() + ":" + triple.getMiddle()).orElse(""),
            maxOptional.map(triple -> triple.getLeft() + ":" + triple.getMiddle()).orElse(""));

        printDetail(snapshot, minMaxPair);

        if (minOptional.isPresent() && StringUtils.isNotBlank(minOptional.get().getMiddle())) {
            if (StringUtils.compare(minOptional.get().getMiddle(), lastFlushedPosition) < 0) {
                throw new PolardbxException(String.format("new position can`t be less than last position, %s, %s",
                    minOptional.get().getMiddle(), lastFlushedPosition));
            }

            if (!StringUtils.equals(minOptional.get().getMiddle(), lastFlushedPosition)) {
                recordAndFlushPosition(minOptional.get().getMiddle(), true);
            }

            snapshot.forEach(t -> tryRemoveTimeOutSchemaChannel(t.getLeft()));
        }

        lastCheckFlushTime = System.currentTimeMillis();
    }

    private void printDetail(List<Triple<String, String, Integer>> snapshot, Pair<String, String> minMaxPair) {
        int totalRemaining = snapshot.stream().mapToInt(Triple::getRight).sum();
        log.info("min position in schema channels is {}, max position is {}, total remaining events is {},"
                + " semaphore available permits {},  remaining schema list is {}.", minMaxPair.getKey(),
            minMaxPair.getValue(), totalRemaining, schemaChannelSemaphore.availablePermits(),
            JSONObject.toJSONString(snapshot, true));
    }

    private void tryRemoveTimeOutSchemaChannel(String schemaName) {
        SchemaChannel channel = schemaChannels.get(schemaName);
        if (System.currentTimeMillis() - channel.lastExecuteTime > 60000 && channel.events.isEmpty()) {
            channel.close(false);
            schemaChannels.remove(channel.getSchemaName());
        }
    }

    private void tryFlushMetrics() {
        if (System.currentTimeMillis() - statistic.lastFlushTime >= FLUSH_METRICS_INTERVAL_MILLS) {
            tryCreateStatisticTable();
            updateStaticsTable();
            statistic.reset();
        }
    }

    private void tryCreateStatisticTable() {
        JdbcTemplate jdbcTemplate = getObject("metaJdbcTemplate");
        String createSql = "create table if not exists rpl_parallel_schema_metrics("
            + "`id` bigint auto_increment primary key, "
            + "`schema_name` varchar(200), "
            + "`total_count` bigint, "
            + "`dml_count` bigint, "
            + "`ddl_count` bigint, "
            + "`total_cost_time` bigint, "
            + "`dml_cost_time` bigint, "
            + "`ddl_cost_time` bigint, "
            + "`effect_cost_time` bigint, "
            + "`gmt_created` datetime not null default CURRENT_TIMESTAMP, "
            + "`gmt_modified` datetime not null default CURRENT_TIMESTAMP, "
            + "unique key uk1(schema_name)"
            + ")";
        jdbcTemplate.execute(createSql);
    }

    private void updateStaticsTable() {
        JdbcTemplate jdbcTemplate = getObject("metaJdbcTemplate");
        statistic.statisticMap.forEach((k, v) -> {
            try {
                jdbcTemplate.execute(String.format(
                    "insert into rpl_parallel_schema_metrics("
                        + "schema_name,"
                        + "total_count,"
                        + "dml_count,"
                        + "ddl_count,"
                        + "total_cost_time,"
                        + "dml_cost_time,"
                        + "ddl_cost_time,"
                        + "effect_cost_time) values('%s', %s , %s , %s , %s , %s , %s , %s)",
                    v.getSchemaName(), v.getTotalCount(), v.getDmlCount(), v.getDdlCount(), v.getTotalCostTime(),
                    v.getDmlCostTime(), v.getDdlCostTime(), v.getEffectCostTime()));
            } catch (DuplicateKeyException e) {
                jdbcTemplate.execute(String.format("update rpl_parallel_schema_metrics "
                        + "set total_count = total_count + %s, "
                        + "dml_count = dml_count + %s, "
                        + "ddl_count = ddl_count + %s, "
                        + "total_cost_time = total_cost_time + %s, "
                        + "dml_cost_time = dml_cost_time + %s, "
                        + "ddl_cost_time = ddl_cost_time + %s, "
                        + "effect_cost_time = effect_cost_time + %s, "
                        + "gmt_modified = now() "
                        + "where schema_name = '%s'",
                    v.getTotalCount(), v.getDmlCount(), v.getDdlCount(), v.getTotalCostTime(),
                    v.getDmlCostTime(), v.getDdlCostTime(), v.getEffectCostTime(), v.getSchemaName()));
            }
        });
    }

    public class SchemaExecutor implements Callable<SchemaStatistic> {
        private final String schemaName;
        private final List<DBMSEvent> events;
        private final String maxDdlTsoCheckpoint;
        private final Consumer<String> positionConsumer;
        private boolean drdsModeDatabase;

        public SchemaExecutor(String schemaName, List<DBMSEvent> events, Consumer<String> positionConsumer) {
            this.schemaName = schemaName;
            this.events = events;
            this.refreshDatabaseMode();
            this.maxDdlTsoCheckpoint = ParallelSchemaApplier.this.maxDdlTsoCheckpointMap
                .getOrDefault(buildCheckPointKey(schemaName, 0), "");
            this.positionConsumer = positionConsumer;
        }

        private void refreshDatabaseMode() {
            JdbcTemplate jdbcTemplate = getObject("metaJdbcTemplate");
            String sql = "select db_type from db_info where db_name = '" + schemaName + "'";
            List<Integer> list = jdbcTemplate.queryForList(sql, Integer.class);
            drdsModeDatabase = !list.isEmpty() && list.get(0) == 0;
        }

        @SneakyThrows
        @Override
        public SchemaStatistic call() {
            if (DynamicApplicationConfig.getBoolean(RPL_PARALLEL_TABLE_APPLY_ENABLED)) {
                return doApplyInParallelMode();
            } else {
                return doApplyInSerialMode();
            }
        }

        @SneakyThrows
        private SchemaStatistic doApplyInParallelMode() {
            Map<Integer, List<DBMSEvent>> groupedEvents = new HashMap<>();
            SchemaStatistic schemaStatistic = new SchemaStatistic(schemaName);
            String lastPosition = "";

            for (DBMSEvent e : events) {
                String objName;
                DependencyCheckResult checkResult = null;

                if (e instanceof DefaultRowChange) {
                    DefaultRowChange rowChange = (DefaultRowChange) e;
                    objName = rowChange.getTable().toLowerCase();
                } else if (e instanceof DefaultQueryLog) {
                    DefaultQueryLog queryLog = (DefaultQueryLog) e;
                    String originalDdlSql = DdlApplyHelper.getOriginSql(queryLog.getQuery());
                    checkResult = checkDependency(originalDdlSql, drdsModeDatabase);
                    objName = checkResult.getObjName();
                } else {
                    continue;
                }

                if (checkResult != null && !checkResult.canParallelApply()) {
                    // flush previous events
                    doTableParallelApplyInternal(groupedEvents, schemaName, schemaStatistic, lastPosition);
                    groupedEvents.clear();

                    // apply this ddl separately
                    schemaStatistic.add(doTableSerialApplyInternal(e));

                    // refresh auto mode db
                    if (DdlApplyHelper.isCreateOrDropDatabase(checkResult.getDdlSql())) {
                        refreshDatabaseMode();
                    }
                    continue;
                }

                // 按Table进行分组
                int seq = Math.abs(Arrays.hashCode(objName.getBytes()) % MAX_PARALLELISM_UNDER_ONE_SCHEMA) + 1;
                List<DBMSEvent> list = groupedEvents.computeIfAbsent(seq, k -> new ArrayList<>());
                list.add(e);
                lastPosition = e.getPosition();
            }

            doTableParallelApplyInternal(groupedEvents, schemaName, schemaStatistic, lastPosition);
            return schemaStatistic;
        }

        private void doTableParallelApplyInternal(Map<Integer, List<DBMSEvent>> groupedEvents, String schemaName,
                                                  SchemaStatistic schemaStatisticTotal, String lastPosition)
            throws Throwable {
            if (groupedEvents.isEmpty()) {
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug("start to apply events in table parallel mode, batch group count is {}, "
                        + "batch event count is {}",
                    groupedEvents.size(), groupedEvents.values().stream().mapToInt(List::size).sum());
            }

            // submit
            Map<Integer, Future<SchemaStatistic>> futures = new HashMap<>();
            groupedEvents.forEach(
                (key, value) -> futures.put(key,
                    parallelTableExecutorService.submit(
                        new TableExecutor(schemaName, key, value, maxDdlTsoCheckpoint))));

            // wait
            Throwable error = null;
            for (Map.Entry<Integer, Future<SchemaStatistic>> entry : futures.entrySet()) {
                try {
                    SchemaStatistic schemaStatistic = entry.getValue().get();
                    schemaStatisticTotal.add(schemaStatistic);
                } catch (Throwable t) {
                    error = t;
                    log.error("apply events error for parallel seq {} of schema {} .", entry.getKey(), schemaName, t);
                }
            }

            if (error != null) {
                throw error;
            }

            if (positionConsumer != null && StringUtils.isNotBlank(lastPosition)) {
                positionConsumer.accept(lastPosition);
            }
        }

        private SchemaStatistic doTableSerialApplyInternal(DBMSEvent event) throws Exception {
            AtomicBoolean firstDdl = schemaFirstDdlFlags.computeIfAbsent(schemaName, k -> new AtomicBoolean(true));
            Executor executor = new Executor(schemaName, maxDdlTsoCheckpoint,
                Lists.newArrayList(event), firstDdl, 0, positionConsumer);
            return executor.call();
        }

        private SchemaStatistic doApplyInSerialMode() throws Exception {
            AtomicBoolean firstDdl = schemaFirstDdlFlags.computeIfAbsent(schemaName, k -> new AtomicBoolean(true));
            Executor executor = new Executor(schemaName, maxDdlTsoCheckpoint, events, firstDdl, 0, positionConsumer);
            return executor.call();
        }
    }

    public class TableExecutor implements Callable<SchemaStatistic> {

        private final String schemaName;
        private final Integer parallelSeq;
        private final List<DBMSEvent> events;
        private final String maxDdlTsoCheckpoint;

        public TableExecutor(String schemaName, Integer parallelSeq, List<DBMSEvent> events,
                             String maxCheckPointBySchema) {
            this.schemaName = schemaName;
            this.parallelSeq = parallelSeq;
            this.events = events;

            String maxCheckPointByTable = ParallelSchemaApplier.this.maxDdlTsoCheckpointMap.getOrDefault(
                buildCheckPointKey(schemaName, parallelSeq), "");
            this.maxDdlTsoCheckpoint = StringUtils.compare(
                maxCheckPointByTable, maxCheckPointBySchema) > 0 ? maxCheckPointByTable : maxCheckPointBySchema;
        }

        @Override
        public SchemaStatistic call() throws Exception {
            AtomicBoolean firstDdl = tableFirstDdlFlags.computeIfAbsent(buildCheckPointKey(schemaName, parallelSeq),
                k -> new AtomicBoolean(true));
            Executor executor = new Executor(schemaName, maxDdlTsoCheckpoint, events, firstDdl, parallelSeq, null);
            return executor.call();
        }

    }

    public class Executor implements Callable<SchemaStatistic> {

        private final String schemaName;
        private final String maxDdlTsoCheckpoint;
        private final List<DBMSEvent> events;
        private final AtomicBoolean firstDdlFlag;
        private final int parallelSeq;
        private final Consumer<String> positionConsumer;

        public Executor(String schemaName, String maxDdlTsoCheckpoint, List<DBMSEvent> events,
                        AtomicBoolean firstDdlFlag, int parallelSeq, Consumer<String> positionConsumer) {
            this.schemaName = schemaName;
            this.maxDdlTsoCheckpoint = maxDdlTsoCheckpoint;
            this.events = events;
            this.firstDdlFlag = firstDdlFlag;
            this.parallelSeq = parallelSeq;
            this.positionConsumer = positionConsumer;
        }

        @Override
        public SchemaStatistic call() throws Exception {
            List<DBMSEvent> eventBatch = new ArrayList<>();
            SchemaStatistic schemaStatistic = new SchemaStatistic(schemaName);

            for (DBMSEvent dbmsEvent : events) {
                if (shouldSkip(dbmsEvent, maxDdlTsoCheckpoint)) {
                    if (getSkipDdlLogger().isDebugEnabled()) {
                        getSkipDdlLogger().debug(
                            "dbms event is skipped , with schema {}, position {}, checkpoint tso {}.",
                            schemaName, dbmsEvent.getPosition(), maxDdlTsoCheckpoint);
                    }
                    continue;
                }

                if (dbmsEvent instanceof DefaultRowChange) {
                    eventBatch.add(dbmsEvent);
                } else if (DdlApplyHelper.isDdl(dbmsEvent)) {
                    // first apply all existing dml events
                    doApply(eventBatch, 0, schemaStatistic);
                    eventBatch.clear();

                    // and then apply DDL one by one
                    ((DefaultQueryLog) dbmsEvent).setFirstDdl(firstDdlFlag);
                    ((DefaultQueryLog) dbmsEvent).setParallelSeq(parallelSeq);
                    doApply(Lists.newArrayList(dbmsEvent), 1, schemaStatistic);
                }
            }

            if (!eventBatch.isEmpty()) {
                doApply(eventBatch, 0, schemaStatistic);
            }

            statistic.addSchemaStatistic(schemaStatistic);
            return schemaStatistic;
        }

        private void doApply(List<DBMSEvent> dbmsEvents, int type, SchemaStatistic schemaStatistic) throws Exception {
            long start = System.currentTimeMillis();
            StatisticalProxy.getInstance().apply(dbmsEvents);
            long end = System.currentTimeMillis();

            if (type == 0) {
                schemaStatistic.dmlCount += dbmsEvents.size();
                schemaStatistic.dmlCostTime += (end - start);
            } else if (type == 1) {
                schemaStatistic.ddlCount += dbmsEvents.size();
                schemaStatistic.ddlCostTime += (end - start);
            }

            if (positionConsumer != null && !dbmsEvents.isEmpty()) {
                positionConsumer.accept(dbmsEvents.get(dbmsEvents.size() - 1).getPosition());
            }
        }
    }

    DependencyCheckResult checkDependency(String ddlSql) {
        return checkDependency(ddlSql, true);
    }

    DependencyCheckResult checkDependency(String ddlSql, boolean drdsModeDatabase) {
        DependencyCheckResult result = new DependencyCheckResult();
        result.setDdlSql(ddlSql);
        SQLStatement sqlStatement = parseSQLStatement(ddlSql);

        if (sqlStatement instanceof MySqlCreateTableStatement && drdsModeDatabase) {
            checkDependencyForCreateTable((MySqlCreateTableStatement) sqlStatement, result);
        } else if (sqlStatement instanceof SQLDropTableStatement && drdsModeDatabase) {
            checkDependencyForDropTable((SQLDropTableStatement) sqlStatement, result);
        } else if (sqlStatement instanceof SQLAlterTableStatement && drdsModeDatabase) {
            checkDependencyForAlterTable((SQLAlterTableStatement) sqlStatement, result, ddlSql);
        } else if (sqlStatement instanceof SQLCreateIndexStatement && drdsModeDatabase) {
            checkDependencyForCreateIndex((SQLCreateIndexStatement) sqlStatement, result);
        } else if (sqlStatement instanceof SQLDropIndexStatement && drdsModeDatabase) {
            checkDependencyForDropIndex((SQLDropIndexStatement) sqlStatement, result);
        } else if (sqlStatement instanceof SQLCreateSequenceStatement) {
            checkDependencyForCreateSequence((SQLCreateSequenceStatement) sqlStatement, result);
        } else if (sqlStatement instanceof SQLDropSequenceStatement) {
            checkDependencyForDropSequence((SQLDropSequenceStatement) sqlStatement, result);
        } else if (sqlStatement instanceof SQLAlterSequenceStatement) {
            checkDependencyForAlterSequence((SQLAlterSequenceStatement) sqlStatement, result);
        } else if (sqlStatement instanceof SQLTruncateStatement) {
            checkDependencyForTruncateTable((SQLTruncateStatement) sqlStatement, result);
        } else if (sqlStatement instanceof SQLAnalyzeTableStatement) {
            checkDependencyForAnalyzeTable((SQLAnalyzeTableStatement) sqlStatement, result);
        } else {
            checkDependencyForRandomUdfTest(ddlSql, result);
        }

        // omc带index的表，并发执行会有ddl死锁问题，将其放入一个队列
        if (result.isParallelPossibility() && isOmcWithIndex(result.getObjName())) {
            result.setObjName("omc");
        }
        return result;
    }

    private boolean isOmcWithIndex(String tableName) {
        return StringUtils.containsIgnoreCase(tableName, "omc_")
            && (StringUtils.containsIgnoreCase(tableName, "index") || StringUtils.containsIgnoreCase(tableName, "gsi"));
    }

    private void checkDependencyForCreateTable(MySqlCreateTableStatement createTableStatement,
                                               DependencyCheckResult result) {
        if (createTableStatement.getLike() != null || SHADOW == createTableStatement.getType()) {
            return;
        }

        result.setObjName(normalizeNoTrim(createTableStatement.getTableName()).toLowerCase());
        result.setParallelPossibility(true);
        result.setObjType(OBJ_TYPE_TABLE);

        List<SQLTableElement> sqlTableElementList = createTableStatement.getTableElementList();
        for (SQLTableElement element : sqlTableElementList) {
            if (element instanceof MysqlForeignKey) {
                MysqlForeignKey foreignKey = (MysqlForeignKey) element;
                String dependencyTable =
                    normalizeNoTrim(foreignKey.getReferencedTableName().getSimpleName()).toLowerCase();
                result.getDependencyObjs().add(Pair.of(OBJ_TYPE_TABLE, dependencyTable));
            }
        }
    }

    private void checkDependencyForDropTable(SQLDropTableStatement dropTableStatement,
                                             DependencyCheckResult result) {
        if (dropTableStatement.getTableSources().size() == 1) {
            result.setObjName(normalizeNoTrim(
                dropTableStatement.getTableSources().get(0).getTableName()).toLowerCase());
            result.setParallelPossibility(true);
            result.setObjType(OBJ_TYPE_TABLE);
        }
    }

    private void checkDependencyForAlterTable(SQLAlterTableStatement alterTableStatement,
                                              DependencyCheckResult result, String ddlSql) {
        for (SQLAlterTableItem item : alterTableStatement.getItems()) {
            if (item instanceof SQLAlterTableAddConstraint) {
                SQLAlterTableAddConstraint addConstraint = (SQLAlterTableAddConstraint) item;
                if (addConstraint.getConstraint() instanceof MysqlForeignKey) {
                    MysqlForeignKey foreignKey = (MysqlForeignKey) addConstraint.getConstraint();
                    String dependencyTable =
                        normalizeNoTrim(foreignKey.getReferencedTableName().getSimpleName()).toLowerCase();
                    result.getDependencyObjs().add(Pair.of(OBJ_TYPE_TABLE, dependencyTable));
                }
            }
        }

        result.setObjName(normalizeNoTrim(alterTableStatement.getTableName()).toLowerCase());
        result.setParallelPossibility(true);
        result.setObjType(OBJ_TYPE_TABLE);
    }

    private void checkDependencyForCreateIndex(SQLCreateIndexStatement createIndexStatement,
                                               DependencyCheckResult result) {
        if (createIndexStatement.isWithImplicitTablegroup()) {
            return;
        }

        String tableName = normalize(createIndexStatement.getTableName()).toLowerCase();
        result.setParallelPossibility(true);
        result.setObjName(tableName);
        result.setObjType(OBJ_TYPE_TABLE);
    }

    private void checkDependencyForDropIndex(SQLDropIndexStatement dropIndexStatement, DependencyCheckResult result) {
        String tableName = normalizeNoTrim(dropIndexStatement.getTableName().getTableName()).toLowerCase();
        result.setParallelPossibility(true);
        result.setObjName(tableName);
        result.setObjType(OBJ_TYPE_TABLE);
    }

    private void checkDependencyForCreateSequence(SQLCreateSequenceStatement createSequenceStatement,
                                                  DependencyCheckResult result) {
        String sequenceName = normalize(createSequenceStatement.getName().getSimpleName()).toLowerCase();
        result.setParallelPossibility(true);
        result.setObjName(sequenceName);
        result.setObjType(OBJ_TYPE_SEQ);
    }

    private void checkDependencyForDropSequence(SQLDropSequenceStatement dropSequenceStatement,
                                                DependencyCheckResult result) {
        String sequenceName = normalize(dropSequenceStatement.getName().getSimpleName()).toLowerCase();
        result.setParallelPossibility(true);
        result.setObjName(sequenceName);
        result.setObjType(OBJ_TYPE_SEQ);
    }

    private void checkDependencyForAlterSequence(SQLAlterSequenceStatement alterSequenceStatement,
                                                 DependencyCheckResult result) {
        String sequenceName = normalize(alterSequenceStatement.getName().getSimpleName()).toLowerCase();
        result.setParallelPossibility(true);
        result.setObjName(sequenceName);
        result.setObjType(OBJ_TYPE_SEQ);
    }

    private void checkDependencyForTruncateTable(SQLTruncateStatement truncateStatement, DependencyCheckResult result) {
        if (truncateStatement.getTableSources().size() == 1) {
            String tableName = normalizeNoTrim(truncateStatement.getTableSources()
                .get(0).getName().getSimpleName()).toLowerCase();
            result.setParallelPossibility(true);
            result.setObjName(tableName);
            result.setObjType(OBJ_TYPE_TABLE);
        }
    }

    private void checkDependencyForAnalyzeTable(SQLAnalyzeTableStatement analyzeTableStatement,
                                                DependencyCheckResult result) {
        if (analyzeTableStatement.getTables().size() == 1) {
            String tableName = normalizeNoTrim(analyzeTableStatement.getTables()
                .get(0).getName().getSimpleName()).toLowerCase();
            result.setParallelPossibility(true);
            result.setObjName(tableName);
            result.setObjType(OBJ_TYPE_TABLE);
        }
    }

    private void checkDependencyForRandomUdfTest(String sql, DependencyCheckResult result) {
        // 实验室特殊逻辑，尽量提升并发度，减少单库承载的ddl数量
        // 参见CN测试代码：com.alibaba.polardbx.qatest.dml.sharding.basecrud.RandomUdfTest
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        if ((statement instanceof SQLCreateFunctionStatement
            || statement instanceof SQLDropFunctionStatement
            || statement instanceof SQLCreateJavaFunctionStatement
            || statement instanceof SQLDropJavaFunctionStatement
            || statement instanceof SQLAlterFunctionStatement) &
            StringUtils.containsIgnoreCase(sql, "random_udf_test_")) {
            String functionName = getFunctionName(statement);
            result.setParallelPossibility(true);
            result.setObjName(functionName);
            result.setObjType(OBJ_TYPE_FUNCTION);
        }
    }

    @NotNull
    private static String getFunctionName(SQLStatement statement) {
        String functionName;
        if (statement instanceof SQLCreateFunctionStatement) {
            functionName = ((SQLCreateFunctionStatement) statement).getName().getSimpleName().toLowerCase();
        } else if (statement instanceof SQLDropFunctionStatement) {
            functionName = ((SQLDropFunctionStatement) statement).getName().getSimpleName().toLowerCase();
        } else if (statement instanceof SQLCreateJavaFunctionStatement) {
            functionName = ((SQLCreateJavaFunctionStatement) statement).getName().getSimpleName().toLowerCase();
        } else if (statement instanceof SQLDropJavaFunctionStatement) {
            functionName = ((SQLDropJavaFunctionStatement) statement).getName().getSimpleName().toLowerCase();
        } else {
            functionName = ((SQLAlterFunctionStatement) statement).getName().getSimpleName().toLowerCase();
        }
        return functionName;
    }

    @Data
    public static class DependencyCheckResult {
        public static final String OBJ_TYPE_TABLE = "TABLE";
        public static final String OBJ_TYPE_SEQ = "SEQ";
        public static final String OBJ_TYPE_FUNCTION = "FUNCTION";

        String ddlSql = "";
        String objName = "";
        String objType = "";
        boolean parallelPossibility = false;
        Set<Pair<String, String>> dependencyObjs = new HashSet<>();

        public boolean canParallelApply() {
            return parallelPossibility & dependencyObjs.isEmpty();
        }

        public Pair<String, String> getFullObjName() {
            return Pair.of(objType, objName);
        }

        public void clear() {
            ddlSql = "";
            objType = "";
            objName = "";
            parallelPossibility = false;
            dependencyObjs = new HashSet<>();
        }
    }

    @Data
    public static class Statistic {
        private Map<String, SchemaStatistic> statisticMap = new ConcurrentHashMap<>();
        private long lastFlushTime = System.currentTimeMillis();

        public void reset() {
            statisticMap.clear();
            lastFlushTime = System.currentTimeMillis();
        }

        public void addSchemaStatistic(SchemaStatistic schemaStatistic) {
            SchemaStatistic value = statisticMap.computeIfAbsent(
                schemaStatistic.getSchemaName().toLowerCase(), SchemaStatistic::new);
            value.add(schemaStatistic);
        }

        public void addEffectCostTime(String schemaName, long time) {
            SchemaStatistic schemaStatistic = statisticMap.get(schemaName.toLowerCase());
            schemaStatistic.addEffectCostTime(time);
        }
    }

    @Data
    public static class SchemaStatistic {
        private String schemaName;
        private long ddlCount;
        private long dmlCount;
        private long ddlCostTime;
        private long dmlCostTime;
        private long effectCostTime;

        public SchemaStatistic(String schemaName) {
            this.schemaName = schemaName;
        }

        public void add(SchemaStatistic in) {
            ddlCount += in.ddlCount;
            dmlCount += in.dmlCount;
            ddlCostTime += in.ddlCostTime;
            dmlCostTime += in.dmlCostTime;
        }

        public void addEffectCostTime(long time) {
            effectCostTime += time;
        }

        public long getTotalCount() {
            return ddlCount + dmlCount;
        }

        public long getTotalCostTime() {
            return ddlCostTime + dmlCostTime;
        }
    }

    @Data
    public class SchemaChannel {

        private final String schemaName;
        private final ConcurrentLinkedQueue<List<DBMSEvent>> events;
        private final ExecutorService executorService;
        private volatile String position = "";
        private volatile long lastExecuteTime = System.currentTimeMillis();

        public SchemaChannel(String schemaName) {
            this.schemaName = schemaName;
            this.events = new ConcurrentLinkedQueue<>();
            this.executorService = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("schema-channel-executor-" + schemaName).build());

            this.executorService.submit(() -> {
                while (true) {
                    if (events.peek() == null) {
                        try {
                            Thread.sleep(1000);
                            continue;
                        } catch (InterruptedException e) {
                            break;
                        }
                    }

                    try {
                        schemaChannelSemaphore.acquire();
                        List<DBMSEvent> dbmsEvents = events.peek();

                        SchemaExecutor schemaExecutor =
                            new SchemaExecutor(schemaName, dbmsEvents, p -> this.position = p);
                        schemaExecutor.call();

                        lastExecuteTime = System.currentTimeMillis();
                        position = dbmsEvents.get(dbmsEvents.size() - 1).getPosition();
                        events.poll();
                    } catch (Throwable t) {
                        schemaChannelError.set(t);
                        log.error("Fatal error in schema channel {}", schemaName, t);
                        break;
                    } finally {
                        schemaChannelSemaphore.release();
                    }
                }
            });
        }

        public int remaining() {
            Iterator<List<DBMSEvent>> iterator = events.iterator();
            int count = 0;
            while (iterator.hasNext()) {
                count += iterator.next().size();
            }
            return count;
        }

        public void close(boolean force) {
            if (!force && !events.isEmpty()) {
                throw new PolardbxException("can`t close schema channel, because events buffer is not empty");
            }
            this.executorService.shutdownNow();
            log.info("schema channel is closed with schema name " + schemaName);
        }
    }
}
