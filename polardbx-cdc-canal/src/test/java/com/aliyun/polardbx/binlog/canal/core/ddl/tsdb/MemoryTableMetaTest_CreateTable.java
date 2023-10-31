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
            + " PARTITION BY KEY (id, k)PARTITIONS 1 \n"
            + " AUTO_SPLIT 'ON'";
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

}
