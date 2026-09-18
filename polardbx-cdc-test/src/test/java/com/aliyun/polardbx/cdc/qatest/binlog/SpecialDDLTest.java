/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.SQLException;

/**
 * created by ziyang.lb
 **/
public class SpecialDDLTest extends RplBaseTestCase {
    private static final String DB_NAME = "cdc_special_ddl";
    private static final String DB_NAME1 = "cdc_special_ddl_1";

    @BeforeClass
    public static void bootStrap() throws SQLException {
        prepareTestDatabase(DB_NAME);
        prepareTestDatabase(DB_NAME1);
    }

    @Test
    public void testUniqueKeyInColumn() {
        String sql1 = "CREATE TABLE IF NOT EXISTS `ZXe5GA6` (\n"
            + "  `aiMzgbaKVCIQtle` INT(1) UNSIGNED NULL COMMENT 'treSay',\n"
            + "  `V8R9mZFvUHxQ` MEDIUMINT UNSIGNED ZEROFILL COMMENT 'WenHosxfI3i',\n"
            + "  `RFKRrCAF` TIMESTAMP UNIQUE,\n"
            + "  `ctv` BIGINT(5) NULL,\n"
            + "  `Vd` TINYINT UNSIGNED ZEROFILL UNIQUE COMMENT 'lysAE',\n"
            + "  `8` MEDIUMINT(4) ZEROFILL COMMENT 'Y',\n"
            + "  `H4rJ5c8d0N1C8Q` BIGINT UNSIGNED ZEROFILL NOT NULL,\n"
            + "  `iE69EIYRLOqXa3` DATE NOT NULL COMMENT 'VAHhex',\n"
            + "  `OsBUdkS` MEDIUMINT ZEROFILL COMMENT 'zgV7ojRAJKgu4XI',\n"
            + "  `LADuM` TIMESTAMP(0) COMMENT 'nkaLg0',\n"
            + "  `kO38Dx6gYUPRtBn` MEDIUMINT UNSIGNED ZEROFILL UNIQUE,\n"
            + "  KEY `Cb` USING HASH (`Vd`),\n"
            + "  INDEX `auto_shard_key_ctv` USING BTREE(`CTV`),\n"
            + "  INDEX `auto_shard_key_ie69eiyrloqxa3` USING BTREE(`IE69EIYRLOQXA3`),\n"
            + "  _drds_implicit_id_ bigint AUTO_INCREMENT,\n"
            + "  PRIMARY KEY (_drds_implicit_id_)\n"
            + ")\n"
            + "DBPARTITION BY RIGHT_SHIFT(`ctv`, 9)\n"
            + "TBPARTITION BY YYYYMM(`iE69EIYRLOqXa3`) TBPARTITIONS 7";
        String sql2 = "DROP INDEX `ko38dx6gyuprtbn` ON `ZXe5GA6`";
        String sql3 = "ALTER TABLE `ZXe5GA6` CHANGE COLUMN `kO38Dx6gYUPRtBn` `fN` TINYBLOB NULL COMMENT 'as' FIRST ";

        JdbcUtil.executeUpdate(polardbxConnection, sql1);
        JdbcUtil.executeUpdate(polardbxConnection, sql2);
        JdbcUtil.executeUpdate(polardbxConnection, sql3);
    }

    @Test
    public void testDropShardKey() {
        String ddl1 =
            "create table order_refund_manage(a int primary key, b int ,c int , index auto_shard_key_b(`b`)) dbpartition by hash(c)";
        String ddl2 = "alter table order_refund_manage drop index auto_shard_key_b";
        String ddl3 = "alter table order_refund_manage change column b b longtext";

        JdbcUtil.executeUpdate(polardbxConnection, ddl1);
        JdbcUtil.executeUpdate(polardbxConnection, ddl2);
        JdbcUtil.executeUpdate(polardbxConnection, ddl3);
    }

