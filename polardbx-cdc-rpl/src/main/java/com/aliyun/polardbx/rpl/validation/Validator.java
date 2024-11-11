/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.dao.ValidationDiffMapper;
import com.aliyun.polardbx.binlog.dao.ValidationTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.ValidationDiff;
import com.aliyun.polardbx.binlog.domain.po.ValidationTask;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.extractor.full.ExtractorUtil;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.DataImportStateMachineContext;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.validation.common.DiffRecord;
import com.aliyun.polardbx.rpl.validation.common.DiffStateEnum;
import com.aliyun.polardbx.rpl.validation.common.ValidationStateEnum;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.CollectionUtils;

import java.math.BigInteger;
import java.rmi.UnexpectedException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.rpl.validation.ValidationTaskRepository.createValTask;
import static com.aliyun.polardbx.rpl.validation.ValidationTaskRepository.getValTaskRecord;
import static com.aliyun.polardbx.rpl.validation.ValidationTaskRepository.resetValTask;
import static com.aliyun.polardbx.rpl.validation.ValidationTaskRepository.updateValTaskState;

@Slf4j
public class Validator {
    private final DataImportMeta.ValidationMeta meta;

    private final ValidationTypeEnum type;

    private final Map<String, DruidDataSource> srcDs;
    private final Map<String, DruidDataSource> dstDs;

    private static final String POLARDBX_DEFAULT_SCHEMA = "polardbx";

    private TableInfo srcTableInfo;

    private ExecutorService srcThreadPool;
    private ExecutorService dstThreadPool;
    private ExecutorService threadPool;

    private static final ValidationDiffMapper diffMapper = SpringContextHolder.getObject(ValidationDiffMapper.class);
    private static final ValidationTaskMapper valTaskMapper = SpringContextHolder.getObject(ValidationTaskMapper.class);
    private static final String stateMachineId = Long.toString(TaskContext.getInstance().getStateMachineId());
    private static final String serviceId = Long.toString(TaskContext.getInstance().getServiceId());
    private static final String taskId = Long.toString(TaskContext.getInstance().getTaskId());

    private static final int rpsLimit = DynamicApplicationConfig.getInt(ConfigKeys.RPL_FULL_VALID_RECORDS_PER_SECOND);
    private static final int parallelism = DynamicApplicationConfig.getInt(ConfigKeys.RPL_FULL_VALID_TABLE_PARALLELISM);

