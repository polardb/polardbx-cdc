/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.DrdsAlterTableRebuildCleanup;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCallStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLGrantStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAlterTableAlterFullTextIndex;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlLockTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlUnlockTablesStatement;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.SQLException;

import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;
import static com.aliyun.polardbx.binlog.util.SQLUtils.removeSomeHints;
import static com.aliyun.polardbx.binlog.util.SQLUtils.toSQLStringWithTrueUcase;

/**
 * created by ziyang.lb
 **/
public class SQLUtilsTest extends BaseTest {

    @Test
    public void testSubPartitionToString() {
        String sql = "create table if not exists `t_hash_hash_not_template_1690868091444` ("
            + "a bigint unsigned not null,"
            + "b bigint unsigned not null,"
            + "c datetime NOT NULL,"
            + "d varchar(16) NOT NULL,"
            + "e varchar(16) NOT NULL) "
            + "partition by hash (c,d) partitions 2 subpartition by hash (a,b) "
            + "(partition p1 subpartitions 2,partition p2 subpartitions 4)";
        String expectSql = "CREATE TABLE IF NOT EXISTS `t_hash_hash_not_template_1690868091444` (\n"
            + "\ta bigint UNSIGNED NOT NULL,\n"
            + "\tb bigint UNSIGNED NOT NULL,\n"
            + "\tc datetime NOT NULL,\n"
            + "\td varchar(16) NOT NULL,\n"
            + "\te varchar(16) NOT NULL\n"
            + ")\n"
            + "PARTITION BY HASH (c, d) PARTITIONS 2\n"
            + "SUBPARTITION BY HASH (a, b) (\n"
            + "\tPARTITION p1\n"
            + "\t\tSUBPARTITIONS 2, \n"
            + "\tPARTITION p2\n"
            + "\t\tSUBPARTITIONS 4\n"
            + ")";

        //check toString
        SQLStatement stmt = SQLUtils.parseSQLStatement(sql);
        Assert.assertEquals(expectSql, stmt.toString());

        //check output
        StringBuffer sb = new StringBuffer();
        stmt.output(sb);
        Assert.assertEquals(expectSql, sb.toString());
    }

    @Test
    public void testSubPartitionToString2() {
        // @see VisitorFeature.OutputHashPartitionsByRange
        String sql =
            "create table if not exists k_k_tp6 ( "
                + "a bigint unsigned not null, "
                + "b bigint unsigned not null, "
                + "c datetime NOT NULL, "
                + "d varchar(16) NOT NULL, "
                + "e varchar(16) NOT NULL ) "
                + "partition by key (c,d) partitions 3 subpartition by key (a,b) "
                + "( "
                + "subpartition sp1 values less than (0,9223372036854775807), "
                + "subpartition sp2 values less than (3,9223372036854775807), "
                + "subpartition sp3 values less than (4611686018427387905,9223372036854775807), "
                + "subpartition sp4 values less than (9223372036854775807,9223372036854775807) ) "
                + "( "
                + "partition p1 values less than (3,9223372036854775807), "
                + "partition p2 values less than (4611686018427387905,9223372036854775807), "
                + "partition p3 values less than (9223372036854775807,9223372036854775807) )";
        SQLStatement stmt = SQLUtils.parseSQLStatement(sql);
        String expectedSql = "CREATE TABLE IF NOT EXISTS k_k_tp6 (\n"
            + "\ta bigint UNSIGNED NOT NULL,\n"
            + "\tb bigint UNSIGNED NOT NULL,\n"
            + "\tc datetime NOT NULL,\n"
            + "\td varchar(16) NOT NULL,\n"
            + "\te varchar(16) NOT NULL\n"
            + ")\n"
            + "PARTITION BY KEY (c, d) PARTITIONS 3\n"
            + "SUBPARTITION BY KEY (a, b) (\n"
            + "\tSUBPARTITION sp1 VALUES LESS THAN (0, 9223372036854775807), \n"
            + "\tSUBPARTITION sp2 VALUES LESS THAN (3, 9223372036854775807), \n"
            + "\tSUBPARTITION sp3 VALUES LESS THAN (4611686018427387905, 9223372036854775807), \n"
            + "\tSUBPARTITION sp4 VALUES LESS THAN (9223372036854775807, 9223372036854775807)\n"
            + ") (\n"
            + "\tPARTITION p1 VALUES LESS THAN (3, 9223372036854775807), \n"
            + "\tPARTITION p2 VALUES LESS THAN (4611686018427387905, 9223372036854775807), \n"
            + "\tPARTITION p3 VALUES LESS THAN (9223372036854775807, 9223372036854775807)\n"
            + ")";
        Assert.assertEquals(expectedSql, stmt.toString());

        String expectedSql2 =
            "CREATE TABLE IF NOT EXISTS k_k_tp6 ( a bigint UNSIGNED NOT NULL, b bigint UNSIGNED NOT NULL, c datetime NOT NULL, d varchar(16) NOT NULL, e varchar(16) NOT NULL ) PARTITION BY KEY (c, d) PARTITIONS 3 SUBPARTITION BY KEY (a, b) ( SUBPARTITION sp1 VALUES LESS THAN (0, 9223372036854775807),  SUBPARTITION sp2 VALUES LESS THAN (3, 9223372036854775807),  SUBPARTITION sp3 VALUES LESS THAN (4611686018427387905, 9223372036854775807),  SUBPARTITION sp4 VALUES LESS THAN (9223372036854775807, 9223372036854775807) ) ( PARTITION p1 VALUES LESS THAN (3, 9223372036854775807),  PARTITION p2 VALUES LESS THAN (4611686018427387905, 9223372036854775807),  PARTITION p3 VALUES LESS THAN (9223372036854775807, 9223372036854775807) )";
        Assert.assertEquals(expectedSql2, toSQLStringWithTrueUcase(stmt));
    }

