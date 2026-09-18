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
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.checkIfTableNotExistError;
import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.executeQuerySuccess;
import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.getColumnNamesByDesc;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

@Slf4j
public class VirtualTableMappingTest extends RplBaseTestCase {

    private static final String DB_NAME = "cdc_virtual_table_mapping";
    private static final String TABLE_NAME_PREFIX = "user_balance_log_";
    private static final String TIMESTAMP_TABLE_NAME = TABLE_NAME_PREFIX + System.currentTimeMillis() / 1000;
    private static final String EXCLUDE_TABLE_NAME = TABLE_NAME_PREFIX + "exclude";
    private static final String VIRTUAL_TABLE_NAME = TABLE_NAME_PREFIX + "virtual";
    private static final String CREATE_SQL_TEMPLATE =
        "create table %s(id bigint auto_increment,name varchar(100),primary key(id))";
    private static final String INSERT_SQL_TEMPLATE = "insert into " + DB_NAME + ".%s(name) values('%s')";

    @BeforeClass
    public static void bootStrap() throws SQLException {
        if (usingBinlogX) {
            return;
        }
        prepareTestDatabase(DB_NAME);
    }

    @Test
    public void test() throws SQLException, InterruptedException {
        try {
            testInternal();
        } catch (Throwable t) {
            log.error("test error!!", t);
            throw t;
        }
    }

    private void testInternal() throws InterruptedException, SQLException {
        if (usingBinlogX) {
            log.warn("no need to test virtual table in binlog x");
            return;
        }

        boolean isMultiBinlogStream = isMultiBinlogStream();
        if (dstIsReplica() && isMultiBinlogStream) {
            log.warn("no need to test virtual table in multi binlog stream for replica lab");
            return;
        }

        // 在mysql命令行执行示例：set cdc global meta_virtual_table_mapping_rule = `cdc_virtual_table_mapping\.user_balance_log_\d+|cdc_virtual_table_mapping.user_balance_log_virtual`;
        log.warn("prepare to set meta_virtual_table_mapping_rule");
        JdbcUtil.executeUpdate(polardbxConnection,
            "set cdc global meta_virtual_table_mapping_rule = `cdc_virtual_table_mapping\\.user_balance_log_\\d+|cdc_virtual_table_mapping.user_balance_log_virtual`");
        JdbcUtil.executeUpdate(polardbxConnection, "stop master");
        if (isMultiBinlogStream) {
            JdbcUtil.executeUpdate(polardbxConnection, "stop master with group1");
        }
        Thread.sleep(10000);
        JdbcUtil.executeUpdate(polardbxConnection, "start master");
        if (isMultiBinlogStream) {
            JdbcUtil.executeUpdate(polardbxConnection, "start master with group1");
        }
        Thread.sleep(10000);

        // prepare tables
        log.warn("prepare to create tables");
        JdbcUtil.executeUpdate(polardbxConnection, "use " + DB_NAME);
        JdbcUtil.executeUpdate(polardbxConnection, String.format(CREATE_SQL_TEMPLATE, TIMESTAMP_TABLE_NAME));
        JdbcUtil.executeUpdate(polardbxConnection, String.format(CREATE_SQL_TEMPLATE, VIRTUAL_TABLE_NAME));
        JdbcUtil.executeUpdate(polardbxConnection, String.format(CREATE_SQL_TEMPLATE, EXCLUDE_TABLE_NAME));

        // insert data
        log.warn("prepare to insert data");
        insertData(polardbxConnection, TIMESTAMP_TABLE_NAME);
        insertData(polardbxConnection, EXCLUDE_TABLE_NAME);

        // check timestamp table and virtual table is same
        log.warn("prepare to check data for downstream virtual table.");
        waitAndCheck(CheckParameter.builder()
            .dbName(DB_NAME)
            .tbName(TIMESTAMP_TABLE_NAME)
            .aliasTbName(VIRTUAL_TABLE_NAME).build());
        check(CheckParameter.builder().dbName(DB_NAME).tbName(EXCLUDE_TABLE_NAME).build());

        // check timestamp table is empty
        log.warn("prepare to check data for downstream timestamp table.");
        JdbcUtil.executeUpdate(cdcSyncDbConnection, "use " + DB_NAME);
        checkDownStreamTableEmpty(cdcSyncDbConnection);

        // prepare before executing some ddl, only affect timestamp table
        log.warn("prepare to check table schema before ddl.");
        List<Pair<String, String>> upstreamTimestampTableCols = getColumnNamesByDesc(
            polardbxConnection, DB_NAME, TIMESTAMP_TABLE_NAME);
        List<Pair<String, String>> downstreamTimestampTableCols = getColumnNamesByDesc(
            cdcSyncDbConnection, DB_NAME, TIMESTAMP_TABLE_NAME);
        List<Pair<String, String>> downstreamVirtualTableCols = getColumnNamesByDesc(
            cdcSyncDbConnection, DB_NAME, VIRTUAL_TABLE_NAME);

        Assert.assertTrue(ListUtils.isEqualList(upstreamTimestampTableCols, downstreamTimestampTableCols));
        Assert.assertTrue(ListUtils.isEqualList(upstreamTimestampTableCols, downstreamVirtualTableCols));

        // add column
        log.warn("prepare to add column.");
        JdbcUtil.executeUpdate(polardbxConnection,
            String.format("alter table %s add column name2 varchar(100) default 'abc'", TIMESTAMP_TABLE_NAME));
        sendTokenAndWait(CheckParameter.builder().build());
        upstreamTimestampTableCols = getColumnNamesByDesc(polardbxConnection, DB_NAME, TIMESTAMP_TABLE_NAME);
        downstreamTimestampTableCols = getColumnNamesByDesc(cdcSyncDbConnection, DB_NAME, TIMESTAMP_TABLE_NAME);
        downstreamVirtualTableCols = getColumnNamesByDesc(cdcSyncDbConnection, DB_NAME, VIRTUAL_TABLE_NAME);

        Assert.assertTrue(String.format("columns should be same for upstream and downstream timestamp table,"
                + " up is %s, down is %s.", upstreamTimestampTableCols, downstreamTimestampTableCols),
            ListUtils.isEqualList(upstreamTimestampTableCols, downstreamTimestampTableCols));

        Assert.assertFalse(String.format("columns should be different for upstream timestamp table and downstream "
                + "virtual table, up is %s, down is %s.", upstreamTimestampTableCols, downstreamVirtualTableCols),
            ListUtils.isEqualList(upstreamTimestampTableCols, downstreamVirtualTableCols));

        Assert.assertFalse("down stream virtual should not contains column `name2`",
            downstreamVirtualTableCols.stream().anyMatch(p -> StringUtils.equals(p.getKey(), "name2")));

        // truncate table
        log.warn("prepare to truncate table.");
        insertData(cdcSyncDbConnection, TIMESTAMP_TABLE_NAME);
        JdbcUtil.executeUpdate(polardbxConnection, String.format("truncate table %s", TIMESTAMP_TABLE_NAME));
        JdbcUtil.executeUpdate(polardbxConnection, String.format("truncate table %s", EXCLUDE_TABLE_NAME));
        sendTokenAndWait(CheckParameter.builder().build());

        List<String> virtualTableIdList = JdbcUtil.executeQueryAndGetStringList(
            String.format("select id from %s", VIRTUAL_TABLE_NAME), cdcSyncDbConnection, 1);
        Assert.assertEquals(
            "row count of virtual table does not meet expectations, the count is " + virtualTableIdList.size(),
            100, virtualTableIdList.size());

        List<String> timestampTableIdList = JdbcUtil.executeQueryAndGetStringList(
            String.format("select id from %s", TIMESTAMP_TABLE_NAME), cdcSyncDbConnection, 1);
        Assert.assertEquals(
            "row count of timestamp table does not meet expectations, the count is " + timestampTableIdList.size(),
            0, timestampTableIdList.size());

        List<String> excludeTableIdList = JdbcUtil.executeQueryAndGetStringList(
            String.format("select id from %s", EXCLUDE_TABLE_NAME), cdcSyncDbConnection, 1);
        Assert.assertEquals(
            "row count of exclude table does not meet expectations, the count is " + timestampTableIdList.size(),
            0, excludeTableIdList.size());

        // drop table
        log.warn("prepare to drop table.");
        JdbcUtil.executeUpdate(polardbxConnection, String.format("drop table %s", TIMESTAMP_TABLE_NAME));
        JdbcUtil.executeUpdate(polardbxConnection, String.format("drop table %s", EXCLUDE_TABLE_NAME));
        sendTokenAndWait(CheckParameter.builder().build());
        Assert.assertTrue(checkTableExists(cdcSyncDbConnection, VIRTUAL_TABLE_NAME));
        Assert.assertFalse(checkTableExists(cdcSyncDbConnection, TIMESTAMP_TABLE_NAME));
        Assert.assertFalse(checkTableExists(cdcSyncDbConnection, EXCLUDE_TABLE_NAME));

        // drop database
        log.warn("prepare to drop database");
        JdbcUtil.executeUpdate(polardbxConnection, "drop database " + DB_NAME);
        // 不use一下，继续使用这个连接会报错：[1a294cb97c402000][192.0.2.35:3306][cdc_virtual_table_mapping]Unknown database 'cdc_virtual_table_mapping'
        JdbcUtil.executeUpdate(polardbxConnection, "use polardbx");
        try {
            JdbcUtil.executeUpdate(cdcSyncDbConnection, "use polardbx");
        } catch (Throwable ignored) {
        }
        sendTokenAndWait(CheckParameter.builder().build());
    }