    private void testCreateTableLikeAndDropShardKey(String t1, String t2) {
        String sql1 = String.format("create table %s(id bigint primary key , name varchar(20))", t1);
        String sql2 = String.format("alter table %s dbpartition by hash(name)", t1);
        String sql3 = String.format("create table %s like %s", t2, t1);
        String sql4 = String.format("alter table %s drop index auto_shard_key_name", t2);
        JdbcUtil.executeUpdate(polardbxConnection, sql1);
        JdbcUtil.executeUpdate(polardbxConnection, sql2);
        JdbcUtil.executeUpdate(polardbxConnection, sql3);
        JdbcUtil.executeUpdate(polardbxConnection, sql4);
    }

    private void testCreateTableLikeAndDropShardKeyV2(String t1, String t2) {
        String sql1 = String.format("create table %s(`id` bigint primary key , `name` varchar(20))", t1);
        String sql2 = String.format("alter table %s dbpartition by hash(`name`)", t1);
        String sql3 = String.format("create table %s like %s", t2, t1);
        String sql4 = String.format("alter table %s drop index auto_shard_key_name", t2);
        JdbcUtil.executeUpdate(polardbxConnection, sql1);
        JdbcUtil.executeUpdate(polardbxConnection, sql2);
        JdbcUtil.executeUpdate(polardbxConnection, sql3);
        JdbcUtil.executeUpdate(polardbxConnection, sql4);
    }

    @Test
    public void testCreateTableLikeAndDropShardKeyWithoutDbName() {
        JdbcUtil.useDb(polardbxConnection, DB_NAME);
        testCreateTableLikeAndDropShardKey(String.format("`%s`", RandomStringUtils.randomAlphanumeric(10)),
            String.format("`%s`", RandomStringUtils.randomAlphanumeric(10)));
        testCreateTableLikeAndDropShardKeyV2(String.format("`%s`", RandomStringUtils.randomAlphanumeric(10)),
            String.format("`%s`", RandomStringUtils.randomAlphanumeric(10)));
    }

    @Test
    public void testCreateTableLikeAndDropShardKeyWithDbName() {
        JdbcUtil.useDb(polardbxConnection, DB_NAME);
        testCreateTableLikeAndDropShardKey(
            String.format("`%s`.`%s`", DB_NAME, RandomStringUtils.randomAlphanumeric(10)),
            String.format("`%s`.`%s`", DB_NAME1, RandomStringUtils.randomAlphanumeric(10)));
        testCreateTableLikeAndDropShardKeyV2(
            String.format("`%s`.`%s`", DB_NAME, RandomStringUtils.randomAlphanumeric(10)),
            String.format("`%s`.`%s`", DB_NAME1, RandomStringUtils.randomAlphanumeric(10)));
    }

    /**
     * Test that CDC correctly handles the recycle bin DDL sequence.
     * When ENABLE_RECYCLEBIN is true, DROP TABLE triggers a RENAME TABLE (t -> BIN_xxx),
     * and PURGE RECYCLEBIN triggers a DROP TABLE BIN_xxx purge.
     * Previously, getRenameTo returned schema-qualified names (e.g., db.BIN_xxx) instead of
     * just the table name (BIN_xxx), causing deltaChangeMap key mismatch and
     * "compare failed, can't find logic table meta" error.
     *
     * @see <a href="https://aliyuque.antfin.com/coronadb/knddog/dhgtizzx09mf5wr6">DBLE RECYCLE BIN issue</a>
     */
    @Test
    public void testRecycleBinDropAndPurge() {
        JdbcUtil.useDb(polardbxConnection, DB_NAME);
        String tableName = "t_recycle_" + RandomStringUtils.randomAlphanumeric(8).toLowerCase();

        // enable recycle bin
        JdbcUtil.executeUpdate(polardbxConnection, "SET ENABLE_RECYCLEBIN = true");
        try {
            // create table
            JdbcUtil.executeUpdate(polardbxConnection,
                String.format("CREATE TABLE `%s` (id bigint, PRIMARY KEY(id))", tableName));

            // drop table with schema-qualified name, which triggers RENAME TABLE t -> BIN_xxx
            JdbcUtil.executeUpdate(polardbxConnection,
                String.format("DROP TABLE `%s`.`%s`", DB_NAME, tableName));

            // purge recyclebin, which triggers DROP TABLE BIN_xxx purge
            JdbcUtil.executeUpdate(polardbxConnection, "PURGE RECYCLEBIN");

            // send token and wait for CDC to process all DDLs without error
            sendTokenAndWait(CheckParameter.builder().build());
        } finally {
            // ensure recycle bin is disabled even if the test fails
            JdbcUtil.executeUpdate(polardbxConnection, "SET ENABLE_RECYCLEBIN = false");
        }
    }

