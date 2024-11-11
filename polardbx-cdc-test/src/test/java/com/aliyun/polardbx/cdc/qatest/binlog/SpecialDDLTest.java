/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog;

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
    public void testDropShardKey(){
        String ddl1 = "create table order_refund_manage(a int primary key, b int ,c int , index auto_shard_key_b(`b`)) dbpartition by hash(c)";
        String ddl2 = "alter table order_refund_manage drop index auto_shard_key_b";
        String ddl3 = "alter table order_refund_manage change column b b longtext";

        JdbcUtil.executeUpdate(polardbxConnection, ddl1);
        JdbcUtil.executeUpdate(polardbxConnection, ddl2);
        JdbcUtil.executeUpdate(polardbxConnection, ddl3);
    }

    private void testCreateTableLikeAndDropShardKey(String t1, String t2){
        String sql1 = String.format("create table %s(id bigint primary key , name varchar(20))", t1);
        String sql2 = String.format("alter table %s dbpartition by hash(name)", t1);
        String sql3 = String.format("create table %s like %s", t2, t1);
        String sql4 = String.format("alter table %s drop index auto_shard_key_name", t2);
        JdbcUtil.executeUpdate(polardbxConnection, sql1);
        JdbcUtil.executeUpdate(polardbxConnection, sql2);
        JdbcUtil.executeUpdate(polardbxConnection, sql3);
        JdbcUtil.executeUpdate(polardbxConnection, sql4);
    }

    private void testCreateTableLikeAndDropShardKeyV2(String t1, String t2){
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
    public void testCreateTableLikeAndDropShardKeyWithoutDbName(){
        JdbcUtil.useDb(polardbxConnection, DB_NAME);
        testCreateTableLikeAndDropShardKey(String.format("`%s`", RandomStringUtils.randomAlphanumeric(10)), String.format("`%s`", RandomStringUtils.randomAlphanumeric(10)));
        testCreateTableLikeAndDropShardKeyV2(String.format("`%s`", RandomStringUtils.randomAlphanumeric(10)), String.format("`%s`", RandomStringUtils.randomAlphanumeric(10)));
    }

    @Test
    public void testCreateTableLikeAndDropShardKeyWithDbName(){
        JdbcUtil.useDb(polardbxConnection, DB_NAME);
        testCreateTableLikeAndDropShardKey(String.format("`%s`.`%s`", DB_NAME, RandomStringUtils.randomAlphanumeric(10)), String.format("`%s`.`%s`", DB_NAME1, RandomStringUtils.randomAlphanumeric(10)));
        testCreateTableLikeAndDropShardKeyV2(String.format("`%s`.`%s`", DB_NAME, RandomStringUtils.randomAlphanumeric(10)), String.format("`%s`.`%s`", DB_NAME1, RandomStringUtils.randomAlphanumeric(10)));
    }
}
