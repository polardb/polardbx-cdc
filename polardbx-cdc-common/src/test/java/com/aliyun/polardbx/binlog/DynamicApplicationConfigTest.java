/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
