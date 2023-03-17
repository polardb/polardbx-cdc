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
package com.aliyun.polardbx.cdc.qatest.check;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yudong
 * @since 2023/2/14 15:55
 **/
@RunWith(PowerMockRunner.class)
public class CheckSumSqlTest {

    // ==================  test DataConsistencyTest::buildCheckSumSql ============= //
    
    @Test
    public void testNameWithEscapeCharacter() throws Exception {
        DataConsistencyTest test = new DataConsistencyTest();
        String db = "`testDB`";
        String table = "`testTable`";
        List<String> columns = new ArrayList<>();
        columns.add("`id`");
        columns.add("`name`");

        Method buildCheckSumSql =
            PowerMockito.method(DataConsistencyTest.class, "buildCheckSumSql", String.class, String.class,
                List.class);
        String checkSumSql = (String) buildCheckSumSql.invoke(test, db, table, columns);
        String expected =
            "SELECT BIT_XOR( CAST( CRC32( CONCAT_WS( ',', ```id```, ```name```, ISNULL(```id```), "
                + "ISNULL(```name```) ) ) AS UNSIGNED )) AS checksum FROM ```testDB```.```testTable```";
        Assert.assertEquals(expected, checkSumSql);
    }

    @Test
    public void testNameWithoutEscapeCharacter() throws Exception {
        DataConsistencyTest test = new DataConsistencyTest();
        String db = "testDB";
        String table = "testTable";
        List<String> columns = new ArrayList<>();
        columns.add("id");
        columns.add("name");

        Method buildCheckSumSql =
            PowerMockito.method(DataConsistencyTest.class, "buildCheckSumSql", String.class, String.class,
                List.class);
        String checkSumSql = (String) buildCheckSumSql.invoke(test, db, table, columns);
        String expected =
            "SELECT BIT_XOR( CAST( CRC32( CONCAT_WS( ',', `id`, `name`, ISNULL(`id`), "
                + "ISNULL(`name`) ) ) AS UNSIGNED )) AS checksum FROM `testDB`.`testTable`";
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
        List<String> columns = new ArrayList<>();
        columns.add("`id`");
        columns.add("`name`");
        String in = "('1','2')";

        Method buildCheckSumWithInSql =
            PowerMockito.method(DataConsistencyTest.class, "buildCheckSumWithInSql", String.class, String.class,
                List.class, List.class, String.class);
        String checkSumSql = (String) buildCheckSumWithInSql.invoke(test, db, table, pks, columns, in);
        String expected =
            "SELECT BIT_XOR( CAST( CRC32( CONCAT_WS( ',', ```id```, ```name```, ISNULL(```id```), "
                + "ISNULL(```name```) ) ) AS UNSIGNED )) AS checksum FROM ```testDB```.```testTable``` WHERE (```id```) IN (('1','2'))";
        Assert.assertEquals(expected, checkSumSql);
    }

    @Test
    public void testNameWithoutEscapeCharacter2() throws Exception {
        DataConsistencyTest test = new DataConsistencyTest();
        String db = "testDB";
        String table = "testTable";
        List<String> pks = new ArrayList<>();
        pks.add("id");
        List<String> columns = new ArrayList<>();
        columns.add("id");
        columns.add("name");
        String in = "('1','2')";

        Method buildCheckSumWithInSql =
            PowerMockito.method(DataConsistencyTest.class, "buildCheckSumWithInSql", String.class, String.class,
                List.class, List.class, String.class);
        String checkSumSql = (String) buildCheckSumWithInSql.invoke(test, db, table, pks, columns, in);
        String expected =
            "SELECT BIT_XOR( CAST( CRC32( CONCAT_WS( ',', `id`, `name`, ISNULL(`id`), "
                + "ISNULL(`name`) ) ) AS UNSIGNED )) AS checksum FROM `testDB`.`testTable` WHERE (`id`) IN (('1','2'))";
        Assert.assertEquals(expected, checkSumSql);
    }
}
