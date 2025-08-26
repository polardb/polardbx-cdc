/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.dump;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;

public class MysqlConnectionTest {
    @Test
    public void testGenerateServerId() {
        MysqlConnection connection = Mockito.mock(MysqlConnection.class);
        Mockito.doCallRealMethod().when(connection).generateUniqueServerId();
        long serverId = connection.generateUniqueServerId();
        Assert.assertTrue("serverId should not equal 0 ", serverId != 0);
    }

    @Test
    public void testBinlogList() throws SQLException {

        Connection innerConn = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        Mockito.when(innerConn.createStatement()).thenReturn(stmt);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.next()).thenReturn(true, true, true, false);
        Mockito.when(resultSet.getString(1)).thenReturn("a.101", "a.1001", "a.11");
        Mockito.when(stmt.executeQuery(anyString())).thenReturn(resultSet);

        MysqlConnection connection =
            Mockito.mock(MysqlConnection.class, Mockito.withSettings().useConstructor(innerConn));
        final List<String> binlogList = new ArrayList<>();
        binlogList.add("a.11");
        binlogList.add("a.101");
        binlogList.add("a.1001");
        Mockito.when(connection.query(Mockito.anyString(), Mockito.any()))
            .thenCallRealMethod();
        Mockito.when(connection.binlogList()).thenCallRealMethod();
        List<String> result = connection.binlogList();
        Assert.assertEquals(binlogList, result);
    }
}
