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
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.Assert;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * @author zm
 */
public class TransparentConsumptionTest extends RplBaseTestCase {
    private static final String QUERY_BINLOG_EVENT = "SELECT * FROM metaDB.binlog_lab_event where event_type = %s";

    public void testTransparentConsumption() throws Exception {
        try (Connection syncDbConnection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            ResultSet rs =
                JdbcUtil.executeQuery(String.format(QUERY_BINLOG_EVENT, LabEventType.TRANSPARENT_CONSUMING.ordinal()),
                    syncDbConnection);
            if (!rs.next()) {
                Assert.fail("Can not find transparent consuming from binlog_lab_event in metaDB.");
            }
        }
    }
}
