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
package com.aliyun.polardbx.rpl.dbmeta;

import com.alibaba.druid.pool.DruidDataSource;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author shicai.xsc 2021/3/25 16:48
 * @since 5.0.0.0
 */

@RunWith(MockitoJUnitRunner.class)
public class DbMetaManagerTest extends BaseTestWithGmsTables {

    // 测试用例: 多个唯一键组且每个组内有多列的情况
    @Test
    public void testGetTableUksMultipleUniqueKeyGroups() throws Exception {
        try (MockedStatic<DbMetaManager> dbMetaManager = Mockito.mockStatic(DbMetaManager.class,
            Mockito.CALLS_REAL_METHODS)) {

            HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
            ukGroups.put("key1", new ArrayList<>());
            ukGroups.put("key2", new ArrayList<>());
            KeyColumnInfo column11 = new KeyColumnInfo("tbName", "key1", "columnName1",
                0, 0);
            KeyColumnInfo column12 = new KeyColumnInfo("tbName", "key1", "columnName2",
                0, 1);
            KeyColumnInfo column21 = new KeyColumnInfo("tbName", "key2", "columnName1",
                0, 0);
            KeyColumnInfo column22 = new KeyColumnInfo("tbName", "key2", "columnName3",
                0, 0);
            ukGroups.get("key1").add(column11);
            ukGroups.get("key1").add(column12);
            ukGroups.get("key2").add(column21);
            ukGroups.get("key2").add(column22);

            dbMetaManager.when(() -> DbMetaManager.getTableUkGroups(Mockito.any(DruidDataSource.class),
                Mockito.anyString(), Mockito.anyString())).thenReturn(ukGroups);

            // 执行
            List<String> actualUks = DbMetaManager.getTableUks(new DruidDataSource(), "SCHEMA",
                "TBNAME");

            // 验证
            List<String> expectedUks = new ArrayList<>();
            expectedUks.add("columnname1");
            expectedUks.add("columnname2");
            expectedUks.add("columnname2");
            assertEquals(3, actualUks.size());
            for (String uk : expectedUks) {
                assertTrue(actualUks.contains(uk.toLowerCase()));
            }
        }
    }

    @Test
    public void metaTest() throws Throwable {
        String schema = "polardbx_meta_db";
        String tbName = "rpl_task";
        DataSource dataSource = getGmsDataSource();
        TableInfo tableInfo = DbMetaManager.getTableInfo(dataSource, schema, tbName, HostType.RDS, false);
        Assert.assertNotNull(tableInfo);
    }

    @Test
    public void testBuildTableBasicInfo() throws SQLException {
        TableInfo tableInfo = new TableInfo("d1", "t1");
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        ResultSetMetaData metaData = Mockito.mock(ResultSetMetaData.class);

        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.createStatement()).thenReturn(statement);
        Mockito.when(statement.executeQuery(Mockito.anyString())).thenReturn(resultSet);
        Mockito.when(resultSet.next()).thenReturn(true).thenReturn(false);
        Mockito.when(resultSet.getString(Mockito.anyInt())).thenReturn("InnoDB");
        Mockito.when(resultSet.getMetaData()).thenReturn(metaData);
        Mockito.when(metaData.getColumnCount()).thenReturn(1);
        Mockito.when(metaData.getColumnName(Mockito.anyInt())).thenReturn("ENGINE");

        DbMetaManager.buildTableBasicInfo(dataSource, "d1", "t1", tableInfo);
        Assert.assertEquals("InnoDB", tableInfo.getEngine());
    }
}
