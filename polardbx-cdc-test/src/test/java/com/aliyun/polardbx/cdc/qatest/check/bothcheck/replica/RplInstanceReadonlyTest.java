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
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

public class RplInstanceReadonlyTest extends RplBaseTestCase {
    @Test
    public void testSlaveInstanceReadonly() throws Exception {
        try (Connection conn = getCdcSyncDbConnection()) {
            JdbcUtil.executeUpdate(conn, "set polardbx_server_id = 2121");
            ResultSet resultSet = JdbcUtil.executeQuery("show global variables like '%instance_read_only%'", conn);
            Assert.assertTrue("instance_read_only not set", resultSet.next());
            Assert.assertEquals("true", resultSet.getString(2));

            // create tmp user;
            // use tmp user execute ddl
            // use tmp user execute dml
            JdbcUtil.executeSuccess(conn, "create user 'gdn_ro_test'@'%' identified by '123456'");
            JdbcUtil.executeSuccess(conn, "grant all on *.* to 'gdn_ro_test'@'%'");

            JdbcUtil.executeSuccess(conn, "create database if not exists gdn_ro_test_for_ctb");
            JdbcUtil.useDb(conn, "gdn_ro_test_for_ctb");
            JdbcUtil.executeSuccess(conn,
                "create table if not exists tmp_gdn_tbl_for_insert(`name` varchar(20), age int(11));");

        }
        final String errorMsg =
            "server is running with the instance-read-only option so it cannot execute this statement";
        try (Connection tmpConn = getCdcSyncDbConnection("gdn_ro_test", "123456")) {
            JdbcUtil.executeUpdate(tmpConn, "set polardbx_server_id = 2121");
            JdbcUtil.executeUpdateFailed(tmpConn, "create database 'gdn_ro_test_db'", errorMsg);
            JdbcUtil.useDb(tmpConn, "gdn_ro_test_for_ctb");
            JdbcUtil.executeUpdateFailed(tmpConn,
                "insert into tmp_gdn_tbl_for_insert(`name`,`age`) values('for_test_name', 20);",
                errorMsg);

            JdbcUtil.executeSuccess(tmpConn, "set SUPER_WRITE=true;");
            JdbcUtil.executeSuccess(tmpConn, "create table tmp_gdn_tb1(`name` varchar(20), age int(11));");
            JdbcUtil.executeSuccess(tmpConn, "insert into tmp_gdn_tb1(`name`, age) values('aaaa', 111);");
            JdbcUtil.executeSuccess(tmpConn, "drop table tmp_gdn_tb1;");

        }

    }

    @Before
    public void dropUserBefore() throws Exception {
        try (Connection conn = getCdcSyncDbConnection()) {
            JdbcUtil.executeUpdate(conn, "set polardbx_server_id = 2121");
            JdbcUtil.executeUpdate(conn, "drop user 'gdn_ro_test'@'%'");
        }
    }

    @After
    public void afterClean() throws Exception {
        try (Connection conn = getCdcSyncDbConnection()) {
            JdbcUtil.executeUpdate(conn, "set polardbx_server_id = 2121");
            JdbcUtil.executeUpdate(conn, "drop user 'gdn_ro_test'@'%'");
            JdbcUtil.executeUpdate(conn, "drop database if exists gdn_ro_test_for_ctb");
        }
    }
}
