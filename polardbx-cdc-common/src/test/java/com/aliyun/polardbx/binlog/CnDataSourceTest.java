/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.binlog.util.ConfigPropMap;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CnDataSourceTest extends BaseTestWithGmsTables {

    private static int timeoutInSec = 10;

    @Test(timeout = 30000)
    public void testWaitTimeout() throws NoSuchFieldException, IllegalAccessException {
        Field field = ConfigPropMap.class.getDeclaredField("CONFIG_MAP");
        field.setAccessible(true);
        Map<String, String> CONFIG_MAP = (Map<String, String>) field.get(null);
        CONFIG_MAP.put(ConfigKeys.DATASOURCE_CN_GET_TIMEOUT_IN_SECOND, String.valueOf(timeoutInSec));
        CnDataSource dataSource = new CnDataSource(null, false);
        long now = System.currentTimeMillis();
        PolardbxException exception = null;
        try {
            dataSource.waitNestedAddressReady();
        } catch (PolardbxException e) {
            exception = e;
        }
        Assert.assertTrue(System.currentTimeMillis() - now > TimeUnit.SECONDS.toMillis(timeoutInSec));
        Assert.assertNotNull(exception);
    }

    @Test(timeout = 30000)
    public void testDoNotWait() {
        setConfig(ConfigKeys.DATASOURCE_CN_GET_TIMEOUT_IN_SECOND, String.valueOf(timeoutInSec));
        CnDataSource dataSource = new CnDataSource(null, false);
        long now = System.currentTimeMillis();
        dataSource.nestedAddresses.add("127.0.0.1");
        dataSource.waitNestedAddressReady();
        Assert.assertTrue(System.currentTimeMillis() - now < TimeUnit.SECONDS.toMillis(timeoutInSec));
    }

}
