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
package com.aliyun.polardbx.binlog.extractor.filter;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.RegexUtil;
import org.junit.Assert;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX;

public class RegexUtilTest extends BaseTest {

    private static final String REGEX_PATTERNS = "pattern";
    private static final String REGEX_STRING = "regex_string";
    private static final String IF_EXPRESS = "if_express";

    static void printSplitLine() {
        System.out.println("---------------------------------------");
    }

    @Test
    public void testAlterSystemTable() {
        String regexString = DynamicApplicationConfig.getString(META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX);
        String empty = "";
        String createTable = "create table";

        String drds6 = "alter table b`__drds_global_tx_log`\r\n"
            + "drop partition `p_1461439929572655104`";

        String drds7 = "alter table `polarx_global_trx_log```b`\n"
            + "drop partition `p_1461439929572655104`";

        String drds8 = "alter table b `__drds_global_tx_log`\r\n"
            + "drop partition `p_1461439929572655104`";

        String drds9 = "alter table `polarx_global_trx_log`drop partition `p_1461439929572655104`";

        String drds2 = "alter table `__drds_global_tx_log`\n"
            + "drop partition `p_1461439929572655104`";
        String drds3 = "alter table `__drds_global_tx_log`\r\n"
            + "drop partition `p_1461439929572655104`";

        String drds4 = "alter table `polarx_global_trx_log`\n"
            + "drop partition `p_1461439929572655104`";
        String drds5 = "alter table `polarx_global_trx_log`\r\n"
            + "drop partition `p_1461439929572655104`";

        String drds10 = "create table `polarx_global_trx_log`(id bigint primary key)";
        String drds11 = "create table polarx_global_trx_log(id bigint primary key)";
        String drds12 = "create table polarx_global_trx_log (id bigint primary key)";
        String drds13 = "create table polarx_global_trx_log\n(id bigint primary key)";
        String drds14 = "create table polarx_global_trx_log_(id bigint primary key)";
        String drds15 = "create table polarx_global_trx_log_ id bigint primary key)";
        String drds16 = "create table `polarx_global_trx_log_ `(id bigint primary key)";

        Assert.assertFalse(RegexUtil.match(regexString, empty));
        Assert.assertFalse(RegexUtil.match(regexString, createTable));

        Assert.assertFalse(RegexUtil.match(regexString, drds6));
        Assert.assertFalse(RegexUtil.match(regexString, drds7));
        Assert.assertFalse(RegexUtil.match(regexString, drds8));
        Assert.assertFalse(RegexUtil.match(regexString, drds9));

        Assert.assertTrue(RegexUtil.match(regexString, drds2));
        Assert.assertTrue(RegexUtil.match(regexString, drds3));
        Assert.assertTrue(RegexUtil.match(regexString, drds4));
        Assert.assertTrue(RegexUtil.match(regexString, drds5));

        Assert.assertTrue(RegexUtil.match(regexString, drds10));
        Assert.assertTrue(RegexUtil.match(regexString, drds11));
        Assert.assertTrue(RegexUtil.match(regexString, drds12));
        Assert.assertTrue(RegexUtil.match(regexString, drds13));
        Assert.assertFalse(RegexUtil.match(regexString, drds14));
        Assert.assertFalse(RegexUtil.match(regexString, drds15));
        Assert.assertFalse(RegexUtil.match(regexString, drds16));
    }

    @Test
    public void testCreateProcedureOrFunction() {
        String c1 =
            "CREATE PROCEDURE citycount (IN country CHAR(3), OUT cities INT)\n"
                + "       BEGIN\n"
                + "         SELECT COUNT(*) INTO cities FROM world.city\n"
                + "         WHERE CountryCode = country;\n"
                + "       END";
        String c2 = "CREATE DEFINER = 'admin'@'localhost' PROCEDURE account_count()\n"
            + "BEGIN\n"
            + "  SELECT 'Number of accounts:', COUNT(*) FROM mysql.user;\n"
            + "END;";

        String c3 = "CREATE FUNCTION calculate_total (price DOUBLE, quantity INT)\n"
            + "RETURNS DOUBLE\n"
            + "BEGIN\n"
            + "  DECLARE total_price DOUBLE;\n"
            + "  SET total_price = price * quantity;\n"
            + "  RETURN total_price;\n"
            + "END";

        String c4 = "create definer=`admin`@`%` function `mysql`.`udf_hfew`(\n"
            + "        data char(255)\n"
            + ") returns char(255) charset utf8\n"
            + "    no sql\n"
            + "    comment 'magic_polarx'\n"
            + "return data";

        String c5 = "create table PROCEDURE(id bigint primary key)";
        String c6 = "create table PROCEDURE_6(id bigint primary key)";

        String regexString = DynamicApplicationConfig.getString(META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX);
        //^create\\s+(DEFINER\\s*=\\s*'\\w+'@'[\\w|\\.]+'\\s+)function\\s+.*,^create\\s+procedure\\s+.*
        Assert.assertTrue(RegexUtil.match(regexString, c1));
        Assert.assertTrue(RegexUtil.match(regexString, c2));
        Assert.assertTrue(RegexUtil.match(regexString, c3));
        Assert.assertTrue(RegexUtil.match(regexString, c4));
        Assert.assertFalse(RegexUtil.match(regexString, c5));
        Assert.assertFalse(RegexUtil.match(regexString, c6));
    }

