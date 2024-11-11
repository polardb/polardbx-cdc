/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.metadata;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Map;

public class TableTopologyManagerTest extends BaseTestWithGmsTables {

    @Test
    @Ignore
    public void testBuildCache() {
        TableTopology value = TableTopologyManager.buildCache();
        for (Map.Entry<String, String> entry : value.getPhysicalTablesMap().entrySet()) {
            if (StringUtils.contains(entry.getKey(), "tso_test")) {
                System.out.println("key:" + entry.getKey());
                System.out.println("value:" + entry.getValue());
            }
        }
        System.out.println(value.getPhysicalTablesMap().get("tso_test.t_user_wzkz"));
        System.out.println(JSONObject.toJSONString(value));
    }
}
