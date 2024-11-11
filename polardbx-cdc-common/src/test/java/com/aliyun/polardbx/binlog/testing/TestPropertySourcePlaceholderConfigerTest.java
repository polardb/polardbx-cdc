/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.testing;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.util.PropertyChangeListener;
import org.junit.Assert;
import org.junit.Test;

public class TestPropertySourcePlaceholderConfigerTest extends BaseTestWithGmsTables implements PropertyChangeListener {

    private boolean isModify = false;
    private int count = 0;

    @Test
    public void testReload() throws InterruptedException {
        DynamicApplicationConfig.addPropListener(ConfigKeys.TASK_REFORMAT_NO_FOREIGN_KEY_CHECK, this);
        DynamicApplicationConfig.setValue(ConfigKeys.TASK_REFORMAT_NO_FOREIGN_KEY_CHECK, "false");
        Thread.sleep(10000);
        Assert.assertEquals(true, isModify);
        Assert.assertEquals(1, count);
    }

    @Override
    public void onInit(String propsName, String value) {

    }

    @Override
    public void onPropertyChange(String propsName, String oldValue, String newValue) {
        isModify = true;
        count++;
    }
}
