/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.dump;

import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(innerConn.createStatement()).thenReturn(stmt);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, true, true, false);
        when(resultSet.getString(1)).thenReturn("a.101", "a.1001", "a.11");
        when(stmt.executeQuery(anyString())).thenReturn(resultSet);

        MysqlConnection connection =
            Mockito.mock(MysqlConnection.class, Mockito.withSettings().useConstructor(innerConn));
        final List<String> binlogList = new ArrayList<>();
        binlogList.add("a.11");
        binlogList.add("a.101");
        binlogList.add("a.1001");
        when(connection.query(Mockito.anyString(), Mockito.any()))
            .thenCallRealMethod();
        when(connection.binlogList()).thenCallRealMethod();
        List<String> result = connection.binlogList();
        Assert.assertEquals(binlogList, result);
    }

    @Test
    @SneakyThrows
    public void testUpdateSettings() {
        MysqlConnection connection = Mockito.mock(MysqlConnection.class);
        doCallRealMethod().when(connection).updateSettings(any());
        Map<String, String> params1 = new HashMap<>();
        params1.put("a", "b");
        connection.updateSettings(params1);
        verify(connection, Mockito.times(1)).update("set `a`='b'");
        Map<String, String> params2 = new HashMap<>();
        params2.put("c", "d");
        when(connection.update("set `c`='d'")).thenThrow(new RuntimeException());
        connection.updateSettings(params2);
        verify(connection, Mockito.times(1)).update("set `c`='d'");
    }

    @Test
    @SneakyThrows
    public void testDump() {
        MysqlConnection connection = Mockito.mock(MysqlConnection.class);
        doCallRealMethod().when(connection).dump(any(), anyLong(), anyLong(), any());
        connection.dump("", 0L, 0L, null);
        verify(connection, Mockito.times(1)).dump("", 0L, 0L, null, null);
    }
}
