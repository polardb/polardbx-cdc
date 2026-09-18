/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * check replica hashcheck sql
 *
 * @author yudong
 * @since 2023/11/21 14:47
 **/
@Slf4j
public class ReplicaHashCheckTest extends BaseTestCase {
    private static final String REPLICA_HASH_CHECK = "REPLICA HASHCHECK * FROM `%s`.`%s`";
    private static final String SHOW_CREATE_TABLE = "SHOW CREATE TABLE `%s`.`%s`";

    @Test
    @SneakyThrows
    public void baseTest() {
        List<String> failedTables = new ArrayList<>();
        List<String> skippedTables = new ArrayList<>();
        int total = 0;
        int failed = 0;
        int skipped = 0;
        try (Connection conn = getPolardbxConnection();
            Statement stmt = conn.createStatement()) {
            List<String> dbList = getDatabaseList();
            for (String db : dbList) {
                List<String> tbList = getTableList(db);
                for (String tb : tbList) {
                    total++;
                    String sql = String.format(REPLICA_HASH_CHECK, escape(db), escape(tb));
                    try {
                        if (isExternalizedTable(stmt, db, tb)) {
                            log.info("skip replica hashcheck for externalized table:{}.{}", db, tb);
                            skipped++;
                            skippedTables.add(String.format("%s.%s", db, tb));
                            continue;
                        }
                        stmt.execute(sql);
                    } catch (Exception e) {
                        log.error("replica hashcheck table:{}.{} failed!", db, tb, e);
                        failed++;
                        failedTables.add(String.format("%s.%s", db, tb));
                    }
                }
            }
        }
        log.info("replica hashcheck total:{}, skipped:{}, skippedTables:{}, failed:{}, failedTables:{}",
            total, skipped, skippedTables, failed, failedTables);
    }

    private static boolean isExternalizedTable(Statement stmt, String db, String tb) throws SQLException {
        String sql = String.format(SHOW_CREATE_TABLE, escape(db), escape(tb));
        try (ResultSet resultSet = stmt.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new SQLException(String.format("SHOW CREATE TABLE returned no row for %s.%s", db, tb));
            }
            String createTable = resultSet.getString(2);
            if (StringUtils.isBlank(createTable)) {
                throw new SQLException(String.format("SHOW CREATE TABLE returned empty DDL for %s.%s", db, tb));
            }
            return StringUtils.containsIgnoreCase(createTable, "EXTERNALIZE");
        }
    }

    private List<String> getDatabaseList() throws SQLException {
        try (Connection conn = getPolardbxConnection()) {
            return JdbcUtil.showDatabases(conn);
        }
    }

    private List<String> getTableList(String db) throws SQLException {
        try (Connection conn = getPolardbxConnection()) {
            return JdbcUtil.showTables(conn, db);
        }
    }

}
