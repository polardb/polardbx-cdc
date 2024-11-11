/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

public class PositionSearchTest extends RplBaseTestCase {

    @Test
    public void testPosition() throws SQLException {
        Set<String> paramList = listSearchPositionLog();
        Assert.assertNotEquals(paramList.size(), 0);
        for (String param : paramList) {
            Assert.assertNotNull(param);
            String[] params = param.split("after");
            BinlogPosition pBefore = extract(params[0]);
            BinlogPosition pAfter = extract(params[1]);
            dumpCheck(pBefore, pAfter);
        }
    }

    private static BinlogPosition extract(String param) {
        int b = param.indexOf("[");
        int e = param.indexOf("]");
        int bTso = param.indexOf("tso");
        String posStr = param.substring(b + 1, e);
        String[] fileAndOffset = posStr.split(":");
        BinlogPosition position =
            new BinlogPosition(fileAndOffset[0], Integer.parseInt(fileAndOffset[1]), -1L, -1L);
        if (bTso != -1) {
            position.setRtso(param.substring(bTso + 4));
        }
        return position;
    }

    private Set<String> listSearchPositionLog() throws SQLException {
        Set<String> paramsList = Sets.newHashSet();
        try (Connection conn = getMetaConnection()) {
            ResultSet rs = JdbcUtil.executeQuery(
                "select * from binlog_lab_event where event_type = " + LabEventType.RPL_SEARCH_POSITION.ordinal(),
                conn);
            while (rs.next()) {
                paramsList.add(rs.getString("params"));
            }

        }
        return paramsList;
    }

    private void dumpCheck(BinlogPosition start, BinlogPosition expect) {

    }
}
