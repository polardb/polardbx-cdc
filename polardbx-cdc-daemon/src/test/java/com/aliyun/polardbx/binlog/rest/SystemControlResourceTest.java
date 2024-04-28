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
package com.aliyun.polardbx.binlog.rest;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.daemon.rest.RestServer;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SystemControlResourceTest extends BaseTest {

    private static final String DAEMON_PORT = "8089";

    @Before
    public void before() {
        setConfig(ConfigKeys.DAEMON_PORT, DAEMON_PORT);
        setConfig(ConfigKeys.RELEASE_NOTE_PATH, this.getClass().getClassLoader().getResource("releaseNote").getFile());
        RestServer restServer = new RestServer();
        restServer.start();
    }

    @Test
    public void testGetVersion() {
        String result = HttpHelper.get(String.format("http://127.0.0.1:%s/system/getVersion", DAEMON_PORT), 2000);
        Assert.assertNotNull(result);
        // 返回 x.y.z-date-buildNum
        Assert.assertTrue(result.matches("\\d+\\.\\d+\\.\\d+\\-\\d{8}_\\d{8}"));
    }
}
