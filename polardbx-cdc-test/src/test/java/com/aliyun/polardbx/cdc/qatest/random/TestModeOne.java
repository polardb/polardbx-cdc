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
package com.aliyun.polardbx.cdc.qatest.random;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RECOVER_TSO_TESTING_ENABLE;
import static com.aliyun.polardbx.cdc.qatest.random.DdlType.AddColumn;
import static com.aliyun.polardbx.cdc.qatest.random.DdlType.DropColumn;
import static com.aliyun.polardbx.cdc.qatest.random.DdlType.ModifyColumn;
import static com.aliyun.polardbx.cdc.qatest.random.SqlConstants.T_RANDOM_CREATE_SQL;
import static com.aliyun.polardbx.cdc.qatest.random.SqlConstants.T_RANDOM_INSERT_SQL;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class TestModeOne extends RplBaseTestCase {
    private final ArrayList<DmlType> dmlTypes = Lists.newArrayList(
        DmlType.INSERT,
        DmlType.INSERT,
        DmlType.INSERT,
        DmlType.UPDATE,
        DmlType.UPDATE,
        DmlType.UPDATE,
        DmlType.INSERT_BATCH,
        DmlType.INSERT_BATCH,
        DmlType.UPDATE_BATCH,
        DmlType.UPDATE_BATCH);

    private final ArrayList<DdlType> ddlTypes = Lists.newArrayList(
        AddColumn,
        AddColumn,
        AddColumn,
        AddColumn,
        ModifyColumn,
        ModifyColumn,
        ModifyColumn,
        ModifyColumn,
        ModifyColumn,
        ModifyColumn,
        DropColumn,
        DropColumn);

    private final String dbName;
    private final String tableName;
    private final ColumnSeeds columnSeeds;
    private final DmlSqlBuilder dmlSqlBuilder;
    private final DdlSqlBuilder ddlSqlBuilder;

    private Thread ddlThread;
    private volatile Throwable ddlError;
    private ExecutorService dmlExecutorService;

    private int testTimeMinutes;
    private int truncateThreshold;
    private int dmlBatchLimitNum;
    private int insertBatchMode;
    private int updateBatchMode;
    private long loopWaitTimeoutMs;

    public TestModeOne() {
        this.dbName = "cdc_reformat_test_mode_one";
        this.tableName = System.getProperty("tableName", "t_random_instant_check");
        this.columnSeeds = new ColumnSeeds(dbName, tableName);
        this.dmlSqlBuilder = new DmlSqlBuilder(dbName, tableName, columnSeeds, true);
        this.ddlSqlBuilder = new DdlSqlBuilder(tableName, columnSeeds, false, false, false, false, true, true);
    }

    @Before
    public void bootStrap() throws SQLException {
        tryCreateDb();
        setSystemParameters();
        prepareTables();
        buildParameters();
    }

    public void testDdlWithCommitDelay() {
        final AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService dmlExecutorService = Executors.newSingleThreadExecutor();
        ExecutorService ddlExecutorService = Executors.newSingleThreadExecutor();
        ddlExecutorService.submit(() -> {
            while (running.get()) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                List<DdlType> ddlTypeList = Lists.newArrayList(AddColumn, DropColumn, ModifyColumn);
                int index = new Random().nextInt(ddlTypeList.size());
                DdlType ddlType = ddlTypeList.get(index);
                switch (ddlType) {
                case AddColumn:
                    addColumn();
                    break;
                case DropColumn:
                    dropColumn();
                    break;
                case ModifyColumn:
                    modifyColumn();
                    break;
                default:
                    throw new PolardbxException("invalid ddl type " + ddlType);
                }
            }
        });
        dmlExecutorService.submit(() -> {
            while (running.get()) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int index = new Random().nextInt(dmlTypes.size());
                DmlType dmlType = dmlTypes.get(index);
                long start = System.currentTimeMillis();

                System.out.println("start start start start");
                switch (dmlType) {
                case INSERT:
                    insertSingle(true);
                    break;
                case UPDATE:
                    updateSingle(true);
                    break;
                case INSERT_BATCH:
                    insertBatch(true);
                    break;
                case UPDATE_BATCH:
                    updateBatch(true);
                    break;
                default:
                    throw new PolardbxException("invalid dml type " + dmlType);
                }
            }
        });

        try {
            sleep(testTimeMinutes * 60 * 1000);
        } catch (Throwable t) {
            log.error("background thread meet an error!", t);
        } finally {
            ddlExecutorService.shutdownNow();
            dmlExecutorService.shutdownNow();
            running.compareAndSet(true, false);
        }
    }

    @Test
    public void testRandomDmlWithDdl() throws InterruptedException {
        execute();
        checkData();
    }

    @SneakyThrows
    private void tryCreateDb() {
        try (Connection polardbxConnection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            JdbcUtil.executeSuccess(polardbxConnection, "CREATE DATABASE IF NOT EXISTS `" + dbName + "`");
            log.info("/*MASTER*/CREATE DATABASE IF NOT EXISTS `" + dbName + "`");
        }
    }

    private void execute() throws InterruptedException {
        dmlExecutorService = Executors.newSingleThreadExecutor();
        AtomicBoolean ddlRunningFlag = new AtomicBoolean(true);
        ddlThread = buildDdlThread(ddlRunningFlag);
        ddlThread.start();

        new Thread(() -> {
            try {
                sleep(testTimeMinutes * 60 * 1000);
            } catch (Throwable t) {
                log.error("background thread meet an error!", t);
            } finally {
                ddlThread.interrupt();
                ddlRunningFlag.compareAndSet(true, false);
            }
        }).start();

        ddlThread.join();
        Metrics.getInstance().print();
        if (ddlError != null) {
            log.error("ddl thread meet an error !! ", ddlError);
        }
        Assert.assertNull("find ddl error : " + ddlError, ddlError);
        Metrics.getInstance().check();

        log.info("random dml&ddl is successfully executed ");
    }

    @SneakyThrows
    private void setSystemParameters() {
        // 随机dml & ddl测试，binlog量比较大，可能造成比较高的延迟，关闭recover tso testing 模式
        try (Connection connection = ConnectionManager.getInstance().getDruidMetaConnection()) {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate(String.format(
                "replace into binlog_system_config(config_key,config_value)values('%s','%s')",
                TOPOLOGY_RECOVER_TSO_TESTING_ENABLE, "false"));
        }
    }

    private void prepareTables() throws SQLException {
        JdbcUtil.executeUpdate(getPolardbxConnection(dbName), String.format(T_RANDOM_CREATE_SQL, tableName));
        JdbcUtil.executeUpdate(getPolardbxConnection(dbName), String.format(T_RANDOM_INSERT_SQL, tableName));
        columnSeeds.buildColumnSeeds();
    }

    private void buildParameters() {
        testTimeMinutes = Integer.parseInt(System.getProperty("testTimeMinutes", "60"));
        loopWaitTimeoutMs = Long.parseLong(System.getProperty("loopWaitTimeoutMs", "180000"));
        dmlBatchLimitNum = Integer.parseInt(System.getProperty("dmlBatchLimitNum", "50"));
        truncateThreshold = Integer.parseInt(System.getProperty("truncateThreshold", "3000"));
        insertBatchMode = Integer.parseInt(System.getProperty("insertBatchMode", "2"));
        updateBatchMode = Integer.parseInt(System.getProperty("updateBatchMode", "2"));
    }

    private Thread buildDdlThread(AtomicBoolean running) {
        return new Thread(() -> {
            try {
                while (running.get()) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    // prepare dml executor
                    DmlExecutor dmlExecutor = new DmlExecutor();
                    dmlExecutorService.submit(dmlExecutor);
                    dmlExecutor.waitStart();

                    // execute ddl sql
                    int index = new Random().nextInt(ddlTypes.size());
                    DdlType ddlType = ddlTypes.get(index);
                    switch (ddlType) {
                    case AddColumn:
                        addColumn();
                        break;
                    case DropColumn:
                        dropColumn();
                        break;
                    case ModifyColumn:
                        modifyColumn();
                        break;
                    default:
                        throw new PolardbxException("invalid ddl type " + ddlType);
                    }

                    // stop dml executor & check
                    dmlExecutor.stopAndWait();
                    checkData();

                    //tryTruncate
                    tryTruncate();
                }
                log.info("ddl thread finished!");
            } catch (Throwable t) {
                ddlError = t;
            }
        });
    }

    private void checkData() {
        waitAndCheck(CheckParameter.builder()
            .dbName(dbName)
            .tbName(tableName)
            .directCompareDetail(true)
            .compareDetailOneByOne(true)
            .loopWaitTimeoutMs(loopWaitTimeoutMs)
            .build());
    }

    private void addColumn() {
        String columnName = RandomUtil.randomIdentifier();
        Pair<String, String> pair = ddlSqlBuilder.buildAddColumnSql(columnName);

        try (Connection connection = getPolardbxConnection(dbName)) {
            setSqlMode("", connection);
            Statement stmt = connection.createStatement();
            stmt.execute(pair.getValue());
            columnSeeds.COLUMN_NAME_COLUMN_TYPE_MAPPING.put(columnName, pair.getKey());
            Metrics.getInstance().getAddColumnSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getAddColumnFail().incrementAndGet();
            log.error("add column error!! \r\nsql : " + pair.getValue(), t);
        }
    }

    private void dropColumn() {
        String columnName = ddlSqlBuilder.findSeedColumn4Drop();
        String sql = ddlSqlBuilder.buildDropColumnSql(columnName);
        columnSeeds.COLUMN_NAME_COLUMN_TYPE_MAPPING.remove(columnName);

        try (Connection connection = getPolardbxConnection(dbName)) {
            setSqlMode("", connection);
            Statement stmt = connection.createStatement();
            stmt.execute(sql);
            Metrics.getInstance().getDropColumnSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getDropColumnFail().incrementAndGet();
            log.error("drop column error!! \r\n sql : " + sql, t);
        }
    }

    private void modifyColumn() {
        Pair<Pair<String, String>, String> pair = ddlSqlBuilder.buildModifyColumnSql();
        String columnName = pair.getKey().getKey();
        String columnType = pair.getKey().getValue();
        String sql = pair.getValue();

        try (Connection connection = getPolardbxConnection(dbName)) {
            setSqlMode("", connection);
            Statement stmt = connection.createStatement();
            stmt.execute(sql);
            columnSeeds.COLUMN_NAME_COLUMN_TYPE_MAPPING.put(columnName, columnType);
            Metrics.getInstance().getModifyColumnSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getModifyColumnFail().incrementAndGet();
            log.error("modify column error!! \r\nsql : " + sql, t);
        }
    }

    private void insertSingle(boolean withDelay) {
        Pair<String, List<Pair<String, Object>>> insertSqlPair = dmlSqlBuilder.buildInsertSql(false);

        try (Connection connection = getPolardbxConnection(dbName)) {
            setSqlMode("", connection);
            trySetCommitDelay(withDelay, connection);
            PreparedStatement statement = connection.prepareStatement(insertSqlPair.getKey());
            List<Pair<String, Object>> parameters = insertSqlPair.getValue();

            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i).getValue());
            }
            statement.execute();
            Metrics.getInstance().getInsertSingleSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getInsertSingleFail().incrementAndGet();
            log.error("insert single error!! \r\n sql : " + insertSqlPair.getKey() + " \r\nparameter : "
                + JSONObject.toJSONString(insertSqlPair.getValue(), true), t);
        }
    }

    private void insertBatch(boolean withDelay) {
        if (insertBatchMode == 1) {
            insertBatch1(withDelay);
        } else {
            insertBatch2(withDelay);
        }
    }

    private void insertBatch1(boolean withDelay) {
        try (Connection connection = getPolardbxConnection(dbName)) {
            setSqlMode("", connection);
            trySetCommitDelay(withDelay, connection);
            String sql = dmlSqlBuilder.buildInsertBatchSql(false, dmlBatchLimitNum);
            Statement stmt = connection.createStatement();
            stmt.execute(sql);
            Metrics.getInstance().getInsertBatchSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getInsertBatchFail().incrementAndGet();
            log.error("insert batch error!!", t);
        }
    }

    private void insertBatch2(boolean withDelay) {
        Pair<String, List<Pair<String, Object>>> insertSqlPair =
            dmlSqlBuilder.buildInsertBatchSql2(false, dmlBatchLimitNum);

        try (Connection connection = getPolardbxConnection(dbName)) {
            setSqlMode("", connection);
            trySetCommitDelay(withDelay, connection);
            PreparedStatement statement = connection.prepareStatement(insertSqlPair.getKey());
            List<Pair<String, Object>> parameters = insertSqlPair.getValue();

            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i).getValue());
            }
            statement.execute();
            Metrics.getInstance().getInsertBatchSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getInsertBatchFail().incrementAndGet();
            log.error("insert batch error!! \r\n sql : " + insertSqlPair.getKey() + " \r\nparameter : "
                + JSONObject.toJSONString(insertSqlPair.getValue(), true), t);
        }
    }

    private void updateSingle(boolean withDelay) {
        Pair<String, List<Pair<String, Object>>> updateSqlPair = dmlSqlBuilder.buildUpdateSql(false);
        try {
            update(updateSqlPair, withDelay);
            Metrics.getInstance().getUpdateSingleSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getUpdateSingleFail().incrementAndGet();
            log.error("update single error!! \r\n sql : " + updateSqlPair.getKey() + "  \r\n parameter : "
                + JSONObject.toJSONString(updateSqlPair.getValue(), true), t);
        }
    }

    private void updateBatch(boolean withDelay) {
        if (updateBatchMode == 1) {
            updateBatch1(withDelay);
        } else {
            updateBatch2(withDelay);
        }
    }

    private void updateBatch1(boolean withDelay) {
        try {
            Pair<String, List<Pair<String, Object>>> updateSqlPair =
                dmlSqlBuilder.buildUpdateBatchSql(false, dmlBatchLimitNum);
            update(updateSqlPair, withDelay);
            Metrics.getInstance().getUpdateBatchSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getUpdateBatchFail().incrementAndGet();
            log.error("update batch error!!", t);
        }
    }

    private void updateBatch2(boolean withDelay) {
        try {
            Pair<String, List<Pair<String, Object>>> updateSqlPair =
                dmlSqlBuilder.buildUpdateBatchSql2(false, dmlBatchLimitNum);
            update(updateSqlPair, withDelay);
            Metrics.getInstance().getUpdateBatchSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getUpdateBatchFail().incrementAndGet();
            log.error("update batch error!!", t);
        }
    }

    private void update(Pair<String, List<Pair<String, Object>>> updateSqlPair, boolean withDelay) throws SQLException {
        try (Connection connection = getPolardbxConnection(dbName)) {
            setSqlMode("", connection);
            trySetCommitDelay(withDelay, connection);
            PreparedStatement statement = connection.prepareStatement(updateSqlPair.getKey());
            List<Pair<String, Object>> parameters = updateSqlPair.getValue();

            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i).getValue());
            }
            statement.execute();
        }
    }

    private void trySetCommitDelay(boolean withCommitDelay, Connection connection) {
        if (withCommitDelay) {
            String sql0 = "set global COMPLEX_DML_WITH_TRX=true";
            String sql1 = "SET FAILURE_INJECTION = true";
            String sql2 = "SET DELAY_XA_COMMIT = 30";
            JdbcUtil.updateDataTddl(connection, sql0, null);
            JdbcUtil.updateDataTddl(connection, sql1, null);
            JdbcUtil.updateDataTddl(connection, sql2, null);
        }
    }

    private void sleep(long mills) {
        try {
            Thread.sleep(mills);
        } catch (InterruptedException e) {

        }
    }

    @SneakyThrows
    private void tryTruncate() {
        // 数据太多的话，数据校验耗时比较久，下游mysql执行ddl也会耗很多时间，触达阈值之后，进行truncate处理
        try (Connection connection = getPolardbxConnection(dbName)) {
            String querySql = "select count(id) from " + tableName;
            String truncateSql = "truncate " + tableName;

            boolean needTruncate = false;
            Statement stmt = connection.createStatement();
            ResultSet resultSet = stmt.executeQuery(querySql);
            while (resultSet.next()) {
                int count = resultSet.getInt(1);
                if (count >= truncateThreshold) {
                    needTruncate = true;
                }
            }

            if (needTruncate) {
                Statement statement = connection.createStatement();
                statement.executeUpdate(truncateSql);

                //truncate 完之后，实时插入一些数据
                for (int i = 0; i < 10; i++) {
                    insertSingle(false);
                }
            }
        }
    }

    private class DmlExecutor implements Runnable {

        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicBoolean isStarted = new AtomicBoolean(false);
        private final AtomicBoolean isComplete = new AtomicBoolean(false);

        public void waitStart() {
            long start = System.currentTimeMillis();
            while (!isStarted.get()) {
                sleep(10);
                if (System.currentTimeMillis() - start > 60000) {
                    throw new PolardbxException("wait start timeout!");
                }
            }
        }

        public void stopAndWait() {
            long start = System.currentTimeMillis();
            running.compareAndSet(true, false);
            while (!isComplete.get()) {
                sleep(10);
                if (System.currentTimeMillis() - start > 60000) {
                    throw new PolardbxException("wait complete timeout!");
                }
            }
        }

        @Override
        public void run() {
            try {
                while (running.get()) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    int index = new Random().nextInt(dmlTypes.size());
                    DmlType dmlType = dmlTypes.get(index);

                    switch (dmlType) {
                    case INSERT:
                        insertSingle(false);
                        break;
                    case UPDATE:
                        updateSingle(false);
                        break;
                    case INSERT_BATCH:
                        insertBatch(false);
                        break;
                    case UPDATE_BATCH:
                        updateBatch(false);
                        break;
                    default:
                        throw new PolardbxException("invalid dml type " + dmlType);
                    }

                    isStarted.set(true);
                }
            } finally {
                isComplete.set(true);
            }
        }
    }
}