    private void insertData(Connection connection, String tableName) {
        for (int i = 0; i < 100; i++) {
            String sql = String.format(INSERT_SQL_TEMPLATE, tableName, RandomStringUtils.randomAlphabetic(50));
            JdbcUtil.executeUpdate(connection, sql);
        }
    }

    private void checkDownStreamTableEmpty(Connection connection) throws SQLException {
        try {
            String sql = String.format("select count(*) from %s.%s", DB_NAME, TIMESTAMP_TABLE_NAME);
            ResultSet resultSet = JdbcUtil.executeQuery(sql, connection);
            resultSet.next();
            Object object = JdbcUtil.getObject(resultSet, 1);
            Assert.assertEquals("0", object.toString());
        } catch (Throwable t) {
            log.error("checkDownStreamTableEmpty error", t);
            throw t;
        }
    }

    private boolean checkTableExists(Connection connection, String table) throws SQLException {
        try {
            getColumnNamesByDesc(connection, DB_NAME, table);
            return true;
        } catch (Throwable e) {
            if (checkIfTableNotExistError(e.getMessage())) {
                return false;
            }
            throw e;
        }
    }

    private boolean isMultiBinlogStream() throws SQLException {
        try (ResultSet rs = executeQuerySuccess(polardbxConnection, "show binary streams")) {
            if (rs.next()) {
                return true;
            }
        }
        return false;
    }
}