    @Test
    public void testDropProcedureOrFunction() {
        //DROP {PROCEDURE | FUNCTION} [IF EXISTS] sp_name
        String s1 = "drop procedure if exist c1";
        String s2 = "drop FUNCTION  c2";
        String s3 = "drop table  procedure_t";
        String s4 = "drop table  function_";
        String s5 = "drop database  function_";
        String regexString = DynamicApplicationConfig.getString(META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX);
        Assert.assertTrue(RegexUtil.match(regexString, s1));
        Assert.assertTrue(RegexUtil.match(regexString, s2));
        Assert.assertFalse(RegexUtil.match(regexString, s3));
        Assert.assertFalse(RegexUtil.match(regexString, s4));
        Assert.assertFalse(RegexUtil.match(regexString, s5));
    }

    @Test
    public void testSequenceAndSavePoint() {

        String s1 = "savepoint s1";
        String s2 = "release SAVEPOINT xx";
        String s3 = "ROLLBACK TO SAVEPOINT xx";
        String s4 = "ROLLBACK WORK TO SAVEPOINT xx";

        String s5 = "create sequence q1 async=true";
        String s6 = "drop sequence sales1_seq";

        String s7 = "XA ROLLBACK xid";
        String regexString = DynamicApplicationConfig.getString(META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX);
        Assert.assertTrue(RegexUtil.match(regexString, s1));
        Assert.assertTrue(RegexUtil.match(regexString, s2));
        Assert.assertTrue(RegexUtil.match(regexString, s3));
        Assert.assertTrue(RegexUtil.match(regexString, s4));
        Assert.assertTrue(RegexUtil.match(regexString, s5));
        Assert.assertTrue(RegexUtil.match(regexString, s6));

        Assert.assertFalse(RegexUtil.match(regexString, s7));
    }

    @Test
    public void testGrant() {
        String s1 = "GRANT SELECT ON *.* TO 'userA'@'localhost' IDENTIFIED BY 'passwordA'";
        String s2 = "GRANT ALL PRIVILEGES ON mydb.* TO 'userB'@'localhost' IDENTIFIED BY 'passwordB'";
        String s3 = "GRANT SELECT, INSERT, UPDATE, DELETE ON db1.* TO 'userC'@'localhost' IDENTIFIED BY 'passwordC'";
        String s4 = "GRANT SELECT ON db2.* TO 'userC'@'localhost';\n";
        String s5 = "GRANT SELECT, UPDATE ON `mydb`.`orders` TO 'userD'@'localhost' IDENTIFIED BY 'passwordD'";
        String s6 = "GRANT SELECT ON `mydb`.`customers` TO 'userD'@'localhost';";
        String s7 = "create table GRANT (ON bigint primary key, to varchar(20));";
        String regexString = DynamicApplicationConfig.getString(META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX);
        Assert.assertTrue(RegexUtil.match(regexString, s1));
        Assert.assertTrue(RegexUtil.match(regexString, s2));
        Assert.assertTrue(RegexUtil.match(regexString, s3));
        Assert.assertTrue(RegexUtil.match(regexString, s4));
        Assert.assertTrue(RegexUtil.match(regexString, s5));
        Assert.assertTrue(RegexUtil.match(regexString, s6));
        Assert.assertFalse(RegexUtil.match(regexString, s7));
    }

    @Test
    public void testAlterUser() {
        String s1 = "ALTER USER 'userA'@'localhost' IDENTIFIED BY 'newPasswordA';\n";
        String s2 = "ALTER USER 'userC'@'%' WITH MAX_QUERIES_PER_HOUR 0;\n";
        String s3 = "ALTER USER 'userC'@'localhost' WITH MAX_UPDATES_PER_HOUR 100;\n";
        String s4 = "ALTER USER 'userD'@'localhost' ACCOUNT UNLOCK;\n";
        String s5 = "ALTER USER 'userE'@'localhost' ACCOUNT LOCK;\n";
        String s6 = "ALTER USER 'userF'@'localhost' WITH MAX_USER_CONNECTIONS 5;\n";
        String s7 = "ALTER USER 'userF'@'localhost' WITH MAX_TMP_TABLE_SIZE 100M;\n";
        String s8 = "ALTER USER 'userF'@'localhost' WITH MAX_TMP_TABLE_SIZE 100M;\n";
        String s9 = "ALTER USER 'userF'@'localhost' WITH MAX_TMP_TABLE_SIZE 100M;\n";
        String s10 = "ALTER table USER add column c bigint default 0";

        String regexString = DynamicApplicationConfig.getString(META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX);
        Assert.assertTrue(RegexUtil.match(regexString, s1));
        Assert.assertTrue(RegexUtil.match(regexString, s2));
        Assert.assertTrue(RegexUtil.match(regexString, s3));
        Assert.assertTrue(RegexUtil.match(regexString, s4));
        Assert.assertTrue(RegexUtil.match(regexString, s5));
        Assert.assertTrue(RegexUtil.match(regexString, s6));
        Assert.assertTrue(RegexUtil.match(regexString, s7));
        Assert.assertTrue(RegexUtil.match(regexString, s8));
        Assert.assertTrue(RegexUtil.match(regexString, s9));

        Assert.assertFalse(RegexUtil.match(regexString, s10));
    }
}
