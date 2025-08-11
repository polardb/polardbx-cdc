/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

public class ServerConfigUtilTest extends BaseTest {
    @Test
    public void testCnVersion() {
        JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        Mockito.when(polarxJdbcTemplate.queryForObject(Mockito.anyString(), Mockito.eq(String.class)))
            .thenReturn("5.7.25-log");
        registerSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
        String cnVersion = ServerConfigUtil.getCnVersion();
        Assert.assertEquals("5.7.25-log", cnVersion);
        unregisterSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
    }
}
