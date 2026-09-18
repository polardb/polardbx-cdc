/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.error.TimeoutException;
import com.aliyun.polardbx.binlog.relay.DdlRouteMode;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.getDdlRouteMode;
import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.tryAttachAsyncDdlHints;
import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.tryRemoveColumnarIndex;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

/**
 * @author shicai.xsc 2021/4/19 11:18
 * @since 5.0.0.0
 */
@Slf4j
public class DdlApplyHelperTest extends RplWithGmsTablesBaseTest {

    @Test
    public void runningDdlQueryUsesPreparedStatementWithoutSqlArgument() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SHOW FULL DDL")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("JOB_ID")).thenReturn(123L);
        when(resultSet.getString("STATE")).thenReturn("PAUSED");
        when(resultSet.getString("DDL_STMT")).thenReturn("/*DDL_SUBMIT_TOKEN=test-token*/ ALTER TABLE t ADD c INT");
        DdlApplyHelper.DdlJobInfo job = DdlApplyHelper.checkIfDdlRunning(dataSource, "test-token");
        Assert.assertEquals(Long.valueOf(123), job.getJobId());
        Assert.assertEquals("PAUSED", job.getState());
        Mockito.verify(statement, Mockito.never()).executeQuery(anyString());
        Mockito.verify(resultSet).close();
    }

    @Test
    public void continuePausedDdlUsesGenericExecution() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);
        Method method = DdlApplyHelper.class.getDeclaredMethod("tryContinueDdl", DataSource.class,
            DdlApplyHelper.DdlJobInfo.class, String.class);
        method.setAccessible(true);
        method.invoke(null, dataSource, new DdlApplyHelper.DdlJobInfo(123L, "PAUSED"), "test-tso");
        Mockito.verify(statement).execute(contains("123"));
        Mockito.verify(statement, Mockito.never()).executeUpdate(anyString());
        Mockito.verify(statement).close();
        Mockito.verify(connection).close();
    }

    @Test
    public void testGetDdlRoutMode() {
        String sql = "# POLARX_DDL_ROUTE_MODE=SINGLE\n"
            + "# POLARX_ORIGIN_SQL=ALTER TABLE t7 ADD COLUMN c1 bigint\n"
            + "# POLARX_TSO=709124912370522528016223143392496885760000000000000000";
        Assert.assertEquals(DdlRouteMode.SINGLE, getDdlRouteMode(sql));

        String sql2 = "# POLARX_ORIGIN_SQL=ALTER TABLE t7 ADD COLUMN c1 bigint\n"
            + "# POLARX_TSO=709124912370522528016223143392496885760000000000000000";
        Assert.assertEquals(DdlRouteMode.BROADCAST, getDdlRouteMode(sql2));
    }

    @Test
    public void getOriginalSql() {
        String sql = "# POLARX_ORIGIN_SQL=CREATE DATABASE BalancerTestBase MODE 'auto'\n"
            + "# POLARX_TSO=699138551269084371215224507282353070080000000000000000\n"
            + "CREATE DATABASE BalancerTestBase CHARACTER SET utf8mb4";
        String originSql = DdlApplyHelper.getOriginSql(sql);
        Assert.assertEquals("CREATE DATABASE BalancerTestBase MODE 'auto'",
            originSql);
    }

    @Test
    public void getDdlSqlContext() {
        String sql = "# POLARX_ORIGIN_SQL=CREATE DATABASE BalancerTestBase MODE 'auto'\n"
            + "# POLARX_TSO=699138551269084371215224507282353070080000000000000000\n"
            + "CREATE DATABASE BalancerTestBase CHARACTER SET utf8mb4";
        DefaultQueryLog queryLog = new DefaultQueryLog("", sql, new Timestamp(12345), 0, 0);
        SqlContext context = DdlApplyHelper.getDdlSqlContext(queryLog, UUID.randomUUID().toString(),
            "699138551269084371215224507282353070080000000000000000");
        Assert.assertTrue(StringUtils.endsWithIgnoreCase(context.getSql(),
            "create database if not exists BalancerTestBase MODE 'auto'"));
    }

    @Test
    public void getCreateUser() {
        String sql = "# POLARX_ORIGIN_SQL=CREATE USER if not exists 'jiyue1'@'%' IDENTIFIED BY '123456'\n"
            + "# POLARX_TSO=699138551269084371215224507282353070080000000000000000\n"
            + "CREATE USER 'jiyue1'@'%' IDENTIFIED BY '123456'";
        DefaultQueryLog queryLog = new DefaultQueryLog("", sql, new Timestamp(12345), 0, 0);
        SqlContext context = DdlApplyHelper.getDdlSqlContext(queryLog, UUID.randomUUID().toString(),
            "699138551269084371215224507282353070080000000000000000");
        Assert.assertTrue(StringUtils.endsWithIgnoreCase(context.getSql(),
            "CREATE USER if not exists 'jiyue1'@'%' IDENTIFIED BY '123456'"));
    }

    @Test
    public void getTso_1() {
        String sql = "# POLARX_ORIGIN_SQL=ALTER TABLE `cdc_datatype`.`numeric` DROP COLUMN _NUMERIC_\n"
            + "# POLARX_TSO=699124861471450732815223138302086389760000000000000000\n"
            + "ALTER TABLE `cdc_datatype`.`numeric`\n"
            + "  DROP COLUMN _NUMERIC_";
        String tso =
            DdlApplyHelper.getTso(sql, new Timestamp(1666843560), "binlog.000004:0000021718#1769892875.1666843560");
        Assert.assertEquals(tso, "699124861471450732815223138302086389760000000000000000");
    }

    @Test
    public void getTso_2() {
        String sql = "/*POLARX_ORIGIN_SQL=CREATE TABLE aaaaaa (\n" + "    id int,\n" + "    value int,\n"
            + "    INDEX `auto_shard_key_id` USING BTREE(`ID`),\n"
            + "    _drds_implicit_id_ bigint AUTO_INCREMENT,\n" + "    PRIMARY KEY (_drds_implicit_id_)\n"
            + ")\n" + "DBPARTITION BY hash(id)\n" + "TBPARTITION BY hash(id) TBPARTITIONS 2*/ "
            + "CREATE TABLE aaaaaa ( id int, value "
            + "int, INDEX `auto_shard_key_id` USING BTREE(`ID`) ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = "
            + "utf8mb4_general_ci";
        String tso = DdlApplyHelper.getTso(sql, new Timestamp(1618802638), "");
        Assert.assertEquals("-35199207716188026380", tso);
    }

    @Test
    public void testTryAttachAsyncDdlHints() {
        mockConfig(ConfigKeys.RPL_ASYNC_DDL_ENABLED, "true");
        mockConfig(ConfigKeys.RPL_ASYNC_DDL_THRESHOLD_IN_SECOND, "600");
        mockConfig(ConfigKeys.RPL_ASYNC_EXTERNALIZE_DDL_ENABLED, "true");
        /*
         * analyze table
         */
        String sql = "analyze table d1.t1";
        tryAttacheAndCheck(sql);

        /*
         * add index & drop index
         */
        sql = "ALTER TABLE t1 ADD INDEX idx_gmt (`gmt_created`)";
        tryAttacheAndCheck(sql);

        sql = "Alter table t1 drop index idx_gmt";
        tryAttacheAndCheck(sql);

        sql = "alter table t1 add global index g_i_1(a,b,c) partition by key(a) partitions 3";
        tryAttacheAndCheck(sql);

        sql = "Alter table t1 add index idx1 (`c1`), drop index idx_gmt";
        tryAttacheAndCheck(sql);

        sql = "alter table t1 add global index g_i_1(a,b,c) partition by key(a) partitions 3, add column c1 bigint";
        tryAttacheAndCheck2(sql);

        sql = "create index idx_gmt on t_ddl_test_JaV1_00(`gmt_created`)";
        tryAttacheAndCheck(sql);

        sql = "DROP INDEX idx_gmt ON `t_ddl_test_JaV1_00`";
        tryAttacheAndCheck(sql);

        /*
         * MODIFY COLUMN ... EXTERNALIZE has its own async switch. It must not depend on the
         * general switch or the source-side execution time, because CDC removes the source
         * PURE_ASYNC_DDL_MODE hint before RPL apply.
         */
        mockConfig(ConfigKeys.RPL_ASYNC_DDL_ENABLED, "false");
        sql = "ALTER TABLE t1 MODIFY COLUMN body LONGTEXT EXTERNALIZE";
        Assert.assertEquals(RplConstants.ASYNC_DDL_HINTS + sql, tryAttachAsyncDdlHints(sql, 1));

        DefaultQueryLog externalize = new DefaultQueryLog("d1", sql, new Timestamp(12345), 0, 1);
        SqlContext externalizeContext = DdlApplyHelper.getDdlSqlContext(externalize, "externalize-token", "tso-1");
        Assert.assertTrue(externalizeContext.isAsyncDdl());
        Assert.assertTrue(externalizeContext.getSql().contains(RplConstants.ASYNC_DDL_HINTS));

        mockConfig(ConfigKeys.RPL_ASYNC_DDL_ENABLED, "true");
        mockConfig(ConfigKeys.RPL_ASYNC_EXTERNALIZE_DDL_ENABLED, "false");
        Assert.assertEquals(sql, tryAttachAsyncDdlHints(sql, Long.MAX_VALUE));
        Assert.assertFalse(DdlApplyHelper.getDdlSqlContext(externalize, "sync-token", "tso-2").isAsyncDdl());

        mockConfig(ConfigKeys.RPL_ASYNC_EXTERNALIZE_DDL_ENABLED, "true");
        SqlContext submittedContext =
            DdlApplyHelper.getDdlSqlContext(externalize, "persisted-async-token", "persisted-async-tso");
        RplDdl submittedDdl = new RplDdl();
        submittedDdl.setDdlStmt(submittedContext.getSql());
        submittedDdl.setAsyncFlag(true);

        mockConfig(ConfigKeys.RPL_ASYNC_EXTERNALIZE_DDL_ENABLED, "false");
        SqlContext recoveredContext =
            DdlApplyHelper.getDdlSqlContext(externalize, "new-token", "persisted-async-tso");
        Assert.assertFalse(recoveredContext.isAsyncDdl());
        DdlApplyHelper.restorePersistedExternalizeAsyncMode(recoveredContext, submittedDdl);
        Assert.assertTrue("recovery must retain the original async path", recoveredContext.isAsyncDdl());

        /*
         * The dedicated switch is intentionally limited to MODIFY COLUMN ... EXTERNALIZE.
         * Ordinary MODIFY/INTERNALIZE and other external-column DDL forms retain the original path.
         */
        mockConfig(ConfigKeys.RPL_ASYNC_EXTERNALIZE_DDL_ENABLED, "true");
        Assert.assertEquals("ALTER TABLE t1 MODIFY COLUMN body LONGTEXT",
            tryAttachAsyncDdlHints("ALTER TABLE t1 MODIFY COLUMN body LONGTEXT", 1));
        Assert.assertEquals("ALTER TABLE t1 MODIFY COLUMN body LONGTEXT INTERNALIZE",
            tryAttachAsyncDdlHints("ALTER TABLE t1 MODIFY COLUMN body LONGTEXT INTERNALIZE", 1));
        Assert.assertEquals("ALTER TABLE t1 ADD COLUMN payload LONGBLOB EXTERNALIZE",
            tryAttachAsyncDdlHints("ALTER TABLE t1 ADD COLUMN payload LONGBLOB EXTERNALIZE", 1));
        Assert.assertEquals("CREATE TABLE t2(id BIGINT, body LONGTEXT EXTERNALIZE)",
            tryAttachAsyncDdlHints("CREATE TABLE t2(id BIGINT, body LONGTEXT EXTERNALIZE)", 1));

        /*
         * partitions
         */
        // com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.DrdsAlterTablePartition
        /*sql = "alter table t1 dbpartition by hash(ID) tbpartition by hash(ID) tbpartitions 8";
        tryAttacheAndCheck(sql);*/

        // com.alibaba.polardbx.druid.sql.ast.statement.DrdsSplitPartition
        /*sql = "ALTER TABLE t1 SPLIT PARTITION p1 INTO \n"
            + "(PARTITION p10 VALUES LESS THAN (1994),\n"
            + "PARTITION p11 VALUES LESS THAN(1996),\n"
            + "PARTITION p12 VALUES LESS THAN(2000))";
        tryAttacheAndCheck(sql);*/
    }

    @Test
    public void testTryRemoveColumnarIndex_4_CreateColumnarIndex() {
        /*
         * add by alter table
         */
        String sql1 = "ALTER TABLE `t_order_0`\n"
            + "\tADD CLUSTERED COLUMNAR INDEX `cci_seller_id` (`seller_id`)";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql1);
        Pair<Boolean, Boolean> pair = tryRemoveColumnarIndex(statement, null);
        Assert.assertTrue(pair.getKey());
        Assert.assertTrue(pair.getValue());
        Assert.assertEquals("ALTER TABLE `t_order_0`", statement.toString());

        String sql1_1 = "ALTER TABLE `t_order_0`\n"
            + "\tADD CLUSTERED INDEX `cci_seller_id` (`seller_id`)";
        statement = SQLUtils.parseSQLStatement(sql1_1);
        pair = tryRemoveColumnarIndex(statement, null);
        Assert.assertFalse(pair.getKey());
        Assert.assertTrue(pair.getValue());
        Assert.assertEquals("ALTER TABLE `t_order_0`\n"
            + "\tADD CLUSTERED INDEX `cci_seller_id` (`seller_id`)", statement.toString());

        /*
         * add by create table
         */
        String sql2 = "CREATE TABLE `t_order_single_1` (\n"
            + "\t`id` bigint(11) NOT NULL AUTO_INCREMENT,\n"
            + "\t`order_id` varchar(20) DEFAULT NULL,\n"
            + "\t`buyer_id` varchar(20) DEFAULT NULL,\n"
            + "\t`seller_id` varchar(20) DEFAULT NULL,\n"
            + "\t`order_snapshot` longtext,\n"
            + "\t`order_detail` longtext,\n"
            + "\t`gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
            + "\t`rint` double(10, 2) DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`),\n"
            + "\tCLUSTERED COLUMNAR INDEX  `cci_seller_id` (`seller_id`)\n"
            + "\t\tPARTITION BY HASH(`id`)\n"
            + "\t\tPARTITIONS 16\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8";
        statement = SQLUtils.parseSQLStatement(sql2);
        pair = tryRemoveColumnarIndex(statement, null);
        Assert.assertTrue(pair.getKey());
        Assert.assertTrue(pair.getValue());
        Assert.assertEquals("CREATE TABLE `t_order_single_1` (\n"
            + "\t`id` bigint(11) NOT NULL AUTO_INCREMENT,\n"
            + "\t`order_id` varchar(20) DEFAULT NULL,\n"
            + "\t`buyer_id` varchar(20) DEFAULT NULL,\n"
            + "\t`seller_id` varchar(20) DEFAULT NULL,\n"
            + "\t`order_snapshot` longtext,\n"
            + "\t`order_detail` longtext,\n"
            + "\t`gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
            + "\t`rint` double(10, 2) DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8", statement.toString());

        String sql2_1 = "CREATE TABLE `t_order_single_1` (\n"
            + "\t`id` bigint(11) NOT NULL AUTO_INCREMENT,\n"
            + "\t`order_id` varchar(20) DEFAULT NULL,\n"
            + "\t`buyer_id` varchar(20) DEFAULT NULL,\n"
            + "\t`seller_id` varchar(20) DEFAULT NULL,\n"
            + "\t`order_snapshot` longtext,\n"
            + "\t`order_detail` longtext,\n"
            + "\t`gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
            + "\t`rint` double(10, 2) DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`),\n"
            + "\tCLUSTERED INDEX `seller_id` (`seller_id`)\n"
            + "\t\tPARTITION BY HASH(`id`)\n"
            + "\t\tPARTITIONS 16\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8";
        statement = SQLUtils.parseSQLStatement(sql2_1);
        pair = tryRemoveColumnarIndex(statement, null);
        Assert.assertFalse(pair.getKey());
        Assert.assertTrue(pair.getValue());
        Assert.assertEquals("CREATE TABLE `t_order_single_1` (\n"
            + "\t`id` bigint(11) NOT NULL AUTO_INCREMENT,\n"
            + "\t`order_id` varchar(20) DEFAULT NULL,\n"
            + "\t`buyer_id` varchar(20) DEFAULT NULL,\n"
            + "\t`seller_id` varchar(20) DEFAULT NULL,\n"
            + "\t`order_snapshot` longtext,\n"
            + "\t`order_detail` longtext,\n"
            + "\t`gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
            + "\t`rint` double(10, 2) DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`),\n"
            + "\tCLUSTERED INDEX `seller_id`(`seller_id`) PARTITION BY HASH (`id`) PARTITIONS 16\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8", statement.toString());

        /*
         *  add by create index
         */
        String sql3 = "CREATE CLUSTERED COLUMNAR INDEX `cci_seller_id` ON `t_order_0` (`seller_id`) "
            + "COVERING (`id`, `order_id`, `buyer_id`, `order_snapshot`, `order_detail`, `gmt_modified`, `rint`) "
            + "PARTITION BY DIRECT_HASH(`ID`)";
        statement = SQLUtils.parseSQLStatement(sql3);
        pair = tryRemoveColumnarIndex(statement, null);
        Assert.assertTrue(pair.getKey());
        Assert.assertFalse(pair.getValue());

        String sql3_1 = "CREATE CLUSTERED INDEX `cci_seller_id` ON `t_order_0` (`seller_id`) "
            + "COVERING (`id`, `order_id`, `buyer_id`, `order_snapshot`, `order_detail`, `gmt_modified`, `rint`) "
            + "PARTITION BY DIRECT_HASH(`ID`)";
        statement = SQLUtils.parseSQLStatement(sql3_1);
        pair = tryRemoveColumnarIndex(statement, null);
        Assert.assertFalse(pair.getKey());
        Assert.assertTrue(pair.getValue());
        Assert.assertEquals(
            "CREATE CLUSTERED INDEX `cci_seller_id` ON `t_order_0` (`seller_id`) "
                + "COVERING (`id`, `order_id`, `buyer_id`, `order_snapshot`, `order_detail`, `gmt_modified`, `rint`) "
                + "PARTITION BY DIRECT_HASH(`ID`)",
            statement.toString());

    }

    @Test
    public void testTryRemoveColumnarIndex_4_DropColumnarIndex() {
        String createSql = "CREATE TABLE `t_order_0` (\n"
            + " `id` bigint(11) NOT NULL AUTO_INCREMENT,\n"
            + " `order_id` varchar(20) DEFAULT NULL,\n"
            + " `buyer_id` varchar(20) DEFAULT NULL,\n"
            + " `seller_id` varchar(20) DEFAULT NULL,\n"
            + " `order_snapshot` longtext,\n"
            + " `order_detail` longtext,\n"
            + " `gmt_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
            + " `rint` double(10, 2) DEFAULT NULL,\n"
            + " PRIMARY KEY (`id`),\n"
            + " INDEX `seller_id` (`seller_id`),\n"
            + " INDEX `buyer_id` (`seller_id`),\n"
            + " CLUSTERED COLUMNAR INDEX  `cci_seller_id` (`seller_id`),\n"
            + " CLUSTERED COLUMNAR INDEX  `cci_buyer_id` (`seller_id`) PARTITION BY HASH(`id`) PARTITIONS 16\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(log, false);
        memoryTableMeta.apply(null, "d1", createSql, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "t_order_0");

        String sql1_2 = "ALTER TABLE `t_order_0`\n" + "\tDROP INDEX `cci_seller_id`";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql1_2);
        Pair<Boolean, Boolean> pair = tryRemoveColumnarIndex(statement, tableMeta);
        Assert.assertTrue(pair.getKey());
        Assert.assertTrue(pair.getValue());
        Assert.assertEquals("ALTER TABLE `t_order_0`", statement.toString());

        String sql1_3 = "ALTER TABLE `t_order_0`\n" + "\tDROP INDEX `seller_id`";
        statement = SQLUtils.parseSQLStatement(sql1_3);
        pair = tryRemoveColumnarIndex(statement, null);
        Assert.assertFalse(pair.getKey());
        Assert.assertTrue(pair.getValue());
        Assert.assertEquals("ALTER TABLE `t_order_0`\n"
            + "\tDROP INDEX `seller_id`", statement.toString());

        String sql4 = "drop index cci_buyer_id on t_order_0";
        statement = SQLUtils.parseSQLStatement(sql4);
        pair = tryRemoveColumnarIndex(statement, tableMeta);
        Assert.assertTrue(pair.getKey());
        Assert.assertFalse(pair.getValue());

        String sql4_1 = "drop index buyer_id on t_order_0";
        statement = SQLUtils.parseSQLStatement(sql4_1);
        pair = tryRemoveColumnarIndex(statement, tableMeta);
        Assert.assertFalse(pair.getKey());
        Assert.assertTrue(pair.getValue());
    }

    @Test
    public void testTryRemoveColumnarIndex_4_CallColumnarSetConfig() {
        String sql = "CALL polardbx.columnar_set_config(256, 'TYPE', 'SNAPSHOT')";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        Pair<Boolean, Boolean> pair = tryRemoveColumnarIndex(statement, null);
        Assert.assertTrue(pair.getKey());
        Assert.assertFalse(pair.getValue());
    }

    private void tryAttacheAndCheck(String sql) {
        String result = tryAttachAsyncDdlHints(sql, Long.MAX_VALUE);
        Assert.assertEquals(RplConstants.ASYNC_DDL_HINTS + sql, result);
    }

    private void tryAttacheAndCheck2(String sql) {
        String result = tryAttachAsyncDdlHints(sql, Long.MAX_VALUE);
        Assert.assertEquals(sql, result);
    }

    @Test
    public void testCciCheck() {
        String ddl =
            "# POLARX_ORIGIN_SQL=ALTER TABLEGROUP tg2241 SPLIT PARTITION pd INTO (PARTITION p3 VALUES IN (1003) SUBPARTITIONS 2, PARTITION `pd` VALUES IN (DEFAULT) ( SUBPARTITION `pdsp1`, SUBPARTITION `pdsp2`, SUBPARTITION `pdsp3`, SUBPARTITION `pdsp4` )) \n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n";
        Assert.assertFalse(DdlApplyHelper.isCciDdl(ddl));
        String ddl2 =
            "# POLARX_ORIGIN_SQL=/*+TDDL({'extra':{'FORBID_DDL_WITH_CCI':'FALSE'}})*/ ALTER TABLE tT1.cci_tT1 SPLIT PARTITION p2  WITH TABLEGROUP=columnar_tg1489 IMPLICIT\n"
                + "# POLARX_TSO=723009401408140089617611592296342528000000000000000000\n"
                + "# POLARX_DDL_ID=7230094005076230208\n"
                + "# POLARX_DDL_TYPES=CCI";

        Assert.assertTrue(DdlApplyHelper.isCciDdl(ddl2));
        DefaultQueryLog defaultQueryLog =
            new DefaultQueryLog("altercciaddpartition", ddl2, new Timestamp(System.currentTimeMillis()), 0, 1);
        SqlContext context = DdlApplyHelper.getDdlSqlContext(defaultQueryLog, "7230094005076230208",
            "723009401408140089617611592296342528000000000000000000");

        Assert.assertNull(context);
    }

    @Test
    public void testGetVariables() {
        String ddl =
            "# POLARX_ORIGIN_SQL=/*+TDDL({'extra':{'FORBID_DDL_WITH_CCI':'FALSE'}})*/ ALTER TABLE tT1.cci_tT1 SPLIT PARTITION p2  WITH TABLEGROUP=columnar_tg1505 IMPLICIT\n"
                + "# POLARX_TSO=\n"
                + "# POLARX_DDL_ID=0\n"
                + "# POLARX_DDL_TYPES=CCI\n"
                + "# POLARX_VARIABLES={\"FP_OVERRIDE_NOW\":\"2024-08-18 10:10:10\"}\n";
        Map<String, Object> variables = DdlApplyHelper.getPolarxVariables(ddl);
        Assert.assertNotNull(variables);
        Assert.assertEquals("2024-08-18 10:10:10", variables.get("FP_OVERRIDE_NOW"));
    }

    @Test
    public void testIsLocalParitionMissError() {
        Assert.assertTrue(DdlApplyHelper.isMissLocalPartitionError(new SQLException(
            "[1879cc30ccc02000][192.0.2.30:3306][cp1_ddl1_1057607069_new]ERR-CODE: [TDDL-4700][ERR_SERVER] server error by local partition p20230922 doesn't exist")));

        Assert.assertFalse(DdlApplyHelper.isMissLocalPartitionError(new SQLException(
            "[1879cc30ccc02000][192.0.2.30:3306][cp1_ddl1_1057607069_new]ERR-CODE: [TDDL-4700][ERR_SERVER] server error by local partition p2023 0922 doesn't exist")));

    }

    /**
     * 测试 isPartitionAlreadyExistsError 方法：
     * ADD PARTITION 时备库已存在该分区的幂等冲突报错识别
     */
    @Test
    public void testIsPartitionAlreadyExistsError() {
        // 标准错误格式：含 [ERR_PARTITION_MANAGEMENT] + Partition name: pXXXX already exists
        Assert.assertTrue(DdlApplyHelper.isPartitionAlreadyExistsError(new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition name: p20260422sp1 already exists. Please use another name.")));

        // 不带分区名的错误不应识别
        Assert.assertFalse(DdlApplyHelper.isPartitionAlreadyExistsError(new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition name:  already exists. Please use another name.")));

        // 其他错误类型不应识别
        Assert.assertFalse(DdlApplyHelper.isPartitionAlreadyExistsError(new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-4700][ERR_SERVER] some other error")));

        // null 异常不应识别
        Assert.assertFalse(DdlApplyHelper.isPartitionAlreadyExistsError(null));
    }

    /**
     * 测试 isPartitionGroupNotExistsError 方法：
     * DROP PARTITION 时备库该分区已删除的幂等冲突报错识别
     */
    @Test
    public void testIsPartitionGroupNotExistsError() {
        // 标准错误格式：含 [ERR_PARTITION_MANAGEMENT] + Partition group 'pXXXX' doesn't exist
        Assert.assertTrue(DdlApplyHelper.isPartitionGroupNotExistsError(new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition group 'p20260301' doesn't exist")));

        // 分区名不能为空
        Assert.assertFalse(DdlApplyHelper.isPartitionGroupNotExistsError(new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition group '' doesn't exist")));

        // 其他错误类型不应识别
        Assert.assertFalse(DdlApplyHelper.isPartitionGroupNotExistsError(new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-4700][ERR_SERVER] some other error")));

        // null 异常不应识别
        Assert.assertFalse(DdlApplyHelper.isPartitionGroupNotExistsError(null));
    }

    /**
     * 测试 detectPartitionDdlType 方法：
     * 识别 SQL 的分区DDL操作类型（ADD_PARTITION / DROP_PARTITION / NONE）
     */
    @Test
    public void testDetectPartitionDdlType() {
        // ADD PARTITION DDL（含 POLARX 头部）应识别为 ADD_PARTITION
        String addPartSql =
            "# POLARX_ORIGIN_SQL=ALTER TABLE `ttl_tbl` ADD PARTITION (PARTITION `p20260422` VALUES LESS THAN ('2026-05-01 00:00:00'))\n"
                + "# POLARX_TSO=12345\n"
                + "# POLARX_DDL_ID=0\n"
                + "ALTER TABLE `ttl_tbl` ADD PARTITION (PARTITION `p20260422` VALUES LESS THAN ('2026-05-01 00:00:00'))";
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.ADD_PARTITION,
            DdlApplyHelper.detectPartitionDdlType(addPartSql));

        // 不含POLARX头部的纯 ADD PARTITION SQL
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.ADD_PARTITION,
            DdlApplyHelper.detectPartitionDdlType(
                "ALTER TABLE `ttl_tbl` ADD PARTITION (PARTITION `p20260422` VALUES LESS THAN ('2026-05-01 00:00:00'))"));

        // DROP PARTITION DDL 应识别为 DROP_PARTITION
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.DROP_PARTITION,
            DdlApplyHelper.detectPartitionDdlType(
                "ALTER TABLE `ttl_tbl` DROP PARTITION `p20260101`"));

        // ADD COLUMN DDL 应识别为 NONE
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.NONE,
            DdlApplyHelper.detectPartitionDdlType(
                "ALTER TABLE `ttl_tbl` ADD COLUMN `new_col` INT"));

        // 混合操作（ADD PARTITION + ADD COLUMN）应识别为 NONE
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.NONE,
            DdlApplyHelper.detectPartitionDdlType(
                "ALTER TABLE `ttl_tbl` ADD PARTITION (PARTITION `p20260422` VALUES LESS THAN ('2026-05-01 00:00:00')), ADD COLUMN `new_col` INT"));

        // CREATE TABLE 应识别为 NONE
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.NONE,
            DdlApplyHelper.detectPartitionDdlType(
                "CREATE TABLE `ttl_tbl` (id INT)"));

        // null/空 SQL 应识别为 NONE
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.NONE,
            DdlApplyHelper.detectPartitionDdlType(null));
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.NONE,
            DdlApplyHelper.detectPartitionDdlType(""));

        // ADD SUBPARTITION 应识别为 ADD_PARTITION
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.ADD_PARTITION,
            DdlApplyHelper.detectPartitionDdlType(
                "ALTER TABLE `ttl_tbl` ADD SUBPARTITION (SUBPARTITION `p20260422sp1` VALUES LESS THAN ('2026-05-01 00:00:00'))"));

        // DROP SUBPARTITION 应识别为 DROP_PARTITION
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.DROP_PARTITION,
            DdlApplyHelper.detectPartitionDdlType(
                "ALTER TABLE `ttl_tbl` DROP SUBPARTITION `p20260101sp1`"));

        // 同时 ADD 多个分区：不安全（部分可能冲突），应识别为 NONE
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.NONE,
            DdlApplyHelper.detectPartitionDdlType(
                "ALTER TABLE `ttl_tbl` ADD PARTITION "
                    + "(PARTITION `p20260422` VALUES LESS THAN ('2026-05-01 00:00:00'), "
                    + "PARTITION `p20260522` VALUES LESS THAN ('2026-06-01 00:00:00'))"));

        // 同时 DROP 多个分区：不安全（部分可能冲突），应识别为 NONE
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.NONE,
            DdlApplyHelper.detectPartitionDdlType(
                "ALTER TABLE `ttl_tbl` DROP PARTITION `p20260101`, `p20260201`"));
    }

    /**
     * 测试 isTtlAutoPartitionConflictError 方法：
     * 主备均开启TTL时，CDC同步分区DDL到备库产生幂等冲突的场景识别
     */
    @Test
    public void testIsTtlAutoPartitionConflictError() {
        String addPartSql = "ALTER TABLE `ttl_tbl` ADD PARTITION "
            + "(PARTITION `p20260422sp1` VALUES LESS THAN ('2026-05-01 00:00:00'))";
        String dropPartSql = "ALTER TABLE `ttl_tbl` DROP PARTITION `p20260101`";
        String addColSql = "ALTER TABLE `ttl_tbl` ADD COLUMN `new_col` INT";

        SQLException addPartitionAlreadyExistsError = new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition name: p20260422sp1 already exists. Please use another name.");
        SQLException dropPartitionNotExistsError = new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition group 'p20260101' doesn't exist");
        SQLException otherError = new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-4700][ERR_SERVER] some other error");

        // ADD PARTITION + 分区已存在 => true
        Assert.assertTrue(DdlApplyHelper.isTtlAutoPartitionConflictError(addPartSql, addPartitionAlreadyExistsError));

        // DROP PARTITION + 分区不存在 => true
        Assert.assertTrue(DdlApplyHelper.isTtlAutoPartitionConflictError(dropPartSql, dropPartitionNotExistsError));

        // ADD PARTITION + 分区不存在错误（不匹配） => false
        Assert.assertFalse(DdlApplyHelper.isTtlAutoPartitionConflictError(addPartSql, dropPartitionNotExistsError));

        // DROP PARTITION + 分区已存在错误（不匹配） => false
        Assert.assertFalse(DdlApplyHelper.isTtlAutoPartitionConflictError(dropPartSql, addPartitionAlreadyExistsError));

        // ADD COLUMN DDL + 分区已存在错误（SQL类型不匹配） => false
        Assert.assertFalse(DdlApplyHelper.isTtlAutoPartitionConflictError(addColSql, addPartitionAlreadyExistsError));

        // ADD PARTITION + 其他错误（错误类型不匹配） => false
        Assert.assertFalse(DdlApplyHelper.isTtlAutoPartitionConflictError(addPartSql, otherError));

        // null SQL => false
        Assert.assertFalse(DdlApplyHelper.isTtlAutoPartitionConflictError(null, addPartitionAlreadyExistsError));

        // null 异常 => false
        Assert.assertFalse(DdlApplyHelper.isTtlAutoPartitionConflictError(addPartSql, null));
    }

    @Test
    public void testTryWaitCreateOrDropDatabase() throws SQLException, InterruptedException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        ResultSet resultSet1 = Mockito.mock(ResultSet.class);
        ResultSet resultSet2 = Mockito.mock(ResultSet.class);
        ResultSet resultSet3 = Mockito.mock(ResultSet.class);
        ResultSet resultSet4 = Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(
            "show full processlist where info like '%token1%' and info not like 'show full processlist%'")).thenReturn(
            resultSet1);
        when(statement.executeQuery(
            "show full processlist where info like '%token2%' and info not like 'show full processlist%'")).thenReturn(
            resultSet2);
        when(statement.executeQuery("select * from metadb.db_info where db_status!=0 and db_name='d1'")).thenReturn(
            resultSet3);
        when(statement.executeQuery("select * from metadb.db_info where db_status!=0 and db_name='d2'")).thenReturn(
            resultSet4);
        when(resultSet1.next()).thenReturn(true);
        when(resultSet2.next()).thenReturn(false);
        when(resultSet3.next()).thenReturn(true);
        when(resultSet4.next()).thenReturn(false);

        try {
            DdlApplyHelper.tryWaitCreateOrDropDatabase(dataSource, "token1", "000000", 2, "d1");
            Assert.fail();
        } catch (TimeoutException ignored) {
        }
        try {
            DdlApplyHelper.tryWaitCreateOrDropDatabase(dataSource, "token1", "000000", 2, "d2");
            Assert.fail();
        } catch (TimeoutException ignored) {
        }
        try {
            DdlApplyHelper.tryWaitCreateOrDropDatabase(dataSource, "token2", "000000", 2, "d1");
            Assert.fail();
        } catch (TimeoutException ignored) {
        }

        DdlApplyHelper.tryWaitCreateOrDropDatabase(dataSource, "token2", "000000", 5, "d2");
    }

    /**
     * 测试 isTtlTable：当 SHOW CREATE TABLE 返回含 TTL 选项的建表语句时，返回 true
     */
    @Test
    public void testIsTtlTable_withTtlOption_returnsTrue() throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        // 模拟含 TTL 选项的建表语句
        when(resultSet.getString(2)).thenReturn(
            "CREATE TABLE `ttl_tbl` (\n"
                + "  `id` bigint NOT NULL,\n"
                + "  `gmt_create` datetime NOT NULL,\n"
                + "  PRIMARY KEY (`id`)\n"
                + ") ENGINE=InnoDB\n"
                + "PARTITION BY RANGE COLUMNS(`gmt_create`)\n"
                + "(PARTITION p20260301 VALUES LESS THAN ('2026-04-01'))\n"
                + "TTL = TTL_DEFINITION(\n"
                + "  TTL_EXPR = `gmt_create` EXPIRE AFTER 3 MONTH TIMEZONE '+08:00'\n"
                + ")");

        Assert.assertTrue(DdlApplyHelper.isTtlTable(dataSource, "db1", "ttl_tbl"));
    }

    /**
     * 测试 isTtlTable：当建表语句不含 TTL 选项时，返回 false
     */
    @Test
    public void testIsTtlTable_withoutTtlOption_returnsFalse() throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        // 普通表，不含 TTL 选项
        when(resultSet.getString(2)).thenReturn(
            "CREATE TABLE `normal_tbl` (\n"
                + "  `id` bigint NOT NULL,\n"
                + "  `name` varchar(64),\n"
                + "  PRIMARY KEY (`id`)\n"
                + ") ENGINE=InnoDB");

        Assert.assertFalse(DdlApplyHelper.isTtlTable(dataSource, "db1", "normal_tbl"));
    }

    /**
     * 测试 isTtlTable：schema 或 tableName 为空时，返回 false
     */
    @Test
    public void testIsTtlTable_blankParams_returnsFalse() {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Assert.assertFalse(DdlApplyHelper.isTtlTable(dataSource, "", "tbl"));
        Assert.assertFalse(DdlApplyHelper.isTtlTable(dataSource, "db", ""));
        Assert.assertFalse(DdlApplyHelper.isTtlTable(dataSource, null, "tbl"));
        Assert.assertFalse(DdlApplyHelper.isTtlTable(dataSource, "db", null));
    }

    /**
     * 测试 isTtlTable：查询抛异常时，保守返回 false（不跳过DDL）
     */
    @Test
    public void testIsTtlTable_exception_returnsFalse() throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        Assert.assertFalse(DdlApplyHelper.isTtlTable(dataSource, "db1", "tbl"));
    }

    /**
     * 测试 isPartitionGroupNotExistsError 的第二种模式（小写 partition）
     */
    @Test
    public void testIsPartitionGroupNotExistsError_lowercasePartition() {
        // 小写 partition 模式也应识别
        Assert.assertTrue(DdlApplyHelper.isPartitionGroupNotExistsError(new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "partition 'p20260301' doesn't exist")));
    }

    /**
     * 测试 detectPartitionDdlType：含 POLARX_ORIGIN_SQL 的 DROP PARTITION DDL
     */
    @Test
    public void testDetectPartitionDdlType_dropPartWithPolarxHeader() {
        String dropPartSql = "# POLARX_ORIGIN_SQL=ALTER TABLE `ttl_tbl` DROP PARTITION `p20260101`\n"
            + "# POLARX_TSO=12345\n"
            + "# POLARX_DDL_ID=0\n"
            + "ALTER TABLE `ttl_tbl` DROP PARTITION `p20260101`";
        Assert.assertEquals(DdlApplyHelper.PartitionDdlType.DROP_PARTITION,
            DdlApplyHelper.detectPartitionDdlType(dropPartSql));
    }

    /**
     * 测试 isTtlAutoPartitionConflictError：含 POLARX 头部的 ADD PARTITION DDL + 分区已存在错误
     */
    @Test
    public void testIsTtlAutoPartitionConflictError_withPolarxHeader() {
        String addPartSql = "# POLARX_ORIGIN_SQL=ALTER TABLE `ttl_tbl` ADD PARTITION "
            + "(PARTITION `p20260422` VALUES LESS THAN ('2026-05-01 00:00:00'))\n"
            + "# POLARX_TSO=12345\n"
            + "# POLARX_DDL_ID=0\n"
            + "ALTER TABLE `ttl_tbl` ADD PARTITION "
            + "(PARTITION `p20260422` VALUES LESS THAN ('2026-05-01 00:00:00'))";
        SQLException err = new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition name: p20260422 already exists. Please use another name.");

        Assert.assertTrue(DdlApplyHelper.isTtlAutoPartitionConflictError(addPartSql, err));
    }

    /**
     * 测试 isTtlAutoPartitionConflictError：DROP SUBPARTITION + 分区不存在错误
     */
    @Test
    public void testIsTtlAutoPartitionConflictError_dropSubpartition() {
        String dropSubPartSql = "ALTER TABLE `ttl_tbl` DROP SUBPARTITION `p20260101sp1`";
        SQLException err = new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition group 'p20260101sp1' doesn't exist");

        Assert.assertTrue(DdlApplyHelper.isTtlAutoPartitionConflictError(dropSubPartSql, err));
    }

    /**
     * 测试 isTtlAutoPartitionConflictError：ADD SUBPARTITION + 分区已存在
     */
    @Test
    public void testIsTtlAutoPartitionConflictError_addSubpartition() {
        String addSubPartSql = "ALTER TABLE `ttl_tbl` ADD SUBPARTITION "
            + "(SUBPARTITION `p20260422sp1` VALUES LESS THAN ('2026-05-01 00:00:00'))";
        SQLException err = new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition name: p20260422sp1 already exists. Please use another name.");

        Assert.assertTrue(DdlApplyHelper.isTtlAutoPartitionConflictError(addSubPartSql, err));
    }

    /**
     * 测试 executeDdl 中 TTL 自动分区幂等冲突跳过逻辑的完整路径：
     * 当 DDL 执行失败 + 错误匹配 + 目标表为 TTL 表时，应跳过错误并标记 DDL 成功。
     * 覆盖 executeDdl 方法中 lines 716-724 的调用代码块。
     */
    @Test
    public void testExecuteDdl_skipTtlAutoPartitionConflict() throws Exception {
        // 设置配置项
        mockConfig(ConfigKeys.RPL_INC_DDL_SKIP_TTL_AUTO_PARTITION_ERROR, "true");
        mockConfig(ConfigKeys.RPL_INC_DDL_SKIP_MISS_LOCAL_PARTITION_ERROR, "false");
        mockConfig(ConfigKeys.RPL_DDL_RETRY_MAX_COUNT, "3");
        mockConfig(ConfigKeys.RPL_DDL_RETRY_INTERVAL_MILLS, "10");

        // 模拟 DataSource：第一次连接用于 DDL 执行（抛错），第二次连接用于 isTtlTable 查询
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection ddlConn = Mockito.mock(Connection.class);
        Statement ddlStmt = Mockito.mock(Statement.class);
        Connection ttlConn = Mockito.mock(Connection.class);
        Statement ttlStmt = Mockito.mock(Statement.class);
        ResultSet ttlRs = Mockito.mock(ResultSet.class);

        // DDL 执行连接：executeUpdate 抛分区已存在错误
        when(dataSource.getConnection()).thenReturn(ddlConn).thenReturn(ttlConn);
        when(ddlConn.createStatement()).thenReturn(ddlStmt);
        when(ddlStmt.execute(anyString())).thenThrow(new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition name: p20260422 already exists. Please use another name."));

        // isTtlTable 查询连接：返回含 TTL 选项的建表语句
        when(ttlConn.createStatement()).thenReturn(ttlStmt);
        when(ttlStmt.executeQuery(contains("SHOW CREATE TABLE"))).thenReturn(ttlRs);
        when(ttlRs.next()).thenReturn(true);
        when(ttlRs.getString(2)).thenReturn(
            "CREATE TABLE `ttl_tbl` (\n"
                + "  `id` bigint NOT NULL,\n"
                + "  `gmt_create` datetime NOT NULL,\n"
                + "  PRIMARY KEY (`id`)\n"
                + ") ENGINE=InnoDB\n"
                + "PARTITION BY RANGE COLUMNS(`gmt_create`)\n"
                + "(PARTITION p20260301 VALUES LESS THAN ('2026-04-01'))\n"
                + "TTL = TTL_DEFINITION(\n"
                + "  TTL_EXPR = `gmt_create` EXPIRE AFTER 3 MONTH TIMEZONE '+08:00'\n"
                + ")");

        // 构造 SqlContext：ADD PARTITION SQL
        String addPartSql = "ALTER TABLE `ttl_tbl` ADD PARTITION "
            + "(PARTITION `p20260422` VALUES LESS THAN ('2026-05-01 00:00:00'))";
        SqlContext sqlContext = new SqlContext(addPartSql, "db1", "ttl_tbl", null);

        // 构造 RplDdl（非 CREATE/DROP DATABASE）
        RplDdl rplDdl = new RplDdl();
        rplDdl.setToken(UUID.randomUUID().toString());
        rplDdl.setGmtCreated(new Date());
        rplDdl.setDdlStmt(addPartSql);

        // 构造 DbMetaCache（不会用到，因为 syncPoint=false）
        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);

        // 调用 executeDdl：应走 TTL 跳过路径，不抛异常
        DdlApplyHelper.executeDdl(dataSource, sqlContext, null, "test_tso_001", rplDdl,
            false, dbMetaCache, false);

        // 如果没有抛异常，说明成功走了 TTL 跳过路径（markDdlSucceed + return）
    }

    /**
     * 测试 executeDdl 中 TTL 自动分区幂等冲突跳过逻辑 - DROP PARTITION 场景：
     * 当 DDL 执行失败 + 分区不存在错误 + 目标表为 TTL 表时，应跳过错误。
     */
    @Test
    public void testExecuteDdl_skipTtlAutoPartitionConflict_dropPartition() throws Exception {
        mockConfig(ConfigKeys.RPL_INC_DDL_SKIP_TTL_AUTO_PARTITION_ERROR, "true");
        mockConfig(ConfigKeys.RPL_INC_DDL_SKIP_MISS_LOCAL_PARTITION_ERROR, "false");
        mockConfig(ConfigKeys.RPL_DDL_RETRY_MAX_COUNT, "3");
        mockConfig(ConfigKeys.RPL_DDL_RETRY_INTERVAL_MILLS, "10");

        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection ddlConn = Mockito.mock(Connection.class);
        Statement ddlStmt = Mockito.mock(Statement.class);
        Connection ttlConn = Mockito.mock(Connection.class);
        Statement ttlStmt = Mockito.mock(Statement.class);
        ResultSet ttlRs = Mockito.mock(ResultSet.class);

        // DDL 执行失败：分区不存在错误
        when(dataSource.getConnection()).thenReturn(ddlConn).thenReturn(ttlConn);
        when(ddlConn.createStatement()).thenReturn(ddlStmt);
        when(ddlStmt.execute(anyString())).thenThrow(new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition group 'p20260101' doesn't exist"));

        // isTtlTable 查询
        when(ttlConn.createStatement()).thenReturn(ttlStmt);
        when(ttlStmt.executeQuery(contains("SHOW CREATE TABLE"))).thenReturn(ttlRs);
        when(ttlRs.next()).thenReturn(true);
        when(ttlRs.getString(2)).thenReturn(
            "CREATE TABLE `ttl_tbl` (\n"
                + "  `id` bigint NOT NULL,\n"
                + "  `gmt_create` datetime NOT NULL,\n"
                + "  PRIMARY KEY (`id`)\n"
                + ") ENGINE=InnoDB\n"
                + "PARTITION BY RANGE COLUMNS(`gmt_create`)\n"
                + "(PARTITION p20260301 VALUES LESS THAN ('2026-04-01'))\n"
                + "TTL = TTL_DEFINITION(\n"
                + "  TTL_EXPR = `gmt_create` EXPIRE AFTER 3 MONTH TIMEZONE '+08:00'\n"
                + ")");

        String dropPartSql = "ALTER TABLE `ttl_tbl` DROP PARTITION `p20260101`";
        SqlContext sqlContext = new SqlContext(dropPartSql, "db1", "ttl_tbl", null);

        RplDdl rplDdl = new RplDdl();
        rplDdl.setToken(UUID.randomUUID().toString());
        rplDdl.setGmtCreated(new Date());
        rplDdl.setDdlStmt(dropPartSql);

        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);

        // 应走 TTL 跳过路径，不抛异常
        DdlApplyHelper.executeDdl(dataSource, sqlContext, null, "test_tso_002", rplDdl,
            false, dbMetaCache, false);
    }

    /**
     * 测试 executeDdl 中当 TTL 跳过开关关闭时，不应跳过错误，最终抛出异常
     */
    @Test
    public void testExecuteDdl_ttlSkipDisabled_shouldThrow() throws Exception {
        mockConfig(ConfigKeys.RPL_INC_DDL_SKIP_TTL_AUTO_PARTITION_ERROR, "false");
        mockConfig(ConfigKeys.RPL_INC_DDL_SKIP_MISS_LOCAL_PARTITION_ERROR, "false");
        mockConfig(ConfigKeys.RPL_DDL_RETRY_MAX_COUNT, "1");
        mockConfig(ConfigKeys.RPL_DDL_RETRY_INTERVAL_MILLS, "10");

        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection ddlConn = Mockito.mock(Connection.class);
        Statement ddlStmt = Mockito.mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(ddlConn);
        when(ddlConn.createStatement()).thenReturn(ddlStmt);
        when(ddlStmt.execute(anyString())).thenThrow(new SQLException(
            "[xxxxx][10.1.1.1:3306][db1]ERR-CODE: [TDDL-9300][ERR_PARTITION_MANAGEMENT] "
                + "Partition name: p20260422 already exists. Please use another name."));

        String addPartSql = "ALTER TABLE `ttl_tbl` ADD PARTITION "
            + "(PARTITION `p20260422` VALUES LESS THAN ('2026-05-01 00:00:00'))";
        SqlContext sqlContext = new SqlContext(addPartSql, "db1", "ttl_tbl", null);

        RplDdl rplDdl = new RplDdl();
        rplDdl.setToken(UUID.randomUUID().toString());
        rplDdl.setGmtCreated(new Date());
        rplDdl.setDdlStmt(addPartSql);

        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);

        // 开关关闭时应最终因超过重试次数抛异常
        try {
            DdlApplyHelper.executeDdl(dataSource, sqlContext, null, "test_tso_003", rplDdl,
                false, dbMetaCache, false);
            Assert.fail("should throw exception when TTL skip is disabled");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("exceeds max retry times"));
        }
    }
}
