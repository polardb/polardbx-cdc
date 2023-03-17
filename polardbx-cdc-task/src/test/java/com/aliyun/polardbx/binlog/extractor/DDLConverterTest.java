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
package com.aliyun.polardbx.binlog.extractor;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.parser.MySqlStatementParser;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class DDLConverterTest {

    SpringContextBootStrap appContextBootStrap;
    Gson gson = new GsonBuilder().create();

    @Before
    public void init() {
        appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void testDDL() {
        String createTableSql = "CREATE ALGORITHM=UNDEFINED DEFINER=`xsy_admin`@`%` SQL SECURITY\n"
            + "DEFINER VIEW `view_user` AS select `smartcourt-platform`.`t_pl_user_base`.`id` AS `id`,`smartcourt-platform`.`t_pl_user_base`.`name` AS `name` from `smartcourt-platfo\n"
            + "rm`.`t_pl_user_base`;";
        MySqlStatementParser parser = new MySqlStatementParser(createTableSql);
        SQLStatement st = parser.parseStatement();
        System.out.println(st.getClass());
    }

    @Test
    public void testShard() {
        String ddl =
            "CREATE PARTITION TABLE `wp_users_user_email` (\n"
                + "        `ID` bigint(20) UNSIGNED NOT NULL,\n"
                + "        `user_email` varchar(100) COLLATE utf8mb4_unicode_520_ci NOT NULL DEFAULT '',\n"
                + "        PRIMARY KEY (`ID`),\n"
                + "        LOCAL KEY `_local_user_email` USING BTREE (`user_email`)\n"
                + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_unicode_520_ci  dbpartition by hash(`user_email`) ";
        String formatDDL = DDLConverter.convertNormalDDL(ddl, null, "utf8_general_cs", 1, "12345667788");
        System.out.println(formatDDL);
        System.out.println(DDLConverter.formatPolarxDDL(ddl, null,
            "utf8_general_cs", 1));
    }

    @Test
    public void testDatabase() {
        String ddl = "create database d0 partition_mode='partitioning';";
        String formatDDL = DDLConverter.convertNormalDDL(ddl, "utf8mb4", null,
            1, "12345667788");
        System.out.println(formatDDL);
    }

    @Test
    public void testDDLWithComment() {
        String ddl =
            "/* applicationname=datagrip 2021.2.4 */ create database /*!32312 if not exists*/ `slt_single` /*!40100 default character set utf8mb4 */";

//        String ddl =
//            " create database  `slt_single` ";
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(ddl, DbType.mysql, FastSQLConstant.MYSQL_FEATURES);
        List<SQLStatement> statementList = parser.parseStatementList();
        SQLStatement statement = statementList.get(0);
        System.out.println(statement);
    }

    @Test
    public void testBroadcast() {
        String ddl = "CREATE TABLE `bt` (\n" + "\t`id` int(11) NOT NULL AUTO_INCREMENT BY GROUP,\n"
            + "\t`name` varchar(20) DEFAULT NULL,\n" + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB AUTO_INCREMENT = 200006 DEFAULT CHARSET = utf8mb4  broadcast";
        String formatDDL = DDLConverter.convertNormalDDL(ddl, null, "utf8_general_cs",
            1, "12345667788");
        System.out.println(formatDDL);
    }

    @Test
    public void testAlter() {
        String ddl = "alter table test.ACCOUNTS add column TEST_NAME varchar(10) default '测试' CHARACTER SET 'gbk'";
        String formatDDL = DDLConverter.formatPolarxDDL(ddl, null, "gbk", 1);
        System.out.println(formatDDL);
        String ddl1 = "alter table ACCOUNTS modify column A BIGINT(20) default 1";
        System.out.println(DDLConverter.formatPolarxDDL(ddl1, null, "gbk", 1));
        String ddl3 = "alter table ACCOUNTS drop column A";
        System.out.println(DDLConverter.formatPolarxDDL(ddl3, null, "gbk", 1));
        String ddl4 = "alter table ACCOUNTS add index A  (column_list) ";
        System.out.println(DDLConverter.formatPolarxDDL(ddl4, null, "gbk", 1));
        String ddl6 = "alter table ACCOUNTS change column AAA BBB BIGINT(20) default 1";
        System.out.println(DDLConverter.formatPolarxDDL(ddl6, null, "gbk", 1));
        String ddl7 = "alter table ACCOUNTS rename to ACCOUNTS_AAA";
        System.out.println(DDLConverter.formatPolarxDDL(ddl7, null, "gbk", 1));
        String ddl5 = "rename table A to B";
        System.out.println(DDLConverter.formatPolarxDDL(ddl5, null, "gbk", 1));
        System.out.println(DDLConverter.formatPolarxDDL("select 1", null,
            "gbk", 1));
    }

    @Test
    public void testDrdsImplcit() {
        String ddl = "CREATE TABLE t3 (\n" + "\tname varchar(10),\n"
            + "\tINDEX `auto_shard_key_name` USING BTREE(`NAME`(10)),\n"
            + "\t_drds_implicit_id_ bigint AUTO_INCREMENT,\n" + "\tPRIMARY KEY (_drds_implicit_id_)\n" + ")\n"
            + "DBPARTITION BY hash(name)";
        System.out.println(DDLConverter.convertNormalDDL(ddl, null, null,
            1, "12345667788"));
    }

    @Test
    public void testCreateWith() {
        String ddl = "CREATE TABLE `wx_azz_ex_details-2` (\n"
            + "        `fd_id` varchar(255) NOT NULL,\n"
            + "        `fd_add_time` varchar(255) DEFAULT NULL,\n"
            + "        `fd_add_way` varchar(255) DEFAULT NULL COMMENT '对应p_contact的getAddWay,添加方式',\n"
            + "        `fd_avatar` varchar(255) DEFAULT NULL COMMENT '对应p_contact的getAvatar',\n"
            + "        `fd_corp_full_name` varchar(255) DEFAULT NULL COMMENT '公司全称',\n"
            + "        `fd_corp_name` varchar(255) DEFAULT NULL COMMENT '公司名称',\n"
            + "        `fd_creat_time` varchar(255) DEFAULT NULL,\n"
            + "        `fd_external_userid` varchar(255) DEFAULT NULL COMMENT '对应p_contact的getWorkID',\n"
            + "        `fd_gender` varchar(255) DEFAULT NULL COMMENT '对应p_contact的getGender',\n"
            + "        `fd_name` varchar(255) DEFAULT NULL COMMENT '对应p_contact的getNickname',\n"
            + "        `fd_position` varchar(255) DEFAULT NULL COMMENT '职位',\n"
            + "        `fd_remark_copr_name` varchar(255) CHARACTER SET utf8mb4 DEFAULT NULL,\n"
            + "        `fd_remark_mobiles` varchar(255) CHARACTER SET utf8mb4 DEFAULT NULL,\n"
            + "        `fd_spare1` varchar(255) DEFAULT NULL,\n"
            + "        `fd_spare2` varchar(255) DEFAULT NULL,\n"
            + "        `fd_spare3` varchar(255) DEFAULT NULL,\n"
            + "        `fd_state` varchar(255) DEFAULT NULL COMMENT '对应p_contact的getAddWayState',\n"
            + "        `fd_sys_code` varchar(255) DEFAULT NULL,\n"
            + "        `fd_tag` text COMMENT '标签',\n"
            + "        `fd_type` varchar(255) DEFAULT NULL,\n"
            + "        `fd_unionid` varchar(255) DEFAULT NULL COMMENT 'uniodid',\n"
            + "        `fd_update_time` varchar(255) DEFAULT NULL COMMENT '对应p_contact的getUpdateTime',\n"
            + "        `fd_user_des` varchar(255) DEFAULT NULL COMMENT '对应p_contact的 wxId的加密',\n"
            + "        `fd_user_id` varchar(255) DEFAULT NULL COMMENT '对应p_contact的 appId',\n"
            + "        `fd_user_remark` varchar(255) DEFAULT NULL COMMENT '对应p_contact的getAppName',\n"
            + "        PRIMARY KEY (`fd_id`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8 COMMENT '科技中心客户信息中间表'";
        System.out.println(DDLConverter.convertNormalDDL("wx_azz_ex_details-2", ddl, null,
            "utf8_general_ci", 1, "12345667788"));
    }

    @Test
    public void testTableGroup() {
        String ddl =
            "create table `meng``shi1` ( `a` int(11) not null, "
                + "`b` char(1) default null, "
                + "`c` double default null, "
                + "primary key (`a`) )"
                + " engine = innodb default charset = utf8mb4 "
                + " default character set = utf8mb4 default collate = utf8mb4_general_ci tablegroup `tgtest`";
        System.out.println(DDLConverter.convertNormalDDL("abc`abc", ddl, null, null,
            1, "12345667788"));
    }

    @Test
    public void testAddClusterIndex() {
        String sql = "ALTER TABLE `auto_partition_idx_tb`\n"
            + "\tADD INDEX `ap_index` (`id`)";
        System.out.println(DDLConverter.convertNormalDDL(sql, null, null,
            1, "12345667788"));
    }

    @Test
    public void testAddUniqueClusterIndex() {
        String sql = "ALTER TABLE `auto_partition_idx_tb`\n"
            + "\tADD UNIQUE CLUSTERED INDEX `ap_index` (`id`)";
        System.out.println(DDLConverter.convertNormalDDL(sql, null, null,
            1, "12345667788"));
    }

    @Test
    public void testCreateIndex() {
        String sql = "CREATE CLUSTERED INDEX `ap_index` ON `auto_partition_idx_tb` (`id`)";
        System.out.println(DDLConverter.convertNormalDDL(sql, null, null, 1, "12345667788"));

        String sql2 = "CREATE INDEX gsi ON alter_partition_ddl_primary_table (id) PARTITION BY HASH (id)";
        String result2 = DDLConverter.convertNormalDDL(sql2, null, null, 1, "12345667788");
        System.out.println(result2);
        Assert.assertEquals("CREATE INDEX gsi ON alter_partition_ddl_primary_table (id)", result2);

        String sql3 =
            "CREATE INDEX gsi ON alter_partition_ddl_primary_table (id) tbpartition BY HASH (id) tbpartitions 16";
        String result3 = DDLConverter.convertNormalDDL(sql3, null, null, 1, "12345667788");
        System.out.println(result3);
    }

    @Test
    public void testCreateUniqueIndex() {
        String sql = "CREATE UNIQUE CLUSTERED INDEX `ap_index` ON `auto_partition_idx_tb` (`id`)";
        System.out.println(DDLConverter.convertNormalDDL(sql, null, null,
            1, "12345667788"));
    }

    @Test
    public void testCreateLocalIndex() {
        String sql = "CREATE LOCAL INDEX l_i_idx_with_clustered ON auto_idx_with_clustered (i)";
        System.out.println(DDLConverter.convertNormalDDL(sql, null, null,
            1, "12345667788"));

        sql = "ALTER TABLE auto_idx_with_clustered ADD LOCAL INDEX l_i_idx_with_clustered (i)";
        System.out.println(DDLConverter.convertNormalDDL(sql, null, null,
            1, "12345667788"));
    }

    @Test
    public void testHints() {
        String sql =
            "/*+tddl:cmd_extra(allow_alter_gsi_indirectly=true)*//!tddl:enable_recyclebin=true*/drop table test_recyclebin_tb";
        System.out.println(DDLConverter.convertNormalDDL(sql, null, null,
            1, "12345667788"));
    }

    @Test
    public void testEscape() {
        String s1 = "ab``ab";
        String s2 = "ab`ab";
        String s3 = "ab```ab";
        String s4 = "ab````ab";
        String s5 = "abab";

        Assert.assertEquals(CommonUtils.escape(s1), s1);
        Assert.assertEquals(CommonUtils.escape(s2), "ab``ab");
        Assert.assertEquals(CommonUtils.escape(s3), s3);
        Assert.assertEquals(CommonUtils.escape(s4), s4);
        Assert.assertEquals(CommonUtils.escape(s5), s5);
    }

    @Test
    public void testBackQuote1() {
        String dropSql = "drop table `xxx``yyy`";
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(dropSql, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> stmtList = parser.parseStatementList();
        for (SQLExprTableSource tableSource : ((SQLDropTableStatement) stmtList.get(0)).getTableSources()) {
            System.out.println(tableSource.getTableName());
            System.out.println(tableSource.getTableName(true));
        }
    }

    @Test
    public void testBackQuote2() {
        String dropSql = "drop database `xxx``yyy`";
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(dropSql, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> stmtList = parser.parseStatementList();
        String databaseName = ((SQLDropDatabaseStatement) stmtList.get(0)).getDatabaseName();
        System.out.println(databaseName);
        System.out.println(SQLUtils.normalize(databaseName));
        System.out.println(SQLUtils.normalize(databaseName, false));
        System.out.println(SQLUtils.normalize("uuu"));
        System.out.println(SQLUtils.normalize("uu`u"));
    }

    @Test
    public void testBackQuote3() {
        String dropSql = "rename table aaa to `bbb``ccc`";
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(dropSql, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> stmtList = parser.parseStatementList();
        String renameTo = ((MySqlRenameTableStatement) stmtList.get(0)).getItems().get(0).getTo()
            .getSimpleName();
        System.out.println(renameTo);
        System.out.println(SQLUtils.normalize(renameTo));
    }

    @Test
    public void testPurge() {
        String dropSql = "DROP TABLE IF EXISTS test_recycle_broadcast_tb PURGE";
        System.out.println(DDLConverter.convertNormalDDL(dropSql, null, null,
            1, "12345667788"));
    }

    @Test
    public void testAutoincrementGroup() {
        String sql = "ALTER TABLE alter_table_without_seq_change\n"
            + "\tMODIFY COLUMN c1 bigint UNSIGNED NOT NULL AUTO_INCREMENT BY GROUP";
        System.out.println(DDLConverter.convertNormalDDL(sql, null, null,
            1, "12345667788"));

        String sql2 = "ALTER TABLE alter_table_without_seq_change\n"
            + "\tADD COLUMN c1 bigint UNSIGNED NOT NULL AUTO_INCREMENT BY GROUP";
        System.out.println(DDLConverter.convertNormalDDL(sql2, null, null,
            1, "12345667788"));
    }

    @Test
    public void testWithHints() {
        String sql = "/* CDC_TOKEN : 0644b5a0-1ce9-43f9-8b62-62d0a0f59d72 */\n"
            + "CREATE TABLE IF NOT EXISTS `t_ddl_test_normal` (\n"
            + "\t`ID` BIGINT(20) NOT NULL AUTO_INCREMENT,\n"
            + "\t`JOB_ID` BIGINT(20) NOT NULL DEFAULT 0,\n"
            + "\t`EXT_ID` BIGINT(20) NOT NULL DEFAULT 0,\n"
            + "\t`TV_ID` BIGINT(20) NOT NULL DEFAULT 0,\n"
            + "\t`SCHEMA_NAME` VARCHAR(200) NOT NULL,\n"
            + "\t`TABLE_NAME` VARCHAR(200) NOT NULL,\n"
            + "\t`GMT_CREATED` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
            + "\t`DDL_SQL` TEXT NOT NULL,\n"
            + "\tPRIMARY KEY (`ID`),\n"
            + "\tKEY `idx1` (`SCHEMA_NAME`),\n"
            + "\tINDEX `auto_shard_key_job_id` USING BTREE(`JOB_ID`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4\n"
            + "DBPARTITION BY hash(ID)\n"
            + "TBPARTITION BY hash(JOB_ID) TBPARTITIONS 16";
        System.out.println(DDLConverter.convertNormalDDL(sql, null, null, 1, "123456666"));
    }

    @Test
    public void testImplicit() {
        String sql = "CREATE TABLE `zqhz0kzsecxfgdf` (\n"
            + "  `zsjzmjsoidxxtr` int(6) unsigned zerofill DEFAULT NULL,\n"
            + "  `7jg0ekks` int(6) unsigned zerofill DEFAULT NULL,\n"
            + "  `3pf6xdowmaf` int(6) unsigned zerofill DEFAULT NULL,\n"
            + "  `hkqh6gd` int(6) unsigned zerofill DEFAULT NULL,\n"
            + "  _drds_implicit_id_ bigint(20) NOT NULL AUTO_INCREMENT,\n"
            + "  PRIMARY KEY (_drds_implicit_id_)\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        System.out.println(DDLConverter.convertNormalDDL(sql, null, null, 1, "123456666"));
    }

    @Test
    public void testConstraint() {
        String sql = "alter table omc_change_column_ordinal_test_tbl change column c cc bigint first ALGORITHM=OMC ";
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(sql, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> stmtList = parser.parseStatementList();
        System.out.println(stmtList.get(0).getClass());
        SQLAlterTableStatement sqlAlterTableStatement = (SQLAlterTableStatement) stmtList.get(0);
        for (SQLAlterTableItem item : sqlAlterTableStatement.getItems()) {
            System.out.println(item.getClass());
        }
    }

    @Test
    public void testModifyColumn() {
        String sql1 = "alter table nnn change column b bb bigint ALGORITHM=OMC";
        String sql2 = DDLConverter.convertNormalDDL(sql1, null, null, 0, "");
        System.out.println(sql2);

        String sql3 = "alter table nnn change column b bb bigint ALGORITHM=XXX";
        String sql4 = DDLConverter.convertNormalDDL(sql3, null, null, 0, "");
        System.out.println(sql4);

        String sql5 = "ALTER TABLE column_backfill_ts_tbl\n"
            + "  MODIFY COLUMN c1_1 timestamp(6) DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),\n"
            + "  ALGORITHM = omc";
        String sql6 = DDLConverter.convertNormalDDL(sql5, null, null, 0, "");
        System.out.println(sql6);
    }

    @Test
    public void testSplitPartition() {
        String sql1 = "alter table t1 split partition p1";
        String sql2 = DDLConverter.convertNormalDDL(sql1, null, null, 0, "");
        System.out.println(sql2);
    }

    @Test
    public void testSplitPartitionxx() {
        String sql =
            "ALTER TABLE t_range_table_1660185492145 SPLIT PARTITION p1 INTO (PARTITION p10 VALUES LESS THAN (1994),\n"
                + " PARTITION p11 VALUES LESS THAN(1996),\n"
                + " PARTITION p12 VALUES LESS THAN(2000))";
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(sql, DbType.mysql, FastSQLConstant.FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);
    }

    @Test
    public void testAlterPartition() {
        String sql =
            "/* cdc_token : a12ea068-626b-4a6c-b6a5-47e19c92b89f */alter tablegroup tg118 modify partition p0 add values (1988,1989)";
        reformatDDL(sql);
    }

    @Test
    public void testAlterPartitionAlignTo() {
        String sql =
            " alter table pt_k_2 partition align to t_a_t_s_tg2";
        reformatDDL(sql);
    }

    private void reformatDDL(String ddl) {
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(ddl, DbType.mysql, FastSQLConstant.FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);
        System.out.println(stmt.toString());
        parser =
            SQLParserUtils.createSQLStatementParser(stmt.toString(), DbType.mysql, FastSQLConstant.FEATURES);
        System.out.println(parser.parseStatementList().get(0));
    }

    @Test
    public void testPrivateDDLSwitch() {
        DynamicApplicationConfig.setValue(ConfigKeys.TASK_DDL_PRIVATEDDL_SUPPORT, "true");
        String sql1 = "alter table nnn change column b bb bigint ALGORITHM=OMC";
        String sql2 = DDLConverter.convertNormalDDL(sql1, null, null, 0, "");
        Assert.assertTrue(sql2.contains("# POLARX_ORIGIN_SQL="));
        Assert.assertTrue(sql2.contains("# POLARX_TSO="));
        DynamicApplicationConfig.setValue(ConfigKeys.TASK_DDL_PRIVATEDDL_SUPPORT, "false");
        String sql3 = "alter table nnn change column b bb bigint ALGORITHM=XXX";
        String sql4 = DDLConverter.convertNormalDDL(sql3, null, null, 0, "");
        Assert.assertFalse(sql4.contains("# POLARX_ORIGIN_SQL="));
        Assert.assertFalse(sql4.contains("# POLARX_TSO="));

    }
}
