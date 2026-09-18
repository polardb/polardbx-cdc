/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.replica;

import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ReplicaIncHeartbeatTest extends RplBaseTestCase {

    @Test
    public void testHeartbeat() throws SQLException {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet rs =
                conn.createStatement().executeQuery("select * from `__polardbx2__`.`__system__mysql__heartbeat__`");
            Assert.assertTrue(rs.next());
        }
    }
}
