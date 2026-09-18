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
public class ForceDownloadLabEventTest {
    private static final String QUERY_BINLOG_EVENT = "SELECT * FROM binlog_lab_event where event_type = %s";

    @Test
    @SneakyThrows
    public void testForceDownloadCheckLabEvent() {
        try (Connection c = ConnectionManager.getInstance().getDruidMetaConnection()) {
            ResultSet rs =
                JdbcUtil.executeQuery(
                    String.format(QUERY_BINLOG_EVENT, LabEventType.FORCE_DOWNLOAD_BINLOG_CHECK.ordinal()), c);
            boolean error = false;
            while (rs.next()) {
                error = true;
                log.error("force download check failed at {}", rs.getString("gmt_created"));
            }
            Assert.assertFalse(error);
        }
    }
}
