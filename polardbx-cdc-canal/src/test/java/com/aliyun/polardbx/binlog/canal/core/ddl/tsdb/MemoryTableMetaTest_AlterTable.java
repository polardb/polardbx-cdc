/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MemoryTableMetaTest_AlterTable extends MemoryTableMetaBase {

    @Test
    public void testChangeColumn() {
        String sql = "create table if not exists `yrxsv7kevh4ce9` (\n"
            + "        `d` int unsigned not null comment 'as',\n"
            + "        `vmk` bigint(1) not null comment '8kenylgzvnb05',\n"
            + "        `w7r1ydbmolofjfm` integer unsigned not null comment 'pwlqfhqi2ncdq',\n"
            + "        index `auto_shard_key_w7r1ydbmolofjfm` using btree(`w7r1ydbmolofjfm`),\n"
            + "        _drds_implicit_id_ bigint auto_increment,\n"
            + "        primary key (_drds_implicit_id_)\n"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1", sql);

        sql = "alter table `yrxsv7kevh4ce9` add column `7f3i` tinyint comment 'qa4qh1w', "
            + "change column `vmk` `o13nfnt8n` integer(6) unsigned unique comment 'ioj'";
        memoryTableMeta.apply(null, "d1", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "yrxsv7kevh4ce9");
        Assert.assertNotNull(tableMeta.getFieldMetaByName("7f3i"));
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("7f3i").getColumnType());
        Assert.assertNull(tableMeta.getFieldMetaByName("vmk", true));
        Assert.assertNotNull(tableMeta.getFieldMetaByName("o13nfnt8n"));
        Assert.assertEquals("int unsigned", tableMeta.getFieldMetaByName("o13nfnt8n").getColumnType());

        sql = "alter table `yrxsv7kevh4ce9` change column `7f3i` `tmu6ki0zxzslb` varbinary(1) null unique";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "yRxSV7kEvh4Ce9");
        Assert.assertNotNull(tableMeta.getFieldMetaByName("tmu6ki0zxzslb"));
        Assert.assertNull(tableMeta.getFieldMetaByName("7f3i", true));
        Assert.assertEquals("varbinary(1)", tableMeta.getFieldMetaByName("tmu6ki0zxzslb").getColumnType());
    }

    @Test
    public void testRemovePK_1() {
        String ddl = " create table t("
            + " id bigint primary key , "
            + " content text)";
        removePkAndCheck(ddl);
    }

    @Test
    public void testRemovePK_2() {
        String ddl = " create table t("
            + " id bigint , "
            + " content text, "
            + " primary key(id))";
        removePkAndCheck(ddl);
    }

    @Test
    public void testAlterTableCharset() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        String ddl = "CREATE TABLE IF NOT EXISTS `all_type`\n"
            + "(`id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "`c_bit_1` bit(1) DEFAULT b'1',\n"
            + "`c_bit_8` bit(8) DEFAULT b'11111111',\n"
            + "`c_bit_16` bit(16) DEFAULT b'1111111111111111',\n"
            + "`c_bit_32` bit(32) DEFAULT b'11111111111111111111111111111111',\n"
            + "`c_bit_64` bit(64) DEFAULT b'1111111111111111111111111111111111111111111111111111111111111111',\n"
            + "`c_bit_hex_8` bit(8) DEFAULT 0xFF,\n"
            + "`c_bit_hex_16` bit(16) DEFAULT 0xFFFF,\n"
            + "`c_bit_hex_32` bit(32) DEFAULT 0xFFFFFFFF,\n"
            + "`c_bit_hex_64` bit(64) DEFAULT 0xFFFFFFFFFFFFFFFF,\n"
            + "`c_boolean` tinyint(1) DEFAULT true,\n"
            + "`c_boolean_2` tinyint(1) DEFAULT false,\n"
            + "`c_boolean_3` boolean DEFAULT false,\n"
            + "`c_tinyint_1` tinyint(1) DEFAULT 127,\n"
            + "`c_tinyint_4` tinyint(4) DEFAULT -128,\n"
            + "`c_tinyint_8` tinyint(8) DEFAULT -75,\n"
            + "`c_tinyint_3_un` tinyint(3) UNSIGNED DEFAULT 0,\n"
            + "`c_tinyint_8_un` tinyint(8) unsigned DEFAULT 255,\n"
            + "`c_tinyint_df_un` tinyint unsigned DEFAULT 255,\n"
            + "`c_tinyint_zerofill_un` tinyint(3) UNSIGNED ZEROFILL DEFAULT 255,\n"
            + "`c_smallint_1` smallint(1) DEFAULT 14497,\n"
            + "`c_smallint_2` smallint(2) DEFAULT 111,\n"
            + "`c_smallint_6` smallint(6) DEFAULT -32768,\n"
            + "`c_smallint_16` smallint(16) DEFAULT 32767,\n"
            + "`c_smallint_16_un` smallint(16) unsigned DEFAULT 65535,\n"
            + "`c_smallint_df_un` smallint unsigned DEFAULT 65535,\n"
            + "`c_smallint_zerofill_un` smallint(5) UNSIGNED ZEROFILL DEFAULT 65535,\n"
            + "`c_mediumint_1` mediumint(1) DEFAULT -8388608,\n"
            + "`c_mediumint_3` mediumint(3) DEFAULT 3456789,\n"
            + "`c_mediumint_9` mediumint(9) DEFAULT 8388607,\n"
            + "`c_mediumint_24` mediumint(24) DEFAULT -1845105,\n"
            + "`c_mediumint_8_un` mediumint(8) UNSIGNED DEFAULT 16777215,\n"
            + "`c_mediumint_24_un` mediumint(24) unsigned DEFAULT 16777215,\n"
            + "`c_mediumint_df_un` mediumint unsigned DEFAULT 16777215,\n"
            + "`c_mediumint_zerofill_un` mediumint(8) UNSIGNED ZEROFILL DEFAULT 7788,\n"
            + "`c_int_1` int(1) DEFAULT -2147483648,\n"
            + "`c_int_4` int(4) DEFAULT 872837,\n"
            + "`c_int_11` int(11) DEFAULT 2147483647,\n"
            + "`c_int_32` int(32) DEFAULT -2147483648,\n"
            + "`c_int_32_un` int(32) unsigned DEFAULT 4294967295,\n"
            + "`c_int_df_un` int unsigned DEFAULT 4294967295,\n"
            + "`c_int_zerofill_un` int(10) UNSIGNED ZEROFILL DEFAULT 4294967295,\n"
            + "`c_bigint_1` bigint(1) DEFAULT -816854218224922624,\n"
            + "`c_bigint_20` bigint(20) DEFAULT -9223372036854775808,\n"
            + "`c_bigint_64` bigint(64) DEFAULT 9223372036854775807,\n"
            + "`c_bigint_20_un` bigint(20) UNSIGNED DEFAULT 9223372036854775808,\n"
            + "`c_bigint_64_un` bigint(64) unsigned DEFAULT 18446744073709551615,\n"
            + "`c_bigint_df_un` bigint unsigned DEFAULT 18446744073709551615,\n"
            + "`c_bigint_zerofill_un` bigint(20) UNSIGNED ZEROFILL DEFAULT 1,\n"
            + "`c_tinyint_hex_1` tinyint(1) DEFAULT 0x3F,\n"
            + "`c_tinyint_hex_4` tinyint(4) DEFAULT 0x4F,\n"
            + "`c_tinyint_hex_8` tinyint(8) DEFAULT 0x5F,\n"
            + "`c_tinyint_hex_3_un` tinyint(3) UNSIGNED DEFAULT 0x2F,\n"
            + "`c_tinyint_hex_8_un` tinyint(8) unsigned DEFAULT 0x4E,\n"
            + "`c_tinyint_hex_zerofill_un` tinyint(3) UNSIGNED ZEROFILL DEFAULT 0x3F,\n"
            + "`c_smallint_hex_1` smallint(1) DEFAULT 0x2FFF,\n"
            + "`c_smallint_hex_2` smallint(2) DEFAULT 0x3FFF,\n"
            + "`c_smallint_hex_6` smallint(6) DEFAULT 0x4FEF,\n"
            + "`c_smallint_hex_16` smallint(16) DEFAULT 0x5EDF,\n"
            + "`c_smallint_hex_16_un` smallint(16) unsigned DEFAULT 0x7EDF,\n"
            + "`c_smallint_hex_zerofill_un` smallint(5) UNSIGNED ZEROFILL DEFAULT 0x8EFF,\n"
            + "`c_mediumint_hex_1` mediumint(1) DEFAULT 0x9EEE,\n"
            + "`c_mediumint_hex_3` mediumint(3) DEFAULT 0x7DDD,\n"
            + "`c_mediumint_hex_9` mediumint(9) DEFAULT 0x6CCC,\n"
            + "`c_mediumint_hex_24` mediumint(24) DEFAULT 0x5FCC,\n"
            + "`c_mediumint_hex_8_un` mediumint(8) UNSIGNED DEFAULT 0xFCFF,\n"
            + "`c_mediumint_hex_24_un` mediumint(24) unsigned DEFAULT 0xFCFF,\n"
            + "`c_mediumint_hex_zerofill_un` mediumint(8) UNSIGNED ZEROFILL DEFAULT 0xFFFF,\n"
            + "`c_int_hex_1` int(1) DEFAULT 0xFFFFFF,\n"
            + "`c_int_hex_4` int(4) DEFAULT 0xEFFFFF,\n"
            + "`c_int_hex_11` int(11) DEFAULT 0xEEFFFF,\n"
            + "`c_int_hex_32` int(32) DEFAULT 0xEEFFFF,\n"
            + "`c_int_hex_32_un` int(32) unsigned DEFAULT 0xFFEEFF,\n"
            + "`c_int_hex_zerofill_un` int(10) UNSIGNED ZEROFILL DEFAULT 0xFFEEFF,\n"
            + "`c_bigint_hex_1` bigint(1) DEFAULT 0xFEFFFFFFFEFFFF,\n"
            + "`c_bigint_hex_20` bigint(20) DEFAULT 0xFFFFFFFFFEFFFF,\n"
            + "`c_bigint_hex_64` bigint(64) DEFAULT 0xEFFFFFFFFEFFFF,\n"
            + "`c_bigint_hex_20_un` bigint(20) UNSIGNED DEFAULT 0xCFFFFFFFFEFFFF,\n"
            + "`c_bigint_hex_64_un` bigint(64) unsigned DEFAULT 0xAFFFFFFFFEFFFF,\n"
            + "`c_bigint_hex_zerofill_un` bigint(20) UNSIGNED ZEROFILL DEFAULT 0x1,\n"
            + "`c_decimal_hex` decimal DEFAULT 0xFFFFFF,\n"
            + "`c_decimal_hex_pr` decimal(10,3) DEFAULT 0xEFFF,\n"
            + "`c_decimal_hex_un` decimal(10,0) UNSIGNED DEFAULT 0xFFFF,\n"
            + "`c_float_hex` float DEFAULT 0xEFFF,\n"
            + "`c_float_hex_pr` float(10,3) DEFAULT 0xEEEE,\n"
            + "`c_float_hex_un` float(10,3) unsigned DEFAULT 0xFFEF,\n"
            + "`c_double_hex` double DEFAULT 0xFFFFEFFF,\n"
            + "`c_double_hex_pr` double(10,3) DEFAULT 0xFFFF,\n"
            + "`c_double_hex_un` double(10,3) unsigned DEFAULT 0xFFFF,\n"
            + "`c_tinyint_hex_x_1` tinyint(1) DEFAULT x'1F',\n"
            + "`c_tinyint_hex_x_4` tinyint(4) DEFAULT x'2F',\n"
            + "`c_tinyint_hex_x_8` tinyint(8) DEFAULT x'3F',\n"
            + "`c_tinyint_hex_x_3_un` tinyint(3) UNSIGNED DEFAULT x'FF',\n"
            + "`c_tinyint_hex_x_8_un` tinyint(8) unsigned DEFAULT x'EE',\n"
            + "`c_tinyint_hex_x_zerofill_un` tinyint(3) UNSIGNED ZEROFILL DEFAULT x'FF',\n"
            + "`c_smallint_hex_x_1` smallint(1) DEFAULT x'1FFF',\n"
            + "`c_smallint_hex_x_2` smallint(2) DEFAULT x'1FFF',\n"
            + "`c_smallint_hex_x_6` smallint(6) DEFAULT x'2FEF',\n"
            + "`c_smallint_hex_x_16` smallint(16) DEFAULT x'1EDF',\n"
            + "`c_smallint_hex_x_16_un` smallint(16) unsigned DEFAULT x'1EDF',\n"
            + "`c_smallint_hex_x_zerofill_un` smallint(5) UNSIGNED ZEROFILL DEFAULT x'5EFF',\n"
            + "`c_mediumint_hex_x_1` mediumint(1) DEFAULT x'4EEE',\n"
            + "`c_mediumint_hex_x_3` mediumint(3) DEFAULT x'3DDD',\n"
            + "`c_mediumint_hex_x_9` mediumint(9) DEFAULT x'2CCC',\n"
            + "`c_mediumint_hex_x_24` mediumint(24) DEFAULT x'1FCC',\n"
            + "`c_mediumint_hex_x_8_un` mediumint(8) UNSIGNED DEFAULT x'FAFF',\n"
            + "`c_mediumint_hex_x_24_un` mediumint(24) unsigned DEFAULT x'FAFF',\n"
            + "`c_mediumint_hex_x_zerofill_un` mediumint(8) UNSIGNED ZEROFILL DEFAULT x'FAFF',\n"
            + "`c_int_hex_x_1` int(1) DEFAULT x'FFFFFF',\n"
            + "`c_int_hex_x_4` int(4) DEFAULT x'FFFFFF',\n"
            + "`c_int_hex_x_11` int(11) DEFAULT x'FFFFFF',\n"
            + "`c_int_hex_x_32` int(32) DEFAULT x'FFFFFF',\n"
            + "`c_int_hex_x_32_un` int(32) unsigned DEFAULT x'FFFFFF',\n"
            + "`c_int_hex_x_zerofill_un` int(10) UNSIGNED ZEROFILL DEFAULT x'FFFFFF',\n"
            + "`c_bigint_hex_x_1` bigint(1) DEFAULT x'FFFFFFFFFFFFFF',\n"
            + "`c_bigint_hex_x_20` bigint(20) DEFAULT x'FFFFFFFFFFFFFF',\n"
            + "`c_bigint_hex_x_64` bigint(64) DEFAULT x'FFFFFFFFFFFFFF',\n"
            + "`c_bigint_hex_x_20_un` bigint(20) UNSIGNED DEFAULT x'FFFFFFFFFFFFFF',\n"
            + "`c_bigint_hex_x_64_un` bigint(64) unsigned DEFAULT x'FFFFFFFFFFFFFF',\n"
            + "`c_bigint_hex_x_zerofill_un` bigint(20) UNSIGNED ZEROFILL DEFAULT x'F1AB',\n"
            + "`c_decimal_hex_x` decimal DEFAULT x'FFFFFFFF',\n"
            + "`c_decimal_hex_x_pr` decimal(10,3) DEFAULT x'FFFF',\n"
            + "`c_decimal_hex_x_un` decimal(10,0) UNSIGNED DEFAULT x'FFFF',\n"
            + "`c_float_hex_x` float DEFAULT x'FFFF',\n"
            + "`c_float_hex_x_pr` float(10,3) DEFAULT x'EEEE',\n"
            + "`c_float_hex_x_un` float(10,3) unsigned DEFAULT x'FFFF',\n"
            + "`c_double_hex_x` double DEFAULT x'FFFFEFFF',\n"
            + "`c_double_hex_x_pr` double(10,3) DEFAULT x'FFFF',\n"
            + "`c_double_hex_x_un` double(10,3) unsigned DEFAULT x'FFFF',\n"
            + "`c_decimal` decimal DEFAULT -1613793319,\n"
            + "`c_decimal_pr` decimal(10,3) DEFAULT 1223077.292,\n"
            + "`c_decimal_un` decimal(10,0) UNSIGNED DEFAULT 10234273,\n"
            + "`c_numeric_df` numeric DEFAULT 10234273,\n"
            + "`c_numeric_10` numeric(10,5) DEFAULT 1,\n"
            + "`c_numeric_df_un` numeric UNSIGNED DEFAULT 10234273,\n"
            + "`c_numeric_un` numeric(10,6) UNSIGNED DEFAULT 1,\n"
            + "`c_dec_df` dec DEFAULT 10234273,\n"
            + "`c_dec_10` dec(10,5) DEFAULT 1,\n"
            + "`c_dec_df_un` dec UNSIGNED DEFAULT 10234273,\n"
            + "`c_dec_un` dec(10,6) UNSIGNED DEFAULT 1,\n"
            + "`c_float` float DEFAULT 9.1096275E8,\n"
            + "`c_float_pr` float(10,3) DEFAULT -5839673.5,\n"
            + "`c_float_un` float(10,3) unsigned DEFAULT 2648.644,\n"
            + "`c_double` double DEFAULT 4.334081673614155E9,\n"
            + "`c_double_pr` double(10,3) DEFAULT 6973286.176,\n"
            + "`c_double_un` double(10,3) unsigned DEFAULT 7630560.182,\n"
            + "`c_date` date DEFAULT '2019-02-15' COMMENT 'date',\n"
            + "`c_datetime` datetime DEFAULT '2019-02-15 14:54:41',\n"
            + "`c_datetime_ms` datetime(3) DEFAULT '2019-02-15 14:54:41.789',\n"
            + "`c_datetime_df` datetime DEFAULT CURRENT_TIMESTAMP,\n"
            + "`c_timestamp` timestamp DEFAULT CURRENT_TIMESTAMP,\n"
            + "`c_timestamp_2` timestamp DEFAULT '2020-12-29 12:27:30',\n"
            + "`c_time` time DEFAULT '20:12:46',\n"
            + "`c_time_3` time(3) DEFAULT '12:30',\n"
            + "`c_year` year DEFAULT '2019',\n"
            + "`c_year_4` year(4) DEFAULT '2029',\n"
            + "`c_char` char(50) DEFAULT 'sjdlfjsdljldfjsldfsd',\n"
            + "`c_char_df` char DEFAULT 'x',\n"
            + "`c_varchar` varchar(50) DEFAULT 'sjdlfjsldhgowuere',\n"
            + "`c_nchar` nchar(100) DEFAULT '你好',\n"
            + "`c_nvarchar` nvarchar(100) DEFAULT '北京',\n"
            + "`c_binary_df` binary DEFAULT 'x',\n"
            + "`c_binary` binary(200) DEFAULT 'qoeuroieshdfs',\n"
            + "`c_varbinary` varbinary(200) DEFAULT 'sdfjsljlewwfs',\n"
            + "`c_blob_tiny` tinyblob DEFAULT NULL,\n"
            + "`c_blob` blob DEFAULT NULL,\n"
            + "`c_blob_medium` mediumblob DEFAULT NULL,\n"
            + "`c_blob_long` longblob DEFAULT NULL,\n"
            + "`c_text_tiny` tinytext DEFAULT NULL,\n"
            + "`c_text` text DEFAULT NULL,\n"
            + "`c_text_medium` mediumtext DEFAULT NULL,\n"
            + "`c_text_long` longtext DEFAULT NULL,\n"
            + "`c_enum` enum('a','b','c') DEFAULT 'a',\n"
            + "`c_enum_2` enum('x-small', 'small', 'medium', 'large', 'x-large') DEFAULT 'small',\n"
            + "`c_set` set('a','b','c') DEFAULT 'a',\n"
            + "`c_json` json DEFAULT NULL,\n"
            + "`c_geo` geometry DEFAULT NULL,`c_idx` bigint not null default 100,PRIMARY KEY (`id`)) default charset gbk collate gbk_chinese_ci";

        applySql(memoryTableMeta, "cps", ddl);
        TableMeta tableMeta = memoryTableMeta.find("cps", "all_type");
        Assert.assertEquals("gbk", tableMeta.getCharset());
        String alter1 = "ALTER TABLE `all_type`\n"
            + "        DEFAULT CHARACTER SET=utf8 collate=utf8_general_ci,\n"
            + "        ADD COLUMN `src` char(3)  NOT NULL DEFAULT '1' COMMENT '来源 1 字节 2 微信 3 快应用 4 快手' AFTER `c_text`";
        applySql(memoryTableMeta, "cps", alter1);
        tableMeta = memoryTableMeta.find("cps", "all_type");
        Assert.assertEquals("utf8", tableMeta.getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_text").getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_text_medium").getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_enum_2").getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_enum").getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_set").getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_char_df").getCharset());
        Assert.assertEquals("utf8", tableMeta.getFieldMetaByName("c_nvarchar").getCharset());

        String alter2 = "ALTER TABLE `all_type`\n"
            + "        DEFAULT CHARACTER SET=utf8mb4 collate=utf8mb4_general_ci,\n"
            + "        ADD COLUMN `src2` char(3)  NOT NULL DEFAULT '1' COMMENT '来源 1 字节 2 微信 3 快应用 4 快手' AFTER `c_text`";
        applySql(memoryTableMeta, "cps", alter2);
        tableMeta = memoryTableMeta.find("cps", "all_type");
        Assert.assertEquals("utf8mb4", tableMeta.getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_text").getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_text_medium").getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_enum_2").getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_enum").getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_set").getCharset());
        Assert.assertEquals("gbk", tableMeta.getFieldMetaByName("c_char_df").getCharset());
        Assert.assertEquals("utf8", tableMeta.getFieldMetaByName("src").getCharset());
        Assert.assertEquals("utf8", tableMeta.getFieldMetaByName("c_nvarchar").getCharset());

        String alter3 = "ALTER TABLE `all_type` CONVERT TO CHARACTER SET latin1 collate latin1_swedish_ci";
        applySql(memoryTableMeta, "cps", alter3);
        tableMeta = memoryTableMeta.find("cps", "all_type");
        Assert.assertEquals("latin1", tableMeta.getCharset());
        for (TableMeta.FieldMeta tm : tableMeta.getFields()) {
            if (tm.getColumnName().equals("c_nvarchar") ||
                tm.getColumnName().equals("c_nchar")) {
                Assert.assertEquals("latin1", tm.getCharset());
            } else {
                Assert.assertEquals("latin1", tm.getCharset());
            }
        }

    }

    @Test
    public void testModifyColumnWithSequence_1() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta,
            "srzp_job_single",
            "create table `group_dissolution_log_lnsi` (\n"
                + " `id` int(11) not null auto_increment comment '',\n"
                + " `group_id` int(11) not null comment '',\n"
                + " `admin_id` int(11) not null default '0' comment '',\n"
                + " `type` tinyint(3) not null default '0' comment '',\n"
                + " `created_at` timestamp not null default current_timestamp comment '',\n"
                + " `updated_at` timestamp not null default current_timestamp on update current_timestamp comment '',\n"
                + " `deleted_at` timestamp null comment '',\n"
                + "  primary key (`id`),\n"
                + "  index `idx_group_id`(`group_id`)\n"
                + ") engine = innodb auto_increment = 1 default charset = `utf8mb4` default collate = `utf8mb4_bin` row_format = dynamic comment '?????'");

        //
        applySql(memoryTableMeta,
            "srzp_job_single",
            "alter table `group_dissolution_log_lnsi`\n"
                + " modify column `id` int(11) not null auto_increment comment '主键id' first");
        TableMeta tableMeta = memoryTableMeta.find("srzp_job_single", "group_dissolution_log_lnsi");
        Assert.assertEquals("id", tableMeta.getFields().get(0).getColumnName());

        //
        applySql(memoryTableMeta,
            "srzp_job_single",
            "alter table `group_dissolution_log_lnsi`\n"
                + " modify column `group_id` int(11) not null comment '群id' after `id`,\n"
                + " modify column `admin_id` int(11) not null default 0 comment 'a端操作人id' after `group_id`");
        Assert.assertEquals("group_id", tableMeta.getFields().get(1).getColumnName());
        Assert.assertEquals("admin_id", tableMeta.getFields().get(2).getColumnName());

        //
        applySql(memoryTableMeta,
            "srzp_job_single",
            "alter table `group_dissolution_log_lnsi`\n"
                + " modify column `type` tinyint(3) not null default 0 comment '类型 1:管理员直接解散 2:用户申请的解释' after `admin_id`,\n"
                + " modify column `created_at` timestamp(0) not null default current_timestamp(0) comment '创建时间' after `type`");
        Assert.assertEquals("type", tableMeta.getFields().get(3).getColumnName());
        Assert.assertEquals("created_at", tableMeta.getFields().get(4).getColumnName());

        //
        applySql(memoryTableMeta,
            "srzp_job_single",
            "alter table `group_dissolution_log_lnsi`\n"
                + " modify column `updated_at` timestamp(0) not null default current_timestamp(0) on update current_timestamp(0) comment '更新时间' after `created_at`");
        Assert.assertEquals("updated_at", tableMeta.getFields().get(5).getColumnName());

        //
        applySql(memoryTableMeta,
            "srzp_job_single",
            "alter table `group_dissolution_log_lnsi`\n"
                + " modify column `deleted_at` timestamp(0) null default null comment '删除时间' after `updated_at`");
        Assert.assertEquals("deleted_at", tableMeta.getFields().get(6).getColumnName());

    }

    @Test
    public void testModifyColumnWithSequence_2() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1",
            "create table `test_compat_yha2_7ajh_00002` (\n"
                + "  a int,\n"
                + "  b double,\n"
                + "  c varchar(10),\n"
                + "  d bigint,\n"
                + "  _drds_implicit_id_ bigint auto_increment,\n"
                + "  primary key (_drds_implicit_id_)\n"
                + ")");
        applySql(memoryTableMeta, "d1",
            "alter table `test_compat_yha2_7ajh_00002`\n"
                + "  modify column a int after b,\n"
                + "  drop column b,\n"
                + "  change column c b int,\n"
                + "  add column c int");
        memoryTableMeta.find("d1", "test_compat_yha2_7ajh_00002");

        TableMeta tableMeta = memoryTableMeta.find("d1", "test_compat_yha2_7ajh_00002");
        Assert.assertEquals(Lists.newArrayList("b", "a", "d", "_drds_implicit_id_", "c"),
            tableMeta.getFields().stream().map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()));
    }

    @Test
    public void testModifyColumnWithSequence_3() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1",
            "create table `t25` (\n"
                + "  a int,\n"
                + "  b double,\n"
                + "  c varchar(10),\n"
                + "  _drds_implicit_id_ bigint auto_increment,\n"
                + "  d bigint,\n"
                + "  primary key (_drds_implicit_id_)\n"
                + ")");
        String expectedDDL = "CREATE TABLE `t25` (\n"
            + "\tc varchar(10),\n"
            + "\tb double,\n"
            + "\ta int,\n"
            + "\t_drds_implicit_id_ bigint AUTO_INCREMENT,\n"
            + "\td bigint,\n"
            + "\tPRIMARY KEY (_drds_implicit_id_)\n"
            + ")";
        applySql(memoryTableMeta, "d1",
            "alter table t25 modify column a int after c,modify column c varchar(10) first");
        TableMeta tableMeta = memoryTableMeta.find("d1", "t25");
        Assert.assertEquals(Lists.newArrayList("c", "b", "a", "_drds_implicit_id_", "d"),
            tableMeta.getFields().stream().map(
                TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()));
    }

    private void removePkAndCheck(String createSql) {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1", createSql);

        TableMeta meta = memoryTableMeta.find("d1", "t");
        Assert.assertEquals(1, meta.getPrimaryFields().size());
        String dropPk = "alter table t drop primary key";
        applySql(memoryTableMeta, "d1", dropPk);

        meta = memoryTableMeta.find("d1", "t");
        Assert.assertTrue(meta.getPrimaryFields().isEmpty());
    }

    @Test
    public void testChangeCharsetText() {
        String ddl = "create table if not exists `t_random_1` (\n"
            + "\t`id` bigint(20) not null auto_increment,\n"
            + "\t`c_bit_1` bit(1) default b'1',\n"
            + "\t`c_bit_8` bit(8) default b'11111111',\n"
            + "\t`c_bit_16` bit(16) default b'1111111111111111',\n"
            + "\t`c_bit_32` bit(32) default b'11111111111111111111111111111111',\n"
            + "\t`c_bit_64` bit(64) default b'1111111111111111111111111111111111111111111111111111111111111111',\n"
            + "\t`c_bit_hex_8` bit(8) default 0xff,\n"
            + "\t`c_bit_hex_16` bit(16) default 0xffff,\n"
            + "\t`c_bit_hex_32` bit(32) default 0xffffffff,\n"
            + "\t`c_bit_hex_64` bit(64) default 0xffffffffffffffff,\n"
            + "\t`c_boolean` tinyint(1) default true,\n"
            + "\t`c_boolean_2` tinyint(1) default false,\n"
            + "\t`c_boolean_3` boolean default false,\n"
            + "\t`c_tinyint_1` tinyint(1) default 127,\n"
            + "\t`c_tinyint_4` tinyint(4) default -128,\n"
            + "\t`c_tinyint_8` tinyint(8) default -75,\n"
            + "\t`c_tinyint_3_un` tinyint(3) unsigned default 0,\n"
            + "\t`c_tinyint_8_un` tinyint(8) unsigned default 255,\n"
            + "\t`c_tinyint_df_un` tinyint unsigned default 255,\n"
            + "\t`c_tinyint_zerofill_un` tinyint(3) unsigned zerofill default 255,\n"
            + "\t`c_smallint_1` smallint(1) default 14497,\n"
            + "\t`c_smallint_2` smallint(2) default 111,\n"
            + "\t`c_smallint_6` smallint(6) default -32768,\n"
            + "\t`c_smallint_16` smallint(16) default 32767,\n"
            + "\t`c_smallint_16_un` smallint(16) unsigned default 65535,\n"
            + "\t`c_smallint_df_un` smallint unsigned default 65535,\n"
            + "\t`c_smallint_zerofill_un` smallint(5) unsigned zerofill default 65535,\n"
            + "\t`c_mediumint_1` mediumint(1) default -8388608,\n"
            + "\t`c_mediumint_3` mediumint(3) default 3456789,\n"
            + "\t`c_mediumint_9` mediumint(9) default 8388607,\n"
            + "\t`c_mediumint_24` mediumint(24) default -1845105,\n"
            + "\t`c_mediumint_8_un` mediumint(8) unsigned default 16777215,\n"
            + "\t`c_mediumint_24_un` mediumint(24) unsigned default 16777215,\n"
            + "\t`c_mediumint_df_un` mediumint unsigned default 16777215,\n"
            + "\t`c_mediumint_zerofill_un` mediumint(8) unsigned zerofill default 7788,\n"
            + "\t`c_int_1` int(1) default -2147483648,\n"
            + "\t`c_int_4` int(4) default 872837,\n"
            + "\t`c_int_11` int(11) default 2147483647,\n"
            + "\t`c_int_32` int(32) default -2147483648,\n"
            + "\t`c_int_32_un` int(32) unsigned default 4294967295,\n"
            + "\t`c_int_df_un` int unsigned default 4294967295,\n"
            + "\t`c_int_zerofill_un` int(10) unsigned zerofill default 4294967295,\n"
            + "\t`c_bigint_1` bigint(1) default -816854218224922624,\n"
            + "\t`c_bigint_20` bigint(20) default -9223372036854775808,\n"
            + "\t`c_bigint_64` bigint(64) default 9223372036854775807,\n"
            + "\t`c_bigint_20_un` bigint(20) unsigned default 9223372036854775808,\n"
            + "\t`c_bigint_64_un` bigint(64) unsigned default 18446744073709551615,\n"
            + "\t`c_bigint_df_un` bigint unsigned default 18446744073709551615,\n"
            + "\t`c_bigint_zerofill_un` bigint(20) unsigned zerofill default 1,\n"
            + "\t`c_tinyint_hex_1` tinyint(1) default 0x3f,\n"
            + "\t`c_tinyint_hex_4` tinyint(4) default 0x4f,\n"
            + "\t`c_tinyint_hex_8` tinyint(8) default 0x5f,\n"
            + "\t`c_tinyint_hex_3_un` tinyint(3) unsigned default 0x2f,\n"
            + "\t`c_tinyint_hex_8_un` tinyint(8) unsigned default 0x4e,\n"
            + "\t`c_tinyint_hex_zerofill_un` tinyint(3) unsigned zerofill default 0x3f,\n"
            + "\t`c_smallint_hex_1` smallint(1) default 0x2fff,\n"
            + "\t`c_smallint_hex_2` smallint(2) default 0x3fff,\n"
            + "\t`c_smallint_hex_6` smallint(6) default 0x4fef,\n"
            + "\t`c_smallint_hex_16` smallint(16) default 0x5edf,\n"
            + "\t`c_smallint_hex_16_un` smallint(16) unsigned default 0x7edf,\n"
            + "\t`c_smallint_hex_zerofill_un` smallint(5) unsigned zerofill default 0x8eff,\n"
            + "\t`c_mediumint_hex_1` mediumint(1) default 0x9eee,\n"
            + "\t`c_mediumint_hex_3` mediumint(3) default 0x7ddd,\n"
            + "\t`c_mediumint_hex_9` mediumint(9) default 0x6ccc,\n"
            + "\t`c_mediumint_hex_24` mediumint(24) default 0x5fcc,\n"
            + "\t`c_mediumint_hex_8_un` mediumint(8) unsigned default 0xfcff,\n"
            + "\t`c_mediumint_hex_24_un` mediumint(24) unsigned default 0xfcff,\n"
            + "\t`c_mediumint_hex_zerofill_un` mediumint(8) unsigned zerofill default 0xffff,\n"
            + "\t`c_int_hex_1` int(1) default 0xffffff,\n"
            + "\t`c_int_hex_4` int(4) default 0xefffff,\n"
            + "\t`c_int_hex_11` int(11) default 0xeeffff,\n"
            + "\t`c_int_hex_32` int(32) default 0xeeffff,\n"
            + "\t`c_int_hex_32_un` int(32) unsigned default 0xffeeff,\n"
            + "\t`c_int_hex_zerofill_un` int(10) unsigned zerofill default 0xffeeff,\n"
            + "\t`c_bigint_hex_1` bigint(1) default 0xfefffffffeffff,\n"
            + "\t`c_bigint_hex_20` bigint(20) default 0xfffffffffeffff,\n"
            + "\t`c_bigint_hex_64` bigint(64) default 0xeffffffffeffff,\n"
            + "\t`c_bigint_hex_20_un` bigint(20) unsigned default 0xcffffffffeffff,\n"
            + "\t`c_bigint_hex_64_un` bigint(64) unsigned default 0xaffffffffeffff,\n"
            + "\t`c_bigint_hex_zerofill_un` bigint(20) unsigned zerofill default 0x1,\n"
            + "\t`c_decimal_hex` decimal default 0xffffff,\n"
            + "\t`c_decimal_hex_pr` decimal(10, 3) default 0xefff,\n"
            + "\t`c_decimal_hex_un` decimal(10, 0) unsigned default 0xffff,\n"
            + "\t`c_float_hex` float default 0xefff,\n"
            + "\t`c_float_hex_pr` float(10, 3) default 0xeeee,\n"
            + "\t`c_float_hex_un` float(10, 3) unsigned default 0xffef,\n"
            + "\t`c_double_hex` double default 0xffffefff,\n"
            + "\t`c_double_hex_pr` double(10, 3) default 0xffff,\n"
            + "\t`c_double_hex_un` double(10, 3) unsigned default 0xffff,\n"
            + "\t`c_tinyint_hex_x_1` tinyint(1) default 0x1f,\n"
            + "\t`c_tinyint_hex_x_4` tinyint(4) default 0x2f,\n"
            + "\t`c_tinyint_hex_x_8` tinyint(8) default 0x3f,\n"
            + "\t`c_tinyint_hex_x_3_un` tinyint(3) unsigned default 0xff,\n"
            + "\t`c_tinyint_hex_x_8_un` tinyint(8) unsigned default 0xee,\n"
            + "\t`c_tinyint_hex_x_zerofill_un` tinyint(3) unsigned zerofill default 0xff,\n"
            + "\t`c_smallint_hex_x_1` smallint(1) default 0x1fff,\n"
            + "\t`c_smallint_hex_x_2` smallint(2) default 0x1fff,\n"
            + "\t`c_smallint_hex_x_6` smallint(6) default 0x2fef,\n"
            + "\t`c_smallint_hex_x_16` smallint(16) default 0x1edf,\n"
            + "\t`c_smallint_hex_x_16_un` smallint(16) unsigned default 0x1edf,\n"
            + "\t`c_smallint_hex_x_zerofill_un` smallint(5) unsigned zerofill default 0x5eff,\n"
            + "\t`c_mediumint_hex_x_1` mediumint(1) default 0x4eee,\n"
            + "\t`c_mediumint_hex_x_3` mediumint(3) default 0x3ddd,\n"
            + "\t`c_mediumint_hex_x_9` mediumint(9) default 0x2ccc,\n"
            + "\t`c_mediumint_hex_x_24` mediumint(24) default 0x1fcc,\n"
            + "\t`c_mediumint_hex_x_8_un` mediumint(8) unsigned default 0xfaff,\n"
            + "\t`c_mediumint_hex_x_24_un` mediumint(24) unsigned default 0xfaff,\n"
            + "\t`c_mediumint_hex_x_zerofill_un` mediumint(8) unsigned zerofill default 0xfaff,\n"
            + "\t`c_int_hex_x_1` int(1) default 0xffffff,\n"
            + "\t`c_int_hex_x_4` int(4) default 0xffffff,\n"
            + "\t`c_int_hex_x_11` int(11) default 0xffffff,\n"
            + "\t`c_int_hex_x_32` int(32) default 0xffffff,\n"
            + "\t`c_int_hex_x_32_un` int(32) unsigned default 0xffffff,\n"
            + "\t`c_int_hex_x_zerofill_un` int(10) unsigned zerofill default 0xffffff,\n"
            + "\t`c_bigint_hex_x_1` bigint(1) default 0xffffffffffffff,\n"
            + "\t`c_bigint_hex_x_20` bigint(20) default 0xffffffffffffff,\n"
            + "\t`c_bigint_hex_x_64` bigint(64) default 0xffffffffffffff,\n"
            + "\t`c_bigint_hex_x_20_un` bigint(20) unsigned default 0xffffffffffffff,\n"
            + "\t`c_bigint_hex_x_64_un` bigint(64) unsigned default 0xffffffffffffff,\n"
            + "\t`c_bigint_hex_x_zerofill_un` bigint(20) unsigned zerofill default 0xf1ab,\n"
            + "\t`c_decimal_hex_x` decimal default 0xffffffff,\n"
            + "\t`c_decimal_hex_x_pr` decimal(10, 3) default 0xffff,\n"
            + "\t`c_decimal_hex_x_un` decimal(10, 0) unsigned default 0xffff,\n"
            + "\t`c_float_hex_x` float default 0xffff,\n"
            + "\t`c_float_hex_x_pr` float(10, 3) default 0xeeee,\n"
            + "\t`c_float_hex_x_un` float(10, 3) unsigned default 0xffff,\n"
            + "\t`c_double_hex_x` double default 0xffffefff,\n"
            + "\t`c_double_hex_x_pr` double(10, 3) default 0xffff,\n"
            + "\t`c_double_hex_x_un` double(10, 3) unsigned default 0xffff,\n"
            + "\t`c_decimal` decimal default -1613793319,\n"
            + "\t`c_decimal_pr` decimal(10, 3) default 1223077.292,\n"
            + "\t`c_decimal_un` decimal(10, 0) unsigned default 10234273,\n"
            + "\t`c_numeric_df` numeric default 10234273,\n"
            + "\t`c_numeric_10` numeric(10, 5) default 1,\n"
            + "\t`c_numeric_df_un` numeric unsigned default 10234273,\n"
            + "\t`c_numeric_un` numeric(10, 6) unsigned default 1,\n"
            + "\t`c_dec_df` dec default 10234273,\n"
            + "\t`c_dec_10` dec(10, 5) default 1,\n"
            + "\t`c_dec_df_un` dec unsigned default 10234273,\n"
            + "\t`c_dec_un` dec(10, 6) unsigned default 1,\n"
            + "\t`c_float` float default 9.1096275e8,\n"
            + "\t`c_float_pr` float(10, 3) default -5839673.5,\n"
            + "\t`c_float_un` float(10, 3) unsigned default 2648.644,\n"
            + "\t`c_double` double default 4.334081673614155e9,\n"
            + "\t`c_double_pr` double(10, 3) default 6973286.176,\n"
            + "\t`c_double_un` double(10, 3) unsigned default 7630560.182,\n"
            + "\t`c_date` date default '2019-02-15' comment 'date',\n"
            + "\t`c_datetime` datetime default '2019-02-15 14:54:41',\n"
            + "\t`c_datetime_ms` datetime(3) default '2019-02-15 14:54:41.789',\n"
            + "\t`c_datetime_df` datetime default current_timestamp,\n"
            + "\t`c_timestamp` timestamp default current_timestamp,\n"
            + "\t`c_timestamp_2` timestamp default '2020-12-29 12:27:30',\n"
            + "\t`c_time` time default '20:12:46',\n"
            + "\t`c_time_3` time(3) default '12:30',\n"
            + "\t`c_year` year default '2019',\n"
            + "\t`c_year_4` year(4) default '2029',\n"
            + "\t`c_char` char(50) default 'sjdlfjsdljldfjsldfsd',\n"
            + "\t`c_char_df` char default 'x',\n"
            + "\t`c_varchar` varchar(50) default 'sjdlfjsldhgowuere',\n"
            + "\t`c_nchar` nchar(100) default '你好',\n"
            + "\t`c_nvarchar` nvarchar(100) default '北京',\n"
            + "\t`c_binary_df` binary default 'x',\n"
            + "\t`c_binary` binary(200) default 'qoeuroieshdfs',\n"
            + "\t`c_varbinary` varbinary(200) default 'sdfjsljlewwfs',\n"
            + "\t`c_blob_tiny` tinyblob default null,\n"
            + "\t`c_blob` blob default null,\n"
            + "\t`c_blob_medium` mediumblob default null,\n"
            + "\t`c_blob_long` longblob default null,\n"
            + "\t`c_text_tiny` tinytext default null,\n"
            + "\t`c_text` text default null,\n"
            + "\t`c_text_medium` mediumtext default null,\n"
            + "\t`c_text_long` longtext default null,\n"
            + "\t`c_enum` enum('a', 'b', 'c') default 'a',\n"
            + "\t`c_enum_2` enum('x-small', 'small', 'medium', 'large', 'x-large') default 'small',\n"
            + "\t`c_set` set('a', 'b', 'c') default 'a',\n"
            + "\t`c_json` json default null,\n"
            + "\t`c_geo` geometry default null,\n"
            + "\t`c_idx` bigint not null default 100,\n"
            + "\tprimary key (`id`)\n"
            + ") default charset = utf8mb4 default collate = utf8mb4_general_ci;\n"
            + "alter table `t_random_1` default character set utf8mb4;\n"
            + "alter table `t_random_1` convert to character set utf8;\n"
            + "alter table `t_random_1` default character set gbk;\n"
            + "alter table `t_random_1` convert to character set utf8mb4;\n"
            + "alter table `t_random_1` convert to character set utf8;\n"
            + "alter table `t_random_1` convert to character set gbk;\n"
            + "alter table `t_random_1` default character set utf8mb4;\n"
            + "alter table `t_random_1` convert to character set utf8;";

        String[] sql = ddl.split(";");
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        for (String s : sql) {
            applySql(memoryTableMeta, "db", s);
        }
        TableMeta tableMeta = memoryTableMeta.find("db", "t_random_1");
        Assert.assertEquals("MEDIUMTEXT", tableMeta.getFieldMetaByName("c_text_tiny").getColumnType());
    }

    @Test
    public void alterTableColumnWithoutCharset() {
        String createSql = "create table `t_order_x5gz_00001` (\n"
            + "\t`order_id` varchar(20) default null,\n"
            + "\t`seller_id` varchar(20) default null,\n"
            + "\tindex `auto_shard_key_order_id` using btree(`order_id`(20)),\n"
            + "\t_drds_implicit_id_ bigint auto_increment,\n"
            + "\tprimary key (_drds_implicit_id_)\n"
            + ") default charset = `utf8mb4` default collate = `utf8mb4_general_ci`";
        String alterSql1 = "alter table `t_order_x5gz_00001` default character set gbk";
        String alterSql2 = "alter table `t_order_x5gz_00001`\n"
            + "\tadd column tmp_id varchar(20) collate utf8mb4_general_ci comment 'test-test'";
        String alterSql3 =
            "alter table `t_order_x5gz_00001` modify column tmp_id varchar(20) collate LATIN1_GENERAL_CI";

        String alterSql4 =
            "alter table `t_order_x5gz_00001` change column tmp_id tmp_id_1 varchar(20) collate KOI8U_GENERAL_CI";
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "db", createSql);
        applySql(memoryTableMeta, "db", alterSql1);
        applySql(memoryTableMeta, "db", alterSql2);
        TableMeta tableMeta = memoryTableMeta.find("db", "t_order_x5gz_00001");
        Assert.assertEquals("gbk", tableMeta.getCharset());
        Assert.assertEquals("UTF8MB4", tableMeta.getFieldMetaByName("tmp_id").getCharset());
        applySql(memoryTableMeta, "db", alterSql3);
        tableMeta = memoryTableMeta.find("db", "t_order_x5gz_00001");
        Assert.assertEquals("LATIN1", tableMeta.getFieldMetaByName("tmp_id").getCharset());
        applySql(memoryTableMeta, "db", alterSql4);
        tableMeta = memoryTableMeta.find("db", "t_order_x5gz_00001");
        Assert.assertEquals("KOI8U", tableMeta.getFieldMetaByName("tmp_id_1").getCharset());
    }

    @Test
    public void testAlterMulCol() {
        String sql3 = "CREATE TABLE test_compat_2wzg (\n"
            + "  a int,\n"
            + "  b double,\n"
            + "  c varchar(10),\n"
            + "  d bigint,\n"
            + "  _drds_implicit_id_ bigint AUTO_INCREMENT,\n"
            + "  PRIMARY KEY (_drds_implicit_id_)\n"
            + ") DEFAULT CHARSET = `utf8mb4` DEFAULT COLLATE = `utf8mb4_general_ci`";
        String sql4 = "alter table test_compat_2wzg\n"
            + "  MODIFY COLUMN a int AFTER b,\n"
            + "  CHANGE COLUMN c b int,\n"
            + "  DROP COLUMN b,\n"
            + "  ADD COLUMN c int";
        MemoryTableMeta memoryTableMeta1 = newMemoryTableMeta();
        memoryTableMeta1.apply(null, "d1", sql3, null);
        memoryTableMeta1.apply(null, "d1", sql4, null);
        TableMeta tableMeta1 = memoryTableMeta1.find("d1", "test_compat_2wzg");

        List<TableMeta.FieldMeta> fieldMetaList = tableMeta1.getFields();
        Assert.assertEquals(5, fieldMetaList.size());
        Assert.assertEquals("b", fieldMetaList.get(0).getColumnName());
        Assert.assertEquals("a", fieldMetaList.get(1).getColumnName());
        Assert.assertEquals("d", fieldMetaList.get(2).getColumnName());
        Assert.assertEquals("_drds_implicit_id_", fieldMetaList.get(3).getColumnName());
        Assert.assertEquals("c", fieldMetaList.get(4).getColumnName());
    }
}
