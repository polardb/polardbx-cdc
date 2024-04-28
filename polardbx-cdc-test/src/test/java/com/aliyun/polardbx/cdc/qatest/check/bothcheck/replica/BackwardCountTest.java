/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
