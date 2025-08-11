/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class LogFetcherFactoryTest {
    @Test
    public void testCreateURLLogFetcher() throws NoSuchFieldException, IllegalAccessException {
        URLLogFetcher fetcher = LogFetcherFactory.createURLLogFetcher("test", "test");
        Assert.assertNotNull(fetcher);
        Field storageField = URLLogFetcher.class.getDeclaredField("storageInstanceId");
        Field fileNameField = URLLogFetcher.class.getDeclaredField("fileName");
        storageField.setAccessible(true);
        fileNameField.setAccessible(true);
        Assert.assertEquals("test", storageField.get(fetcher));
        Assert.assertEquals("test", fileNameField.get(fetcher));
    }
}