    /**
     * Test that CDC correctly handles CREATE TABLE statements prefixed with `--` line comments.
     * <p>
     * The DDL structure with DDL_ID block comment, `--` line comment, TDDL hint and CREATE TABLE triggers
     * hasBeforeComment=false and goes through the prettyFormat=false single-line output path.
     * Without the fix, the `--` comment would swallow the CREATE TABLE on single-line output,
     * causing the downstream to miss the table and fail on subsequent INSERT sync.
     */
    @Test
    public void testCreateTableWithHyphenComment() {
        JdbcUtil.useDb(polardbxConnection, DB_NAME);
        String tableName = "t_hyphen_comment_" + RandomStringUtils.randomAlphanumeric(8).toLowerCase();

        // 前缀结构：/*DDL_ID*/ + -- 行注释 + TDDL hint + CREATE TABLE
        // hasBeforeComment=false，走 prettyFormat=false 单行输出路径
        String ddl = "/*DDL_ID=7481263829662302272*/\n"
            + "-- ===== Regular BJ tables =====\n"
            + "/*+TDDL:cmd_extra(SEQUENTIAL_CONCURRENT_POLICY=true)*/\n"
            + String.format(
            "CREATE TABLE `%s` (id bigint, data_source TINYINT NOT NULL DEFAULT 0 COMMENT 'test', PRIMARY KEY(id))",
            tableName);
        JdbcUtil.executeUpdate(polardbxConnection, ddl);

        // 如果 CREATE TABLE 被行注释吞掉，下游没有该表，INSERT 同步失败，waitAndCheck 会超时
        JdbcUtil.executeUpdate(polardbxConnection,
            String.format("INSERT INTO `%s` (id, data_source) VALUES (1, 1), (2, 2)", tableName));

        waitAndCheck(CheckParameter.builder().dbName(DB_NAME).tbName(tableName).build());
    }

    /**
     * Test that CDC correctly handles CREATE TABLE statements prefixed with `#` line comments.
     */
    @Test
    public void testCreateTableWithHashComment() {
        JdbcUtil.useDb(polardbxConnection, DB_NAME);
        String tableName = "t_hash_comment_" + RandomStringUtils.randomAlphanumeric(8).toLowerCase();

        // 前缀结构：/*DDL_ID*/ + # 行注释 + TDDL hint + CREATE TABLE
        String ddl = "/*DDL_ID=7481263829662302272*/\n"
            + "# ===== hash line comment =====\n"
            + "/*+TDDL:cmd_extra(SEQUENTIAL_CONCURRENT_POLICY=true)*/\n"
            + String.format(
            "CREATE TABLE `%s` (id bigint, data_source TINYINT NOT NULL DEFAULT 0 COMMENT 'test', PRIMARY KEY(id))",
            tableName);
        JdbcUtil.executeUpdate(polardbxConnection, ddl);

        JdbcUtil.executeUpdate(polardbxConnection,
            String.format("INSERT INTO `%s` (id, data_source) VALUES (1, 1), (2, 2)", tableName));

        waitAndCheck(CheckParameter.builder().dbName(DB_NAME).tbName(tableName).build());
    }
}
