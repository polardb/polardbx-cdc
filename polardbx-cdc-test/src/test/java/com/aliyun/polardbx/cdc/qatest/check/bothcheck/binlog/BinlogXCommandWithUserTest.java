/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.binlog;

import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.URL_PATTERN_WITHOUT_DB;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.getConnectionProperties;

public class BinlogXCommandWithUserTest extends BaseTestCase {

    private static final String SHOW_BINARY_STREAMS = "show binary streams";
    private static final String SHOW_BINARY_LOGS = "show binary logs";
    private static final String SHOW_BINARY_LOGS_WITH_STREAM = "show binary logs with '%s'";
    private static final String SHOW_MASTER_STATUS = "show master status";
    private static final String SHOW_MASTER_STATUS_WITH_STREAM = "show master status with '%s'";
    private static final String SHOW_BINLOG_EVENTS = "show binlog events limit 10";
    private static final String SHOW_BINLOG_EVENTS_WITH_STREAM = "show binlog events with '%s' limit 10";

    @Test
    public void before() {
        List<String> streams = getBinaryStreams();
        streams.forEach(s -> {
            String userName = getUserFromStream(s);
            String sql1 = "CREATE USER IF NOT EXISTS '" + userName + "'@'%' identified by '123456'";
            String sql2 = "grant ALL PRIVILEGES on *.* to '" + userName + "'@'%'";
            JdbcUtil.executeUpdate(getPolardbxConnection(), sql1);
            JdbcUtil.executeUpdate(getPolardbxConnection(), sql2);
        });
    }

    @Test
    public void testShowBinaryLogs() throws ExecutionException, RetryException {
        List<String> streams = getBinaryStreams();
        for (String s : streams) {
            compare(s, SHOW_BINARY_LOGS, SHOW_BINARY_LOGS_WITH_STREAM);
        }
    }

    @Test
    public void testShowMasterStatus() throws ExecutionException, RetryException {
        List<String> streams = getBinaryStreams();
        for (String s : streams) {
            compare(s, SHOW_MASTER_STATUS, SHOW_MASTER_STATUS_WITH_STREAM);
        }
    }

    @Test
    public void testShowBinlogEvents() throws ExecutionException, RetryException {
        List<String> streams = getBinaryStreams();
        for (String s : streams) {
            compare(s, SHOW_BINLOG_EVENTS, SHOW_BINLOG_EVENTS_WITH_STREAM);
        }
    }

    private void compare(String stream, String sql1, String sql2) throws ExecutionException, RetryException {
        Retryer<Object> retryer = buildRetryer();
        retryer.call(() -> {
            List<String> list1;
            List<String> list2;
            List<String> list3;

            String userName = getUserFromStream(stream);
            try (Connection connection = getConnectionWithUser(userName)) {
                list1 = JdbcUtil.executeQueryAndGetStringList(sql1, connection, 1);
            }
            list2 = JdbcUtil.executeQueryAndGetStringList(String.format(sql2, stream),
                getPolardbxConnection(), 1);
            list3 = JdbcUtil.executeQueryAndGetStringList(sql1, getPolardbxConnection(), 1);

            Assert.assertEquals(list1, list2);
            Assert.assertNotEquals(list1, list3);

            if (!list1.isEmpty()) {
                Assert.assertTrue(StringUtils.contains(list1.get(0), stream));
            }
            if (!list3.isEmpty()) {
                Assert.assertFalse(StringUtils.contains(list3.get(0), stream));
            }
            return null;
        });
    }

    private List<String> getBinaryStreams() {
        return JdbcUtil.executeQueryAndGetStringList(SHOW_BINARY_STREAMS, getPolardbxConnection(), 2);
    }

    private String getUserFromStream(String streamName) {
        return streamName + "_cdc_user";
    }

    private Connection getConnectionWithUser(String userName) {
        String url = String.format(URL_PATTERN_WITHOUT_DB + getConnectionProperties(false),
            ConnectionManager.getInstance().getPolardbxAddress(),
            ConnectionManager.getInstance().getPolardbxPort());
        return JdbcUtil.createConnection(url, userName, "123456");
    }

    private Retryer<Object> buildRetryer() {
        return RetryerBuilder.newBuilder().retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(600)).build();
    }
}
