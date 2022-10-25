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
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.aliyun.polardbx.cdc.qatest.binlog.metadata.TableMocker;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;

@Slf4j
@FixMethodOrder(MethodSorters.JVM)
@RunWith(Parameterized.class)
public class DataTypeTest extends RplBaseTestCase {
    private static final String DB_NAME = "cdc_datatype";
    private static final String[] TABLES = new String[] {"Numeric", "DateTime", "String", "Spatial", "JSON"};
    private static final int COUNT = 20;

    @Parameterized.Parameters(name = "table ({0})")
    public static Object[] data() {
        return Stream.of(TABLES).map(t -> new TableMocker(DB_NAME, t)).toArray();
    }

    @Parameterized.Parameter // first data value (0) is default
    public TableMocker tableMocker;

    @BeforeClass
    public static void bootStrap() throws SQLException {
        prepareTestDatabase(DB_NAME);
    }

    @AfterClass
    public static void after() throws SQLException {
        try (Connection polardbxConnection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            JdbcUtil.executeSuccess(polardbxConnection, "DROP DATABASE IF EXISTS `" + DB_NAME + "`");
        }
    }

    @Before
    public void init() {
        tableMocker.setPolardbxJdbcTemplate(polardbxJdbcTemplate);
        tableMocker.create();
    }

    @Test
    public void testInsert() {
        for (int i = 0; i < COUNT; i++) {
            tableMocker.insert();
        }
        check();
    }

    @Test
    public void testUpdate() {
        tableMocker.update();
        check();
    }

    @Test
    public void testDropColumn() {
        tableMocker.dropColumn();
        check();
    }

    @Test
    public void testInsertAfterDropColumn() {
        for (int i = 0; i < COUNT; i++) {
            tableMocker.insert();
        }
        check();
    }

    @Test
    public void testUpdateAfterDropColumn() {
        tableMocker.update();
        check();
    }

    @Test
    public void testAddColumn() {
        tableMocker.addColumn();
        check();
    }

    @Test
    public void testUpdateAfterAddColumn() {
        tableMocker.update();
        check();
    }

    @Test
    public void testInsertAfterAddColumn() {
        for (int i = 0; i < COUNT; i++) {
            tableMocker.insert();
        }
        check();
    }

    private void check() {
        //numeric date_time spatial, polardb-x子查询兼容性错误，checksum不一致
        boolean directCompareDetail = false;
        if ("numeric".equals(tableMocker.toString()) || "datetime".equals(tableMocker.toString()) || "spatial".equals(
            tableMocker.toString())) {
            directCompareDetail = true;
        }
        waitAndCheck(CheckParameter.builder()
            .dbName(DB_NAME)
            .tbName(tableMocker.getTableDetail().getTableName())
            .directCompareDetail(directCompareDetail)
            .build());
    }
}