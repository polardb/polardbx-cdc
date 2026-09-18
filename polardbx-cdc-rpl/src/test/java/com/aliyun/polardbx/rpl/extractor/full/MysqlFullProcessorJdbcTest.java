/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.full;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MysqlFullProcessorJdbcTest extends BaseTest {
    @Test
    public void metadataQueryUsesPreparedSqlAndClosesResources() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        String sql = "select count(1) from `t`";
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.prepareStatement(sql)).thenReturn(statement);
        Mockito.when(statement.executeQuery(Mockito.anyString()))
            .thenThrow(new SQLException("SQL-argument overload is not permitted on PreparedStatement"));
        Mockito.when(statement.executeQuery()).thenReturn(resultSet);
        Mockito.when(resultSet.next()).thenReturn(true);
        Mockito.when(resultSet.getObject(1)).thenReturn(42L);

        MysqlFullProcessor processor = new MysqlFullProcessor();
        processor.setDataSource(dataSource);
        Method method = MysqlFullProcessor.class.getDeclaredMethod("getMetaInfo", String.class);
        method.setAccessible(true);
        Assert.assertEquals(42L, method.invoke(processor, sql));
        Mockito.verify(statement).executeQuery();
        Mockito.verify(statement, Mockito.never()).executeQuery(Mockito.anyString());
        Mockito.verify(resultSet).close();
        Mockito.verify(statement).close();
        Mockito.verify(connection).close();
    }
}
