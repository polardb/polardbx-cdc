/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;


import java.util.concurrent.TimeUnit;

public class DruidDataSourceWrapperTest extends BaseTestWithGmsTables {

    private static final int timeoutInSec = 30;

    @Test(timeout = 35000)
    public void testWaitTimeout() throws Exception {
        DruidDataSourceWrapper dataSource = new DruidDataSourceWrapper("db", "user",
            "passwd", "utf8", 1, 20, null, null);
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

    @Test(timeout = 35000)
    public void testDoNotWait() throws Exception {
        DruidDataSourceWrapper dataSource = new DruidDataSourceWrapper("db", "user",
            "passwd", "utf8", 1, 20, null, null);
        long now = System.currentTimeMillis();
        dataSource.nestedAddresses.add("127.0.0.1");
        dataSource.waitNestedAddressReady();
        Assert.assertTrue(System.currentTimeMillis() - now < TimeUnit.SECONDS.toMillis(timeoutInSec));
    }

}
