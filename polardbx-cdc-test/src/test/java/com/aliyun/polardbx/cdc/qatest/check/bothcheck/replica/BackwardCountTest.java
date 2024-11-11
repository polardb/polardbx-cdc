/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

public class BackwardCountTest extends RplBaseTestCase {

    @Test
    public void testCount() throws Exception {
        try (Connection polardbxConnection = getPolardbxConnection()) {
            // 清空回流产生的commit count
            long count = getCommitCount(polardbxConnection);
            Assert.assertEquals("backward commit count != 0 , commit count is " + count, 0, count);
        }

    }

    private long getCommitCount(Connection conn) throws Exception {
        final String queryMetricsSql = "select total_commit_count from metadb.rpl_stat_metrics";
        ResultSet rs = JdbcUtil.executeQuery(queryMetricsSql, conn);
        long totalCommit = 0;
        while (rs.next()) {
            totalCommit += rs.getLong(1);
        }
        return totalCommit;
    }
}
