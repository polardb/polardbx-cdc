/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
