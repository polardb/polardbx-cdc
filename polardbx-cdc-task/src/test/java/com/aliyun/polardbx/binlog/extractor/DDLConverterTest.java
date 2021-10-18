/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter;
import org.junit.Test;

public class DDLConverterTest {

    @Test
    public void testShard() {
        String ddl =
            "CREATE PARTITION TABLE `wp_users_user_email` (\n"
                + "        `ID` bigint(20) UNSIGNED NOT NULL,\n"
                + "        `user_email` varchar(100) COLLATE utf8mb4_unicode_520_ci NOT NULL DEFAULT '',\n"
                + "        PRIMARY KEY (`ID`),\n"
                + "        LOCAL KEY `_local_user_email` USING BTREE (`user_email`)\n"
                + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_unicode_520_ci  dbpartition by hash(`user_email`) ";
        String formatDDL = DDLConverter.convertNormalDDL(ddl, null, "utf8_general_cs", "12345667788");
        System.out.println(formatDDL);
        System.out.println(DDLConverter.formatPolarxDDL(ddl, null, "utf8_general_cs", 1));
    }

    @Test
    public void testDatabase() {
        String ddl = " create database test_ddl;";
        String formatDDL = DDLConverter.convertNormalDDL(ddl, "utf8mb4", null, "12345667788");
        System.out.println(formatDDL);
    }

    @Test
    public void testBroadcast() {
        String ddl = "CREATE TABLE `bt` (\n" + "\t`id` int(11) NOT NULL AUTO_INCREMENT BY GROUP,\n"
            + "\t`name` varchar(20) DEFAULT NULL,\n" + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB AUTO_INCREMENT = 200006 DEFAULT CHARSET = utf8mb4  broadcast";
        String formatDDL = DDLConverter.convertNormalDDL(ddl, null, "utf8_general_cs", "12345667788");
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
        System.out.println(DDLConverter.formatPolarxDDL("select 1", null, "gbk", 1));
    }

    @Test
    public void testDrdsImplcit() {
        String ddl = "CREATE TABLE t3 (\n" + "\tname varchar(10),\n"
            + "\tINDEX `auto_shard_key_name` USING BTREE(`NAME`(10)),\n"
            + "\t_drds_implicit_id_ bigint AUTO_INCREMENT,\n" + "\tPRIMARY KEY (_drds_implicit_id_)\n" + ")\n"
            + "DBPARTITION BY hash(name)";
        System.out.println(DDLConverter.convertNormalDDL(ddl, null, null, "12345667788"));
    }
}
