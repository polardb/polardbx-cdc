/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

@Slf4j
public class DumpFileStatusCheckLabEventTest {
    private static final String QUERY_BINLOG_EVENT = "SELECT * FROM binlog_lab_event where event_type = %s";
    private static final int MAX_ERROR_COUNT = 10;

    @Test
    @SneakyThrows
    public void testBinlogDumpFileStatusCheckLabEvent() {
        try (Connection c = ConnectionManager.getInstance().getDruidMetaConnection()) {
            ResultSet rs =
                JdbcUtil.executeQuery(
                    String.format(QUERY_BINLOG_EVENT, LabEventType.DUMPER_FILE_STATUS_CHECK.ordinal()), c);
            int errorCount = 0;
            while (rs.next()) {
                errorCount++;
                String logEvent = rs.getString("params");
                log.error("Find unexpected file status during dumping {} ", logEvent);
            }
            Assert.assertTrue(errorCount <= MAX_ERROR_COUNT);
        }
    }
}