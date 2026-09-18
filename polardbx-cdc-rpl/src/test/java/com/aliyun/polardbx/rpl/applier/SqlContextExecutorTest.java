/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.polardbx.core.cj.NativeSession;
import com.alibaba.polardbx.core.cj.conf.PropertyKey;
import com.alibaba.polardbx.core.cj.jdbc.JdbcConnection;
import com.alibaba.polardbx.core.cj.jdbc.JdbcPropertySetImpl;
import com.alibaba.polardbx.core.cj.jdbc.StatementImpl;
import com.alibaba.polardbx.core.cj.jdbc.result.ResultSetInternalMethods;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SqlContextExecutorTest {

    @Test
    public void realDriverRejectsUpdateForResultSetStatementsButDdlExecutorAcceptsThem() throws Exception {
        String[] commands = {
            "ANALYZE TABLE t", "CHECK TABLE t", "CHECKSUM TABLE t", "OPTIMIZE TABLE t", "REPAIR TABLE t",
            "SHOW TABLES", "DESC t", "EXPLAIN SELECT * FROM t", "CALL p()", "CONTINUE DDL 123"
        };
        for (String command : commands) {
            DriverFixture fixture = new DriverFixture(true, -1);
            String sql = "/*DDL_SUBMIT_TOKEN=regression*/ /*+TDDL:CMD_EXTRA(ENABLE_ASYNC_DDL=false)*/ " + command;
            // Connector/J classifies these five administrative statements as result-producing before sending SQL.
            if (command.startsWith("ANALYZE") || command.startsWith("CHECK") || command.startsWith("OPTIMIZE")
                || command.startsWith("REPAIR")) {
                try (Statement statement = fixture.connection.createStatement()) {
                    try {
                        statement.executeUpdate(sql);
                        Assert.fail("driver must reject executeUpdate for " + command);
                    } catch (SQLException expected) {
                        Assert.assertTrue(expected.getMessage().contains("produce result sets"));
                    }
                }
            }
            SqlContextExecutor.execDdl(fixture.connection, context(sql));
            verify(fixture.session).execSQL(any(), Mockito.eq(sql), anyInt(), isNull(), anyBoolean(), any(),
                isNull(), anyBoolean());
        }
    }

    @Test
    public void realDriverAcceptsDdlWithUpdateCountAndPreservesDmlAffectedRows() throws Exception {
        DriverFixture ddl = new DriverFixture(false, 0);
        SqlContextExecutor.execDdl(ddl.connection, context("CREATE TABLE t(id INT)"));
        DriverFixture dml = new DriverFixture(false, 2);
        Assert.assertEquals(2, SqlContextExecutor.execUpdate(dml.connection, context("UPDATE t SET id=2")));
    }

    @Test
    public void realDriverRejectsQueryForUseAndSet() throws Exception {
        for (String sql : new String[] {"USE test", "SET @v=1"}) {
            DriverFixture fixture = new DriverFixture(false, 0);
            try (Statement statement = fixture.connection.createStatement()) {
                try {
                    statement.executeQuery(sql);
                    Assert.fail("executeQuery must reject " + sql);
                } catch (SQLException expected) {
                    Assert.assertTrue(expected.getMessage().contains("do not produce result sets"));
                }
                statement.execute(sql);
            }
        }
    }

    @Test
    public void preparedDdlUsesExecuteAndPreparedDmlKeepsAffectedRows() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        SqlContext context = new SqlContext("CALL p(?)", "test", "t", Collections.singletonList(7));
        SqlContextExecutor.execDdl(connection, context);
        verify(statement).setObject(1, 7);
        verify(statement).execute();
        verify(statement, never()).executeUpdate();
        when(statement.executeUpdate()).thenReturn(3);
        context.setSql("UPDATE t SET id=?");
        Assert.assertEquals(3, SqlContextExecutor.execUpdate(connection, context));
    }

    @Test
    public void ddlRestoresSessionContextOnSuccess() throws Exception {
        assertSessionRestored(false);
    }

    @Test
    public void ddlPropagatesFailureAndRestoresSessionContext() throws Exception {
        assertSessionRestored(true);
    }

    private void assertSessionRestored(boolean fail) throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show variables like 'sql_mode'")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(2)).thenReturn("STRICT_TRANS_TABLES");
        SqlContext context = context("ANALYZE TABLE t");
        context.setSqlMode("ANSI_QUOTES");
        context.setFpOverrideNow("2026-09-16 12:00:00");
        SQLException failure = new SQLException("real execution failure");
        if (fail) {
            when(statement.execute(context.getSql())).thenThrow(failure);
        }
        try {
            SqlContextExecutor.execDdl(connection, context);
            Assert.assertFalse("SQL failure must propagate", fail);
        } catch (SQLException e) {
            Assert.assertTrue(fail);
            Assert.assertSame(failure, e);
        }
        InOrder order = Mockito.inOrder(statement);
        order.verify(statement).execute("set sql_mode='ANSI_QUOTES'");
        order.verify(statement).execute("set @FP_OVERRIDE_NOW='2026-09-16 12:00:00'");
        order.verify(statement).execute(context.getSql());
        order.verify(statement).execute("set sql_mode='STRICT_TRANS_TABLES'");
        order.verify(statement).execute("set @FP_OVERRIDE_NOW=null");
        verify(statement, never()).executeUpdate(anyString());
        verify(connection, never()).close();
        verify(resultSet).close();
    }

    private static SqlContext context(String sql) {
        return new SqlContext(sql, "test", "t", null);
    }

    /** Real Connector/J statement and parser; only the server transport/result is mocked. No external DB required. */
    private static class DriverFixture {
        final JdbcConnection connection = Mockito.mock(JdbcConnection.class);
        final NativeSession session = Mockito.mock(NativeSession.class, Mockito.RETURNS_DEEP_STUBS);

        DriverFixture(boolean hasRows, long updateCount) throws Exception {
            JdbcPropertySetImpl properties = new JdbcPropertySetImpl();
            properties.getBooleanProperty(PropertyKey.enableEscapeProcessing).setValue(false);
            when(connection.getPropertySet()).thenReturn(properties);
            when(connection.getSession()).thenReturn(session);
            when(connection.getConnectionMutex()).thenReturn(this);
            when(connection.getDatabase()).thenReturn("test");
            when(session.getPropertySet()).thenReturn(properties);
            ResultSetInternalMethods result = Mockito.mock(ResultSetInternalMethods.class);
            when(result.hasRows()).thenReturn(hasRows);
            when(result.getUpdateCount()).thenReturn(updateCount);
            when(session.execSQL(any(), anyString(), anyInt(), isNull(), anyBoolean(), any(), isNull(), anyBoolean()))
                .thenReturn(result);
            when(connection.createStatement()).thenAnswer(invocation -> new StatementImpl(connection, "test"));
        }
    }
}
