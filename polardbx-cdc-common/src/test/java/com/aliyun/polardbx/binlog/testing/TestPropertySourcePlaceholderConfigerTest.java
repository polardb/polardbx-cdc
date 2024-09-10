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
