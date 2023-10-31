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

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlUnique;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlTableIndex;
import com.alibaba.polardbx.druid.sql.repository.Schema;
import com.alibaba.polardbx.druid.sql.repository.SchemaObject;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MemoryTableMetaTest_Index extends MemoryTableMetaBase {

    // create index ... on ...
    // alter table ... drop index ...
    @Test
    public void testAddDropIndex_1() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        createTable(memoryTableMeta);

        applySql(memoryTableMeta, "d1", "create index idx_gmt on t_ddl_test_JaV1_00(`gmt_created`)");
        Set<String> indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "idx_gmt", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1",
            "/*DRDS /127.0.0.1/11d66d5292400000/ */alter table `t_ddl_test_JaV1_00` DROP INDEX idx_gmt");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "auto_shard_key_job_id", "g_i_tv"), indexes);
    }

    // create index ... on ...
    // drop index ... on ...
    @Test
    public void testAddDropIndex_2() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        createTable(memoryTableMeta);

        applySql(memoryTableMeta, "d1", "create index idx_gmt on t_ddl_test_JaV1_00(`gmt_created`)");
        Set<String> indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "idx_gmt", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1",
            "/*DRDS /127.0.0.1/11d66d5292400000/ */DROP INDEX idx_gmt ON `t_ddl_test_JaV1_00`");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "auto_shard_key_job_id", "g_i_tv"), indexes);
    }

    // alter table ... add index ...
    // alter table ... drop index ...
    @Test
    public void testAddDropIndex_3() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        createTable(memoryTableMeta);

        applySql(memoryTableMeta, "d1", "ALTER TABLE t_ddl_test_JaV1_00 ADD INDEX idx_gmt (`gmt_created`)");
        Set<String> indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "idx_gmt", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1",
            "/*DRDS /127.0.0.1/11d66d5292400000/ */alter table `t_ddl_test_JaV1_00` DROP INDEX idx_gmt");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "auto_shard_key_job_id", "g_i_tv"), indexes);
    }

    // alter table ... add index ...
    // drop index ... on ...
    @Test
    public void testAddDropIndex_4() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        createTable(memoryTableMeta);

        applySql(memoryTableMeta, "d1", "ALTER TABLE t_ddl_test_JaV1_00 ADD INDEX idx_gmt (`gmt_created`)");
        Set<String> indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "idx_gmt", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1",
            "/*DRDS /127.0.0.1/11d66d5292400000/ */DROP INDEX idx_gmt ON `t_ddl_test_JaV1_00`");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "auto_shard_key_job_id", "g_i_tv"), indexes);
    }

    @Test
    public void testIndexComposite() {
        String ddl = "CREATE TABLE `t_order_2_n6yu_00000` (\n"
            + "  `x` int(11) NOT NULL AUTO_INCREMENT,\n"
            + "  `order_id` varchar(20) DEFAULT NULL,\n"
            + "  `seller_id` varchar(20) DEFAULT NULL,\n"
            + "  PRIMARY KEY (`x`),\n"
            + "  UNIQUE KEY `_local__local_i_1111` (`order_id`),\n"
            + "  KEY `_local__local_i_000` (`seller_id`),\n"
            + "  index `xxx` (`seller_id`),\n"
            + "  unique index `yyy`(`order_id`)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "test_db", ddl, null);
        Schema schemaRep = memoryTableMeta.getRepository().findSchema("test_db");
        if (schemaRep == null) {
            return;
        }
        SchemaObject data = schemaRep.findTable("t_order_2_n6yu_00000");
        if (data == null) {
            return;
        }
        SQLStatement statement = data.getStatement();
        if (statement == null) {
            return;
        }
        if (statement instanceof SQLCreateTableStatement) {
            SQLCreateTableStatement sqlCreateTableStatement = (SQLCreateTableStatement) statement;
            sqlCreateTableStatement.getTableElementList().forEach(e -> {
                if (e instanceof MySqlPrimaryKey) {
                    // do nothing
                } else if (e instanceof MySqlUnique) {
                    String name = SQLUtils.normalize(((MySqlUnique) e).getName().getSimpleName());
                    Assert.assertTrue(StringUtils.equalsAny(name, "_local__local_i_1111", "yyy"));
                } else if (e instanceof MySqlKey) {
                    String name = SQLUtils.normalize(((MySqlKey) e).getName().getSimpleName());
                    Assert.assertEquals("_local__local_i_000", name);
                } else if (e instanceof MySqlTableIndex) {
                    String name = SQLUtils.normalize(((MySqlTableIndex) e).getName().getSimpleName());
                    Assert.assertEquals("xxx", name);
                }
            });
        }

        Set<String> indexes = memoryTableMeta.findIndexes("test_db", "t_order_2_n6yu_00000");
        Assert.assertEquals(Sets.newHashSet("_local__local_i_1111", "_local__local_i_000", "xxx", "yyy"), indexes);
    }

    @Test
    public void testAddDropIndex_Idempotent() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        createTable(memoryTableMeta);

        applySql(memoryTableMeta, "d1", "create index idx_gmt on t_ddl_test_JaV1_00(`gmt_created`)");
        Set<String> indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "idx_gmt", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1", "create index idx_gmt on t_ddl_test_JaV1_00(`gmt_created`)");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "idx_gmt", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1", "ALTER TABLE t_ddl_test_JaV1_00 ADD INDEX idx_gmt (`gmt_created`)");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "idx_gmt", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1", "ALTER TABLE t_ddl_test_JaV1_00 ADD INDEX idx_gmt (`gmt_created`)");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "idx_gmt", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1",
            "/*DRDS /127.0.0.1/11d66d5292400000/ */alter table `t_ddl_test_JaV1_00` DROP INDEX idx_gmt");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1",
            "/*DRDS /127.0.0.1/11d66d5292400000/ */alter table `t_ddl_test_JaV1_00` DROP INDEX idx_gmt");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1",
            "/*DRDS /127.0.0.1/11d66d5292400000/ */DROP INDEX idx_gmt ON `t_ddl_test_JaV1_00`");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "auto_shard_key_job_id", "g_i_tv"), indexes);

        applySql(memoryTableMeta, "d1",
            "/*DRDS /127.0.0.1/11d66d5292400000/ */DROP INDEX idx_gmt ON `t_ddl_test_JaV1_00`");
        indexes = memoryTableMeta.findIndexes("d1", "t_ddl_test_JaV1_00");
        Assert.assertEquals(Sets.newHashSet("g_i_ext", "idx1", "auto_shard_key_job_id", "g_i_tv"), indexes);
    }

    @Test
    public void testClusterIndex() {
        String sql = "create table auto_partition_idx_tb (\n"
            + "\t`c_bit_1` bit(1) default null,\n"
            + "\t`c_bit_8` bit(8) default null,\n"
            + "\t`c_bit_16` bit(16) default null,\n"
            + "\t`c_bit_32` bit(32) default null,\n"
            + "\t`c_bit_64` bit(64) default null,\n"
            + "\t`c_tinyint_1` tinyint(1) default null,\n"
            + "\t`c_tinyint_1_un` tinyint(1) unsigned default null,\n"
            + "\t`c_tinyint_4` tinyint(4) default null,\n"
            + "\t`c_tinyint_4_un` tinyint(4) unsigned default null,\n"
            + "\t`c_tinyint_8` tinyint(8) default null,\n"
            + "\t`c_tinyint_8_un` tinyint(8) unsigned default null,\n"
            + "\t`c_smallint_1` smallint(1) default null,\n"
            + "\t`c_smallint_16` smallint(16) default null,\n"
            + "\t`c_smallint_16_un` smallint(16) unsigned default null,\n"
            + "\t`c_mediumint_1` mediumint(1) default null,\n"
            + "\t`c_mediumint_24` mediumint(24) default null,\n"
            + "\t`c_mediumint_24_un` mediumint(24) unsigned default null,\n"
            + "\t`c_int_1` int(1) default null,\n"
            + "\t`c_int_32` int(32) default null,\n"
            + "\t`c_int_32_un` int(32) unsigned default null,\n"
            + "\t`c_bigint_1` bigint(1) default null,\n"
            + "\t`c_bigint_64` bigint(64) default null,\n"
            + "\t`c_bigint_64_un` bigint(64) unsigned default null,\n"
            + "\t`c_decimal` decimal default null,\n"
            + "\t`c_decimal_pr` decimal(65, 30) default null,\n"
            + "\t`c_float` float default null,\n"
            + "\t`c_float_pr` float(10, 3) default null,\n"
            + "\t`c_float_un` float(10, 3) unsigned default null,\n"
            + "\t`c_double` double default null,\n"
            + "\t`c_double_pr` double(10, 3) default null,\n"
            + "\t`c_double_un` double(10, 3) unsigned default null,\n"
            + "\t`c_date` date default null comment 'date',\n"
            + "\t`c_datetime` datetime default null,\n"
            + "\t`c_datetime_1` datetime(1) default null,\n"
            + "\t`c_datetime_3` datetime(3) default null,\n"
            + "\t`c_datetime_6` datetime(6) default null,\n"
            + "\t`c_timestamp` timestamp default current_timestamp,\n"
            + "\t`c_timestamp_1` timestamp(1) default '2000-01-01 00:00:00',\n"
            + "\t`c_timestamp_3` timestamp(3) default '2000-01-01 00:00:00',\n"
            + "\t`c_timestamp_6` timestamp(6) default '2000-01-01 00:00:00',\n"
            + "\t`c_time` time default null,\n"
            + "\t`c_time_1` time(1) default null,\n"
            + "\t`c_time_3` time(3) default null,\n"
            + "\t`c_time_6` time(6) default null,\n"
            + "\t`c_year` year default null,\n"
            + "\t`c_year_4` year(4) default null,\n"
            + "\t`c_char` char(10) default null,\n"
            + "\t`c_varchar` varchar(10) default null,\n"
            + "\t`c_binary` binary(10) default null,\n"
            + "\t`c_varbinary` varbinary(10) default null,\n"
            + "\t`c_blob_tiny` tinyblob default null,\n"
            + "\t`c_blob` blob default null,\n"
            + "\t`c_blob_medium` mediumblob default null,\n"
            + "\t`c_blob_long` longblob default null,\n"
            + "\t`c_text_tiny` tinytext default null,\n"
            + "\t`c_text` text default null,\n"
            + "\t`c_text_medium` mediumtext default null,\n"
            + "\t`c_text_long` longtext default null,\n"
            + "\t`c_enum` enum('a', 'b', 'c') default null,\n"
            + "\t`c_set` set('a', 'b', 'c') default null,\n"
            + "\t`c_json` json default null,\n"
            + "\t`c_geometory` geometry default null,\n"
            + "\t`c_point` point default null,\n"
            + "\t`c_linestring` linestring default null,\n"
            + "\t`c_polygon` polygon default null,\n"
            + "\t`c_multipoint` multipoint default null,\n"
            + "\t`c_multilinestring` multilinestring default null,\n"
            + "\t`c_multipolygon` multipolygon default null,\n"
            + "\t`id` bigint(20) default null,\n"
            + "\tkey `_local_ap_index`(`id`),\n"
            + "\t_drds_implicit_id_ bigint auto_increment,\n"
            + "\tprimary key (_drds_implicit_id_)\n"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "drds_polarx1_ddl_qatest_app", sql, null);
        memoryTableMeta.apply(null, "drds_polarx1_ddl_qatest_app",
            "create clustered index `ap_index_xx` on `auto_partition_idx_tb` (`id`)", null);
        Set<String> indexes = memoryTableMeta.findIndexes(
            "drds_polarx1_ddl_qatest_app", "auto_partition_idx_tb");
        Assert.assertEquals(Sets.newHashSet("_local_ap_index", "ap_index_xx"), indexes);
    }

    @Test
    public void testImplicitUniqueKey() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        String sql = "create table if not exists `vksdriodaebjn` (\n"
            + " `xus8hsp` integer(1) not null comment '0rtl',\n"
            + " `fsg0` bigint(6) unsigned zerofill not null comment 'fthandpbzn34ach',\n"
            + " `bikb01bdmc4` int(0) unsigned not null,\n"
            + "  primary key using btree (`bikb01bdmc4`, `fsg0` asc),\n"
            + "  index `auto_shard_key_fsg0` using btree(`fsg0`)\n"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci\n"
            + "partition by key (`fsg0`)";
        memoryTableMeta.apply(null, "d1", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);
        Assert.assertTrue(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("auto_shard_key_fsg0"));

        sql = "alter table `vksdriodaebjn` broadcast";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);

        sql = "alter table `vksdriodaebjn` single";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);

        sql = "alter tablegroup `single_tg` move partitions "
            + "`p1` , `p1`, `p1`, `p1`, `p1`, `p1`, `p1`, `p1` to `pxc-xdb-s-pxchzrcylvib2vdtua032`";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);

        sql = "create  index `kde`  on `vksdriodaebjn` ( `bikb01bdmc4` , `fsg0` asc )";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);
        Assert.assertTrue(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("kde"));

        sql = "alter table `vksdriodaebjn` add column ("
            + " `yc0hg` binary ( 5 ) null unique  comment 'wstdno8' , "
            + " `zgrwpgdiyaecmi1` bigint  unsigned zerofill  unique  comment 'yqcjbcvfg' ) , "
            + " add  ( `usd0eefy3zf` timestamp    comment 'szr'  )";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);
        Assert.assertNotNull(tableMeta.getFieldMetaByName("yc0hg"));
        Assert.assertNotNull(tableMeta.getFieldMetaByName("zgrwpgdiyaecmi1"));
        Assert.assertNotNull(tableMeta.getFieldMetaByName("usd0eefy3zf"));
        Assert.assertTrue(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("yc0hg"));
        Assert.assertTrue(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("zgrwpgdiyaecmi1"));

        sql = "drop index `yc0hg` on `vksdriodaebjn`";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);
        Assert.assertNotNull(tableMeta.getFieldMetaByName("yc0hg"));
        Assert.assertFalse(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("yc0hg"));

        sql = "create  index `psd0u`  on `vksdriodaebjn` ( `yc0hg` , `bikb01bdmc4`  )";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);
        Assert.assertTrue(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("psd0u"));

        sql = "drop index `psd0u` on `vksdriodaebjn`";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);
        Assert.assertFalse(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("psd0u"));

        sql = "drop index `zgrwpgdiyaecmi1` on `vksdriodaebjn`";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);
        Assert.assertNotNull(tableMeta.getFieldMetaByName("zgrwpgdiyaecmi1"));
        Assert.assertFalse(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("zgrwpgdiyaecmi1"));

        sql = "drop index `kde` on `vksdriodaebjn`";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);
        Assert.assertFalse(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("kde"));

        sql = "alter table `vksdriodaebjn` partition by list ( `xus8hsp` ) "
            + "( partition `hnoq7a8` values in ( 14 , 6 ) , "
            + "  partition `cv2ev` values in ( 8  ), "
            + "  partition `daia8skmocba` values in ( 75, 105 ), "
            + "  partition `l` values in ( 71  ) )";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);

        sql = "alter table `vksdriodaebjn` "
            + " change column `fsg0` `bj` bigint  unsigned zerofill  unique  comment 'gk1ipvn2kvdlwl' after `yc0hg` , "
            + " add column ( `s1ntx51mhrglzo` bigint ( 2 ) unsigned zerofill unique not null    )";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);
        Assert.assertNull(tableMeta.getFieldMetaByName("fsg0", true));
        Assert.assertNotNull(tableMeta.getFieldMetaByName("bj"));
        Assert.assertNotNull(tableMeta.getFieldMetaByName("s1ntx51mhrglzo"));
        Assert.assertTrue(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("bj"));
        Assert.assertTrue(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("s1ntx51mhrglzo"));

        sql = "drop index `bj` on `vksdriodaebjn`";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "vksdriodaebjn");
        Assert.assertNotNull(tableMeta);
        Assert.assertFalse(memoryTableMeta.findIndexes("d1", "vksdriodaebjn").contains("bj"));
    }

    @Test
    public void testIndexOverwriteTable() {
        // 测试当索引名字和表名重复的时候，互不影响
        String sql = "create table if not exists `ng` ("
            + "        `2kkxyfni` char(1) not null comment 'kkvy',"
            + "        `i1iavmsfrvs1cpk` char(5),"
            + "        _drds_implicit_id_ bigint auto_increment,"
            + "        primary key (_drds_implicit_id_)"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "d1", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "ng");
        Assert.assertNotNull(tableMeta);

        memoryTableMeta.apply(null, "d1", "create table or0b(feesesihp3qx bigint)", null);
        sql = "create local index `ng`  on `or0b` ( `feesesihp3qx`   )";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "ng");
        Assert.assertNotNull(tableMeta);
        Assert.assertTrue(memoryTableMeta.findIndexes("d1", "or0b").contains("ng"));
    }

    private void createTable(MemoryTableMeta memoryTableMeta) {
        applySql(memoryTableMeta, "d1",
            "CREATE TABLE `t_ddl_test_JaV1_00` (\n"
                + " `ID` bigint(20) NOT NULL AUTO_INCREMENT BY GROUP,\n"
                + " `JOB_ID` bigint(20) NOT NULL DEFAULT '0',\n"
                + " `EXT_ID` bigint(20) NOT NULL DEFAULT '0',\n"
                + " `TV_ID` bigint(20) NOT NULL DEFAULT '0',\n"
                + " `SCHEMA_NAME` varchar(200) NOT NULL,\n"
                + " `TABLE_NAME` varchar(200) NOT NULL,\n"
                + " `GMT_CREATED` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
                + " `DDL_SQL` text NOT NULL,\n"
                + "  PRIMARY KEY (`ID`),\n"
                + "  KEY `idx1` (`SCHEMA_NAME`),\n"
                + "  KEY `auto_shard_key_job_id` USING BTREE (`JOB_ID`),\n"
                + "  GLOBAL INDEX `g_i_ext`(`EXT_ID`) COVERING (`ID`, `JOB_ID`) DBPARTITION BY HASH(`EXT_ID`),\n"
                + "  GLOBAL INDEX `g_i_tv`(`TV_ID`) COVERING (`ID`, `JOB_ID`, `GMT_CREATED`) DBPARTITION BY HASH(`TV_ID`)\n"
                + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4  dbpartition by hash(`ID`) tbpartition by hash(`JOB_ID`) "
                + "tbpartitions 32");
    }
}
