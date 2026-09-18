/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import org.apache.commons.lang.math.NumberUtils;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SetCompressionCmdCheckTest extends RplBaseTestCase {
    @Test
    public void testCheckSetCompression() throws ExecutionException, RetryException {
        Retryer retryer = RetryerBuilder.newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(2, TimeUnit.SECONDS))
            .retryIfException()
            .retryIfExceptionOfType(AssertionError.class)
            .withStopStrategy(
                StopStrategies.stopAfterDelay(2, TimeUnit.MINUTES)).build();
        retryer.call(() -> {
            doCheck();
            return null;
        });
    }

    public void doCheck() {
        int triggerCount = 0;
        int applyCount = 0;
        String labEventCountQuery =
            "select count(*) as c from binlog_lab_event where event_type = %d";
        String historyQuery =
            "select change_env_content from binlog_env_config_history";

        try (Connection conn = getMetaConnection()) {
            triggerCount = NumberUtils.createInteger(JdbcUtil.executeQueryAndGetFirstStringResult(
                String.format(labEventCountQuery, LabEventType.SCHEDULE_SET_COMPRESSION
                    .ordinal()), conn));

            ResultSet rs = JdbcUtil.executeQuery(historyQuery, conn);
            while (rs.next()) {
                String content = rs.getString(1);
                JSONObject jsonObject = JSON.parseObject(content);
                for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION)) {
                        applyCount++;
                    }
                }
            }

        } catch (Exception e) {
            throw new PolardbxException("query trigger and flush counter error!", e);
        }
        Assert.assertTrue("expect trigger count > 0, but " + triggerCount + " <= 0", triggerCount > 0);
        Assert.assertTrue("expect flush count > trigger count, but [" + applyCount + " <= " + triggerCount + "]",
            applyCount >= triggerCount);
    }
}
