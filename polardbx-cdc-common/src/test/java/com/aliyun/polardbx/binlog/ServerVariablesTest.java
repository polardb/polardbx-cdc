/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

/**
 * ServerVariables 中注册的配置项决定了哪些配置可以通过
 * {@code set cdc global xxx = yyy} 动态修改，并被 CdcMetaManager 初始化写入
 * binlog_system_config 表，因此新增可动态调整的配置时必须在此注册。
 */
public class ServerVariablesTest extends BaseTest {

    @Test
    public void testVariablesNotEmpty() {
        Assert.assertNotNull(ServerVariables.variables);
        Assert.assertFalse(ServerVariables.variables.isEmpty());
    }

    /**
     * CHARSET/COLLATE 保留字白名单需要支持运维在线调整（新增保留字无需发版），
     * 故必须注册到 ServerVariables 中。
     */
    @Test
    public void testCharacterQuoteKeywordsRegistered() {
        Assert.assertTrue(ServerVariables.variables.contains(ConfigKeys.TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS));
    }

    @Test
    public void testReformatRelatedVariablesRegistered() {
        Assert.assertTrue(ServerVariables.variables.contains(ConfigKeys.TASK_REFORMAT_DDL_ALGORITHM_BLACKLIST));
        Assert.assertTrue(ServerVariables.variables.contains(ConfigKeys.TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED));
    }
}
