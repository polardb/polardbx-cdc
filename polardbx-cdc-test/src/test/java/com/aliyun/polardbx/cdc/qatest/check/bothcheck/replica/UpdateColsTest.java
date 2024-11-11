/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UpdateColsTest extends RplBaseTestCase {
    private static final String DB_NAME = "cdc_update_db";
    private static final String CREATE_TABLE = "CREATE TABLE `cdc_update` (\n"
        + "  `id` int(11) NOT NULL AUTO_INCREMENT,\n"
        + "  `c1` int(11) NOT NULL,\n"
        + "  `c2` int(11) NOT NULL,\n"
        + "  `t` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
        + "  PRIMARY KEY (`id`)\n"
        + ") ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4";
    private static final String INSERT_SQL = "INSERT INTO cdc_update (`c1`, `c2`) VALUES (%s,%s)";
    private static final String UPDATE_SQL = "UPDATE cdc_update SET c1 = '%s' WHERE id = %s";
    private static final String SELECT_SQL = "SELECT t FROM cdc_update";
    private static final String QUERY_BINLOG_EVENT = "SELECT * FROM metaDB.binlog_lab_event where event_type = %s";

    @BeforeClass
    public static void bootStrap() throws SQLException {
        prepareTestDatabase(DB_NAME);
    }

    @Test
    public void testUpdate() throws SQLException {
        String sourceTime, targetTime;
        sourceTime = updateSourceAndGetTimeStamp();

        // 等待token被同步
        sendTokenAndWait(CheckParameter.builder().build());
        // 目标端metaDB中应有binlog event记录了update的列
        try (Connection syncDbConnection = ConnectionManager.getInstance().getDruidCdcSyncDbConnection()) {
            ResultSet rs =
                JdbcUtil.executeQuery(String.format(QUERY_BINLOG_EVENT, LabEventType.UPDATE_QUERY_INFO.ordinal()),
                syncDbConnection);
            if (rs.next()) {
                String changeCols = rs.getString("params");
                // update的列仅包括变更列+on update 列
                Assert.assertEquals("t,c1", changeCols);
            } else {
                Assert.fail("Can not find update cols from binlog_lab_event in metaDB.");
            }
        }

        targetTime = getTimeStampFromSyncDB();
        // 两端时间戳一致
        Assert.assertEquals(sourceTime, targetTime);
    }

    private String updateSourceAndGetTimeStamp() throws SQLException {
        try (Connection polardbxConnection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            JdbcUtil.useDb(polardbxConnection, DB_NAME);
            JdbcUtil.executeUpdate(polardbxConnection, CREATE_TABLE);
            JdbcUtil.executeSuccess(polardbxConnection, String.format(INSERT_SQL, 1, 1));
            JdbcUtil.executeSuccess(polardbxConnection, String.format(UPDATE_SQL, 2, 1));
            ResultSet rs = JdbcUtil.executeQuery(SELECT_SQL, polardbxConnection);
            if (rs.next()) {
                return rs.getString("t");
            } else {
                Assert.fail("[Statement execute] source failed:" + SELECT_SQL + "\n");
                return null;
            }
        }
    }

    private String getTimeStampFromSyncDB() throws SQLException {

        try (Connection syncDbConnection = ConnectionManager.getInstance().getDruidCdcSyncDbConnection()) {
            JdbcUtil.useDb(syncDbConnection, DB_NAME);
            ResultSet rs = JdbcUtil.executeQuery(SELECT_SQL, syncDbConnection);
            if (rs.next()) {
                return rs.getString("t");
            } else {
                Assert.fail("[Statement execute] target failed:" + SELECT_SQL + "\n");
                return null;
            }
        }
    }

}
