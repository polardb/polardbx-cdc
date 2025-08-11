/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation.common;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.mockito.Mockito.*;

public class ValidationUtilTest {

    @Mock
    private Connection mockConnection;

    @Mock
    private Statement mockStatement;

    @Mock
    private ResultSet mockResultSet;

    @InjectMocks
    private ValidationUtil validationUtil;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getAvgTableLengthFromInformationSchema_WithExistingTable_ReturnsAvgRowLength() throws SQLException {
        String dbName = "testDb";
        String tbName = "testTable";
        long expectedAvgRowLength = 100L;

        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getLong("AVG_ROW_LENGTH")).thenReturn(expectedAvgRowLength);

        long result = ValidationUtil.getAvgTableLengthFromInformationSchema(mockConnection, dbName, tbName);

        Assert.assertEquals(expectedAvgRowLength, result);
    }

    @Test
    public void getAvgTableLengthFromInformationSchema_WithNonExistingTable_ReturnsZero() throws SQLException {
        String dbName = "testDb";
        String tbName = "nonExistingTable";

        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        long result = ValidationUtil.getAvgTableLengthFromInformationSchema(mockConnection, dbName, tbName);

        Assert.assertEquals(0L, result);
    }

    @Test(expected = SQLException.class)
    public void getAvgTableLengthFromInformationSchema_WithSQLException_ThrowsSQLException() throws SQLException {
        String dbName = "testDb";
        String tbName = "testTable";

        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenThrow(SQLException.class);

        ValidationUtil.getAvgTableLengthFromInformationSchema(mockConnection, dbName, tbName);
    }
}
