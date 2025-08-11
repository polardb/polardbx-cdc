/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation;


import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.validation.common.ValidationUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;


import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

public class ValidationSamplerTest extends BaseTest {

    @Test
    public void calculateBatchRowSize_AvgRowSizeZero_ReturnsConfiguredBatchRowSize() throws SQLException {
        Connection conn = Mockito.mock(Connection.class);
        try (MockedStatic<ValidationUtil> validationUtilMockedStatic = Mockito.mockStatic(ValidationUtil.class)) {
            validationUtilMockedStatic.when(() -> ValidationUtil.getAvgTableLengthFromInformationSchema(
                any(Connection.class), anyString(), anyString())).thenReturn(0L);
            Mockito.when(DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_BATCH_ROW_SIZE)).thenReturn(100L);
            Mockito.when(DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_BATCH_BYTE_SIZE)).thenReturn(1000L);
            mockConfig(ConfigKeys.RPL_FULL_VALID_BATCH_ROW_SIZE, "100");
            mockConfig(ConfigKeys.RPL_FULL_VALID_BATCH_BYTE_SIZE, "1000");
            long result = ValidationSampler.calculateBatchRowSize(conn, "testDb", "testTable");
            Assert.assertEquals(100L, result);
        }
    }

    @Test
    public void calculateBatchRowSize_AvgRowSizeGreaterThanZero_ReturnsMinOfConfiguredAndCalculated() throws SQLException {
        Connection conn = Mockito.mock(Connection.class);
        try (MockedStatic<ValidationUtil> validationUtilMockedStatic = Mockito.mockStatic(ValidationUtil.class)) {
            validationUtilMockedStatic.when(() -> ValidationUtil.getAvgTableLengthFromInformationSchema(
                any(Connection.class), anyString(), anyString())).thenReturn(50L);
            Mockito.when(DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_BATCH_ROW_SIZE)).thenReturn(100L);
            Mockito.when(DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_BATCH_BYTE_SIZE)).thenReturn(1000L);
            mockConfig(ConfigKeys.RPL_FULL_VALID_BATCH_ROW_SIZE, "100");
            mockConfig(ConfigKeys.RPL_FULL_VALID_BATCH_BYTE_SIZE, "1000");
            long result = ValidationSampler.calculateBatchRowSize(conn, "testDb", "testTable");
            Assert.assertEquals(20L, result);
        }
    }
}