    @Test
    public void testSubPartitionToString3() {
        String sql = "create table if not exists k_k_tp5 ( "
            + "a bigint unsigned not null, "
            + "b bigint unsigned not null, "
            + "c datetime NOT NULL, "
            + "d varchar(16) NOT NULL, "
            + "e varchar(16) NOT NULL ) "
            + "partition by key (c,d) partitions 3 "
            + "subpartition by key (a,b) subpartitions 4 ( "
            + "     subpartition sp0, "
            + "     subpartition sp1, "
            + "     subpartition sp2, "
            + "     subpartition sp3 "
            + ") ( "
            + "     partition p0, "
            + "     partition p1, "
            + "     partition p2 )";
        SQLStatement stmt = SQLUtils.parseSQLStatement(sql);
        String expectSql = "CREATE TABLE IF NOT EXISTS k_k_tp5 (\n"
            + "\ta bigint UNSIGNED NOT NULL,\n"
            + "\tb bigint UNSIGNED NOT NULL,\n"
            + "\tc datetime NOT NULL,\n"
            + "\td varchar(16) NOT NULL,\n"
            + "\te varchar(16) NOT NULL\n"
            + ")\n"
            + "PARTITION BY KEY (c, d) PARTITIONS 3\n"
            + "SUBPARTITION BY KEY (a, b) SUBPARTITIONS 4 (\n"
            + "\tSUBPARTITION sp0, \n"
            + "\tSUBPARTITION sp1, \n"
            + "\tSUBPARTITION sp2, \n"
            + "\tSUBPARTITION sp3\n"
            + ") (\n"
            + "\tPARTITION p0, \n"
            + "\tPARTITION p1, \n"
            + "\tPARTITION p2\n"
            + ")";
        Assert.assertEquals(expectSql, stmt.toString());

        String expectSql2 = "CREATE TABLE IF NOT EXISTS k_k_tp5 ( "
            + "a bigint UNSIGNED NOT NULL, "
            + "b bigint UNSIGNED NOT NULL, "
            + "c datetime NOT NULL, "
            + "d varchar(16) NOT NULL, "
            + "e varchar(16) NOT NULL ) "
            + "PARTITION BY KEY (c, d) PARTITIONS 3 SUBPARTITION BY KEY (a, b) SUBPARTITIONS 4 ( "
            + "SUBPARTITION sp0,  "
            + "SUBPARTITION sp1,  "
            + "SUBPARTITION sp2,  "
            + "SUBPARTITION sp3 ) ( PARTITION p0,  PARTITION p1,  PARTITION p2 )";
        Assert.assertEquals(expectSql2, SQLUtils.toSQLStringWithTrueUcase(stmt));
    }

    @Test
    public void testSubPartitionToString4() {
        String sql = "create table if not exists k_lc_tp2 ( "
            + "     a bigint unsigned not null, "
            + "     b bigint unsigned not null, "
            + "     c datetime NOT NULL, "
            + "     d varchar(16) NOT NULL, "
            + "     e varchar(16) NOT NULL "
            + ") "
            + "partition by key (c,d) "
            + "subpartition by list columns (a,b) ( "
            + "     subpartition sp0 values in ((5,5),(6,6)), "
            + "     subpartition sp1 values in ((7,7),(8,8))) "
            + "( partition p1, partition p2 )";
        SQLStatement stmt = SQLUtils.parseSQLStatement(sql);
        String expectSql = "CREATE TABLE IF NOT EXISTS k_lc_tp2 (\n"
            + "\ta bigint UNSIGNED NOT NULL,\n"
            + "\tb bigint UNSIGNED NOT NULL,\n"
            + "\tc datetime NOT NULL,\n"
            + "\td varchar(16) NOT NULL,\n"
            + "\te varchar(16) NOT NULL\n"
            + ")\n"
            + "PARTITION BY KEY (c, d)\n"
            + "SUBPARTITION BY LIST COLUMNS (a, b) (\n"
            + "\tSUBPARTITION sp0 VALUES IN ((5, 5), (6, 6)),\n"
            + "\tSUBPARTITION sp1 VALUES IN ((7, 7), (8, 8))\n"
            + ") (\n"
            + "\tPARTITION p1, \n"
            + "\tPARTITION p2\n"
            + ")";
        Assert.assertEquals(expectSql, stmt.toString());

        String expectSql2 = "CREATE TABLE IF NOT EXISTS k_lc_tp2 ( "
            + "a bigint UNSIGNED NOT NULL, "
            + "b bigint UNSIGNED NOT NULL, "
            + "c datetime NOT NULL, "
            + "d varchar(16) NOT NULL, "
            + "e varchar(16) NOT NULL ) "
            + "PARTITION BY KEY (c, d) SUBPARTITION BY LIST COLUMNS (a, b) ( "
            + "SUBPARTITION sp0 VALUES IN ((5, 5), (6, 6)), "
            + "SUBPARTITION sp1 VALUES IN ((7, 7), (8, 8)) ) ( PARTITION p1,  PARTITION p2 )";
        Assert.assertEquals(expectSql2, SQLUtils.toSQLStringWithTrueUcase(stmt));
    }

    @Test
    public void testCreateView() {
        String ddl = "create or replace algorithm=undefined definer=`admin`@`%` "
            + "sql security definer view `v` as select * from select_base_two_one_db_one_tb";
        SQLStatement stmt = parseSQLStatement(ddl);
        Assert.assertEquals(stmt.getClass(), com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateViewStatement.class);

        // check if parse failed
        ddl = "CREATE ALGORITHM=UNDEFINED DEFINER=`xsy_admin`@`%` SQL SECURITY\n DEFINER VIEW `view_user` AS "
            + " select "
            + "   `smartcourt-platform`.`t_pl_user_base`.`id` AS `id`,"
            + "   `smartcourt-platform`.`t_pl_user_base`.`name` AS `name` "
            + " from `smartcourt-platform`.`t_pl_user_base`;";
        parseSQLStatement(ddl);
    }

    @Test
    public void testMovePartitions() {
        String ddl = "ALTER TABLE ydy MOVE PARTITIONS `p1` TO `pxc-xdb-s-hzrnulgc5eujhq1485`;";
        parseSQLStatement(ddl);
    }

    @Test
    public void testSplitPartition() {
        String sql = "ALTER TABLE t_range_table_1660185492145 "
            + " SPLIT PARTITION p1 INTO (PARTITION p10 VALUES LESS THAN (1994),\n"
            + " PARTITION p11 VALUES LESS THAN(1996),\n"
            + " PARTITION p12 VALUES LESS THAN(2000))";
        parseSQLStatement(sql);
    }

    @Test
    public void testLockUnLockTable() {
        String sql = "LOCK TABLES `binlog_dumper_info` WRITE";
        SQLStatement stmt = parseSQLStatement(sql);
        Assert.assertEquals(stmt.getClass(), MySqlLockTableStatement.class);

        sql = "UNLOCK TABLES";
        stmt = parseSQLStatement(sql);
        Assert.assertEquals(stmt.getClass(), MySqlUnlockTablesStatement.class);
    }

