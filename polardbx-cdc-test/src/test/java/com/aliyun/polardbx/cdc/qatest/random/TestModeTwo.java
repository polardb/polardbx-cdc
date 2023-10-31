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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED;
import static com.aliyun.polardbx.cdc.qatest.random.SqlConstants.T_RANDOM_CREATE_SQL;
import static com.aliyun.polardbx.cdc.qatest.random.SqlConstants.T_RANDOM_INSERT_SQL;
import static com.aliyun.polardbx.cdc.qatest.random.SqlConstants.T_RANDOM_TABLE_PREFIX;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class TestModeTwo extends RplBaseTestCase {
    private static final String DB_NAME = "cdc_reformat_test_mode_two";
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    // ---------------------------------------- dml types ------------------------------------------
    private final ArrayList<DmlType> insertTypes = Lists.newArrayList(
        DmlType.INSERT, DmlType.INSERT, DmlType.INSERT, DmlType.INSERT, DmlType.INSERT,
        DmlType.INSERT, DmlType.INSERT, DmlType.INSERT, DmlType.INSERT, DmlType.INSERT);

    private final ArrayList<DmlType> updateTypes = Lists.newArrayList(
        DmlType.UPDATE, DmlType.UPDATE, DmlType.UPDATE, DmlType.UPDATE, DmlType.UPDATE,
        DmlType.UPDATE, DmlType.UPDATE, DmlType.UPDATE, DmlType.UPDATE, DmlType.UPDATE);

    private final ArrayList<DmlType> deleteTypes = Lists.newArrayList(
        DmlType.DELETE, DmlType.DELETE, DmlType.DELETE, DmlType.DELETE, DmlType.DELETE);

    private final ArrayList<DmlType> insertBatchTypes = Lists.newArrayList(
        DmlType.INSERT_BATCH, DmlType.INSERT_BATCH, DmlType.INSERT_BATCH);

    private final ArrayList<DmlType> updateBatchTypes = Lists.newArrayList(
        DmlType.UPDATE_BATCH, DmlType.UPDATE_BATCH, DmlType.UPDATE_BATCH);

    private final ArrayList<DmlType> deleteBatchTypes = Lists.newArrayList(
        DmlType.DELETE_BATCH, DmlType.DELETE_BATCH, DmlType.DELETE_BATCH);

    private final ArrayList<DmlType> dmlTypes = Lists.newArrayList();

    // ---------------------------------------- ddl types ------------------------------------------
    private final ArrayList<DdlType> addColumnTypes = Lists.newArrayList(
        DdlType.AddColumn, DdlType.AddColumn, DdlType.AddColumn, DdlType.AddColumn);

    private final ArrayList<DdlType> dropColumnTypes = Lists.newArrayList(
        DdlType.DropColumn, DdlType.DropColumn);

    private final ArrayList<DdlType> modifyColumnTypes = Lists.newArrayList(
        DdlType.ModifyColumn, DdlType.ModifyColumn, DdlType.ModifyColumn,
        DdlType.ModifyColumn, DdlType.ModifyColumn, DdlType.ModifyColumn);

    private final ArrayList<DdlType> ddlTypes = Lists.newArrayList();

    // ----------------------------------------- parameters -----------------------------------------
    private final int stage;
    private final String tableName;
    private final AtomicBoolean isDdlExecuting;
    private Thread dmlThread;
    private Thread ddlThread;

    private final ColumnSeeds columnSeeds;
    private final DmlSqlBuilder dmlSqlBuilder;
    private final DdlSqlBuilder ddlSqlBuilder;

    private int testTimeMinutes;
    private int dmlBatchLimitNum;
    private long dmlIntervalMs;
    private long ddlIntervalMs;
    private long loopWaitTimeoutMs;
    private boolean useRandomColumn4Dml;

    public TestModeTwo(String stageId) {
        this.stage = Integer.parseInt(stageId);
        this.tableName = T_RANDOM_TABLE_PREFIX + stageId;
        this.isDdlExecuting = new AtomicBoolean(false);

        this.columnSeeds = new ColumnSeeds(DB_NAME, tableName);
        this.dmlSqlBuilder = new DmlSqlBuilder(DB_NAME, tableName, columnSeeds, stage == 1);
        this.ddlSqlBuilder = new DdlSqlBuilder(tableName, columnSeeds,
            stage == 2,
            stage == 2,
            stage == 2,
            stage == 2,
            stage == 1,
            stage == 1);
    }

    @Before
    public void bootStrap() throws SQLException {
        if (INITIALIZED.compareAndSet(false, true)) {
            prepareTestDatabase(DB_NAME);
        }

        setSystemParameters();
        prepareTables();
        buildParameters();
        buildDmlTypes();
        buildDdlTypes();
    }

    @Parameterized.Parameters
    public static List<String[]> getTestParameters() {
        return Arrays.asList(new String[][] {{"1"}, {"2"}});
    }

    @Test
    public void testRandomDmlWithDdl() throws InterruptedException {
        execute();
        Metrics.getInstance().print();

        if (stage == 1) {
            waitAndCheck(CheckParameter.builder()
                .dbName(DB_NAME)
                .tbName(tableName)
                .directCompareDetail(true)
                .compareDetailOneByOne(true)
                .loopWaitTimeoutMs(loopWaitTimeoutMs)
                .build());
        } else {
            sendTokenAndWait(CheckParameter.builder().loopWaitTimeoutMs(loopWaitTimeoutMs).build());
        }

        Metrics.getInstance().check();
    }

    private void execute() throws InterruptedException {
        AtomicBoolean dmlRunningFlag = new AtomicBoolean(true);
        AtomicBoolean ddlRunningFlag = new AtomicBoolean(true);

        dmlThread = buildDmlThread(dmlRunningFlag);
        ddlThread = buildDdlThread(ddlRunningFlag);

        dmlThread.start();
        ddlThread.start();

        new Thread(() -> {
            try {
                sleep(testTimeMinutes * 60 * 1000);
            } catch (Throwable t) {
                log.error("background thread meet an error!", t);
            } finally {
                dmlThread.interrupt();
                ddlThread.interrupt();
                dmlRunningFlag.compareAndSet(true, false);
                ddlRunningFlag.compareAndSet(true, false);
            }
        }).start();

        dmlThread.join();
        ddlThread.join();
        log.info("random dml&ddl is successfully executed for stage " + stage);
    }

    @SneakyThrows
    private void setSystemParameters() {
        // 随机dml & ddl测试，binlog量比较大，可能造成比较高的延迟，关闭recover tso testing 模式
        try (Connection connection = ConnectionManager.getInstance().getDruidMetaConnection()) {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate(String.format(
                "replace into binlog_system_config(config_key,config_value)values('%s','%s')",
                TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED, "false"));
        }
    }

    private void prepareTables() throws SQLException {
        JdbcUtil.executeUpdate(getPolardbxConnection(DB_NAME), String.format(T_RANDOM_CREATE_SQL, tableName));
        JdbcUtil.executeUpdate(getPolardbxConnection(DB_NAME), String.format(T_RANDOM_INSERT_SQL, tableName));
        columnSeeds.buildColumnSeeds();
    }

    private void buildParameters() {
        testTimeMinutes = Integer.parseInt(System.getProperty("testTimeMinutes", "10"));
        dmlIntervalMs = Long.parseLong(System.getProperty("dmlIntervalMs", "1"));
        ddlIntervalMs = Long.parseLong(System.getProperty("ddlIntervalMs", "10"));
        loopWaitTimeoutMs = Long.parseLong(System.getProperty("loopWaitTimeoutMs", "300000"));

        if (stage == 1) {
            useRandomColumn4Dml = false;
            //数量太大可能导致比较高的延迟
            dmlBatchLimitNum = Integer.parseInt(System.getProperty("dmlBatchLimitNum1", "10"));
        } else {
            useRandomColumn4Dml = true;
            dmlBatchLimitNum = Integer.parseInt(System.getProperty("dmlBatchLimitNum2", "20"));
        }
    }

    private void buildDmlTypes() {
        String dmlTypeConfig;
        if (stage == 1) {
            dmlTypeConfig = "INSERT,INSERT_BATCH,UPDATE,UPDATE_BATCH";
        } else {
            dmlTypeConfig = "INSERT,INSERT_BATCH,UPDATE,UPDATE_BATCH,DELETE,DELETE_BATCH";
        }

        String[] tokens = StringUtils.split(dmlTypeConfig, ",");
        for (String token : tokens) {
            if (DmlType.INSERT.name().equals(token)) {
                dmlTypes.addAll(insertTypes);
            } else if (DmlType.INSERT_BATCH.name().equals(token)) {
                dmlTypes.addAll(insertBatchTypes);
            } else if (DmlType.UPDATE.name().equals(token)) {
                dmlTypes.addAll(updateTypes);
            } else if (DmlType.UPDATE_BATCH.name().equals(token)) {
                dmlTypes.addAll(updateBatchTypes);
            } else if (DmlType.DELETE.name().equals(token)) {
                dmlTypes.addAll(deleteTypes);
            } else if (DmlType.DELETE_BATCH.name().equals(token)) {
                dmlTypes.addAll(deleteBatchTypes);
            }
        }
    }

    private void buildDdlTypes() {
        String ddlTypeConfig = System.getProperty("ddlTypes", "AddColumn,DropColumn,ModifyColumn");
        String[] tokens = StringUtils.split(ddlTypeConfig, ",");
        for (String token : tokens) {
            if (DdlType.AddColumn.name().equals(token)) {
                ddlTypes.addAll(addColumnTypes);
            } else if (DdlType.DropColumn.name().equals(token)) {
                ddlTypes.addAll(dropColumnTypes);
            } else if (DdlType.ModifyColumn.name().equals(token)) {
                ddlTypes.addAll(modifyColumnTypes);
            }
        }
    }

    private Thread buildDmlThread(AtomicBoolean running) {
        return new Thread(() -> {

            while (running.get()) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                int index = new Random().nextInt(dmlTypes.size());
                DmlType dmlType = dmlTypes.get(index);

                switch (dmlType) {
                case INSERT:
                    insertSingle();
                    break;
                case UPDATE:
                    updateSingle();
                    break;
                case DELETE:
                    deleteSingle();
                    break;
                case INSERT_BATCH:
                    insertBatch();
                    break;
                case UPDATE_BATCH:
                    updateBatch();
                    break;
                case DELETE_BATCH:
                    deleteBatch();
                    break;
                default:
                    throw new PolardbxException("invalid dml type " + dmlType);
                }

                if (!isDdlExecuting.get()) {
                    sleep(dmlIntervalMs);
                }
            }
            log.info("dml thread finished!");
        });
    }

    private Thread buildDdlThread(AtomicBoolean running) {
        return new Thread(() -> {
            while (running.get()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                isDdlExecuting.set(true);
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

                isDdlExecuting.set(false);
                sleep(ddlIntervalMs);
            }
            log.info("ddl thread finished!");
        });
    }

    private void addColumn() {
        String columnName = RandomUtil.randomIdentifier();
        Pair<String, String> pair = ddlSqlBuilder.buildAddColumnSql(columnName);

        try (Connection connection = getPolardbxConnection(DB_NAME)) {
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

        try (Connection connection = getPolardbxConnection(DB_NAME)) {
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

        try (Connection connection = getPolardbxConnection(DB_NAME)) {
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

    private void insertSingle() {
        Pair<String, List<Pair<String, Object>>> insertSqlPair = dmlSqlBuilder.buildInsertSql(useRandomColumn4Dml);

        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            setSqlMode("", connection);
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

    private void insertBatch() {
        if (stage == 2) {
            insertBatch1();
        } else {
            insertBatch2();
        }
    }

    private void insertBatch1() {
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            setSqlMode("", connection);
            String sql = dmlSqlBuilder.buildInsertBatchSql(useRandomColumn4Dml, dmlBatchLimitNum);
            Statement stmt = connection.createStatement();
            stmt.execute(sql);
            Metrics.getInstance().getInsertBatchSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getInsertBatchFail().incrementAndGet();
            log.error("insert batch error!!", t);
        }
    }

    private void insertBatch2() {
        Pair<String, List<Pair<String, Object>>> insertSqlPair =
            dmlSqlBuilder.buildInsertBatchSql2(false, dmlBatchLimitNum);

        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            setSqlMode("", connection);
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

    private void updateSingle() {
        Pair<String, List<Pair<String, Object>>> updateSqlPair = dmlSqlBuilder.buildUpdateSql(useRandomColumn4Dml);
        try {
            update(updateSqlPair);
            Metrics.getInstance().getUpdateSingleSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getUpdateSingleFail().incrementAndGet();
            log.error("update single error!! \r\n sql : " + updateSqlPair.getKey() + "  \r\n parameter : "
                + JSONObject.toJSONString(updateSqlPair.getValue(), true), t);
        }
    }

    private void updateBatch() {
        if (stage == 2) {
            updateBatch1();
        } else {
            updateBatch2();
        }
    }

    private void updateBatch1() {
        try {
            Pair<String, List<Pair<String, Object>>> updateSqlPair =
                dmlSqlBuilder.buildUpdateBatchSql(useRandomColumn4Dml, dmlBatchLimitNum);
            update(updateSqlPair);
            Metrics.getInstance().getUpdateBatchSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getUpdateBatchFail().incrementAndGet();
            log.error("update batch error!!", t);
        }
    }

    private void updateBatch2() {
        try {
            Pair<String, List<Pair<String, Object>>> updateSqlPair =
                dmlSqlBuilder.buildUpdateBatchSql2(useRandomColumn4Dml, dmlBatchLimitNum);
            update(updateSqlPair);
            Metrics.getInstance().getUpdateBatchSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getUpdateBatchFail().incrementAndGet();
            log.error("update batch error!!", t);
        }
    }

    private void deleteSingle() {
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            setSqlMode("", connection);
            String sql = dmlSqlBuilder.buildDeleteSql();
            Statement stmt = connection.createStatement();
            stmt.execute(sql);
            Metrics.getInstance().getDeleteSingleSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getDeleteSingleFail().incrementAndGet();
            log.error("delete single error!!", t);
        }
    }

    private void deleteBatch() {
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            setSqlMode("", connection);
            String sql = dmlSqlBuilder.buildDeleteBatchSql(dmlBatchLimitNum);
            Statement stmt = connection.createStatement();
            stmt.execute(sql);
            Metrics.getInstance().getDeleteBatchSuccess().incrementAndGet();
        } catch (Throwable t) {
            Metrics.getInstance().getDeleteBatchFail().incrementAndGet();
            log.error("delete batch error!!", t);
        }
    }

    private void update(Pair<String, List<Pair<String, Object>>> updateSqlPair) throws SQLException {
        try (Connection connection = getPolardbxConnection(DB_NAME)) {
            setSqlMode("", connection);
            PreparedStatement statement = connection.prepareStatement(updateSqlPair.getKey());
            List<Pair<String, Object>> parameters = updateSqlPair.getValue();

            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i).getValue());
            }
            statement.execute();
        }
    }

    private void sleep(long mills) {
        try {
            Thread.sleep(mills);
        } catch (InterruptedException e) {

        }
    }
}
