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
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * created by ziyang.lb
 */
@Slf4j
public class BigEventTest extends RplBaseTestCase {
    private static final String DB_NAME = "cdc_big_event";
    private static final String TABLE_NAME = "t_big_event";

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

    @Test
    public void testBigEvent() {
        JdbcUtil.executeUpdate(polardbxConnection, "CREATE TABLE `" + DB_NAME + "`.`" + TABLE_NAME + "` ( "
            + " `id` int unsigned NOT NULL AUTO_INCREMENT,"
            + " `content` longtext,"
            + " PRIMARY KEY (`id`))"
            + " dbpartition by hash(id) tbpartition by hash(id) tbpartitions 2");
        JdbcUtil.executeSuccess(polardbxConnection,
            "insert into " + DB_NAME + "." + TABLE_NAME + " values (1,'a')");
        JdbcUtil.executeSuccess(polardbxConnection,
            "insert into " + DB_NAME + "." + TABLE_NAME + " values (2, repeat('2',16*1024*1024))");
        JdbcUtil.executeSuccess(polardbxConnection,
            "insert into " + DB_NAME + "." + TABLE_NAME + " values (3, repeat('2',3*16*1024*1024))");

        waitAndCheck(CheckParameter.builder().dbName(DB_NAME).tbName(TABLE_NAME).build());
    }
}