    @Test
    public void testBackQuote() {
        String sql = "rename table aaa to `bbb``ccc`";
        SQLStatement stmt = parseSQLStatement(sql);
        String renameTo = ((MySqlRenameTableStatement) stmt).getItems().get(0).getTo().getSimpleName();
        Assert.assertEquals("`bbb``ccc`", renameTo);
        Assert.assertEquals("bbb`ccc", com.alibaba.polardbx.druid.sql.SQLUtils.normalize(renameTo));

        sql = "drop table `xxx``yyy`";
        stmt = parseSQLStatement(sql);
        for (SQLExprTableSource tableSource : ((SQLDropTableStatement) stmt).getTableSources()) {
            Assert.assertEquals("`xxx``yyy`", tableSource.getTableName());
            Assert.assertEquals("xxx`yyy", tableSource.getTableName(true));
        }

        sql = "drop database `xxx``yyy`";
        stmt = parseSQLStatement(sql);
        String databaseName = ((SQLDropDatabaseStatement) stmt).getDatabaseName();
        Assert.assertEquals("`xxx``yyy`", databaseName);
        Assert.assertEquals("xxx`yyy", com.alibaba.polardbx.druid.sql.SQLUtils.normalize(databaseName));
        Assert.assertEquals("xxx`yyy", com.alibaba.polardbx.druid.sql.SQLUtils.normalize(databaseName, false));
    }

    @Test
    public void testDDLDropTable() {
        String ddl = "drop table aaa_A;";
        SQLDropTableStatement statement = (SQLDropTableStatement) parseSQLStatement(ddl);
        for (SQLExprTableSource expr : statement.getTableSources()) {
            Assert.assertEquals("aaa_A", expr.getTableName());
        }
    }

    @Test
    public void testDDLCreateTable() {
        String ddl = "create table test_abcdefgAAA(id int primary key auto_increment, name varchar(20))";
        SQLCreateTableStatement statement = (SQLCreateTableStatement) parseSQLStatement(ddl);
        Assert.assertEquals("test_abcdefgAAA", statement.getTableName());
    }

    @Test
    public void testDDLCreateDatabase() {
        String ddl = "create database test_DDD";
        SQLCreateDatabaseStatement statement = (SQLCreateDatabaseStatement) parseSQLStatement(ddl);
        Assert.assertEquals("test_DDD", statement.getDatabaseName());
    }

    @Test
    public void testDDLRenameTable() {
        String ddl = "rename table table_aaaA to table_BBBB";
        MySqlRenameTableStatement statement = (MySqlRenameTableStatement) parseSQLStatement(ddl);
        Assert.assertEquals("table_aaaA", statement.getItems().get(0).getName().getSimpleName());
        Assert.assertEquals("table_BBBB", statement.getItems().get(0).getTo().getSimpleName());
    }

    @Test
    public void testChinese() {
        String sql = "CREATE INDEX 检查时间 ON yyk_pacs_patient (examine_time,hid,examine_type)";
        SQLStatement statement = parseSQLStatement(sql);
        SQLCreateIndexStatement createIndexStatement = (SQLCreateIndexStatement) statement;
        Assert.assertEquals("检查时间", createIndexStatement.getName().getSimpleName());
    }

    @Test
    public void testExecutalbeComment() {
        try {
            String sql1 = "create database /*!32312 if not exists*/ "
                + "`slt_single` /*!40100 default character set utf8mb4 */";
            parseSQLStatement(sql1);
        } catch (Exception e) {
            //TODO
        }
    }

    @Test
    public void testReWriteWrongSql() {
        String ddl = "alter table `payment_voucher` drop key `out_voucher_id`,drop key pay_no\n"
            + "add index key `uid_pay_no`(`user_id`,`pay_no`) using btree\n"
            + "add unique key `uid_voucherid_source`(`user_id`,`out_voucher_id`,`source`) using btree";

        String expect = "alter table `payment_voucher` drop key `out_voucher_id`,drop key pay_no \n"
            + "add index `uid_pay_no`(`user_id`,`pay_no`) using btree \n"
            + "add unique key `uid_voucherid_source`(`user_id`,`out_voucher_id`,`source`) using btree";
        String newDdl = SQLUtils.reWriteWrongDdl(ddl);
        Assert.assertEquals(expect, newDdl);
    }

    @Test
    public void testCreteFunction() {
        String sql = "create function auto_gen_add(num1 int, num2 int)\n"
            + "  returns int\n"
            + "begin\n"
            + "  return num1 + num2;\n"
            + "end";
        SQLStatement sqlStatement = SQLUtils.parseSQLStatement(sql);
        System.out.println(sqlStatement.toString());
    }

    @Test
    public void testHintsInCreateIndex() {
        String sql = "/*+TDDL:CMD_EXTRA(ENABLE_CREATE_EXPRESSION_INDEX=TRUE,ENABLE_UNIQUE_KEY_ON_GEN_COL=TRUE)*/"
            + "create local unique index expr_multi_column_tbl_idx on expr_multi_column_tbl((a+1) desc,b,c-1,substr(d,-2) asc,a+b+c*2)";
        SQLStatement sqlStatement = SQLUtils.parseSQLStatement(sql);
        Assert.assertTrue(StringUtils.contains(sqlStatement.toString(),
            "/*+TDDL:CMD_EXTRA(ENABLE_CREATE_EXPRESSION_INDEX=TRUE,ENABLE_UNIQUE_KEY_ON_GEN_COL=TRUE)*/"));

        sql = "/*+TDDL:CMD_EXTRA(ENABLE_CREATE_EXPRESSION_INDEX=TRUE,ENABLE_UNIQUE_KEY_ON_GEN_COL=TRUE)*/"
            + "alter table expr_multi_column_tbl add local unique index expr_multi_column_tbl_idx((a+1) desc,b,c-1,substr(d,-2) asc,a+b+c*2)";
        sqlStatement = SQLUtils.parseSQLStatement(sql);
        Assert.assertTrue(StringUtils.contains(sqlStatement.toString(),
            "/*+TDDL:CMD_EXTRA(ENABLE_CREATE_EXPRESSION_INDEX=TRUE,ENABLE_UNIQUE_KEY_ON_GEN_COL=TRUE)*/"));
    }

    @Test
    public void testTruncateSubPartition() {
        String sql = "alter table t_key_key_template_1694514577176 truncate subpartition p1sp1";
        SQLStatement stmt = SQLUtils.parseSQLStatement(sql);
        String expectSql = "ALTER TABLE t_key_key_template_1694514577176\n"
            + "\tTRUNCATE SUBPARTITION p1sp1";
        Assert.assertEquals(expectSql, stmt.toString());
    }

