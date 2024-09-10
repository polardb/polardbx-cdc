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

import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.binlog.util.PropertyChangeListener;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DynamicApplicationConfigTest extends BaseTestWithGmsTables {

    @Test
    public void getValue() {
        System.out.println(DynamicApplicationConfig.getValue("daemon_tso_heartbeat_interval_ms"));
        DynamicApplicationConfig.setValue("daemon_tso_heartbeat_interval_ms", "10");
        System.out.println(DynamicApplicationConfig.getValue("daemon_tso_heartbeat_interval_ms"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void setValue1() {
        DynamicApplicationConfig.setValue(null, "1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void setValue2() {
        DynamicApplicationConfig.setValue("k", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setValue3() {
        DynamicApplicationConfig.setValue("k", "");
    }

    @Test
    public void testMultiPropertiesChange() throws InterruptedException {
        List<String> properChangeList = new ArrayList<>();
        DynamicApplicationConfig.addPropListener(ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL_MS,
            new PropertyChangeListener() {
                @Override
                public void onInit(String propsName, String value) {

                }

                @Override
                public void onPropertyChange(String propsName, String oldValue, String newValue) {
                    properChangeList.add(newValue);
                }
            });

        DynamicApplicationConfig.setValue(ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL_MS, "10");
        DynamicApplicationConfig.setValue(ConfigKeys.BINLOG_WRITE_HEARTBEAT_AS_TXN, "false");
        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        Assert.assertEquals(1, properChangeList.size());
        Assert.assertEquals("10", properChangeList.get(0));
        DynamicApplicationConfig.setValue(ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL_MS, "20");
        DynamicApplicationConfig.setValue(ConfigKeys.BINLOG_WRITE_HEARTBEAT_AS_TXN, "true");
        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        Assert.assertEquals(2, properChangeList.size());
        Assert.assertEquals("20", properChangeList.get(1));
        DynamicApplicationConfig.setValue(ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL_MS, "30");
        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        Assert.assertEquals(3, properChangeList.size());
        Assert.assertEquals("30", properChangeList.get(2));
        DynamicApplicationConfig.setValue(ConfigKeys.BINLOG_WRITE_HEARTBEAT_AS_TXN, "false");
        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        Assert.assertEquals(3, properChangeList.size());
        DynamicApplicationConfig.setValue(ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL_MS, "40");
        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        Assert.assertEquals(4, properChangeList.size());
        Assert.assertEquals("40", properChangeList.get(3));

    }
}