    private static final boolean skipCollect =
        DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_FULL_VALID_SKIP_COLLECT_STATISTIC);

    private final RateLimiter rateLimiter;

    private DataImportStateMachineContext stateMachineContext;

    public Validator(final DataImportMeta.ValidationMeta validationMeta) {
        this.meta = validationMeta;
        this.type = meta.getType();
        rateLimiter = RateLimiter.create(rpsLimit);
        this.srcDs = new HashMap<>();
        this.dstDs = new HashMap<>();
    }

    public void start() throws Exception {
        initThreadPool();

        initDataSource();

        collectStatistic();

        startCheck();
    }

    private void initThreadPool() {
        threadPool = ThreadPoolUtil.createExecutorWithFixedNum(parallelism, "check-thread");
        srcThreadPool = ThreadPoolUtil.createExecutorWithFixedNum(parallelism, "src-hash-check-thread");
        dstThreadPool = ThreadPoolUtil.createExecutorWithFixedNum(parallelism, "dst-hash-check-thread");
    }

    private void initDataSource() throws Exception {
        for (String srcLogicalDb : meta.getSrcLogicalDbList()) {
            srcDs.put(srcLogicalDb, createDataSourceHelper(meta.getSrcLogicalConnInfo(), srcLogicalDb));
            String dstDb = meta.getDbMapping().get(srcLogicalDb);
            dstDs.put(dstDb, createDataSourceHelper(meta.getDstLogicalConnInfo(), dstDb));
        }
        dstDs.put(POLARDBX_DEFAULT_SCHEMA, createDataSourceHelper(meta.getDstLogicalConnInfo(),
            POLARDBX_DEFAULT_SCHEMA));
    }

    private DruidDataSource createDataSourceHelper(DataImportMeta.ConnInfo connInfo, String dbName) throws Exception {
        return DataSourceUtil.createDruidMySqlDataSource(false, connInfo.getHost(), connInfo.getPort(),
            dbName, connInfo.getUser(), connInfo.getPassword(), "", parallelism, parallelism, true,
            null, null);
    }

    private void collectStatistic() throws SQLException {
        if (stateMachineContext == null) {
            if (TaskContext.getInstance().getStateMachine().getContext() != null) {
                stateMachineContext = JSONObject.parseObject(TaskContext.getInstance().getStateMachine().getContext(),
                    DataImportStateMachineContext.class);
            } else {
                stateMachineContext = new DataImportStateMachineContext();
            }
        }
        if (skipCollect || stateMachineContext.isHasCollectStatistic()) {
            log.info("skip collect statistic");
            return;
        }

        try (Connection conn = dstDs.get(POLARDBX_DEFAULT_SCHEMA).getConnection();
            Statement stmt = conn.createStatement()) {
            log.info("prepare to collect statistic on dst...");
            stmt.execute("/*+TDDL:CMD_EXTRA(SOCKET_TIMEOUT=90000000)*/collect statistic");
            log.info("finished to collect statistic.");
        } catch (SQLException e) {
            log.error("failed to collect statistic.", e);
            throw new SQLException(e);
        }

        stateMachineContext.setHasCollectStatistic(true);
        String context = JSON.toJSONString(stateMachineContext);
        DbTaskMetaManager.updateStateMachineContext(TaskContext.getInstance().getStateMachineId(), context);
    }

    private void startCheck() throws Exception {
        Set<String> srcDbList = meta.getSrcLogicalDbList();
        Map<String, String> dbMapping = meta.getDbMapping();
        Map<String, Set<String>> srcDbToTables = meta.getSrcDbToTables();
        for (String srcDb : srcDbList) {
            log.info("start check db:{}", srcDb);

            String dstDb = dbMapping.get(srcDb);
            Set<String> tables = srcDbToTables.get(srcDb);
            List<Future<?>> futures = new ArrayList<>();
            for (String table : tables) {
                Future<?> future = threadPool.submit(() -> {
                    try {
                        validTable(srcDb, dstDb, table);
                    } catch (Exception e) {
                        log.error("error while valid table. src db:{}, table:{}", srcDb, table, e);
                        StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR,
                            TaskContext.getInstance().getTaskId(), e.getMessage());

                        try {
                            // mark state of no primary key table as done
                            updateValTaskState(srcDb, table, type, e instanceof NoPrimaryKeyException ?
                                ValidationStateEnum.DONE : ValidationStateEnum.ERROR);
                        } catch (Exception e1) {
                            log.error("error when update val task to error state. src db:{}, table:{}", srcDb,
                                table, e1);
                            // make sure that all valid tasks has been inserted into db when startCheck finished
                            throw e1;
                        }
                    }
                });
                futures.add(future);
            }

            for (Future<?> future : futures) {
                future.get();
            }

            log.info("db {} check finished.", srcDb);
        }

        threadPool.shutdown();
    }

    private void validTable(String srcDbName, String dstDbName, String tableName) throws Exception {
        log.info("start check table:{}.{}", srcDbName, tableName);

        Optional<ValidationTask> valTaskRecord = getValTaskRecord(srcDbName, tableName, type);
        String checkPoint = null;

        if (valTaskRecord.isPresent()) {
            ValidationTask task = valTaskRecord.get();
            if (ValidationStateEnum.valueOf(task.getState()) == ValidationStateEnum.DONE) {
                log.info("table {}.{} already check finished, will not check again", srcDbName, tableName);
                return;
            } else if (ValidationStateEnum.valueOf(task.getState()) == ValidationStateEnum.ERROR) {
                log.info("check table {}.{} error, will reset.", srcDbName, tableName);
                resetValTask(task);
                checkPoint = task.getTaskRange();
            } else {
                checkPoint = task.getTaskRange();
            }
        } else {
            createValTask(srcDbName, dstDbName, tableName, type);
        }

        srcTableInfo = DbMetaManager.getTableInfo(srcDs.get(srcDbName), srcDbName, tableName, HostType.POLARX1, false);

        List<List<Object>> sampleResult = doSample(srcDbName, dstDbName, tableName);
        if (checkPoint != null) {
            List<String> keyNames = srcTableInfo.getKeyList();
            List<Integer> fieldTypes = new ArrayList<>();
            for (String keyName : keyNames) {
                fieldTypes.add(srcTableInfo.getColumnType(keyName));
            }
            List<Object> cp = JSON.parseObject(checkPoint, List.class);
            sampleResult = filterSample(sampleResult, cp, fieldTypes);
        }

        if (CollectionUtils.isEmpty(sampleResult)) {
            check(srcDbName, dstDbName, tableName, null, null);
        } else {
            // 如果check point不为null，不要重复校验小于check point的数据了
            if (checkPoint == null) {
                check(srcDbName, dstDbName, tableName, null, sampleResult.get(0));
            }
            for (int i = 0; i < sampleResult.size() - 1; i++) {
                check(srcDbName, dstDbName, tableName, sampleResult.get(i), sampleResult.get(i + 1));
            }
            check(srcDbName, dstDbName, tableName, sampleResult.get(sampleResult.size() - 1), null);
        }

        updateValTaskState(srcDbName, tableName, type, ValidationStateEnum.DONE);

        log.info("check table:{}.{} finished.", srcDbName, tableName);
    }

    private void check(String srcDbName, String dstDbName, String tableName, List<Object> lowerBound,
                       List<Object> upperBound) throws Exception {
        boolean res = batchCheck(srcDbName, dstDbName, tableName, lowerBound, upperBound);
        if (!res) {
            detailCheck(srcDbName, dstDbName, tableName, lowerBound, upperBound);
        }

        // persist check point
        if (lowerBound != null || upperBound != null) {
            Optional<ValidationTask> valTaskRecord = getValTaskRecord(srcDbName, tableName, type);
            if (!valTaskRecord.isPresent()) {
                throw new UnexpectedException("failed to get task from metadb!");
            }
            if (upperBound != null) {
                persistCheckPoint(valTaskRecord.get(), upperBound);
            } else {
                persistCheckPoint(valTaskRecord.get(), lowerBound);
            }
        }
    }

    private void persistCheckPoint(ValidationTask task, List<Object> checkPoint) throws SQLException {
        String str = JSON.toJSONString(checkPoint);
        task.setTaskRange(str);
        int res = valTaskMapper.updateByPrimaryKeySelective(task);
        if (res != 1) {
            throw new SQLException("failed to persist check point!");
        }
    }

    private List<List<Object>> filterSample(List<List<Object>> sampleResult, List<Object> checkPoint,
                                            List<Integer> fieldTypes) {
        List<List<Object>> res = new ArrayList<>();
        res.add(checkPoint);

        List<String> cpKeyVal = new ArrayList<>();
        for (Object o : checkPoint) {
            cpKeyVal.add(o.toString());
        }

        int idx = 0;
        while (idx < sampleResult.size()) {
            List<String> keyVal = new ArrayList<>();
            for (Object o : sampleResult.get(idx)) {
                keyVal.add(o.toString());
            }
            if (compareKeyVal(cpKeyVal, keyVal, fieldTypes) < 0) {
                break;
            }
            idx++;
        }

        for (int i = idx; i < sampleResult.size(); i++) {
            res.add(sampleResult.get(i));
        }
        return res;
    }

    private List<List<Object>> doSample(String srcDbName, String dstDbName, String tableName) throws Exception {
        List<List<Object>> res =
            ValidationSampler.sample(dstDs.get(dstDbName), dstDbName, tableName, srcTableInfo.getPks());
        log.info("sample table {}.{} finished, sample point count:{}", srcDbName, tableName, res.size());
        return res;
    }

    private boolean batchCheck(String srcDbName, String dstDbName, String tableName, List<Object> lowerBound,
                               List<Object> upperBound) throws Exception {
        TableInfo tableInfo =
            DbMetaManager.getTableInfo(srcDs.get(srcDbName), srcDbName, tableName, HostType.POLARX1, false);
        SqlContextBuilder.SqlContext srcContext =
            ValSQLGenerator.getBatchCheckSql(srcDbName, tableName, tableInfo, lowerBound, upperBound);
        SqlContextBuilder.SqlContext dstContext =
            ValSQLGenerator.getBatchCheckSql(dstDbName, tableName, tableInfo, lowerBound, upperBound);

        Future<Pair<Integer, String>> srcFuture = srcThreadPool.submit(() -> {
            Stopwatch stopwatch = Stopwatch.createStarted();
            Pair<Integer, String> srcCheckResult =
                execBatchCheck(srcDs.get(srcDbName), srcContext.sql, srcContext.params);
            stopwatch.stop();
            log.info("exec batch check on src cost {} seconds, check cnt:{}, checksum:{}",
                stopwatch.elapsed(TimeUnit.SECONDS), srcCheckResult.getLeft(), srcCheckResult.getRight());
            return srcCheckResult;
        });

        Future<Pair<Integer, String>> dstFuture = dstThreadPool.submit(() -> {
            Stopwatch stopwatch = Stopwatch.createStarted();
            Pair<Integer, String> dstCheckResult =
                execBatchCheck(dstDs.get(dstDbName), dstContext.sql, dstContext.params);
            stopwatch.stop();
            log.info("exec batch check on dst cost {} seconds, check cnt:{}, checksum:{}",
                stopwatch.elapsed(TimeUnit.SECONDS),
                dstCheckResult.getLeft(), dstCheckResult.getRight());
            return dstCheckResult;
        });

        Pair<Integer, String> srcCheckResult = srcFuture.get();
        Pair<Integer, String> dstCheckResult = dstFuture.get();

        int cnt = srcCheckResult.getLeft();
        StatMetrics.getInstance().addOutMessageCount(cnt);
        rateLimiter.acquire(Math.max(cnt, 1));

        if (!srcCheckResult.equals(dstCheckResult)) {
            log.info("table:{}.{} batch check failed, will check row one by one.", srcDbName, tableName);
            return false;
        }
        return true;
    }

    private Pair<Integer, String> execBatchCheck(DruidDataSource ds, String sql, List<Object> params)
        throws SQLException {
        try (Connection conn = ds.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int cnt = rs.getInt(1);
                    String checksum = rs.getString(2);
                    return Pair.of(cnt, checksum);
                } else {
                    throw new SQLException("no result for batch check!");
                }
            }
        }
    }

    private void detailCheck(String srcDbName, String dstDbName, String tableName, List<Object> lowerBound,
                             List<Object> upperBound) throws Exception {
        List<DiffRecord> diffRows = findDiff(srcDbName, dstDbName, tableName, lowerBound, upperBound);
        persistDiff(srcDbName, dstDbName, tableName, diffRows);
    }

    private List<DiffRecord> findDiff(String srcDbName, String dstDbName, String tableName, List<Object> lowerBound,
                                      List<Object> upperBound) throws Exception {
        TableInfo tableInfo =
            DbMetaManager.getTableInfo(srcDs.get(srcDbName), srcDbName, tableName, HostType.POLARX1, false);

        List<String> keyNames = tableInfo.getKeyList();
        List<Integer> fieldTypes = new ArrayList<>();
        for (String keyName : keyNames) {
            fieldTypes.add(tableInfo.getColumnType(keyName));
        }

        SqlContextBuilder.SqlContext srcContext =
            ValSQLGenerator.getRowCheckSql(srcDbName, tableName, tableInfo, lowerBound, upperBound);
        SqlContextBuilder.SqlContext dstContext =
            ValSQLGenerator.getRowCheckSql(dstDbName, tableName, tableInfo, lowerBound, upperBound);
        if (log.isDebugEnabled()) {
            log.debug("start to check row. check sql:{}", srcContext.sql);
        }

        final long maxCount = DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_MAX_PERSIST_ROWS_COUNT);

        List<DiffRecord> res = new ArrayList<>();
        try (Connection srcConn = srcDs.get(srcDbName).getConnection();
            Connection dstConn = dstDs.get(dstDbName).getConnection();
            PreparedStatement srcStmt = srcConn.prepareStatement(srcContext.sql, ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY);
            PreparedStatement dstStmt = dstConn.prepareStatement(dstContext.sql, ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY)) {
            srcConn.setAutoCommit(false);
            dstConn.setAutoCommit(false);
            srcStmt.setFetchSize(Integer.MIN_VALUE);
            dstStmt.setFetchSize(Integer.MIN_VALUE);

            List<Object> params = srcContext.params;
            for (int i = 0; i < params.size(); i++) {
                srcStmt.setObject(i + 1, params.get(i));
                dstStmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet srcRs = srcStmt.executeQuery();
                ResultSet dstRs = dstStmt.executeQuery()) {
                List<Object> srcKeyVal = null, dstKeyVal = null;
                List<String> srcKeyStr = null, dstKeyStr = null;
                String lastSrcCheckSum = null, lastDstCheckSum = null;

                while (true) {
                    rateLimiter.acquire();

                    if (lastSrcCheckSum == null && srcRs.next()) {
                        lastSrcCheckSum = srcRs.getString("checksum");
                        srcKeyVal = new ArrayList<>();
                        srcKeyStr = new ArrayList<>();
                        for (String keyName : keyNames) {
                            srcKeyStr.add(srcRs.getString(keyName));
                            srcKeyVal.add(
                                ExtractorUtil.getColumnValue(srcRs, keyName, tableInfo.getColumnType(keyName)));
                        }
                    }

                    if (lastDstCheckSum == null && dstRs.next()) {
                        lastDstCheckSum = dstRs.getString("checksum");
                        dstKeyVal = new ArrayList<>();
                        dstKeyStr = new ArrayList<>();
                        for (String keyName : keyNames) {
                            dstKeyStr.add(dstRs.getString(keyName));
                            dstKeyVal.add(
                                ExtractorUtil.getColumnValue(dstRs, keyName, tableInfo.getColumnType(keyName)));
                        }
                    }

                    if (lastSrcCheckSum == null && lastDstCheckSum == null) {
                        break;
                    } else if (lastSrcCheckSum == null) {
                        // don't have source data, so all the target row's data is redundant, should be deleted

                        // todo by jiyue add recheck to confirm diff

                        log.info("Found orphan rows, dstKey:{}", dstKeyVal);
                        if (res.size() < maxCount) {
                            res.add(
                                DiffRecord.builder().keys(keyNames).dstKeyVal(dstKeyVal)
                                    .diffType(DiffRecord.DiffType.ORPHAN)
                                    .build());
                        } else {
                            log.warn("Too many diff rows, will skip persist diff!");
                        }
                        lastDstCheckSum = null;
                    } else if (lastDstCheckSum == null) {
                        // target lack some data, should insert the last source data
                        log.info("Found miss rows, srcKey:{}", srcKeyVal);

                        if (res.size() < maxCount) {
                            res.add(
                                DiffRecord.builder().keys(keyNames).srcKeyVal(srcKeyVal)
                                    .diffType(DiffRecord.DiffType.MISS)
                                    .build());
                        } else {
                            log.warn("Too many diff rows, will skip persist diff!");
                        }
                        lastSrcCheckSum = null;
                    } else {
                        if (lastSrcCheckSum.equals(lastDstCheckSum)) {
                            lastSrcCheckSum = null;
                            lastDstCheckSum = null;
                            continue;
                        }

                        DiffRecord diffRecord;
                        int cmp = compareKeyVal(srcKeyStr, dstKeyStr, fieldTypes);
                        if (cmp == 0) {
                            log.info("Found diff rows, srcKey:{}, dstKey:{}, src checksum:{}, dst checksum{}",
                                srcKeyVal, dstKeyVal, lastSrcCheckSum, lastDstCheckSum);

                            diffRecord = DiffRecord.builder().keys(keyNames).srcKeyVal(srcKeyVal).dstKeyVal(dstKeyVal)
                                .diffType(DiffRecord.DiffType.DIFF).build();
                            lastSrcCheckSum = null;
                            lastDstCheckSum = null;
                        } else if (cmp > 0) {
                            log.info("Found orphan rows, dstKey:{}", dstKeyVal);
                            diffRecord =
                                DiffRecord.builder().keys(keyNames).dstKeyVal(dstKeyVal)
                                    .diffType(DiffRecord.DiffType.ORPHAN)
                                    .build();
                            lastDstCheckSum = null;
                        } else {
                            log.info("Found miss rows, srcKey:{}", srcKeyVal);
                            diffRecord =
                                DiffRecord.builder().keys(keyNames).srcKeyVal(srcKeyVal)
                                    .diffType(DiffRecord.DiffType.MISS)
                                    .build();
                            lastSrcCheckSum = null;
                        }

                        if (res.size() < maxCount) {
                            res.add(diffRecord);
                        } else {
                            log.warn("Too many diff rows, will skip persist diff!");
                        }
                    }
                }
            }
        }

        return res;
    }

    private int compareKeyVal(List<String> keyVal1, List<String> keyVal2, List<Integer> fieldTypes) {
        int result = 0;
        for (int i = 0; i < keyVal1.size(); i++) {
            int type = fieldTypes.get(i);
            String k1 = keyVal1.get(i);
            String k2 = keyVal2.get(i);
            if (ExtractorUtil.isInteger(type)) {
                BigInteger v1 = new BigInteger(k1);
                BigInteger v2 = new BigInteger(k2);
                result = v1.compareTo(v2);
            } else if (ExtractorUtil.isFloat(type)) {
                double v1 = Double.parseDouble(k1);
                double v2 = Double.parseDouble(k2);
                result = Double.compare(v1, v2);
            } else {
                result = k1.compareTo(k2);
            }

            if (result != 0) {
                return result;
            }
        }
        return result;
    }

    private void persistDiff(String srcDbName, String dstDbName, String tableName, List<DiffRecord> diffRows) {
        log.info("Persisting diff rows. Src db name:{}, Table name:{}, Diff rows number:{}", srcDbName, tableName,
            diffRows.size());
        ValidationTask task =
            getValTaskRecord(srcDbName, tableName, type).orElseThrow(
                () -> new IllegalStateException("validation task not exist!"));

        List<ValidationDiff> diffList = new ArrayList<>();
        for (int i = 0; i < diffRows.size(); i++) {
            DiffRecord r = diffRows.get(i);
            ValidationDiff diff = new ValidationDiff();
            diff.setStateMachineId(stateMachineId);
            diff.setServiceId(serviceId);
            diff.setTaskId(taskId);
            diff.setValidationTaskId(task.getExternalId());
            diff.setType(r.getDiffType().name());
            diff.setState(DiffStateEnum.INIT.name());
            diff.setSrcLogicalDb(srcDbName);
            diff.setSrcLogicalTable(tableName);
            diff.setSrcLogicalKeyCol(JSONObject.toJSONString(r.getKeys()));
            diff.setSrcPhyDb("");
            diff.setSrcPhyTable("");
            diff.setSrcPhyKeyCol("");
            if (r.getSrcKeyVal() != null) {
                diff.setSrcKeyColVal(JSONObject.toJSONString(r.getSrcKeyVal()));
            } else {
                diff.setSrcKeyColVal("");
            }
            diff.setDstLogicalDb(dstDbName);
            diff.setDstLogicalTable(tableName);
            diff.setDstLogicalKeyCol("");
            if (r.getDstKeyVal() != null) {
                diff.setDstKeyColVal(JSONObject.toJSONString(r.getDstKeyVal()));
            } else {
                diff.setDstKeyColVal("");
            }
            diff.setDeleted(false);
            diff.setCreateTime(Date.from(Instant.now()));
            diff.setUpdateTime(Date.from(Instant.now()));
            diffList.add(diff);

            try {
                if (diffList.size() >= 1000 || i == diffRows.size() - 1) {
                    log.info("Try inserting batch records. Records size: {}", diffList.size());
                    diffMapper.insertMultiple(diffList);
                    diffList = new ArrayList<>();
                }
            } catch (Exception e) {
                StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR,
                    TaskContext.getInstance().getTaskId(), e.getMessage());
                log.error("Bulk insert into validation_diff table exception. Try insert records one by one", e);
                diffList.forEach(diffRecord -> {
                    log.info("insert one by one for diff record: {}", diffRecord.getSrcLogicalDb() + "."
                        + diffRecord.getSrcLogicalTable() + " " + diffRecord.getSrcKeyColVal());
                    diffMapper.insertSelective(diffRecord);
                });
                diffList = new ArrayList<>();
            }
        }
    }
}
