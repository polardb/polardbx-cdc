/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.testing.h2;

import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-08-16 17:00
 **/
public class H2UtilTest {

    @Test
    public void testConnection_1() throws SQLException {
        Connection connection = H2Util.getH2Connection();
        H2Util.executeUpdate(connection, "create table t1(name varchar(200))");
        List<String> tables = H2Util.showTables(connection, null);
        Assert.assertTrue(tables.contains("T1"));

        connection.close();

        Connection connection2 = H2Util.getH2Connection();
        tables = H2Util.showTables(connection2, null);
        Assert.assertTrue(tables.contains("T1"));
    }

    @Test
    public void testConnection_2() {
        Connection connection = H2Util.getH2Connection();
        List<String> tables = H2Util.showTables(connection, null);
        Assert.assertTrue(tables.contains("T1"));
    }

    @Test
    public void testConnection_3() throws SQLException {
        JdbcConnectionPool pool = H2Util.getH2JdbcConnectionPool();
        List<String> tables = H2Util.showTables(pool.getConnection(), null);
        Assert.assertTrue(tables.contains("T1"));
    }
}
