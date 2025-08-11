/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.replica;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * @author zm
 */
@Slf4j
public class BinlogDumpPosLabEventTest extends RplBaseTestCase {
    private static final String QUERY_BINLOG_EVENT = "SELECT * FROM metaDB.binlog_lab_event where event_type = %s";

    @Test
    @SneakyThrows
    public void testBinlogDumpPosLabEvent() {
        try (Connection pdxConnection = ConnectionManager.getInstance().getDruidCdcSyncDbConnection()) {
            ResultSet rs =
                JdbcUtil.executeQuery(
                    String.format(QUERY_BINLOG_EVENT, LabEventType.REPLICA_BINLOG_POS_CHECK.ordinal()),
                    pdxConnection);
            if (rs.next()) {
                String logEvent = rs.getString("params");
                Assert.fail("Find smaller pos lab event in rpl metaDB, " + logEvent);
            }
        }
    }
}
