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
package com.aliyun.polardbx.rpl.applier;

import org.junit.Assert;
import org.junit.Test;

import java.sql.Timestamp;

/**
 * @author shicai.xsc 2021/4/19 11:18
 * @since 5.0.0.0
 */
public class DdlHelperTest {

    @Test
    public void getOriginalSql() {
        String sql = "/*POLARX_ORIGIN_SQL=CREATE TABLE aaaaaa (\n" + "    id int,\n" + "    value int,\n"
            + "    INDEX `auto_shard_key_id` USING BTREE(`ID`),\n"
            + "    _drds_implicit_id_ bigint AUTO_INCREMENT,\n" + "    PRIMARY KEY (_drds_implicit_id_)\n"
            + ")\n" + "DBPARTITION BY hash(id)\n" + "TBPARTITION BY hash(id) TBPARTITIONS 2*/ "
            + "/*TSO=678700134612901433613180665615960145920000000000000000*/ CREATE TABLE aaaaaa ( id int, value "
            + "int, INDEX `auto_shard_key_id` USING BTREE(`ID`) ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = "
            + "utf8mb4_general_ci";
        String originSql = DdlHelper.getOriginSql(sql);
        Assert.assertEquals("CREATE TABLE aaaaaa (\n" + "\tid int,\n" + "\tvalue int,\n"
                + "\tINDEX `auto_shard_key_id` USING BTREE(`ID`)\n" + ")\n" + "DBPARTITION BY hash(id)\n"
                + "TBPARTITION BY hash(id) TBPARTITIONS 2",
            originSql);
    }

    @Test
    public void getTso_1() {
        String sql = "/*POLARX_ORIGIN_SQL=CREATE TABLE aaaaaa (\n" + "    id int,\n" + "    value int,\n"
            + "    INDEX `auto_shard_key_id` USING BTREE(`ID`),\n"
            + "    _drds_implicit_id_ bigint AUTO_INCREMENT,\n" + "    PRIMARY KEY (_drds_implicit_id_)\n"
            + ")\n" + "DBPARTITION BY hash(id)\n" + "TBPARTITION BY hash(id) TBPARTITIONS 2*/ "
            + "/*TSO=678700134612901433613180665615960145920000000000000000*/ CREATE TABLE aaaaaa ( id int, value "
            + "int, INDEX `auto_shard_key_id` USING BTREE(`ID`) ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = "
            + "utf8mb4_general_ci";
        String tso = DdlHelper.getTso(sql, null, "");
        Assert.assertEquals(tso, "678700134612901433613180665615960145920000000000000000");
    }

    @Test
    public void getTso_2() {
        String sql = "/*POLARX_ORIGIN_SQL=CREATE TABLE aaaaaa (\n" + "    id int,\n" + "    value int,\n"
            + "    INDEX `auto_shard_key_id` USING BTREE(`ID`),\n"
            + "    _drds_implicit_id_ bigint AUTO_INCREMENT,\n" + "    PRIMARY KEY (_drds_implicit_id_)\n"
            + ")\n" + "DBPARTITION BY hash(id)\n" + "TBPARTITION BY hash(id) TBPARTITIONS 2*/ "
            + "CREATE TABLE aaaaaa ( id int, value "
            + "int, INDEX `auto_shard_key_id` USING BTREE(`ID`) ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = "
            + "utf8mb4_general_ci";
        String tso = DdlHelper.getTso(sql, new Timestamp(1618802638), "");
        Assert.assertEquals("-3519920771618802638", tso);
    }
}
