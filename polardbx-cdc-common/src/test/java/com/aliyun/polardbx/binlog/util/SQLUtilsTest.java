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
package com.aliyun.polardbx.binlog.util;

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlLockTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlUnlockTablesStatement;
import org.junit.Assert;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * created by ziyang.lb
 **/
public class SQLUtilsTest {

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
}
