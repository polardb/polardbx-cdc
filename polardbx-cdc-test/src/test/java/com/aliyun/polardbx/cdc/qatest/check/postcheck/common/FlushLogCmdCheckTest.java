/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

@Slf4j
public class FlushLogCmdCheckTest extends RplBaseTestCase {

    @Test
    public void testCheckFlushLog() throws ExecutionException, RetryException {
        Retryer retryer = RetryerBuilder.newBuilder()
            .withWaitStrategy(WaitStrategies.fixedWait(2, TimeUnit.SECONDS))
            .retryIfException()
            .withStopStrategy(
                StopStrategies.stopAfterDelay(2, TimeUnit.MINUTES)).build();
        retryer.call(() -> {
            doCheck();
            return null;
        });
    }

    public void doCheck() {
        int triggerCount = 0;
        int flushCount = 0;
        String labEventCountQuery =
            "select count(*) as c from binlog_lab_event where event_type = %d and params = '%s'";

        String clusterType;
        if (usingBinlogX) {
            clusterType = ClusterType.BINLOG_X.name();
        } else {
            clusterType = ClusterType.BINLOG.name();
        }
        try (Connection conn = getMetaConnection()) {
            triggerCount = NumberUtils.createInteger(JdbcUtil.executeQueryAndGetFirstStringResult(
                String.format(labEventCountQuery, LabEventType.SCHEDULE_TRIGGER_FLUSH_LOGS
                    .ordinal(), clusterType), conn));

            flushCount = NumberUtils.createInteger(JdbcUtil.executeQueryAndGetFirstStringResult(
                String.format(labEventCountQuery, LabEventType.FLUSH_LOGS
                    .ordinal(), clusterType), conn));

        } catch (Exception e) {
            throw new PolardbxException("query trigger and flush counter error!", e);
        }
        Assert.assertTrue("expect trigger count > 0, but " + triggerCount + " <= 0", triggerCount > 0);
        Assert.assertTrue("expect flush count > trigger count, but [" + flushCount + " <= " + triggerCount + "]",
            flushCount >= triggerCount);
    }
}
