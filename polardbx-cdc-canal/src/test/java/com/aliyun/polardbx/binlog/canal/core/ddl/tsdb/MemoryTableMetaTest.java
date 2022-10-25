/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.alibaba.polardbx.druid.sql.repository.Schema;
import com.alibaba.polardbx.druid.sql.repository.SchemaObject;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static com.alibaba.polardbx.druid.sql.parser.SQLParserUtils.createSQLStatementParser;

/**
 * Created by ziyang.lb
 **/
public class MemoryTableMetaTest {

    private static final String sql = "create table if not exists Abc (\n"
        + "        `id` int(11) unsigned not null comment 'id',\n"
        + "        `xx` int(11) unsigned not null comment 'xx',\n"
        + "        `vv` varchar(30) not null comment 'vv',\n"
        + "        `bb` varchar(300) not null comment 'bb',\n"
        + "        `gg` varchar(100) not null comment 'gg',\n"
        + "        `jj` varchar(100) not null comment 'jj',\n"
        + "        `ll` varchar(100) not null comment 'll',\n"
        + "        `kk` varchar(60) not null comment 'kk',\n"
        + "        `oo` smallint(5) unsigned not null comment 'oo',\n"
        + "        `pp` tinyint(3) unsigned not null comment 'pp',\n"
        + "        `yy` varchar(100) not null comment 'yy',\n"
        + "        primary key (`id`)\n"
        + ") engine = innodb default charset = utf8mb4 default character set = utf8mb4 default collate = utf8mb4_general_ci comment 'xxyyzz'";

