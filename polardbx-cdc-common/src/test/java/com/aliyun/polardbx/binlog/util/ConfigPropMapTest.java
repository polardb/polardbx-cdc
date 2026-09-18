/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

public class ConfigPropMapTest extends BaseTest {
    @Test
    public void testConfigPropMap() {
        Assert.assertEquals("30",
            ConfigPropMap.getPropertyValue(ConfigKeys.DATASOURCE_CN_GET_TIMEOUT_IN_SECOND));
        Assert.assertEquals("2",
            ConfigPropMap.getPropertyValue(ConfigKeys.DATASOURCE_CN_CONNECT_TIMEOUT_IN_SECOND));
    }

    @Test
    public void testGetSpringPropertyBoolean() {
        Assert.assertTrue(ConfigPropMap.getSpringPropertyBoolean(ConfigKeys.DAEMON_METADB_SCAN_BY_SELECT_ENABLED));
        Assert.assertTrue(ConfigPropMap.getSpringPropertyBoolean(ConfigKeys.DAEMON_METADB_SCAN_CHECK_ENV_ENABLED));
    }
}
