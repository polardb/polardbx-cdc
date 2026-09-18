/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.precheck.binlog;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * 验证 GENERATED 生成列 DROP 期间的整形（reformat）场景：
 * 1. 建表包含 STORED 生成列，分表（tbpartitions）
 * 2. DDL 同步到下游后，将下游该列改为 NOT NULL 且无 DEFAULT
 * 3. 在源端执行 ALTER TABLE DROP 生成列
 * 4. DDL 执行期间持续写入 DML，触发中间态整形（部分分表已 drop 列，部分未 drop）
 * 5. DDL 完成后，验证同步不中断、数据一致
 */
@Slf4j
public class GeneratedColumnDropTest extends RplBaseTestCase {

    private static final String DB_NAME = "cdc_gen_col_drop_" + (usingBinlogX ? "binlogx" : "single");
    private static final String TABLE_NAME = "t_gen_col";

    // 建表 SQL：包含生成列 gen_value (STORED)，分 8 个分表以增加中间态窗口
    private static final String CREATE_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS `" + TABLE_NAME + "` (\n"
            + "  `id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "  `c_idx` bigint(20) NOT NULL DEFAULT 100,\n"
            + "  `c_name` varchar(64) DEFAULT 'default_name',\n"
            + "  `c_amount` int(11) NOT NULL DEFAULT 0,\n"
            + "  `gen_value` bigint GENERATED ALWAYS AS (`c_idx` + `c_amount`) STORED,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n"
            + "  dbpartition by hash(`id`) tbpartition by hash(`id`) tbpartitions 128";

    // 初始数据插入（不包含生成列）
    private static final String INSERT_SQL_TEMPLATE =
        "INSERT INTO `%s`.`" + TABLE_NAME + "` (`c_idx`, `c_name`, `c_amount`) VALUES (%d, '%s', %d)";

    // 忽略生成列进行对比
    private static final Set<String> IGNORE_COLUMNS = Collections.unmodifiableSet(
        new HashSet<String>() {{
            add("gen_value");
        }}
    );

    @BeforeClass
    public static void bootStrap() throws SQLException {
        prepareTestDatabase(DB_NAME);
    }

    @Test
    public void testDropGeneratedColumnWithConcurrentDml() throws Exception {
        log.info("=== Step 1: Create table with generated column ===");
        try (Connection conn = getPolardbxConnection(DB_NAME)) {
            JdbcUtil.executeUpdate(conn, CREATE_TABLE_SQL);
        }

        // 等待建表 DDL 同步到下游（此时下游表无数据）
        log.info("=== Step 1.1: Wait for CREATE TABLE sync to downstream ===");
        sendTokenAndWait(CheckParameter.builder().build());

        // Step 2: 在下游表无数据时，将 gen_value 列改为普通列 NOT NULL 无 DEFAULT
        // 必须在写入数据之前执行，否则已有数据的表 ADD NOT NULL 列（无 DEFAULT）会报错
        log.info("=== Step 2: Modify downstream gen_value column to NOT NULL without DEFAULT ===");
        modifyDownstreamGeneratedColumn();

        // Step 2.1: 写入初始数据（此时下游 gen_value 为 NOT NULL，源端数据同步时需整形填充）
        log.info("=== Step 2.1: Insert initial data ===");
        try (Connection conn = getPolardbxConnection(DB_NAME)) {
            for (int i = 0; i < 100; i++) {
                String sql = String.format(INSERT_SQL_TEMPLATE, DB_NAME, i, "name_" + i, i * 10);
                JdbcUtil.executeSuccess(conn, sql);
            }
        }

        // Step 3 & 4: 启动 DML 后台线程，然后执行 DROP 生成列 DDL
        log.info("=== Step 3 & 4: Start DML traffic and execute DROP generated column DDL ===");
        AtomicBoolean dmlRunning = new AtomicBoolean(true);
        ExecutorService dmlExecutor = Executors.newFixedThreadPool(4);
        List<Future<?>> dmlFutures = new ArrayList<>();

        try {
            // 启动多个 DML 写入线程
            for (int t = 0; t < 4; t++) {
                final int threadId = t;
                Future<?> future = dmlExecutor.submit(() -> {
                    Random random = new Random(threadId);
                    int count = 0;
                    while (dmlRunning.get()) {
                        try (Connection conn = getPolardbxConnection(DB_NAME)) {
                            int cIdx = random.nextInt(10000);
                            int cAmount = random.nextInt(1000);
                            String name = "dml_" + threadId + "_" + count;
                            String insertSql = String.format(INSERT_SQL_TEMPLATE,
                                DB_NAME, cIdx, name, cAmount);
                            JdbcUtil.executeSuccess(conn, insertSql);

                            // 也执行一些 UPDATE
                            String updateSql = String.format(
                                "UPDATE `%s`.`%s` SET `c_amount` = `c_amount` + 1 WHERE `id` = %d",
                                DB_NAME, TABLE_NAME, random.nextInt(100) + 1);
                            JdbcUtil.executeSuccess(conn, updateSql);

                            count++;
                        } catch (Throwable e) {
                            // DDL 期间可能有短暂失败，忽略继续
                            log.debug("DML during DDL got expected error: {}", e.getMessage());
                        }
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                    log.info("DML thread {} finished, total {} operations", threadId, count);
                });
                dmlFutures.add(future);
            }

            // 等一会让 DML 先跑起来
            Thread.sleep(1000);

            // 在源端执行 DROP 生成列
            log.info("=== Executing ALTER TABLE DROP COLUMN gen_value on source ===");
            try (Connection conn = getPolardbxConnection(DB_NAME)) {
                Statement stmt = conn.createStatement();
                // 分表 DDL 会逐个分表执行，期间 DML 会触发整形
                stmt.execute("ALTER TABLE `" + DB_NAME + "`.`" + TABLE_NAME + "` DROP COLUMN `gen_value`");
            }
            log.info("=== ALTER TABLE DROP COLUMN completed ===");

            // DDL 完成后让 DML 再跑一会，确保 DDL 后的 DML 也能正常同步
            Thread.sleep(3000);
        } finally {
            dmlRunning.set(false);
            dmlExecutor.shutdown();
            for (Future<?> f : dmlFutures) {
                f.get();
            }
            log.info("=== All DML threads stopped ===");
        }

        // Step 5: 验证同步不中断，数据一致
        log.info("=== Step 5: Verify sync integrity and data consistency ===");

        // 等待所有数据同步到下游
        waitAndCheck(CheckParameter.builder()
            .dbName(DB_NAME)
            .tbName(TABLE_NAME)
            .directCompareDetail(true)
            .compareDetailOneByOne(true)
            .loopWaitTimeoutMs(300000)
            .ignoreColumns(IGNORE_COLUMNS)
            .build());

        log.info("=== GeneratedColumnDropTest PASSED ===");
    }

    private void modifyDownstreamGeneratedColumn() throws SQLException {
        if (usingBinlogX) {
            try (Connection first = getCdcSyncDbConnectionFirst();
                Connection second = getCdcSyncDbConnectionSecond();
                Connection third = getCdcSyncDbConnectionThird()) {
                modifyDownstreamGeneratedColumn(first);
                modifyDownstreamGeneratedColumn(second);
                modifyDownstreamGeneratedColumn(third);
            }
        } else {
            try (Connection connection = getCdcSyncDbConnection()) {
                modifyDownstreamGeneratedColumn(connection);
            }
        }
    }

    private void modifyDownstreamGeneratedColumn(Connection connection) throws SQLException {
        // MySQL 不支持直接 MODIFY generated -> normal，所以先 drop 再 add 普通列
        JdbcUtil.executeSuccess(connection,
            "ALTER TABLE `" + DB_NAME + "`.`" + TABLE_NAME + "` DROP COLUMN `gen_value`");
        JdbcUtil.executeSuccess(connection,
            "ALTER TABLE `" + DB_NAME + "`.`" + TABLE_NAME + "` ADD COLUMN `gen_value` bigint NOT NULL");
    }

}
