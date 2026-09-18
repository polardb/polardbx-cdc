/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

public class TimeEnvConfigTest extends BaseTest {
    private final TimelineEnvConfig timelineEnvConfig = new TimelineEnvConfig();

    @Test
    public void testGetIgnoreMetaDb() {
        boolean resB = timelineEnvConfig.getBooleanIgnoreMetaDb(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION, false);
        String resS = timelineEnvConfig.getStringIgnoreMetaDb(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION_TYPE, "zstd");
        int resI = timelineEnvConfig.getIntIgnoreMetaDb(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION_LEVEL_ZSTD, 1);
        Assert.assertFalse(resB);
        Assert.assertEquals("zstd", resS);
        Assert.assertEquals(1, resI);

        // set metaDB
        setConfig(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION, "ON");
        setConfig(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION_TYPE, "none");
        setConfig(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION_LEVEL_ZSTD, "2");

        // metaDB的值影响时间线
        resB = timelineEnvConfig.getBoolean(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION);
        Assert.assertTrue(resB);

        // metaDB的值不影响时间线
        resB = timelineEnvConfig.getBooleanIgnoreMetaDb(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION, false);
        resS = timelineEnvConfig.getStringIgnoreMetaDb(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION_TYPE, "zstd");
        resI = timelineEnvConfig.getIntIgnoreMetaDb(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION_LEVEL_ZSTD, 1);
        Assert.assertFalse(resB);
        Assert.assertEquals("zstd", resS);
        Assert.assertEquals(1, resI);

    }

    @Test
    public void testConfigExists() {
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        boolean exists = timelineEnvConfig.configExistsAlready(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION, true, "1000");
        Assert.assertFalse(exists);
        Map<String, String> configContent = new HashMap<>(1);
        configContent.put(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION, "ON");
        String configContentStr = JSON.toJSONString(configContent);
        String insertSql =
            String.format(
                "insert into binlog_env_config_history(`change_env_content`,`tso`,`instruction_id`) values ('%s','%s','%s')",
                configContentStr, "1000", "zm_test_config_exists");
        metaTemplate.execute(insertSql);
        exists = timelineEnvConfig.configExistsAlready(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION, true, "1000");
        Assert.assertTrue(exists);
    }
}