    @Test
    public void testAlterTableAddTablesWithForce() {
        String sql = "ALTER TABLEGROUP mytg2 ADD TABLES tb1, tb2, tb_add_3, tb_add_4, tb_add_5 force";
        SQLStatement stmt = SQLUtils.parseSQLStatement(sql);
        String expectSql = "ALTER TABLEGROUP mytg2 ADD TABLES tb1, tb2, tb_add_3, tb_add_4, tb_add_5 FORCE ";
        Assert.assertEquals(expectSql, stmt.toString());
    }

    @Test
    public void testMergeTableGroup() {
        String sql = " MERGE TABLEGROUPS mytg2v INTO mytg1qItM FORCE";
        SQLStatement statement = parseSQLStatement(sql);
        Assert.assertEquals("MERGE TABLEGROUPS mytg2v INTO mytg1qItM FORCE", statement.toString());
    }

    @Test
    public void testAlterIndexToString() {
        String sql = "alter index idx_name on table tb3 split partition p4";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        Assert.assertEquals("ALTER INDEX idx_name ON TABLE tb3 SPLIT PARTITION p4 ",
            toSQLStringWithTrueUcase(statement));
        Assert.assertEquals("ALTER INDEX idx_name ON TABLE tb3\n"
            + "\tSPLIT PARTITION p4 ", statement.toString());
    }

    @Test
    public void testSqlWithHashComment() {
        String sql =
            "/*DDL_ID=7481263829662302272*/\n"
                + "# ===== hash line comment =====\n"
                + "/*+TDDL:cmd_extra(SEQUENTIAL_CONCURRENT_POLICY=true)*/\n"
                + "ALTER TABLE k_line_mark_1s\n"
                + "        ADD COLUMN data_source TINYINT NOT NULL DEFAULT 0 COMMENT 'test'";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);

