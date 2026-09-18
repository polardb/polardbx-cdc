/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.unit.test;

import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.flashback.FlashBackTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;

import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.analyzeTable;
import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.checkIfTableNotExistError;

public class JdbcUtilTest {

    @Test
    public void testDriverMajorVersionDoesNotParseDisplayString() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metadata = Mockito.mock(DatabaseMetaData.class);
        Mockito.when(connection.getMetaData()).thenReturn(metadata);
        Mockito.when(metadata.getDriverVersion()).thenReturn("@MYSQL_CJ_FULL_PROD_NAME@ (Revision: @MYSQL_CJ_REVISION@)");
        Mockito.when(metadata.getDriverMajorVersion()).thenReturn(8);
        Assert.assertEquals(8, JdbcUtil.getDriverMajorVersion(connection));
        Mockito.verify(metadata, Mockito.never()).getDriverVersion();
    }

    @Test
    public void testNumericResultUsesJdbcConversionAndPreservesNull() throws Exception {
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.next()).thenReturn(true, false, true, false);
        Mockito.when(resultSet.getObject(1)).thenReturn(new java.math.BigInteger("42"));
        Mockito.when(resultSet.getLong(1)).thenReturn(42L, 0L);
        Mockito.when(resultSet.wasNull()).thenReturn(false, true);
        Assert.assertEquals(Long.valueOf(42), JdbcUtil.resultLong(resultSet));
        Assert.assertNull(JdbcUtil.resultLong(resultSet));
        Mockito.verify(resultSet, Mockito.never()).getObject(1);
    }

    @Test
    public void testMetadataHelpersExecuteTheirPreparedSql() throws Exception {
        BaseTestCase base = new BaseTestCase();
        FlashBackTest flashback = new FlashBackTest();
        for (int path = 0; path < 4; path++) {
            Connection connection = Mockito.mock(Connection.class);
            DataSource dataSource = Mockito.mock(DataSource.class);
            PreparedStatement statement = Mockito.mock(PreparedStatement.class);
            ResultSet resultSet = Mockito.mock(ResultSet.class);
            Mockito.when(dataSource.getConnection()).thenReturn(connection);
            Mockito.when(connection.prepareStatement(Mockito.anyString())).thenReturn(statement);
            Mockito.when(statement.executeQuery()).thenReturn(resultSet);
            Mockito.when(resultSet.next()).thenReturn(true, false);
            Mockito.when(resultSet.getString(1)).thenReturn("id");
            if (path == 0) {
                Assert.assertEquals(Collections.singletonList("id"), base.getColumnsByDesc("db", "t", connection));
            } else if (path == 1) {
                Assert.assertEquals(Collections.singletonList("id"), base.getColumns("db", "t", connection));
            } else if (path == 2) {
                Assert.assertEquals(Collections.singletonList("id"), flashback.getTableList("db", dataSource));
            } else {
                Assert.assertEquals(Collections.singletonList("id"), flashback.getColumns("db", "t", dataSource));
            }
            Mockito.verify(statement).executeQuery();
            Mockito.verify(statement, Mockito.never()).executeQuery(Mockito.anyString());
            Mockito.verify(statement).close();
            Mockito.verify(resultSet).close();
        }
    }

    @Test
    public void testAnalyzeTableAcceptsResultSet() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        Mockito.when(connection.createStatement()).thenReturn(statement);
        Mockito.when(statement.execute("analyze table t")).thenReturn(true);
        analyzeTable(connection, "t");
        Mockito.verify(statement).execute("analyze table t");
        Mockito.verify(statement, Mockito.never()).executeUpdate(Mockito.anyString());
        Mockito.verify(statement).close();
    }

    @Test
    public void testCheckIfTableNotExistError() {
        String input = "Error: Table 'cdc_virtual_table_mapping_single.user_balance_log_1753279596' doesn't exist ";
        Assert.assertTrue(checkIfTableNotExistError(input));

        input = "[1a2857d0dac01000][192.0.2.33:3306][cdc_virtual_table_mapping]ERR-CODE: [TDDL-4614]"
            + "[ERR_EXECUTE_ON_MYSQL] Error occurs when execute on GROUP 'CDC_VIRTUAL_TABLE_MAPPING_SINGLE_GROUP' ATOM "
            + "'dskey_cdc_virtual_table_mapping_single_group#test-instance-dn-0#192.0.2.34-3306#"
            + "cdc_virtual_table_mapping_single': Table 'cdc_virtual_table_mapping_single.user_balance_log_1753279596' doesn't exist ";
        Assert.assertTrue(checkIfTableNotExistError(input));

        input = "Error: Table 'cdc_virtual_table_mapping_single.user_balance_log_1753279596' doesn'tx exist ";
        Assert.assertFalse(checkIfTableNotExistError(input));
    }
}
