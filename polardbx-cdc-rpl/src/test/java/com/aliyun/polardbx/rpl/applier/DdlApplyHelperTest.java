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
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.relay.DdlRouteMode;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.common.RplConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.UUID;

import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.getDdlRouteMode;
import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.tryAttachAsyncDdlHints;
import static com.aliyun.polardbx.rpl.applier.DdlApplyHelper.tryRemoveColumnarIndex;

/**
 * @author shicai.xsc 2021/4/19 11:18
 * @since 5.0.0.0
 */
@Slf4j
public class DdlApplyHelperTest extends RplWithGmsTablesBaseTest {

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
        setConfig(ConfigKeys.RPL_ASYNC_DDL_ENABLED, "true");
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

    private void tryAttacheAndCheck(String sql) {
        String result = tryAttachAsyncDdlHints(sql, Long.MAX_VALUE);
        Assert.assertEquals(RplConstants.ASYNC_DDL_HINTS + sql, result);
    }

    private void tryAttacheAndCheck2(String sql) {
        String result = tryAttachAsyncDdlHints(sql, Long.MAX_VALUE);
        Assert.assertEquals(sql, result);
    }
}
