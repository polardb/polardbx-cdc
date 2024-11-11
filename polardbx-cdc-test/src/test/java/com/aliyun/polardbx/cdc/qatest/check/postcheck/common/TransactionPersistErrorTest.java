/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * description: check if exists transaction persist error
 * author: ziyang.lb
 * create: 2023-08-11 15:15
 **/
public class TransactionPersistErrorTest extends RplBaseTestCase {

    @Test
    public void testTransactionPersistError() throws SQLException {
        String labEventCountQuery =
            "select count(*) as c from binlog_lab_event where event_type = %d";

        try (Connection conn = getMetaConnection()) {
            String result = JdbcUtil.executeQueryAndGetFirstStringResult(
                String.format(labEventCountQuery, LabEventType.TASK_TRANSACTION_PERSIST_ERROR.ordinal()), conn);
            Assert.assertEquals(0L, NumberUtils.createLong(result).longValue());
        }
    }
}
