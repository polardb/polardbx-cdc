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
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * description: test case for create table
 * author: ziyang.lb
 * create: 2023-08-20 17:33
 **/
@Slf4j
public class MemoryTableMetaTest_CreateTable extends MemoryTableMetaBase {

    @Test
    public void testCreateTableLike() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        String sql = "CREATE TABLE lbkkfddjvc (\n"
            + " id varchar(24),\n"
            + " k int \n) "
            + " DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_ci \n"
            + " PARTITION BY KEY (id, k)PARTITIONS 1";
        memoryTableMeta.apply(null, "test_db", sql, null);
        TableMeta tableMeta1 = memoryTableMeta.find("test_db", "lbkkfddjvc");

        memoryTableMeta.apply(null, "test_db",
            "create table tt like lbkkfddjvc  CHARACTER SET = utf8  COLLATE = utf8_general_ci", null);
        TableMeta tableMeta2 = memoryTableMeta.find("test_db", "tt");

        Assert.assertEquals(tableMeta1.getFieldMetaByName("id"), tableMeta2.getFieldMetaByName("id"));
        Assert.assertEquals(tableMeta1.getFieldMetaByName("k"), tableMeta2.getFieldMetaByName("k"));
        Assert.assertEquals(tableMeta1.getCharset(), tableMeta2.getCharset());
    }

    @Test
    public void testCreateTableLikeExtendCharset() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        String sql = "CREATE TABLE lbkkfddjvc (\n"
            + " id varchar(24),\n"
            + " k int \n) "
            + " DEFAULT CHARACTER SET = utf8 DEFAULT COLLATE = utf8_general_ci \n"
            + " PARTITION BY KEY (id, k)PARTITIONS 1";
        memoryTableMeta.apply(null, "test_db", sql, null);
        TableMeta tableMeta1 = memoryTableMeta.find("test_db", "lbkkfddjvc");

        memoryTableMeta.apply(null, "test_db",
            "create table tt like lbkkfddjvc ", null);
        TableMeta tableMeta2 = memoryTableMeta.find("test_db", "tt");

        Assert.assertEquals(tableMeta1.getFieldMetaByName("id"), tableMeta2.getFieldMetaByName("id"));
        Assert.assertEquals(tableMeta1.getFieldMetaByName("k"), tableMeta2.getFieldMetaByName("k"));
        Assert.assertEquals(tableMeta1.getCharset(), tableMeta2.getCharset());
    }

    @Test
    public void testCreateTableIdempotent() {
        final String create = "CREATE TABLE all_type ("
            + " type_bit bit(8) DEFAULT 0, "
            + " type_tinyint tinyint DEFAULT 0,"
            + " type_smallint smallint DEFAULT 0,"
            + " type_mediumint mediumint DEFAULT 0,"
            + " type_int int DEFAULT 0, type_integer "
            + " integer DEFAULT 0,"
            + " type_bigint bigint DEFAULT 0,"
            + " type_decimal decimal(3, 2) DEFAULT 1.1, "
            + " type_float float(3, 2) DEFAULT 1.1,"
            + " type_double double(3, 2) DEFAULT 1.1,"
            + " type_date date DEFAULT '2021-1-11 14:12:00',"
            + " type_datetime datetime(3) DEFAULT '2021-1-11 14:12:00',"
            + " type_timestamp timestamp(3) DEFAULT '2021-1-11 14:12:00',"
            + " type_time time(3) DEFAULT '14:12:00',"
            + " type_year year DEFAULT '2021',"
            + " type_char char(10) CHARACTER SET utf8 DEFAULT '你好',"
            + " type_varchar varchar(10) CHARACTER SET gbk DEFAULT '你好',"
            + " type_binary binary(10) DEFAULT '你好',"
            + " type_varbinary varbinary(10) DEFAULT '你好',"
            + " type_blob blob(10) DEFAULT NULL,"
            + " type_text text DEFAULT NULL,"
            + " type_enum enum('a', 'b', 'c') DEFAULT 'a',"
            + " type_set set('a', 'b', 'c', 'd') DEFAULT 'b',"
            + " type_pt POINT DEFAULT NULL )";

        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(log, false);
        memoryTableMeta.apply(new BinlogPosition("", ""), "d1", create, null);
        System.out.println(memoryTableMeta.find("d1", "all_type").getFieldMetaByName("type_char"));

        memoryTableMeta.apply(new BinlogPosition("", ""), "d1", create, null);
        System.out.println(memoryTableMeta.find("d1", "all_type").getFieldMetaByName("type_char"));

        memoryTableMeta.apply(new BinlogPosition("", ""), "d1", create, null);
        System.out.println(memoryTableMeta.find("d1", "all_type").getFieldMetaByName("type_char"));

        memoryTableMeta.apply(new BinlogPosition("", ""), "d1", create, null);
        System.out.println(memoryTableMeta.find("d1", "all_type").getFieldMetaByName("type_char"));

        memoryTableMeta.apply(null, "d1", "create table t1 (id int)", null);
        Assert.assertEquals(Sets.newHashSet("id"), memoryTableMeta.find("d1", "t1").getFields().stream().map(
            TableMeta.FieldMeta::getColumnName).collect(Collectors.toSet()));

        memoryTableMeta.apply(null, "d1", "create table t1 (id int,name varchar(16))", null);
        Assert.assertEquals(Sets.newHashSet("id", "name"), memoryTableMeta.find("d1", "t1").getFields().stream().map(
            TableMeta.FieldMeta::getColumnName).collect(Collectors.toSet()));
    }

    @Test
    public void testGeneratedColumns() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        applySql(memoryTableMeta, "d1", "create table if not exists gen_col_create_tbl (\n"
            + "  a int,\n"
            + "  b int,\n"
            + "  e int GENERATED ALWAYS AS (a - b) LOGICAL,\n"
            + "  c int GENERATED ALWAYS AS (a) LOGICAL,\n"
            + "  d int GENERATED ALWAYS AS (a + c) LOGICAL,\n"
            + "  CLUSTERED INDEX gen_col_create_tbl_gsi(c) DBPARTITION BY hash(c)\n"
            + ")");
        applySql(memoryTableMeta, "d1", "alter table gen_col_create_tbl add column g bigint as (id+1) stored");
        TableMeta tableMeta = memoryTableMeta.find("d1", "gen_col_create_tbl");

        Assert.assertFalse(tableMeta.getFieldMetaByName("a").isGenerated());
        Assert.assertFalse(tableMeta.getFieldMetaByName("b").isGenerated());
        Assert.assertTrue(tableMeta.getFieldMetaByName("e").isGenerated());
        Assert.assertTrue(tableMeta.getFieldMetaByName("d").isGenerated());
        Assert.assertTrue(tableMeta.getFieldMetaByName("d").isGenerated());
        Assert.assertTrue(tableMeta.getFieldMetaByName("g").isGenerated());
    }

    @Test
    public void testIsSchemaExists() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(log, false);
        boolean result = memoryTableMeta.isSchemaExists("aaa");
        Assert.assertFalse(result);

        memoryTableMeta.apply(null, "aaa", "create table t1(id int)", null);
        boolean result2 = memoryTableMeta.isSchemaExists("aaa");
        Assert.assertTrue(result2);
        TableMeta tableMeta1 = memoryTableMeta.find("aaa", "t1");
        Assert.assertNotNull(tableMeta1);

        memoryTableMeta.apply(null, "aaa", "drop database aaa", null);
        boolean result3 = memoryTableMeta.isSchemaExists("aaa");
        Assert.assertFalse(result3);
        Assert.assertNull(memoryTableMeta.find("aaa", "t1"));

        memoryTableMeta.apply(null, "aaa", "create table t2(id int)", null);
        boolean result4 = memoryTableMeta.isSchemaExists("aaa");
        Assert.assertTrue(result4);
        TableMeta tableMeta2 = memoryTableMeta.find("aaa", "t2");
        Assert.assertNotNull(tableMeta2);

        memoryTableMeta.apply(null, "aaa", "create database aaa", null);
        boolean result5 = memoryTableMeta.isSchemaExists("aaa");
        Assert.assertTrue(result5);
        Assert.assertNotNull(tableMeta2);
    }

    @Test
    public void testCreateTableWithPartition() {
        String ddl = "CREATE TABLE `t_order_2_scri_00000` (\n"
            + "  `id` bigint(20) DEFAULT NULL,\n"
            + "  `gmt_modified` datetime NOT NULL,\n"
            + "  PRIMARY KEY (`gmt_modified`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4\n"
            + "/*!50500 PARTITION BY RANGE  COLUMNS(gmt_modified)\n"
            + "(PARTITION p20210101 VALUES LESS THAN ('2021-01-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20210201 VALUES LESS THAN ('2021-02-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20210301 VALUES LESS THAN ('2021-03-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20210401 VALUES LESS THAN ('2021-04-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20210501 VALUES LESS THAN ('2021-05-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20210601 VALUES LESS THAN ('2021-06-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20210701 VALUES LESS THAN ('2021-07-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20210801 VALUES LESS THAN ('2021-08-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20210901 VALUES LESS THAN ('2021-09-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20211001 VALUES LESS THAN ('2021-10-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20211101 VALUES LESS THAN ('2021-11-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20211201 VALUES LESS THAN ('2021-12-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20220101 VALUES LESS THAN ('2022-01-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20220201 VALUES LESS THAN ('2022-02-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20220301 VALUES LESS THAN ('2022-03-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20220401 VALUES LESS THAN ('2022-04-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION p20220501 VALUES LESS THAN ('2022-05-01') COMMENT = '' ENGINE = InnoDB,\n"
            + " PARTITION pmax VALUES LESS THAN (MAXVALUE) COMMENT = '' ENGINE = InnoDB) */";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "test_db", ddl, null);
        memoryTableMeta.find("test_db", "t_order_2_scri_00000");
    }

    @Test
    public void testMulitCreate() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "test",
            "create table t1(id int,name varchar(10),primary key(id));"
                + "create table t2(id int,name varchar(10),primary key(id))",
            null);
        TableMeta tm1 = memoryTableMeta.find("test", "t1");
        TableMeta tm2 = memoryTableMeta.find("test", "t2");
        Assert.assertNotNull(tm1);
        Assert.assertNotNull(tm2);
    }

    @Test
    public void testSubPartitionDdl() {
        String s = "CREATE TABLE `ts` (\n"
            + "        `id` int(11) DEFAULT NULL,\n"
            + "        `purchased` date DEFAULT NULL,\n"
            + "        KEY `auto_shard_key_purchased` USING BTREE (`purchased`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4\n"
            + "PARTITION BY RANGE(YEAR(`purchased`))\n"
            + "SUBPARTITION BY HASH(TO_DAYS(`purchased`))\n"
            + "SUBPARTITIONS 2\n"
            + "(PARTITION `p0` VALUES LESS THAN (1990),\n"
            + " PARTITION `p1` VALUES LESS THAN (2000),\n"
            + " PARTITION `p2` VALUES LESS THAN (MAXVALUE))";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "d1", s, null);
        Map<String, String> snapshot = memoryTableMeta.snapshot();
        String ddl = snapshot.get("d1");
        SQLStatement st = SQLUtils.parseSQLStatement(ddl);
        Assert.assertEquals(StringUtils.trim(ddl), StringUtils.trim(st.toString()));
    }

    @Test
    public void testAllTypeFields() {
        String s = "CREATE TABLE IF NOT EXISTS `t_random_instant_check_1` (\n"
            + "\t`id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "\t`c_bit_1` bit(1) DEFAULT b'1',\n"
            + "\t`c_bit_8` bit(8) DEFAULT b'11111111',\n"
            + "\t`c_bit_16` bit(16) DEFAULT b'1111111111111111',\n"
            + "\t`c_bit_32` bit(32) DEFAULT b'11111111111111111111111111111111',\n"
            + "\t`c_bit_64` bit(64) DEFAULT b'1111111111111111111111111111111111111111111111111111111111111111',\n"
            + "\t`c_bit_hex_8` bit(8) DEFAULT 0xFF,\n"
            + "\t`c_bit_hex_16` bit(16) DEFAULT 0xFFFF,\n"
            + "\t`c_bit_hex_32` bit(32) DEFAULT 0xFFFFFFFF,\n"
            + "\t`c_bit_hex_64` bit(64) DEFAULT 0xFFFFFFFFFFFFFFFF,\n"
            + "\t`c_boolean` tinyint(1) DEFAULT true,\n"
            + "\t`c_boolean_2` tinyint(1) DEFAULT false,\n"
            + "\t`c_boolean_3` boolean DEFAULT false,\n"
            + "\t`c_tinyint_1` tinyint(1) DEFAULT 127,\n"
            + "\t`c_tinyint_4` tinyint(4) DEFAULT -128,\n"
            + "\t`c_tinyint_8` tinyint(8) DEFAULT -75,\n"
            + "\t`c_tinyint_3_un` tinyint(3) UNSIGNED DEFAULT 0,\n"
            + "\t`c_tinyint_8_un` tinyint(8) UNSIGNED DEFAULT 255,\n"
            + "\t`c_tinyint_df_un` tinyint UNSIGNED DEFAULT 255,\n"
            + "\t`c_tinyint_zerofill_un` tinyint(3) UNSIGNED ZEROFILL DEFAULT 255,\n"
            + "\t`c_smallint_1` smallint(1) DEFAULT 14497,\n"
            + "\t`c_smallint_2` smallint(2) DEFAULT 111,\n"
            + "\t`c_smallint_6` smallint(6) DEFAULT -32768,\n"
            + "\t`c_smallint_16` smallint(16) DEFAULT 32767,\n"
            + "\t`c_smallint_16_un` smallint(16) UNSIGNED DEFAULT 65535,\n"
            + "\t`c_smallint_df_un` smallint UNSIGNED DEFAULT 65535,\n"
            + "\t`c_smallint_zerofill_un` smallint(5) UNSIGNED ZEROFILL DEFAULT 65535,\n"
            + "\t`c_mediumint_1` mediumint(1) DEFAULT -8388608,\n"
            + "\t`c_mediumint_3` mediumint(3) DEFAULT 3456789,\n"
            + "\t`c_mediumint_9` mediumint(9) DEFAULT 8388607,\n"
            + "\t`c_mediumint_24` mediumint(24) DEFAULT -1845105,\n"
            + "\t`c_mediumint_8_un` mediumint(8) UNSIGNED DEFAULT 16777215,\n"
            + "\t`c_mediumint_24_un` mediumint(24) UNSIGNED DEFAULT 16777215,\n"
            + "\t`c_mediumint_df_un` mediumint UNSIGNED DEFAULT 16777215,\n"
            + "\t`c_mediumint_zerofill_un` mediumint(8) UNSIGNED ZEROFILL DEFAULT 7788,\n"
            + "\t`c_int_1` int(1) DEFAULT -2147483648,\n"
            + "\t`c_int_4` int(4) DEFAULT 872837,\n"
            + "\t`c_int_11` int(11) DEFAULT 2147483647,\n"
            + "\t`c_int_32` int(32) DEFAULT -2147483648,\n"
            + "\t`c_int_32_un` int(32) UNSIGNED DEFAULT 4294967295,\n"
            + "\t`c_int_df_un` int UNSIGNED DEFAULT 4294967295,\n"
            + "\t`c_int_zerofill_un` int(10) UNSIGNED ZEROFILL DEFAULT 4294967295,\n"
            + "\t`c_bigint_1` bigint(1) DEFAULT -816854218224922624,\n"
            + "\t`c_bigint_20` bigint(20) DEFAULT -9223372036854775808,\n"
            + "\t`c_bigint_64` bigint(64) DEFAULT 9223372036854775807,\n"
            + "\t`c_bigint_20_un` bigint(20) UNSIGNED DEFAULT 9223372036854775808,\n"
            + "\t`c_bigint_64_un` bigint(64) UNSIGNED DEFAULT 18446744073709551615,\n"
            + "\t`c_bigint_df_un` bigint UNSIGNED DEFAULT 18446744073709551615,\n"
            + "\t`c_bigint_zerofill_un` bigint(20) UNSIGNED ZEROFILL DEFAULT 1,\n"
            + "\t`c_tinyint_hex_1` tinyint(1) DEFAULT 0x3F,\n"
            + "\t`c_tinyint_hex_4` tinyint(4) DEFAULT 0x4F,\n"
            + "\t`c_tinyint_hex_8` tinyint(8) DEFAULT 0x5F,\n"
            + "\t`c_tinyint_hex_3_un` tinyint(3) UNSIGNED DEFAULT 0x2F,\n"
            + "\t`c_tinyint_hex_8_un` tinyint(8) UNSIGNED DEFAULT 0x4E,\n"
            + "\t`c_tinyint_hex_zerofill_un` tinyint(3) UNSIGNED ZEROFILL DEFAULT 0x3F,\n"
            + "\t`c_smallint_hex_1` smallint(1) DEFAULT 0x2FFF,\n"
            + "\t`c_smallint_hex_2` smallint(2) DEFAULT 0x3FFF,\n"
            + "\t`c_smallint_hex_6` smallint(6) DEFAULT 0x4FEF,\n"
            + "\t`c_smallint_hex_16` smallint(16) DEFAULT 0x5EDF,\n"
            + "\t`c_smallint_hex_16_un` smallint(16) UNSIGNED DEFAULT 0x7EDF,\n"
            + "\t`c_smallint_hex_zerofill_un` smallint(5) UNSIGNED ZEROFILL DEFAULT 0x8EFF,\n"
            + "\t`c_mediumint_hex_1` mediumint(1) DEFAULT 0x9EEE,\n"
            + "\t`c_mediumint_hex_3` mediumint(3) DEFAULT 0x7DDD,\n"
            + "\t`c_mediumint_hex_9` mediumint(9) DEFAULT 0x6CCC,\n"
            + "\t`c_mediumint_hex_24` mediumint(24) DEFAULT 0x5FCC,\n"
            + "\t`c_mediumint_hex_8_un` mediumint(8) UNSIGNED DEFAULT 0xFCFF,\n"
            + "\t`c_mediumint_hex_24_un` mediumint(24) UNSIGNED DEFAULT 0xFCFF,\n"
            + "\t`c_mediumint_hex_zerofill_un` mediumint(8) UNSIGNED ZEROFILL DEFAULT 0xFFFF,\n"
            + "\t`c_int_hex_1` int(1) DEFAULT 0xFFFFFF,\n"
            + "\t`c_int_hex_4` int(4) DEFAULT 0xEFFFFF,\n"
            + "\t`c_int_hex_11` int(11) DEFAULT 0xEEFFFF,\n"
            + "\t`c_int_hex_32` int(32) DEFAULT 0xEEFFFF,\n"
            + "\t`c_int_hex_32_un` int(32) UNSIGNED DEFAULT 0xFFEEFF,\n"
            + "\t`c_int_hex_zerofill_un` int(10) UNSIGNED ZEROFILL DEFAULT 0xFFEEFF,\n"
            + "\t`c_bigint_hex_1` bigint(1) DEFAULT 0xFEFFFFFFFEFFFF,\n"
            + "\t`c_bigint_hex_20` bigint(20) DEFAULT 0xFFFFFFFFFEFFFF,\n"
            + "\t`c_bigint_hex_64` bigint(64) DEFAULT 0xEFFFFFFFFEFFFF,\n"
            + "\t`c_bigint_hex_20_un` bigint(20) UNSIGNED DEFAULT 0xCFFFFFFFFEFFFF,\n"
            + "\t`c_bigint_hex_64_un` bigint(64) UNSIGNED DEFAULT 0xAFFFFFFFFEFFFF,\n"
            + "\t`c_bigint_hex_zerofill_un` bigint(20) UNSIGNED ZEROFILL DEFAULT 0x1,\n"
            + "\t`c_decimal_hex` decimal DEFAULT 0xFFFFFF,\n"
            + "\t`c_decimal_hex_pr` decimal(10, 3) DEFAULT 0xEFFF,\n"
            + "\t`c_decimal_hex_un` decimal(10, 0) UNSIGNED DEFAULT 0xFFFF,\n"
            + "\t`c_float_hex` float DEFAULT 0xEFFF,\n"
            + "\t`c_float_hex_pr` float(10, 3) DEFAULT 0xEEEE,\n"
            + "\t`c_float_hex_un` float(10, 3) UNSIGNED DEFAULT 0xFFEF,\n"
            + "\t`c_double_hex` double DEFAULT 0xFFFFEFFF,\n"
            + "\t`c_double_hex_pr` double(10, 3) DEFAULT 0xFFFF,\n"
            + "\t`c_double_hex_un` double(10, 3) UNSIGNED DEFAULT 0xFFFF,\n"
            + "\t`c_tinyint_hex_x_1` tinyint(1) DEFAULT 0x1F,\n"
            + "\t`c_tinyint_hex_x_4` tinyint(4) DEFAULT 0x2F,\n"
            + "\t`c_tinyint_hex_x_8` tinyint(8) DEFAULT 0x3F,\n"
            + "\t`c_tinyint_hex_x_3_un` tinyint(3) UNSIGNED DEFAULT 0xFF,\n"
            + "\t`c_tinyint_hex_x_8_un` tinyint(8) UNSIGNED DEFAULT 0xEE,\n"
            + "\t`c_tinyint_hex_x_zerofill_un` tinyint(3) UNSIGNED ZEROFILL DEFAULT 0xFF,\n"
            + "\t`c_smallint_hex_x_1` smallint(1) DEFAULT 0x1FFF,\n"
            + "\t`c_smallint_hex_x_2` smallint(2) DEFAULT 0x1FFF,\n"
            + "\t`c_smallint_hex_x_6` smallint(6) DEFAULT 0x2FEF,\n"
            + "\t`c_smallint_hex_x_16` smallint(16) DEFAULT 0x1EDF,\n"
            + "\t`c_smallint_hex_x_16_un` smallint(16) UNSIGNED DEFAULT 0x1EDF,\n"
            + "\t`c_smallint_hex_x_zerofill_un` smallint(5) UNSIGNED ZEROFILL DEFAULT 0x5EFF,\n"
            + "\t`c_mediumint_hex_x_1` mediumint(1) DEFAULT 0x4EEE,\n"
            + "\t`c_mediumint_hex_x_3` mediumint(3) DEFAULT 0x3DDD,\n"
            + "\t`c_mediumint_hex_x_9` mediumint(9) DEFAULT 0x2CCC,\n"
            + "\t`c_mediumint_hex_x_24` mediumint(24) DEFAULT 0x1FCC,\n"
            + "\t`c_mediumint_hex_x_8_un` mediumint(8) UNSIGNED DEFAULT 0xFAFF,\n"
            + "\t`c_mediumint_hex_x_24_un` mediumint(24) UNSIGNED DEFAULT 0xFAFF,\n"
            + "\t`c_mediumint_hex_x_zerofill_un` mediumint(8) UNSIGNED ZEROFILL DEFAULT 0xFAFF,\n"
            + "\t`c_int_hex_x_1` int(1) DEFAULT 0xFFFFFF,\n"
            + "\t`c_int_hex_x_4` int(4) DEFAULT 0xFFFFFF,\n"
            + "\t`c_int_hex_x_11` int(11) DEFAULT 0xFFFFFF,\n"
            + "\t`c_int_hex_x_32` int(32) DEFAULT 0xFFFFFF,\n"
            + "\t`c_int_hex_x_32_un` int(32) UNSIGNED DEFAULT 0xFFFFFF,\n"
            + "\t`c_int_hex_x_zerofill_un` int(10) UNSIGNED ZEROFILL DEFAULT 0xFFFFFF,\n"
            + "\t`c_bigint_hex_x_1` bigint(1) DEFAULT 0xFFFFFFFFFFFFFF,\n"
            + "\t`c_bigint_hex_x_20` bigint(20) DEFAULT 0xFFFFFFFFFFFFFF,\n"
            + "\t`c_bigint_hex_x_64` bigint(64) DEFAULT 0xFFFFFFFFFFFFFF,\n"
            + "\t`c_bigint_hex_x_20_un` bigint(20) UNSIGNED DEFAULT 0xFFFFFFFFFFFFFF,\n"
            + "\t`c_bigint_hex_x_64_un` bigint(64) UNSIGNED DEFAULT 0xFFFFFFFFFFFFFF,\n"
            + "\t`c_bigint_hex_x_zerofill_un` bigint(20) UNSIGNED ZEROFILL DEFAULT 0xF1AB,\n"
            + "\t`c_decimal_hex_x` decimal DEFAULT 0xFFFFFFFF,\n"
            + "\t`c_decimal_hex_x_pr` decimal(10, 3) DEFAULT 0xFFFF,\n"
            + "\t`c_decimal_hex_x_un` decimal(10, 0) UNSIGNED DEFAULT 0xFFFF,\n"
            + "\t`c_float_hex_x` float DEFAULT 0xFFFF,\n"
            + "\t`c_float_hex_x_pr` float(10, 3) DEFAULT 0xEEEE,\n"
            + "\t`c_float_hex_x_un` float(10, 3) UNSIGNED DEFAULT 0xFFFF,\n"
            + "\t`c_double_hex_x` double DEFAULT 0xFFFFEFFF,\n"
            + "\t`c_double_hex_x_pr` double(10, 3) DEFAULT 0xFFFF,\n"
            + "\t`c_double_hex_x_un` double(10, 3) UNSIGNED DEFAULT 0xFFFF,\n"
            + "\t`c_decimal` decimal DEFAULT -1613793319,\n"
            + "\t`c_decimal_pr` decimal(10, 3) DEFAULT 1223077.292,\n"
            + "\t`c_decimal_un` decimal(10, 0) UNSIGNED DEFAULT 10234273,\n"
            + "\t`c_numeric_df` numeric DEFAULT 10234273,\n"
            + "\t`c_numeric_10` numeric(10, 5) DEFAULT 1,\n"
            + "\t`c_numeric_df_un` numeric UNSIGNED DEFAULT 10234273,\n"
            + "\t`c_numeric_un` numeric(10, 6) UNSIGNED DEFAULT 1,\n"
            + "\t`c_dec_df` dec DEFAULT 10234273,\n"
            + "\t`c_dec_10` dec(10, 5) DEFAULT 1,\n"
            + "\t`c_dec_df_un` dec UNSIGNED DEFAULT 10234273,\n"
            + "\t`c_dec_un` dec(10, 6) UNSIGNED DEFAULT 1,\n"
            + "\t`c_float` float DEFAULT 9.1096275E8,\n"
            + "\t`c_float_pr` float(10, 3) DEFAULT -5839673.5,\n"
            + "\t`c_float_un` float(10, 3) UNSIGNED DEFAULT 2648.644,\n"
            + "\t`c_double` double DEFAULT 4.334081673614155E9,\n"
            + "\t`c_double_pr` double(10, 3) DEFAULT 6973286.176,\n"
            + "\t`c_double_un` double(10, 3) UNSIGNED DEFAULT 7630560.182,\n"
            + "\t`c_date` date DEFAULT '2019-02-15' COMMENT 'date',\n"
            + "\t`c_datetime` datetime DEFAULT '2019-02-15 14:54:41',\n"
            + "\t`c_datetime_ms` datetime(3) DEFAULT '2019-02-15 14:54:41.789',\n"
            + "\t`c_datetime_df` datetime DEFAULT CURRENT_TIMESTAMP,\n"
            + "\t`c_timestamp` timestamp DEFAULT CURRENT_TIMESTAMP,\n"
            + "\t`c_timestamp_2` timestamp DEFAULT '2020-12-29 12:27:30',\n"
            + "\t`c_time` time DEFAULT '20:12:46',\n"
            + "\t`c_time_3` time(3) DEFAULT '12:30',\n"
            + "\t`c_year` year DEFAULT '2019',\n"
            + "\t`c_year_4` year(4) DEFAULT '2029',\n"
            + "\t`c_char` char(50) DEFAULT 'sjdlfjsdljldfjsldfsd',\n"
            + "\t`c_char_df` char DEFAULT 'x',\n"
            + "\t`c_varchar` varchar(50) DEFAULT 'sjdlfjsldhgowuere',\n"
            + "\t`c_nchar` nchar(100) DEFAULT '你好',\n"
            + "\t`c_nvarchar` nvarchar(100) DEFAULT '北京',\n"
            + "\t`c_binary_df` binary DEFAULT 'x',\n"
            + "\t`c_binary` binary(200) DEFAULT 'qoeuroieshdfs',\n"
            + "\t`c_varbinary` varbinary(200) DEFAULT 'sdfjsljlewwfs',\n"
            + "\t`c_blob_tiny` tinyblob DEFAULT NULL,\n"
            + "\t`c_blob` blob DEFAULT NULL,\n"
            + "\t`c_blob_medium` mediumblob DEFAULT NULL,\n"
            + "\t`c_blob_long` longblob DEFAULT NULL,\n"
            + "\t`c_text_tiny` tinytext DEFAULT NULL,\n"
            + "\t`c_text` text DEFAULT NULL,\n"
            + "\t`c_text_medium` mediumtext DEFAULT NULL,\n"
            + "\t`c_text_long` longtext DEFAULT NULL,\n"
            + "\t`c_enum` enum('a', 'b', 'c') DEFAULT 'a',\n"
            + "\t`c_enum_2` enum('x-small', 'small', 'medium', 'large', 'x-large') DEFAULT 'small',\n"
            + "\t`c_set` set('a', 'b', 'c') DEFAULT 'a',\n"
            + "\t`c_json` json DEFAULT NULL,\n"
            + "\t`c_geo` geometry DEFAULT NULL,\n"
            + "\t`c_idx` bigint NOT NULL DEFAULT 100,\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") DEFAULT CHARSET = `utf8mb4` DEFAULT COLLATE = `utf8mb4_general_ci`\n"
            + "DBPARTITION BY hash(`id`)\n"
            + "TBPARTITION BY hash(`id`) TBPARTITIONS 3";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "d1", s, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "t_random_instant_check_1");
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("id").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_boolean").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_boolean_2").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_boolean_3").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_tinyint_1").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_tinyint_4").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_tinyint_8").getColumnType());
        Assert.assertEquals("tinyint unsigned", tableMeta.getFieldMetaByName("c_tinyint_3_un").getColumnType());
        Assert.assertEquals("tinyint unsigned", tableMeta.getFieldMetaByName("c_tinyint_8_un").getColumnType());
        Assert.assertEquals("tinyint unsigned", tableMeta.getFieldMetaByName("c_tinyint_df_un").getColumnType());
        Assert.assertEquals("tinyint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_tinyint_zerofill_un").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_1").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_2").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_6").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_16").getColumnType());
        Assert.assertEquals("smallint unsigned", tableMeta.getFieldMetaByName("c_smallint_16_un").getColumnType());
        Assert.assertEquals("smallint unsigned", tableMeta.getFieldMetaByName("c_smallint_df_un").getColumnType());
        Assert.assertEquals("smallint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_smallint_zerofill_un").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_1").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_3").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_9").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_24").getColumnType());
        Assert.assertEquals("mediumint unsigned", tableMeta.getFieldMetaByName("c_mediumint_8_un").getColumnType());
        Assert.assertEquals("mediumint unsigned", tableMeta.getFieldMetaByName("c_mediumint_24_un").getColumnType());
        Assert.assertEquals("mediumint unsigned", tableMeta.getFieldMetaByName("c_mediumint_df_un").getColumnType());
        Assert.assertEquals("mediumint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_mediumint_zerofill_un").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_1").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_4").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_11").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_32").getColumnType());
        Assert.assertEquals("int unsigned", tableMeta.getFieldMetaByName("c_int_32_un").getColumnType());
        Assert.assertEquals("int unsigned", tableMeta.getFieldMetaByName("c_int_df_un").getColumnType());
        Assert.assertEquals("int unsigned zerofill", tableMeta.getFieldMetaByName("c_int_zerofill_un").getColumnType());
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("c_bigint_1").getColumnType());
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("c_bigint_20").getColumnType());
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("c_bigint_64").getColumnType());
        Assert.assertEquals("bigint unsigned", tableMeta.getFieldMetaByName("c_bigint_20_un").getColumnType());
        Assert.assertEquals("bigint unsigned", tableMeta.getFieldMetaByName("c_bigint_64_un").getColumnType());
        Assert.assertEquals("bigint unsigned", tableMeta.getFieldMetaByName("c_bigint_df_un").getColumnType());
        Assert.assertEquals("bigint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_bigint_zerofill_un").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_tinyint_hex_1").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_tinyint_hex_4").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_tinyint_hex_8").getColumnType());
        Assert.assertEquals("tinyint unsigned", tableMeta.getFieldMetaByName("c_tinyint_hex_3_un").getColumnType());
        Assert.assertEquals("tinyint unsigned", tableMeta.getFieldMetaByName("c_tinyint_hex_8_un").getColumnType());
        Assert.assertEquals("tinyint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_tinyint_hex_zerofill_un").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_hex_1").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_hex_2").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_hex_6").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_hex_16").getColumnType());
        Assert.assertEquals("smallint unsigned", tableMeta.getFieldMetaByName("c_smallint_hex_16_un").getColumnType());
        Assert.assertEquals("smallint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_smallint_hex_zerofill_un").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_hex_1").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_hex_3").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_hex_9").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_hex_24").getColumnType());
        Assert.assertEquals("mediumint unsigned", tableMeta.getFieldMetaByName("c_mediumint_hex_8_un").getColumnType());
        Assert.assertEquals("mediumint unsigned",
            tableMeta.getFieldMetaByName("c_mediumint_hex_24_un").getColumnType());
        Assert.assertEquals("mediumint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_mediumint_hex_zerofill_un").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_hex_1").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_hex_4").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_hex_11").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_hex_32").getColumnType());
        Assert.assertEquals("int unsigned", tableMeta.getFieldMetaByName("c_int_hex_32_un").getColumnType());
        Assert.assertEquals("int unsigned zerofill",
            tableMeta.getFieldMetaByName("c_int_hex_zerofill_un").getColumnType());
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("c_bigint_hex_1").getColumnType());
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("c_bigint_hex_20").getColumnType());
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("c_bigint_hex_64").getColumnType());
        Assert.assertEquals("bigint unsigned", tableMeta.getFieldMetaByName("c_bigint_hex_20_un").getColumnType());
        Assert.assertEquals("bigint unsigned", tableMeta.getFieldMetaByName("c_bigint_hex_64_un").getColumnType());
        Assert.assertEquals("bigint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_bigint_hex_zerofill_un").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_tinyint_hex_x_1").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_tinyint_hex_x_4").getColumnType());
        Assert.assertEquals("tinyint", tableMeta.getFieldMetaByName("c_tinyint_hex_x_8").getColumnType());
        Assert.assertEquals("tinyint unsigned", tableMeta.getFieldMetaByName("c_tinyint_hex_x_3_un").getColumnType());
        Assert.assertEquals("tinyint unsigned", tableMeta.getFieldMetaByName("c_tinyint_hex_x_8_un").getColumnType());
        Assert.assertEquals("tinyint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_tinyint_hex_x_zerofill_un").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_hex_x_1").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_hex_x_2").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_hex_x_6").getColumnType());
        Assert.assertEquals("smallint", tableMeta.getFieldMetaByName("c_smallint_hex_x_16").getColumnType());
        Assert.assertEquals("smallint unsigned",
            tableMeta.getFieldMetaByName("c_smallint_hex_x_16_un").getColumnType());
        Assert.assertEquals("smallint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_smallint_hex_x_zerofill_un").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_hex_x_1").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_hex_x_3").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_hex_x_9").getColumnType());
        Assert.assertEquals("mediumint", tableMeta.getFieldMetaByName("c_mediumint_hex_x_24").getColumnType());
        Assert.assertEquals("mediumint unsigned",
            tableMeta.getFieldMetaByName("c_mediumint_hex_x_8_un").getColumnType());
        Assert.assertEquals("mediumint unsigned",
            tableMeta.getFieldMetaByName("c_mediumint_hex_x_24_un").getColumnType());
        Assert.assertEquals("mediumint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_mediumint_hex_x_zerofill_un").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_hex_x_1").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_hex_x_4").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_hex_x_11").getColumnType());
        Assert.assertEquals("int", tableMeta.getFieldMetaByName("c_int_hex_x_32").getColumnType());
        Assert.assertEquals("int unsigned", tableMeta.getFieldMetaByName("c_int_hex_x_32_un").getColumnType());
        Assert.assertEquals("int unsigned zerofill",
            tableMeta.getFieldMetaByName("c_int_hex_x_zerofill_un").getColumnType());
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("c_bigint_hex_x_1").getColumnType());
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("c_bigint_hex_x_20").getColumnType());
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("c_bigint_hex_x_64").getColumnType());
        Assert.assertEquals("bigint unsigned", tableMeta.getFieldMetaByName("c_bigint_hex_x_20_un").getColumnType());
        Assert.assertEquals("bigint unsigned", tableMeta.getFieldMetaByName("c_bigint_hex_x_64_un").getColumnType());
        Assert.assertEquals("bigint unsigned zerofill",
            tableMeta.getFieldMetaByName("c_bigint_hex_x_zerofill_un").getColumnType());
        Assert.assertEquals("bigint", tableMeta.getFieldMetaByName("c_idx").getColumnType());
    }

    @Test
    public void testCoHashSubPartitionDdl() {
        String s = "CREATE TABLE `ch_t1_sp_ch_ch` (\n"
            + "\t`id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "\t`sid` bigint DEFAULT NULL,\n"
            + "\t`tid` bigint DEFAULT NULL,\n"
            + "\t`user_id` varchar(32) DEFAULT NULL,\n"
            + "\t`order_id` varchar(32) DEFAULT NULL,\n"
            + "\t`order_time` datetime DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4\n"
            + "PARTITION BY CO_HASH (SUBSTR(`order_id`, -8), SUBSTR(`user_id`, -4)) PARTITIONS 2\n"
            + "SUBPARTITION BY CO_HASH (SUBSTR(`sid`, -4), SUBSTR(`tid`, -4)) SUBPARTITIONS 2";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "d1", s, null);
        Map<String, String> snapshot = memoryTableMeta.snapshot();
        String ddl = snapshot.get("d1");
        SQLStatement st = SQLUtils.parseSQLStatement(ddl);
        Assert.assertEquals(StringUtils.trim(ddl), StringUtils.trim(st.toString()));
    }

    @Test
    public void testDefaultCharset() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        String s = "CREATE TABLE `ch_t1_sp_ch_ch` (\n"
            + "\t`id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "\t`sid` bigint DEFAULT NULL,\n"
            + "\t`tid` bigint DEFAULT NULL,\n"
            + "\t`user_id` varchar(32) DEFAULT NULL,\n"
            + "\t`order_id` varchar(32) DEFAULT NULL,\n"
            + "\t`order_time` datetime DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB\n"
            + "PARTITION BY CO_HASH (SUBSTR(`order_id`, -8), SUBSTR(`user_id`, -4)) PARTITIONS 2\n"
            + "SUBPARTITION BY CO_HASH (SUBSTR(`sid`, -4), SUBSTR(`tid`, -4)) SUBPARTITIONS 2";

        String expectWithCharset = "CREATE TABLE `ch_t1_sp_ch_ch` (\n"
            + "\t`id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "\t`sid` bigint DEFAULT NULL,\n"
            + "\t`tid` bigint DEFAULT NULL,\n"
            + "\t`user_id` varchar(32) DEFAULT NULL,\n"
            + "\t`order_id` varchar(32) DEFAULT NULL,\n"
            + "\t`order_time` datetime DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = innodb DEFAULT CHARACTER SET = utf8mb4\n"
            + "PARTITION BY CO_HASH (substr(`order_id`, -8), substr(`user_id`, -4)) PARTITIONS 2\n"
            + "SUBPARTITION BY CO_HASH (substr(`sid`, -4), substr(`tid`, -4)) SUBPARTITIONS 2;";
        memoryTableMeta.apply(null, "d1", s, null);
        Map<String, String> snapshot = memoryTableMeta.snapshot();
        String ddl = snapshot.get("d1");
        SQLStatement st = SQLUtils.parseSQLStatement(ddl);
        Assert.assertEquals(StringUtils.trim(ddl), StringUtils.trim(st.toString()));
        Assert.assertEquals(expectWithCharset, StringUtils.trim(ddl));
    }

    @Test
    public void testDefaultDbCharset() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);

        memoryTableMeta.apply(null, "d1", "create database d1 default charset gbk", null);
        String s = "CREATE TABLE `ch_t1_sp_ch_ch` (\n"
            + "\t`id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "\t`sid` bigint DEFAULT NULL,\n"
            + "\t`tid` bigint DEFAULT NULL,\n"
            + "\t`user_id` varchar(32) DEFAULT NULL,\n"
            + "\t`order_id` varchar(32) DEFAULT NULL,\n"
            + "\t`order_time` datetime DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB\n"
            + "PARTITION BY CO_HASH (SUBSTR(`order_id`, -8), SUBSTR(`user_id`, -4)) PARTITIONS 2\n"
            + "SUBPARTITION BY CO_HASH (SUBSTR(`sid`, -4), SUBSTR(`tid`, -4)) SUBPARTITIONS 2";

        String expectWithCharset = "CREATE TABLE `ch_t1_sp_ch_ch` (\n"
            + "\t`id` bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "\t`sid` bigint DEFAULT NULL,\n"
            + "\t`tid` bigint DEFAULT NULL,\n"
            + "\t`user_id` varchar(32) DEFAULT NULL,\n"
            + "\t`order_id` varchar(32) DEFAULT NULL,\n"
            + "\t`order_time` datetime DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = innodb DEFAULT CHARACTER SET = gbk\n"
            + "PARTITION BY CO_HASH (substr(`order_id`, -8), substr(`user_id`, -4)) PARTITIONS 2\n"
            + "SUBPARTITION BY CO_HASH (substr(`sid`, -4), substr(`tid`, -4)) SUBPARTITIONS 2;";
        memoryTableMeta.apply(null, "d1", s, null);
        Map<String, String> snapshot = memoryTableMeta.snapshot();
        String ddl = snapshot.get("d1");
        SQLStatement st = SQLUtils.parseSQLStatement(ddl);
        Assert.assertEquals(StringUtils.trim(ddl), StringUtils.trim(st.toString()));
        Assert.assertEquals(expectWithCharset, StringUtils.trim(ddl));
    }

    @Test
    public void testAddBooleanColumn() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.setMySql8(true);
        memoryTableMeta.apply(null, "d1",
            "CREATE TABLE `t_bool_1` (\n"
                + "\t`a` int NOT NULL AUTO_INCREMENT,\n"
                + "\t`b` bool DEFAULT NULL,\n"
                + "\t`c` int DEFAULT NULL,\n"
                + "\tPRIMARY KEY (a)\n"
                + ") DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci", null);

        String alterColumn = "ALTER TABLE t_bool_1\n"
            + "\tMODIFY COLUMN c boolean";

        String alterAddColumn = "ALTER TABLE t_bool_1\n"
            + "\tADD COLUMN d bool";
        memoryTableMeta.apply(null, "d1", alterColumn, null);
        memoryTableMeta.apply(null, "d1", alterAddColumn, null);
        TableMeta meta = memoryTableMeta.find("d1", "t_bool_1");
        Assert.assertEquals("int", meta.getFieldMetaByName("a").getColumnType());
        Assert.assertEquals("tinyint", meta.getFieldMetaByName("b").getColumnType());
        Assert.assertEquals("tinyint", meta.getFieldMetaByName("c").getColumnType());
        Assert.assertEquals("tinyint", meta.getFieldMetaByName("d").getColumnType());
    }

    @Test
    public void testCreateExperIndexTable() {
        String createSql = "CREATE TABLE `t_wide_billing_item_apportion_jsonindex_exs4_00003` (\n"
            + "  `seq_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n"
            + "  `id` varchar(128) DEFAULT NULL COMMENT ' ',\n"
            + "  `order_developer_ids` json DEFAULT NULL COMMENT '订单开发人',\n"
            + "  PRIMARY KEY (`seq_id`),\n"
            + "  KEY `ix_billing_apportion_order_developer_ids` ((cast(json_extract(`order_developer_ids`,_utf8mb4'$[*]') as char(64) array)))\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付和分摊宽表'";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.setMySql8(true);
        memoryTableMeta.apply(null, "d1", createSql, null);
    }

    @Test
    public void testParserTgGroup() {
        String createSql = "ALTER TABLE `event_record`\n"
            + "  ADD COLUMN `notifyId` varchar(32) NOT NULL COMMENT '消息通知ID' AFTER `id`,\n"
            + "  MODIFY COLUMN `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '事件类型：order_pay，refund，refund_finish' AFTER `event_no`,\n"
            + "  MODIFY COLUMN `biz_type` tinyint NOT NULL COMMENT '业务类型：1订单，2履约，3退款' AFTER `event_type`,\n"
            + "  MODIFY COLUMN `user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户id' AFTER `biz_no` WITH TABLEGROUP=tg28 IMPLICIT";

        SQLStatement st = SQLUtils.parseSQLStatement(createSql);
        String expected = "alter table `event_record`\n"
            + "\tadd column `notifyId` varchar(32) not null comment '消息通知ID' after `id`,\n"
            + "\tmodify column `event_type` varchar(32) character set utf8mb4 collate utf8mb4_general_ci not null default '' comment '事件类型：order_pay，refund，refund_finish' after `event_no`,\n"
            + "\tmodify column `biz_type` tinyint not null comment '业务类型：1订单，2履约，3退款' after `event_type`,\n"
            + "\tmodify column `user_id` varchar(32) character set utf8mb4 collate utf8mb4_general_ci not null comment '用户id' after `biz_no` with tablegroup=tg28 implicit";
        Assert.assertEquals(expected, st.toLowerCaseString());
    }

    @Test
    public void testParserSpecialIndex() {
        String createSql = "create table if not exists `expr_create_80_test` (\n"
            + "  a int PRIMARY KEY,\n"
            + "  b int,\n"
            + "  c int,\n"
            + "  d varchar(64),\n"
            + "  e varchar(64),\n"
            + "  INDEX cc((b + c))\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci\n"
            + "DBPARTITION BY hash(`a`)";
        SQLStatement st = SQLUtils.parseSQLStatement(createSql);
        String expected = "create table if not exists `expr_create_80_test` (\n"
            + "\ta int primary key,\n"
            + "\tb int,\n"
            + "\tc int,\n"
            + "\td varchar(64),\n"
            + "\te varchar(64),\n"
            + "\tindex cc((b + c))\n"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci\n"
            + "dbpartition by hash(`a`)";
        Assert.assertEquals(expected, st.toLowerCaseString());
    }

    @Test
    public void testApplyAndRenameColumnWithIndex() {
        String createSql =
            "create table my_alter_test(id bigint primary key auto_increment, name varchar(20) , order_no varchar(20), key mc2(`name`), unique (`name`), index(`name`, order_no), unique(`name`, order_no), unique(order_no,`name`));";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        applySql(memoryTableMeta, "d1", createSql);
        String alterSql = "alter table my_alter_test rename column `name` to `name2`;";
        applySql(memoryTableMeta, "d1", alterSql);
        String alterSql1 = "alter table my_alter_test rename column `id` to `id2`;";
        applySql(memoryTableMeta, "d1", alterSql1);
        TableMeta tableMeta = memoryTableMeta.find("d1", "my_alter_test");
        Assert.assertNotNull(tableMeta);
        Assert.assertNotNull(tableMeta.getFieldMetaByName("id2"));
        Assert.assertNotNull(tableMeta.getFieldMetaByName("name2"));
        Assert.assertNotNull(tableMeta.getFieldMetaByName("order_no"));

    }
}
