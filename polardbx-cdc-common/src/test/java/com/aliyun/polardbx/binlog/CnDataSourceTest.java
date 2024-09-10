/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
