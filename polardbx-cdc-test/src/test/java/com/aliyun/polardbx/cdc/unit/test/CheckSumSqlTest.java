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
package com.aliyun.polardbx.cdc.unit.test;

import com.aliyun.polardbx.cdc.qatest.check.bothcheck.common.DataConsistencyTest;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yudong
 * @since 2023/2/14 15:55
 **/

public class CheckSumSqlTest {
    // ==================  test DataConsistencyTest::buildCheckSumSql ============= //

    @Test
    public void testNameWithEscapeCharacter() {
        DataConsistencyTest test = new DataConsistencyTest();
        String db = "`testDB`";
        String table = "`testTable`";
        List<Pair<String, String>> columnPairs = Lists.newArrayList();
        columnPairs.add(new ImmutablePair<>("`id`", "bigint"));
        columnPairs.add(new ImmutablePair<>("`name`", "varchar(20)"));

        String checkSumSql = test.buildCheckSumSql(db, table, columnPairs);
        String expected =
            "SELECT BIT_XOR( CAST( CRC32( CONCAT_WS( ',', ```id```, ```name```, ISNULL(```id```), ISNULL(```name```) ) ) AS UNSIGNED )) AS checksum FROM ( SELECT HEX(```id```) as ```id```, HEX(```name```) as ```name``` FROM ```testDB```.```testTable``` )t";
        Assert.assertEquals(expected, checkSumSql);
    }

    @Test
    public void testNameWithoutEscapeCharacter() throws Exception {
        DataConsistencyTest test = new DataConsistencyTest();
        String db = "testDB";
        String table = "testTable";
        List<Pair<String, String>> columnPairs = Lists.newArrayList();
        columnPairs.add(new ImmutablePair<>("id", "bigint"));
        columnPairs.add(new ImmutablePair<>("name", "varchar(20)"));

        String checkSumSql = test.buildCheckSumSql(db, table, columnPairs);
        String expected =
            "SELECT BIT_XOR( CAST( CRC32( CONCAT_WS( ',', `id`, `name`, ISNULL(`id`), ISNULL(`name`) ) ) AS UNSIGNED )) AS checksum FROM ( SELECT HEX(`id`) as `id`, HEX(`name`) as `name` FROM `testDB`.`testTable` )t";
        Assert.assertEquals(expected, checkSumSql);
    }

    // ==================  test DataConsistencyTest::buildCheckSumWithInSql ============= //

    @Test
    public void testNameWithEscapeCharacter2() throws Exception {
        DataConsistencyTest test = new DataConsistencyTest();
        String db = "`testDB`";
        String table = "`testTable`";
        List<String> pks = new ArrayList<>();
        pks.add("`id`");
        List<Pair<String, String>> columnPairs = Lists.newArrayList();
        columnPairs.add(new ImmutablePair<>("`id`", "bigint"));
        columnPairs.add(new ImmutablePair<>("`name`", "varchar(20)"));

        String in = "'1','2'";

        String checkSumSql = test.buildCheckSumWithInSql(db, table, pks, columnPairs, in);
        String expected =
            "SELECT BIT_XOR( CAST( CRC32( CONCAT_WS( ',', ```id```, ```name```, ISNULL(```id```), ISNULL(```name```) ) ) AS UNSIGNED ) ) AS checksum FROM ( SELECT HEX(```id```) as ```id```, HEX(```name```) as ```name``` FROM ```testDB```.```testTable``` WHERE (```id```) IN ('1','2') ORDER BY ```id``` )t";
        Assert.assertEquals(expected, checkSumSql);
    }

    @Test
    public void testNameWithoutEscapeCharacter2() throws Exception {
        DataConsistencyTest test = new DataConsistencyTest();
        String db = "testDB";
        String table = "testTable";
        List<String> pks = new ArrayList<>();
        pks.add("id");

        List<Pair<String, String>> columnPairs = Lists.newArrayList();
        columnPairs.add(new ImmutablePair<>("id", "bigint"));
        columnPairs.add(new ImmutablePair<>("name", "varchar(20)"));
        String in = "'1','2'";

        String checkSumSql = test.buildCheckSumWithInSql(db, table, pks, columnPairs, in);
        String expected =
            "SELECT BIT_XOR( CAST( CRC32( CONCAT_WS( ',', `id`, `name`, ISNULL(`id`), ISNULL(`name`) ) ) AS UNSIGNED ) ) AS checksum FROM ( SELECT HEX(`id`) as `id`, HEX(`name`) as `name` FROM `testDB`.`testTable` WHERE (`id`) IN ('1','2') ORDER BY `id` )t";
        Assert.assertEquals(expected, checkSumSql);
    }
}