    @Test
    public void testCaseSensitive1() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "DbDB", sql.toLowerCase(), null);
        assertR(memoryTableMeta);
    }

    @Test
    public void testCaseSensitive2() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "DbDB", sql, null);
        assertR(memoryTableMeta);
    }

    @Test
    public void testCaseSensitive3() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "DbDB".toLowerCase(), sql.toLowerCase(), null);
        assertR(memoryTableMeta);
    }

    @Test
    public void testCaseSensitive4() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "DbDB".toLowerCase(), sql, null);
        assertR(memoryTableMeta);
    }

    @Test
    public void testMuiltSql() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "test",
            "create table t1(id int,name varchar(10),primary key(id));create table t2(id int,name varchar(10),primary key(id))",
            null);
        TableMeta tm1 = memoryTableMeta.find("test", "t1");
        TableMeta tm2 = memoryTableMeta.find("test", "t2");
        Assert.assertNotNull(tm1);
        System.out.println(tm1);
        Assert.assertNotNull(tm2);
        System.out.println(tm2);
    }

    @Test
    public void testChinese() {
        String sql = "CREATE INDEX 检查时间 ON yyk_pacs_patient (examine_time,hid,examine_type)";
        //String sql = "CREATE INDEX = ON yyk_pacs_patient (examine_time,hid,examine_type)";
        //MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null,false);
        //memoryTableMeta.apply(null, "test", sql, null);
        List<SQLStatement> stmtList = SQLUtils.parseStatements(sql, DbType.mysql);
    }

    private void assertR(MemoryTableMeta memoryTableMeta) {
        TableMeta t1 = memoryTableMeta.find("DbDB", "Abc");
        TableMeta t2 = memoryTableMeta.find("DbDB", "abc");
        TableMeta t3 = memoryTableMeta.find("dbdb", "Abc");
        TableMeta t4 = memoryTableMeta.find("dbdb", "abc");

        Assert.assertNotNull(t1);
        Assert.assertNotNull(t2);
        Assert.assertNotNull(t3);
        Assert.assertNotNull(t4);
    }

    @Test
    public void testDDL() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "srzp_job_single",
            "create table `group_dissolution_log_lnsi` (\n"
                + "        `id` int(11) not null auto_increment comment '??id',\n"
                + "        `group_id` int(11) not null comment '?id',\n"
                + "        `admin_id` int(11) not null default '0' comment 'a????id',\n"
                + "        `type` tinyint(3) not null default '0' comment '?? 1:??????? 2:???????',\n"
                + "        `created_at` timestamp not null default current_timestamp comment '????',\n"
                + "        `updated_at` timestamp not null default current_timestamp on update current_timestamp comment '????',\n"
                + "        `deleted_at` timestamp null comment '????',\n"
                + "        primary key (`id`),\n"
                + "        index `idx_group_id`(`group_id`)\n"
                + ") engine = innodb auto_increment = 1 default charset = `utf8mb4` default collate = `utf8mb4_bin` row_format = dynamic comment '?????'",
            null);
        memoryTableMeta.apply(null, "srzp_job_single", "alter table `group_dissolution_log_lnsi`\n"
            + "        modify column `id` int(11) not null auto_increment comment '主键id' first", null);
        memoryTableMeta.apply(null, "srzp_job_single", "alter table `group_dissolution_log_lnsi`\n"
            + "        modify column `group_id` int(11) not null comment '群id' after `id`,\n"
            + "        modify column `admin_id` int(11) not null default 0 comment 'a端操作人id' after `group_id`", null);
        memoryTableMeta.apply(null, "srzp_job_single", "alter table `group_dissolution_log_lnsi`\n"
                + "        modify column `type` tinyint(3) not null default 0 comment '类型 1:管理员直接解散 2:用户申请的解释' after `admin_id`,\n"
                + "        modify column `created_at` timestamp(0) not null default current_timestamp(0) comment '创建时间' after `type`",
            null);
        memoryTableMeta.apply(null, "srzp_job_single", "alter table `group_dissolution_log_lnsi`\n"
                + "        modify column `updated_at` timestamp(0) not null default current_timestamp(0) on update current_timestamp(0) comment '更新时间' after `created_at`",
            null);
        memoryTableMeta.apply(null, "srzp_job_single", "alter table `group_dissolution_log_lnsi`\n"
                + "        modify column `deleted_at` timestamp(0) null default null comment '删除时间' after `updated_at`",
            null);
        memoryTableMeta.apply(null, "srzp_job_single", "alter table `group_dissolution_log_lnsi`\n"
            + "        comment = '群解散记录'", null);
        TableMeta tm1 = memoryTableMeta.find("srzp_job_single", "group_dissolution_log_lnsi");
        Assert.assertNotNull(tm1);
        System.out.println(tm1);
    }

    @Test
    public void testNoDatabaseCreate() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "test_db", "create table t1(id int)", null);
        TableMeta tableMeta = memoryTableMeta.find("test_db", "t1");
        System.out.println(tableMeta);

        memoryTableMeta.apply(null, "test_db", "create table t1(id int,name varchar(20))", null);
        tableMeta = memoryTableMeta.find("test_db", "t1");
        System.out.println(tableMeta);
    }

    @Test
    public void testTableWithPartition() {
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
        TableMeta tableMeta = memoryTableMeta.find("test_db", "t_order_2_scri_00000");
        System.out.println(tableMeta);
    }

    @Test
    public void testIndex() {
        String ddl = "CREATE TABLE `t_order_2_n6yu_00000` (\n"
            + "  `x` int(11) NOT NULL AUTO_INCREMENT,\n"
            + "  `order_id` varchar(20) DEFAULT NULL,\n"
            + "  `seller_id` varchar(20) DEFAULT NULL,\n"
            + "  PRIMARY KEY (`x`),\n"
            + "  UNIQUE KEY `_local__local_i_1111` (`order_id`),\n"
            + "  KEY `_local__local_i_000` (`seller_id`),\n"
            + "  index `xxx` (`seller_id`),\n"
            + "  unique index `xxx`(`order_id`)\n"
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
                System.out.println(e.getClass());
                System.out.println(e instanceof SQLConstraint);
                if (e instanceof SQLConstraint) {
                    System.out.println(((SQLConstraint) e).getName());
                }
            });
        }
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
        Set<String> indexes =
            memoryTableMeta.findIndexes("drds_polarx1_ddl_qatest_app", "auto_partition_idx_tb");
        indexes.forEach(System.out::println);
    }

    @Test
    public void testIsSchemaExists() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
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

        memoryTableMeta.apply(null, "aaa", "create table t2(id int)", null);
        boolean result4 = memoryTableMeta.isSchemaExists("aaa");
        Assert.assertTrue(result4);
        TableMeta tableMeta2 = memoryTableMeta.find("aaa", "t2");
        Assert.assertNotNull(tableMeta2);

        memoryTableMeta.apply(null, "aaa", "create database aaa", null);
        boolean result5 = memoryTableMeta.isSchemaExists("aaa");
        Assert.assertTrue(result5);
    }

    @Test
    public void testRename1() {
        String sql = "rename table xx to yy";
        SQLStatementParser parser = createSQLStatementParser(sql, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> statementList = parser.parseStatementList();
        SQLStatement sqlStatement = statementList.get(0);
        System.out.println(sqlStatement.getClass());
    }

    @Test
    public void testRename2() {
        String sql1 =
            "/* applicationname=datagrip 2021.2.4 */ create database /*!32312 if not exists*/ `slt_single` /*!40100 default character set utf8mb4 */";
        SQLStatementParser parser1 = createSQLStatementParser(sql1, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> statementList1 = parser1.parseStatementList();
        SQLStatement sqlStatement1 = statementList1.get(0);
        System.out.println(sqlStatement1.getClass());
    }

    @Test
    public void testBacktickDrop() {
        String sql = "create table if not exists `gxw_testbacktick`("
            + "  `col-minus` int,"
            + "  c2 int,"
            + "  _drds_implicit_id_ bigint auto_increment,"
            + "  primary key (_drds_implicit_id_)"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "ddl1_1573919240", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick");
        System.out.println(tableMeta);

        memoryTableMeta.apply(null, "ddl1_1573919240", "drop table if exists `gxw_testbacktick`", null);
        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick");
        System.out.println(tableMeta);
    }

    @Test
    public void testBacktickDrop2() {
        String sql = "create table if not exists `gxw_test``backtick`("
            + "  `col-minus` int,"
            + "  c2 int,"
            + "  _drds_implicit_id_ bigint auto_increment,"
            + "  primary key (_drds_implicit_id_)"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "ddl1_1573919240", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_test`backtick");
        System.out.println(tableMeta);

        memoryTableMeta.apply(null, "ddl1_1573919240", "drop table if exists `gxw_test``backtick`", null);
        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_test`backtick");
        System.out.println(tableMeta);
    }

    @Test
    public void testBacktickRename() {
        String sql = "create table if not exists `gxw_test``backtick`("
            + "  `col-minus` int,"
            + "  c2 int,"
            + "  _drds_implicit_id_ bigint auto_increment,"
            + "  primary key (_drds_implicit_id_)"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "ddl1_1573919240", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_test`backtick");
        System.out.println(tableMeta);

        memoryTableMeta
            .apply(null, "ddl1_1573919240", "rename table `gxw_test``backtick` to `gxw_testbacktick_new`", null);
        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_test`backtick");
        System.out.println(tableMeta);

        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick_new");
        System.out.println(tableMeta);
    }

    @Test
    public void testBacktickRename2() {
        String sql = "create table if not exists `gxw_testbacktick`("
            + "  `col-minus` int,"
            + "  c2 int,"
            + "  _drds_implicit_id_ bigint auto_increment,"
            + "  primary key (_drds_implicit_id_)"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "ddl1_1573919240", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick");
        System.out.println(tableMeta);

        memoryTableMeta
            .apply(null, "ddl1_1573919240", "rename table `gxw_testbacktick` to `gxw_testbacktick_new`", null);
        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick");
        System.out.println(tableMeta);

        tableMeta = memoryTableMeta.find("ddl1_1573919240", "gxw_testbacktick_new");
        System.out.println(tableMeta);
    }

    @Test
    public void test11() {
        String sql = "create table if not exists `ng` ("
            + "        `2kkxyfni` char(1) not null comment 'kkvy',"
            + "        `i1iavmsfrvs1cpk` char(5),"
            + "        _drds_implicit_id_ bigint auto_increment,"
            + "        primary key (_drds_implicit_id_)"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci";
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "d1", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "ng");
        System.out.println(tableMeta);

        sql = "create local index `ng`  on `or0b` ( `feesesihp3qx`   )";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "ng");
        System.out.println(tableMeta);
//
//        sql = "alter table `ng` dbpartition by hash(`2kkxyfni`)";
//        memoryTableMeta.apply(null, "d1", sql, null);
//        tableMeta = memoryTableMeta.find("d1", "ng");
//        System.out.println(tableMeta);
//
//        sql = "drop index `774vnqsv6p` on `ng`";
//        memoryTableMeta.apply(null, "d1", sql, null);
//        tableMeta = memoryTableMeta.find("d1", "ng");
//        System.out.println(tableMeta);
//
//        sql = "alter table `ng` dbpartition by hash(`2kkxyfni`)";
//        memoryTableMeta.apply(null, "d1", sql, null);
//        tableMeta = memoryTableMeta.find("d1", "ng");
//        System.out.println(tableMeta);
    }

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
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "d1", sql, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "yrxsv7kevh4ce9");
        System.out.println(tableMeta);

        sql =
            "alter table `yrxsv7kevh4ce9` add column `7f3i` tinyint comment 'qa4qh1w', change column `vmk` `o13nfnt8n` integer(6) unsigned unique comment 'ioj'";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "yrxsv7kevh4ce9");
        System.out.println(tableMeta);

        sql = "alter table `yrxsv7kevh4ce9` change column `7f3i` `tmu6ki0zxzslb` varbinary(1) null unique";
        memoryTableMeta.apply(null, "d1", sql, null);
        tableMeta = memoryTableMeta.find("d1", "yRxSV7kEvh4Ce9");
        System.out.println(tableMeta);

        SQLStatementParser parser = createSQLStatementParser(
            "alter table `yrxsv7kevh4ce9` add column `7f3i` tinyint comment 'qa4qh1w', change column `vmk` `o13nfnt8n` integer(6) unsigned unique comment 'ioj'",
            DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> statementList = parser.parseStatementList();
        SQLStatement sqlStatement = statementList.get(0);

        if (sqlStatement instanceof SQLAlterTableStatement) {
            SQLAlterTableStatement sqlAlterTableStatement = (SQLAlterTableStatement) sqlStatement;
            String phyTableName = SQLUtils.normalize(sqlAlterTableStatement.getTableName());
            for (SQLAlterTableItem item : sqlAlterTableStatement.getItems()) {
                System.out.println(item.getClass());
            }
        }
    }

    @Test
    public void testttt() {
        String sql1 =
            "/*drds /11.122.76.56/13e123c82c802001/null// */create table if not exists `gxw_test``backtick_bpzj` ( \t`col-minus` int, \tc2 int, \t_drds_implicit_id_ bigint auto_increment, \tprimary key (_drds_implicit_id_) )";
        String sql2 =
            "/*drds /11.122.76.56/13e123c894402001/null// */alter table `gxw_test``backtick_bpzj` add column c3 int";

        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "d1", sql1, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "gxw_test`backtick_bpzj");
        System.out.println(tableMeta);

        memoryTableMeta.apply(null, "d1", sql2, null);
        tableMeta = memoryTableMeta.find("d1", "gxw_test`backtick_bpzj");
        System.out.println(tableMeta);
    }

    @Test
    public void testjjjs() {
        String sql1 =
            "create table if not exists `gxw_test``backtick` ( \t`col-minus` int, \tc2 int, \t_drds_implicit_id_ bigint auto_increment, \tprimary key (_drds_implicit_id_) ) default character set = utf8mb4 default collate = utf8mb4_general_ci";
        String sql2 =
            "alter table `gxw_testbacktick` add c3 int";

        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);
        memoryTableMeta.apply(null, "d1", sql1, null);
        TableMeta tableMeta = memoryTableMeta.find("d1", "gxw_test`backtick");
        System.out.println(tableMeta);

        memoryTableMeta.apply(null, "d1", sql2, null);
        tableMeta = memoryTableMeta.find("d1", "gxw_test`backtick");
        System.out.println(tableMeta);
    }

    @Test
    public void testDropIndex() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);

        String sql = "create table if not exists `vksdriodaebjn` (\n"
            + "        `xus8hsp` integer(1) not null comment '0rtl',\n"
            + "        `fsg0` bigint(6) unsigned zerofill not null comment 'fthandpbzn34ach',\n"
            + "        `bikb01bdmc4` int(0) unsigned not null,\n"
            + "        primary key using btree (`bikb01bdmc4`, `fsg0` asc),\n"
            + "        index `auto_shard_key_fsg0` using btree(`fsg0`)\n"
            + ") default character set = utf8mb4 default collate = utf8mb4_general_ci\n"
            + "partition by key (`fsg0`)";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql = "alter table `vksdriodaebjn` broadcast";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql = "alter table `vksdriodaebjn` single";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql =
            "alter tablegroup `single_tg` move partitions `p1` , `p1`, `p1`, `p1`, `p1`, `p1`, `p1`, `p1` to `pxc-xdb-s-pxchzrcylvib2vdtua032`";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql = "create  index `kde`  on `vksdriodaebjn` ( `bikb01bdmc4` , `fsg0` asc )";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql =
            "alter table `vksdriodaebjn` add column ( `yc0hg` binary ( 5 ) null unique  comment 'wstdno8' , `zgrwpgdiyaecmi1` bigint  unsigned zerofill  unique  comment 'yqcjbcvfg' ) , add  ( `usd0eefy3zf` timestamp    comment 'szr'  )";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql = "drop index `yc0hg` on `vksdriodaebjn`";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql = "create  index `psd0u`  on `vksdriodaebjn` ( `yc0hg` , `bikb01bdmc4`  )";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql = "drop index `psd0u` on `vksdriodaebjn`";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql = "drop index `zgrwpgdiyaecmi1` on `vksdriodaebjn`";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql = "drop index `kde` on `vksdriodaebjn`";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql =
            "alter table `vksdriodaebjn` partition by list ( `xus8hsp` ) ( partition `hnoq7a8` values in ( 14 , 6 ) , partition `cv2ev` values in ( 8  ), partition `daia8skmocba` values in ( 75, 105 ), partition `l` values in ( 71  ) )";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql =
            "alter table `vksdriodaebjn` change column `fsg0` `bj` bigint  unsigned zerofill  unique  comment 'gk1ipvn2kvdlwl' after `yc0hg` , add column ( `s1ntx51mhrglzo` bigint ( 2 ) unsigned zerofill not null    )";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");

        sql = "drop index `bj` on `vksdriodaebjn`";
        memoryTableMeta.apply(null, "d1", sql, null);
        memoryTableMeta.find("d1", "vksdriodaebjn");
    }

    @Test
    public void testMemory() throws InterruptedException {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(null, false);

        for (int i = 0; i < 10000; i++) {
            String tableName = "test_" + i;
            String sql = "create table if not exists `" + tableName + "` (\n"
                + "        `xus8hsp` integer(1) not null comment '0rtl',\n"
                + "        `fsg0` bigint(6) unsigned zerofill not null comment 'fthandpbzn34ach',\n"
                + "        `bikb01bdmc4` int(0) unsigned not null,\n"
                + "        primary key using btree (`bikb01bdmc4`, `fsg0` asc),\n"
                + "        index `auto_shard_key_fsg0` using btree(`fsg0`)\n"
                + ") default character set = utf8mb4 default collate = utf8mb4_general_ci\n"
                + "partition by key (`fsg0`)";
            memoryTableMeta.apply(null, "d1", sql, null);
            memoryTableMeta.find("d1", tableName);

        }
        gc();
        printMemory();

        for (int i = 0; i < 10000; i++) {
            String tableName = "test_" + i;
            String sql = "drop table " + tableName;
            memoryTableMeta.apply(null, "d1", sql, null);
            memoryTableMeta.find("d1", tableName);
        }
        gc();
        printMemory();
    }

    @Test
    public void testView() {
        String ddl =
            " create or replace algorithm=undefined definer=`admin`@`%` sql security definer view `v`(`c1`) as select a.pk from select_base_two_one_db_one_tb a join select_base_three_multi_db_one_tb b on a.pk = b.pk";
        List<SQLStatement> stmtList = SQLUtils.parseStatements(ddl, DbType.mysql, FastSQLConstant.FEATURES);
        StringBuffer sb = new StringBuffer();
        stmtList.get(0).clone().output(sb);
        System.out.println(sb);
//        TableMeta t1 = memoryTableMeta.find("andor_qatest_polarx1", "v");
//        Assert.assertNotNull(t1);
    }

    private void gc() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            System.gc();
        }
        Thread.sleep(5000);
    }

    private void printMemory() {
        Runtime run = Runtime.getRuntime();
        double total = run.totalMemory();
        double free = run.freeMemory();
        double used = (total - free) / (1024 * 1024);
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.##");
        System.out.println("已使用内存 = " + df.format(used));
    }
}
