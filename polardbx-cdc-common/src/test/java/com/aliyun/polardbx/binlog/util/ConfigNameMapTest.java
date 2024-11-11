/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author yudong
 * @since 2023/2/22 17:13
 **/
public class ConfigNameMapTest {

    @Test
    public void getConfigNameTest() {
        String configName = "binlogx_stream_group_name";
        String expect = "binlogx.stream.group.name";
        String actual = ConfigNameMap.getOldConfigName(configName);
        Assert.assertEquals(expect, actual);

        configName = "not_exist_config";
        expect = "";
        actual = ConfigNameMap.getOldConfigName(configName);
        Assert.assertEquals(expect, actual);
    }

    @Test
    public void isOldConfigNameTest() {
        String configName = "binlog.write.supportRowsQueryLog";
        Assert.assertTrue(ConfigNameMap.isOldConfigName(configName));

        configName = "binlog.write.supportRowsQueryLog.fake";
        Assert.assertFalse(ConfigNameMap.isOldConfigName(configName));
    }

    @Test
    public void getNewConfigNameTest() {
        String configName = "binlog.write.supportRowsQueryLog";
        String expect = "binlog_write_rows_query_event_enabled";
        Assert.assertEquals(expect, ConfigNameMap.getNewConfigName(configName));

        configName = "binlog.write.supportRowsQueryLog.fake";
        Assert.assertNull(ConfigNameMap.getNewConfigName(configName));
    }
}
