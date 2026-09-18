/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.StreamHashUtil;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class CreataTableWithCharSetTest extends RplBaseTestCase {
    private static final String TABLE_NAME = "zm_test_charset";
    private static final String SELECT_SQL = "select * from " + TABLE_NAME + " where id = 1";

    @Test
    public void testCreateTableWithCharset() throws Exception {
        String dbName = PropertiesUtil.polardbXDBName1(usingNewPartDb());
        String sql = "create table if not exists `" + TABLE_NAME + "` (\n"
            + "        `id` int NOT NULL AUTO_INCREMENT,\n"
            + "        `v` int,\n"
            + "        primary key (`id`)\n"
            + ") engine = 'innodb' default character set = 'utf8mb4' default collate = 'utf8mb4_general_ci'";
        try (Connection c = getPolardbxConnection(dbName)) {
            c.createStatement().execute(sql);
            c.createStatement().execute("insert into " + TABLE_NAME + "(`v`) values (1)");
        }
        sendTokenAndWait(CheckParameter.builder().build());

        if (!PropertiesUtil.usingBinlogX) {
            try (Connection c = getCdcSyncDbConnection(dbName)) {
                Assert.assertEquals(1, countMatchedRows(c));
            }
            return;
        }

        HashLevel hashLevel = StreamHashUtil.getHashLevel(dbName, TABLE_NAME);
        if (hashLevel == HashLevel.RECORD) {
            int matchedRows = 0;
            try (Connection c = getCdcSyncDbConnectionFirst(dbName)) {
                matchedRows += countMatchedRows(c);
            }
            try (Connection c = getCdcSyncDbConnectionSecond(dbName)) {
                matchedRows += countMatchedRows(c);
            }
            try (Connection c = getCdcSyncDbConnectionThird(dbName)) {
                matchedRows += countMatchedRows(c);
            }
            Assert.assertEquals("the row should exist in exactly one BinlogX stream", 1, matchedRows);
        } else {
            int streamSeq = StreamHashUtil.getHashStreamSeq(dbName, TABLE_NAME);
            try (Connection c = getCdcSyncDbConnectionByStreamSeq(dbName, streamSeq)) {
                Assert.assertEquals(1, countMatchedRows(c));
            }
        }
    }

    private Connection getCdcSyncDbConnectionByStreamSeq(String dbName, int streamSeq) {
        if (streamSeq == 0) {
            return getCdcSyncDbConnectionFirst(dbName);
        } else if (streamSeq == 1) {
            return getCdcSyncDbConnectionSecond(dbName);
        } else if (streamSeq == 2) {
            return getCdcSyncDbConnectionThird(dbName);
        } else {
            throw new IllegalArgumentException("invalid stream seq " + streamSeq);
        }
    }

    private int countMatchedRows(Connection connection) throws SQLException {
        int matchedRows = 0;
        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(SELECT_SQL)) {
            while (resultSet.next()) {
                Assert.assertEquals(1, resultSet.getInt("v"));
                matchedRows++;
            }
        }
        return matchedRows;
    }
}