        String result = SQLUtils.toSQLStringWithTrueUcase(statement);
        Assert.assertFalse("result should be single-line after # line comment conversion: " + result,
            result.contains("\n"));
        Assert.assertFalse("result should not contain # line comment after conversion: " + result,
            result.contains("# ===== hash line comment ====="));
        Assert.assertTrue("# line comment should be converted to block comment: " + result,
            result.contains("/* ===== hash line comment =====*/"));
        Assert.assertTrue("ALTER TABLE should be preserved after converted block comment: " + result,
            StringUtils.containsIgnoreCase(result, "ALTER TABLE k_line_mark_1s"));
    }

    @Test
    public void testHyphenInSingleQuoteString() {
        // -- 在单引号 COMMENT 字符串内，不是行注释，不应触发回退
        String sql = "CREATE TABLE t_hyphen_in_str (id int COMMENT 'use -- for decrement', name varchar(20))";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        String result = SQLUtils.toSQLStringWithTrueUcase(statement);
        Assert.assertFalse("should be single-line when -- is inside single-quoted string: " + result,
            result.contains("\n"));
        Assert.assertTrue("COMMENT string with -- should be preserved: " + result,
            result.contains("-- for decrement"));
    }

    @Test
    public void testHashInSingleQuoteString() {
        // # 在单引号 COMMENT 字符串内，不是行注释，不应触发回退
        String sql = "CREATE TABLE t_hash_in_str (id int COMMENT 'price #1', name varchar(20))";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        String result = SQLUtils.toSQLStringWithTrueUcase(statement);
        Assert.assertFalse("should be single-line when # is inside single-quoted string: " + result,
            result.contains("\n"));
        Assert.assertTrue("COMMENT string with # should be preserved: " + result,
            result.contains("#1"));
    }

    @Test
    public void testHyphenInBacktickIdentifier() {
        // -- 在反引号标识符内，不是行注释，不应触发回退
        String sql = "CREATE TABLE `t--test` (id int, name varchar(20))";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        String result = SQLUtils.toSQLStringWithTrueUcase(statement);
        Assert.assertFalse("should be single-line when -- is inside backtick identifier: " + result,
            result.contains("\n"));
        Assert.assertTrue("backtick identifier with -- should be preserved: " + result,
            result.contains("t--test"));
    }

    @Test
    public void testHyphenInDoubleQuoteString() {
        // -- 在双引号字符串内，不是行注释，不应触发回退
        String sql = "CREATE TABLE t_hyphen_in_dq (id int COMMENT \"use -- for decrement\", name varchar(20))";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        String result = SQLUtils.toSQLStringWithTrueUcase(statement);
        Assert.assertFalse("should be single-line when -- is inside double-quoted string: " + result,
            result.contains("\n"));
    }

    @Test
    public void testEscapedQuoteWithHyphen() {
        // 转义引号 '' 内的 -- ，不应触发回退
        String sql = "CREATE TABLE t_escape_hyphen (id int COMMENT 'it''s -- not comment', name varchar(20))";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        String result = SQLUtils.toSQLStringWithTrueUcase(statement);
        Assert.assertFalse("should be single-line when -- is inside escaped quote: " + result,
            result.contains("\n"));
        Assert.assertTrue("escaped quote content should be preserved: " + result,
            result.contains("-- not comment"));
    }

    @Test
    public void testDoubleHyphenWithoutWhitespaceIsNotLineComment() {
        // MySQL 规则要求 -- 后必须跟空白字符，--1 不是行注释
        String sql = "SELECT 1--1";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        String result = SQLUtils.toSQLStringWithTrueUcase(statement);
        Assert.assertFalse("-- without whitespace should not be converted to block comment: " + result,
            result.contains("/*"));
        Assert.assertFalse("result should be single-line: " + result, result.contains("\n"));
    }

    // --------------------------
    // 私有方法边界测试：CDC 侧只负责识别真实行注释风险，转换由 parser 侧完成
    // --------------------------

    private static boolean invokeContainsLineComment(String sql) throws Exception {
        Method m = SQLUtils.class.getDeclaredMethod("containsLineComment", String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, sql);
    }

    private static boolean invokeShouldFallbackForLineComment(String sql) throws Exception {
        Method m = SQLUtils.class.getDeclaredMethod("shouldFallbackForLineComment", String.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, sql);
    }

    @Test
    public void testShouldFallbackForLineCommentSwitch() throws Exception {
        String formattedResultWithLineComment = "-- hyphen comment\nALTER TABLE t ADD COLUMN c1 INT";
        // 模拟 parser 漏网后的格式化结果：开关开启时，CDC 侧需要触发 prettyFormat 回退
        mockedAppConfig.when(() -> DynamicApplicationConfig.getValue(
            ConfigKeys.BINLOG_DDL_LINE_COMMENT_DEFENSE_ENABLED)).thenReturn("true");
        Assert.assertTrue("line comment defense should fallback when switch is enabled",
            invokeShouldFallbackForLineComment(formattedResultWithLineComment));

        // 模拟 parser 漏网后的格式化结果：开关关闭时，CDC 侧不再触发回退
        mockedAppConfig.when(() -> DynamicApplicationConfig.getValue(
            ConfigKeys.BINLOG_DDL_LINE_COMMENT_DEFENSE_ENABLED)).thenReturn("false");
        Assert.assertFalse("line comment defense should not fallback when switch is disabled",
            invokeShouldFallbackForLineComment(formattedResultWithLineComment));
    }

    @Test
    public void testShouldFallbackForLineCommentUsesDefaultTrueWhenConfigMissing() throws Exception {
        // 配置缺失时默认安全开启；这里直接模拟 parser 漏网后的 formatted result，而不是正常 parser 转换路径
        mockedAppConfig.when(() -> DynamicApplicationConfig.getValue(
            ConfigKeys.BINLOG_DDL_LINE_COMMENT_DEFENSE_ENABLED)).thenReturn("");
        Assert.assertTrue("line comment defense should fallback by default when config is missing",
            invokeShouldFallbackForLineComment("# hash comment\nALTER TABLE t ADD COLUMN c1 INT"));
    }

    @Test
    public void testShouldFallbackForLineCommentDoesNotFallbackWithoutLineComment() throws Exception {
        mockedAppConfig.when(() -> DynamicApplicationConfig.getValue(
            ConfigKeys.BINLOG_DDL_LINE_COMMENT_DEFENSE_ENABLED)).thenReturn("true");
        Assert.assertFalse("line comment defense should not fallback when result has no line comment",
            invokeShouldFallbackForLineComment("/* block comment */ ALTER TABLE t ADD COLUMN c1 INT"));
    }

    @Test
    public void testContainsLineCommentSkipBlockComment() throws Exception {
        String input = "/* block with -- and # inside */ ALTER TABLE t ADD COLUMN c1 INT";
        Assert.assertFalse("containsLineComment should skip block comment content",
            invokeContainsLineComment(input));
    }

    @Test
    public void testContainsLineCommentDetectsHyphenAndHash() throws Exception {
        Assert.assertTrue("should detect -- line comment",
            invokeContainsLineComment("-- hyphen comment\nALTER TABLE t ADD COLUMN c1 INT"));
        Assert.assertTrue("should detect # line comment",
            invokeContainsLineComment("# hash comment\nALTER TABLE t ADD COLUMN c1 INT"));
        Assert.assertTrue("should detect -- line comment with CRLF",
            invokeContainsLineComment("-- hyphen comment\r\nALTER TABLE t ADD COLUMN c1 INT"));
        Assert.assertTrue("should detect # line comment at EOF",
            invokeContainsLineComment("ALTER TABLE t ADD COLUMN c1 INT\n# hash comment without newline"));
    }

    @Test
    public void testContainsLineCommentSkipQuotedContent() throws Exception {
        Assert.assertFalse("should skip -- inside single quoted string",
            invokeContainsLineComment("CREATE TABLE t (c varchar(20) COMMENT 'use -- as text')"));
        Assert.assertFalse("should skip # inside single quoted string",
            invokeContainsLineComment("CREATE TABLE t (c varchar(20) COMMENT 'price #1')"));
        Assert.assertFalse("should skip -- inside backtick identifier",
            invokeContainsLineComment("CREATE TABLE `t--name` (id int)"));
    }

    @Test
    public void testContainsLineCommentDoesNotTreatDoubleHyphenWithoutWhitespaceAsComment() throws Exception {
        Assert.assertFalse("-- without following whitespace is not MySQL line comment",
            invokeContainsLineComment("SELECT 1--1"));
    }

    @Test
    public void testSqlWithBeforeComment() {
        String sql = "#xxyyzz\n"
            + "/* //1/ */ /* //2/ */ /* //3/ */"
            + "CREATE TABLE if not exists cp1_ddl2_1573919236.gxw_test_102 (id int,name varchar(30),primary key(id))";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);

        String expectSql = "#xxyyzz\n"
            + "\n"
            + "/* //1/ */\n"
            + "/* //2/ */\n"
            + "/* //3/ */\n"
            + "CREATE TABLE IF NOT EXISTS cp1_ddl2_1573919236.gxw_test_102 (\n"
            + "\tid int,\n"
            + "\tname varchar(30),\n"
            + "\tPRIMARY KEY (id)\n"
            + ")";
        Assert.assertEquals(expectSql, statement.toString());
        Assert.assertEquals(expectSql, SQLUtils.toSQLStringWithTrueUcase(statement));
    }

    @Test
    public void testParsePolarxOriginSql() {
        String sql = "# POLARX_ORIGIN_SQL_ENCODE=BASE64\n"
            + "# POLARX_ORIGIN_SQL=LypERExfSUQ9NzEzNjE4MTY0OTkxMzQxMzY5NiovCkNSRUFURSBUQUJMRSBgYmFzZVRhYmxlYCAoCglgaWRgIGJpZ2ludCgyMCkgTk9UIE5VTEwgQVVUT19JTkNSRU1FTlQsCglgY19iaXRfMWAgYml0KDEpIERFRkFVTFQgTlVMTCwKCWBjX2JpdF84YCBiaXQoOCkgREVGQVVMVCBOVUxMLAoJYGNfYml0XzE2YCBiaXQoMTYpIERFRkFVTFQgTlVMTCwKCWBjX2JpdF8zMmAgYml0KDMyKSBERUZBVUxUIE5VTEwsCglgY19iaXRfNjRgIGJpdCg2NCkgREVGQVVMVCBOVUxMLAoJYGNfdGlueWludF8xYCB0aW55aW50KDEpIERFRkFVTFQgTlVMTCwKCWBjX3RpbnlpbnRfMV91bmAgdGlueWludCgxKSBVTlNJR05FRCBERUZBVUxUIE5VTEwsCglgY190aW55aW50XzRgIHRpbnlpbnQoNCkgREVGQVVMVCBOVUxMLAoJYGNfdGlueWludF80X3VuYCB0aW55aW50KDQpIFVOU0lHTkVEIERFRkFVTFQgTlVMTCwKCWBjX3RpbnlpbnRfOGAgdGlueWludCg4KSBERUZBVUxUIE5VTEwsCglgY190aW55aW50XzhfdW5gIHRpbnlpbnQoOCkgVU5TSUdORUQgREVGQVVMVCBOVUxMLAoJYGNfc21hbGxpbnRfMWAgc21hbGxpbnQoMSkgREVGQVVMVCBOVUxMLAoJYGNfc21hbGxpbnRfMTZgIHNtYWxsaW50KDE2KSBERUZBVUxUIE5VTEwsCglgY19zbWFsbGludF8xNl91bmAgc21hbGxpbnQoMTYpIFVOU0lHTkVEIERFRkFVTFQgTlVMTCwKCWBjX21lZGl1bWludF8xYCBtZWRpdW1pbnQoMSkgREVGQVVMVCBOVUxMLAoJYGNfbWVkaXVtaW50XzI0YCBtZWRpdW1pbnQoMjQpIERFRkFVTFQgTlVMTCwKCWBjX21lZGl1bWludF8yNF91bmAgbWVkaXVtaW50KDI0KSBVTlNJR05FRCBERUZBVUxUIE5VTEwsCglgY19pbnRfMWAgaW50KDEpIERFRkFVTFQgTlVMTCwKCWBjX2ludF8zMmAgaW50KDMyKSBOT1QgTlVMTCBERUZBVUxUICcwJyBDT01NRU5UICdGb3IgbXVsdGkgcGsuJywKCWBjX2ludF8zMl91bmAgaW50KDMyKSBVTlNJR05FRCBERUZBVUxUIE5VTEwsCglgY19iaWdpbnRfMWAgYmlnaW50KDEpIERFRkFVTFQgTlVMTCwKCWBjX2JpZ2ludF82NGAgYmlnaW50KDY0KSBERUZBVUxUIE5VTEwsCglgY19iaWdpbnRfNjRfdW5gIGJpZ2ludCg2NCkgVU5TSUdORUQgREVGQVVMVCBOVUxMLAoJYGNfZGVjaW1hbGAgZGVjaW1hbCgxMCwgMCkgREVGQVVMVCBOVUxMLAoJYGNfZGVjaW1hbF9wcmAgZGVjaW1hbCg2NSwgMzApIERFRkFVTFQgTlVMTCwKCWBjX2Zsb2F0YCBmbG9hdCBERUZBVUxUIE5VTEwsCglgY19mbG9hdF9wcmAgZmxvYXQoMTAsIDMpIERFRkFVTFQgTlVMTCwKCWBjX2Zsb2F0X3VuYCBmbG9hdCgxMCwgMykgVU5TSUdORUQgREVGQVVMVCBOVUxMLAoJYGNfZG91YmxlYCBkb3VibGUgREVGQVVMVCBOVUxMLAoJYGNfZG91YmxlX3ByYCBkb3VibGUoMTAsIDMpIERFRkFVTFQgTlVMTCwKCWBjX2RvdWJsZV91bmAgZG91YmxlKDEwLCAzKSBVTlNJR05FRCBERUZBVUxUIE5VTEwsCglgY19kYXRlYCBkYXRlIERFRkFVTFQgTlVMTCBDT01NRU5UICdkYXRlJywKCWBjX2RhdGV0aW1lYCBkYXRldGltZSBERUZBVUxUIE5VTEwsCglgY19kYXRldGltZV8xYCBkYXRldGltZSgxKSBERUZBVUxUIE5VTEwsCglgY19kYXRldGltZV8zYCBkYXRldGltZSgzKSBERUZBVUxUIE5VTEwsCglgY19kYXRldGltZV82YCBkYXRldGltZSg2KSBERUZBVUxUIE5VTEwsCglgY190aW1lc3RhbXBgIHRpbWVzdGFtcCBOT1QgTlVMTCBERUZBVUxUIENVUlJFTlRfVElNRVNUQU1QLAoJYGNfdGltZXN0YW1wXzFgIHRpbWVzdGFtcCgxKSBOT1QgTlVMTCBERUZBVUxUICcxOTk5LTEyLTMxIDEyOjAwOjAwLjAnLAoJYGNfdGltZXN0YW1wXzNgIHRpbWVzdGFtcCgzKSBOT1QgTlVMTCBERUZBVUxUICcxOTk5LTEyLTMxIDEyOjAwOjAwLjAwMCcsCglgY190aW1lc3RhbXBfNmAgdGltZXN0YW1wKDYpIE5PVCBOVUxMIERFRkFVTFQgJzE5OTktMTItMzEgMTI6MDA6MDAuMDAwMDAwJywKCWBjX3RpbWVgIHRpbWUgREVGQVVMVCBOVUxMLAoJYGNfdGltZV8xYCB0aW1lKDEpIERFRkFVTFQgTlVMTCwKCWBjX3RpbWVfM2AgdGltZSgzKSBERUZBVUxUIE5VTEwsCglgY190aW1lXzZgIHRpbWUoNikgREVGQVVMVCBOVUxMLAoJYGNfeWVhcmAgeWVhcig0KSBERUZBVUxUIE5VTEwsCglgY195ZWFyXzRgIHllYXIoNCkgREVGQVVMVCBOVUxMLAoJYGNfY2hhcmAgY2hhcigxMCkgREVGQVVMVCBOVUxMLAoJYGNfdmFyY2hhcmAgdmFyY2hhcigxMCkgREVGQVVMVCBOVUxMLAoJYGNfYmluYXJ5YCBiaW5hcnkoMTApIERFRkFVTFQgTlVMTCwKCWBjX3ZhcmJpbmFyeWAgdmFyYmluYXJ5KDEwKSBERUZBVUxUIE5VTEwsCglgY19ibG9iX3RpbnlgIHRpbnlibG9iLAoJYGNfYmxvYmAgYmxvYiwKCWBjX2Jsb2JfbWVkaXVtYCBtZWRpdW1ibG9iLAoJYGNfYmxvYl9sb25nYCBsb25nYmxvYiwKCWBjX3RleHRfdGlueWAgdGlueXRleHQsCglgY190ZXh0YCB0ZXh0LAoJYGNfdGV4dF9tZWRpdW1gIG1lZGl1bXRleHQsCglgY190ZXh0X2xvbmdgIGxvbmd0ZXh0LAoJYGNfZW51bWAgZW51bSgnYScsICdiJywgJ2MnKSBERUZBVUxUIE5VTEwsCglgY19qc29uYCBqc29uIERFRkFVTFQgTlVMTCwKCWBjX2dlb21ldG9yeWAgZ2VvbWV0cnkgREVGQVVMVCBOVUxMLAoJYGNfcG9pbnRgIHBvaW50IERFRkFVTFQgTlVMTCwKCWBjX2xpbmVzdHJpbmdgIGxpbmVzdHJpbmcgREVGQVVMVCBOVUxMLAoJYGNfcG9seWdvbmAgcG9seWdvbiBERUZBVUxUIE5VTEwsCglgY19tdWx0aXBvaW50YCBtdWx0aXBvaW50IERFRkFVTFQgTlVMTCwKCWBjX211bHRpbGluZXN0cmluZ2AgbXVsdGlsaW5lc3RyaW5nIERFRkFVTFQgTlVMTCwKCWBjX211bHRpcG9seWdvbmAgbXVsdGlwb2x5Z29uIERFRkFVTFQgTlVMTCwKCWBjX2dlb21ldHJ5Y29sbGVjdGlvbmAgZ2VvbWV0cnljb2xsZWN0aW9uIERFRkFVTFQgTlVMTCwKCVBSSU1BUlkgS0VZIChgaWRgKSwKCUtFWSBgaWR4X2NfZG91YmxlYCAoYGNfZG91YmxlYCksCglLRVkgYGlkeF9jX2Zsb2F0YCAoYGNfZmxvYXRgKQopIEVOR0lORSA9ICdJTk5PREInIEFVVE9fSU5DUkVNRU5UID0gMTAwMjgxIERFRkFVTFQgQ0hBUlNFVCA9IHV0ZjhtYjQgREVGQVVMVCBDT0xMQVRFID0gdXRmOG1iNF9nZW5lcmFsX2NpIENPTU1FTlQgJzEwMDAwMDAwJwpQQVJUSVRJT04gQlkgS0VZIChgaWRgKSBQQVJUSVRJT05TIDM7\n"
            + "# POLARX_TSO=713618165110040172816672468666448650240000000000000000\n"
            + "CREATE TABLE `basetable` ( `id` bigint(20) NOT NULL AUTO_INCREMENT, `c_bit_1` bit(1) DEFAULT NULL, `c_bit_8` bit(8) DEFAULT NULL, `c_bit_16` bit(16) DEFAULT NULL, `c_bit_32` bit(32) DEFAULT NULL, `c_bit_64` bit(64) DEFAULT NULL, `c_tinyint_1` tinyint(1) DEFAULT NULL, `c_tinyint_1_un` tinyint(1) UNSIGNED DEFAULT NULL, `c_tinyint_4` tinyint(4) DEFAULT NULL, `c_tinyint_4_un` tinyint(4) UNSIGNED DEFAULT NULL, `c_tinyint_8` tinyint(8) DEFAULT NULL, `c_tinyint_8_un` tinyint(8) UNSIGNED DEFAULT NULL, `c_smallint_1` smallint(1) DEFAULT NULL, `c_smallint_16` smallint(16) DEFAULT NULL, `c_smallint_16_un` smallint(16) UNSIGNED DEFAULT NULL, `c_mediumint_1`mediumint(1) DEFAULT NULL, `c_mediumint_24` mediumint(24) DEFAULT NULL, `c_mediumint_24_un` mediumint(24) UNSIGNED DEFAULT NULL, `c_int_1` int(1) DEFAULT NULL, `c_int_32` int(32) NOT NULL DEFAULT '0' COMMENT 'For multi pk.', `c_int_32_un` int(32) UNSIGNED DEFAULT NULL, `c_bigint_1` bigint(1) DEFAULT NULL, `c_bigint_64` bigint(64) DEFAULT NULL, `c_bigint_64_un` bigint(64) UNSIGNED DEFAULT NULL, `c_decimal` decimal(10, 0) DEFAULT NULL, `c_decimal_pr` decimal(65, 30) DEFAULT NULL, `c_float` float DEFAULT NULL, `c_float_pr` float(10, 3) DEFAULT NULL, `c_float_un` float(10, 3) UNSIGNED DEFAULT NULL, `c_double` double DEFAULT NULL, `c_double_pr` double(10, 3) DEFAULT NULL, `c_double_un` double(10, 3) UNSIGNED DEFAULT NULL, `c_date` date DEFAULT NULL COMMENT 'date', `c_datetime` datetime DEFAULT NULL, `c_datetime_1` datetime(1) DEFAULT NULL, `c_datetime_3` datetime(3) DEFAULT NULL, `c_datetime_6` datetime(6) DEFAULT NULL, `c_timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, `c_timestamp_1` timestamp(1) NOT NULL DEFAULT '1999-12-31 12:00:00.0', `c_timestamp_3` timestamp(3) NOT NULL DEFAULT '1999-12-31 12:00:00.000', `c_timestamp_6` timestamp(6) NOT NULL DEFAULT '1999-12-31 12:00:00.000000', `c_time` time DEFAULT NULL, `c_time_1` time(1) DEFAULT NULL, `c_time_3` time(3) DEFAULT NULL, `c_time_6` time(6) DEFAULT NULL, `c_year` year(4) DEFAULT NULL, `c_year_4` year(4) DEFAULT NULL, `c_char` char(10) DEFAULT NULL, `c_varchar` varchar(10) DEFAULT NULL, `c_binary` binary(10) DEFAULT NULL, `c_varbinary` varbinary(10) DEFAULT NULL, `c_blob_tiny` tinyblob, `c_blob` blob, `c_blob_medium` mediumblob, `c_blob_long` longblob, `c_text_tiny` tinytext, `c_text` text, `c_text_medium` mediumtext, `c_text_long` longtext, `c_enum` enum('a', 'b', 'c') DEFAULT NULL, `c_json` json DEFAULT NULL, `c_geometory` geometry DEFAULT NULL, `c_point` point DEFAULT NULL, `c_linestring` linestring DEFAULT NULL, `c_polygon` polygon DEFAULT NULL, `c_multipoint` multipoint DEFAULT NULL, `c_multilinestring` multilinestring DEFAULT NULL, `c_multipolygon` multipolygon DEFAULT NULL, `c_geometrycollection` geometrycollection DEFAULT NULL, PRIMARY KEY (`id`), KEY `idx_c_double` (`c_double`), KEY `idx_c_float` (`c_float`) ) ENGINE = 'INNODB' AUTO_INCREMENT = 100281 DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci COMMENT '10000000'";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
    }

    @Test
    public void testRemoveSomeHints() {
        String sql = "/*DDL_ID=7144339830632087616*/\n"
            + "/* //1/ */\n"
            + "/*DDL_SUBMIT_TOKEN=bb121b6c-8cfa-42ee-b0f0-f8ff68ee424a*/\n"
            + "CREATE TABLE IF NOT EXISTS cp1_ddl2_1573919209.gxw_test_103 LIKE cp1_ddl2_1573919209.gxw_test_102";
        String result = removeSomeHints(sql);
        String expectSql = "/* //1/ */\n"
            + "CREATE TABLE IF NOT EXISTS cp1_ddl2_1573919209.gxw_test_103 LIKE cp1_ddl2_1573919209.gxw_test_102";
        Assert.assertEquals(expectSql, result);
    }

    @Test
    public void testAlterTableModifyColumn() {
        String sql = "ALTER TABLE t_modify\n"
            + "  MODIFY COLUMN b bigint WITH TABLEGROUP=tg1591 IMPLICIT";
        SQLStatement statement = parseSQLStatement(sql);
        Assert.assertEquals("ALTER TABLE t_modify\n"
            + "\tMODIFY COLUMN b bigint WITH TABLEGROUP=tg1591 IMPLICIT", statement.toString());
    }

    @Test
    public void testAlterIndexVisible() {
        String sql = "ALTER TABLE `t_order_0`  ALTER INDEX `cci_0` VISIBLE";
        SQLAlterTableStatement statement = parseSQLStatement(sql);
        statement.getItems().removeIf(i -> i instanceof MySqlAlterTableAlterFullTextIndex);
        Assert.assertEquals("ALTER TABLE `t_order_0`", statement.toString());
    }

    @Test
    public void testLeaderBySqlQuery() {
        DataSource dataSource = SpringContextHolder.getObject("metaDataSource");
        try {
            SQLUtils.isLeaderBySqlQuery(dataSource);
        } catch (SQLException e) {
            Assert.assertEquals("Table \"ALISQL_CLUSTER_LOCAL\" not found; SQL statement:\n"
                + "select * from information_schema.alisql_cluster_local [42102-220]", e.getMessage());
        }
    }

    @Test
    public void testLeaderByDdl() {
        DataSource dataSource = SpringContextHolder.getObject("metaDataSource");
        try {
            SQLUtils.isLeaderByDdl(dataSource);
        } catch (SQLException e) {
            Assert.assertEquals(
                "Syntax error in SQL statement \"DROP [*]TEMPORARY TABLE IF EXISTS binlog_leader_test\"; expected \"TABLE, INDEX, USER, SEQUENCE, CONSTANT, TRIGGER, MATERIALIZED, VIEW, ROLE, ALIAS, SCHEMA, ALL OBJECTS, DOMAIN, TYPE, DATATYPE, AGGREGATE, SYNONYM\"; SQL statement:\n"
                    + "DROP TEMPORARY TABLE IF EXISTS binlog_leader_test [42001-220]", e.getMessage());
        }
    }

    @Test
    public void testAlterTableRemovePartitioning() {
        String sql = "ALTER TABLE t1\n"
            + "\tREMOVE PARTITIONING WITH TABLEGROUP=tg604 IMPLICIT, INDEX `i``1` WITH TABLEGROUP=tg604 IMPLICIT";
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        Assert.assertEquals("ALTER TABLE t1\n"
                + "\tREMOVE PARTITIONING WITH TABLEGROUP=tg604 IMPLICIT, INDEX `i``1` WITH TABLEGROUP=tg604 IMPLICIT",
            statement.toString());
    }

    @Test
    public void testAlterTableWithFullTextIndexAndParser() {
        String sql = " /*DRDS /null/18b7a7dc76002000/null/181818/ */CREATE TABLE `tb1_u0fQ_00001` (\n"
            + "        a int PRIMARY KEY,\n"
            + "        b text,\n"
            + "        c text,\n"
            + "        d text,\n"
            + "        e text,\n"
            + "        FULLTEXT KEY `test_idx1` (`b`) COMMENT '123' /*!50100 WITH PARSER `ngram` */,\n"
            + "        FULLTEXT INDEX `test_idx2`(`b`) COMMENT '234' /*!50100 WITH PARSER `ngram` */,\n"
            + "        FULLTEXT KEY `test_idx3` (`d`) WITH PARSER ngram,\n"
            + "        FULLTEXT INDEX `test_idx4`(`e`) WITH PARSER ngram\n"
            + ") DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci";
        SQLStatement statement = parseSQLStatement(sql);
    }

    @Test
    public void testGrantNl2Sql() {
        String sql = "GRANT NL2SQL ON *.* TO 'your_user'@'%';";
        SQLGrantStatement statement = parseSQLStatement(sql);
        Assert.assertEquals(1, statement.getPrivileges().size());
        Assert.assertEquals("NL2SQL", statement.getPrivileges().get(0).getAction().toString());
        Assert.assertEquals("GRANT NL2SQL ON *.* TO 'your_user'@'%';", statement.toString());
    }

    @Test
    public void testAlterTableRebuildCleanupWhere() {
        String sql = "ALTER TABLE `t_order` REBUILD CLEANUP WHERE `gmt_modified` < '2026-08-01 00:00:00'";
        SQLAlterTableStatement statement = parseSQLStatement(sql);

        Assert.assertEquals(1, statement.getItems().size());
        Assert.assertTrue(statement.getItems().get(0) instanceof DrdsAlterTableRebuildCleanup);
        DrdsAlterTableRebuildCleanup rebuildCleanup =
            (DrdsAlterTableRebuildCleanup) statement.getItems().get(0);
        Assert.assertEquals("`gmt_modified` < '2026-08-01 00:00:00'",
            rebuildCleanup.getCleanupPredicate().toString());
        Assert.assertFalse(rebuildCleanup.isDryRun());
        Assert.assertEquals("ALTER TABLE `t_order`\n"
            + "\tREBUILD CLEANUP WHERE `gmt_modified` < '2026-08-01 00:00:00'", statement.toString());
    }

    @Test
    public void testCallColumnarSetConfig() {
        String sql = "CALL polardbx.columnar_set_config(256, 'TYPE', 'SNAPSHOT')";
        SQLCallStatement statement = parseSQLStatement(sql);
        Assert.assertEquals(statement.getProcedureName().getSimpleName(), "columnar_set_config");
    }

    @Test
    public void test() {
        String sql = "CREATE JAVA FUNCTION `java_udf_concat` "
            + "RETURN_TYPE varchar(255) INPUT_TYPES int,varchar(255) CODE "
            + "public class Java_udf_concat extends UserDefinedJavaFunction {\n"
            + "public Object compute(Object[] args) {\n"
            + "Integer a = (Integer) args[0];\n"
            + "String b = (String) args[1];\n"
            + "return \"polarx_\" + b + \"_\" + a;\n"
            + "}\n"
            + "}; END_CODE";
        SQLStatement statement = parseSQLStatement(sql);
        System.out.println(statement);
    }
}
