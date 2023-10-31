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
package com.aliyun.polardbx.cdc.qatest.rpl;

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
