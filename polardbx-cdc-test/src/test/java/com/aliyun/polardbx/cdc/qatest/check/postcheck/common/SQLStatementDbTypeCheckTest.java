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
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SQLStatementDbTypeCheckTest extends RplBaseTestCase {

    @Test
    public void dbTypeCheckTest() throws SQLException {

        final String sql = "select count(*) from binlog_lab_event where event_type = "
            + LabEventType.SQL_STATMENT_DB_TYPE_NOT_MYSQL.ordinal();
        try (Connection conn = getMetaConnection()) {
            ResultSet rs = JdbcUtil.executeQuery(sql, conn);
            while (rs.next()) {
                int count = rs.getInt(1);
                Assert.assertEquals(0, count);
            }
        }
    }
}
