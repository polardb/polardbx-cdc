/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.executeQuery;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

@Slf4j
public class ShowBinlogDumpStatusTest extends RplBaseTestCase {

    private static final String SHOW_BINLOG_DUMP_STATUS = "show binlog dump status";
    private static final String SHOW_BINLOG_DUMP_STATUS_WITH = "show binlog dump status with '%s'";

    @Test
    @SneakyThrows
    public void testShowBinlogDumpStatus() {
        sendTokenAndWait(CheckParameter.builder().build());
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_BINLOG_DUMP_STATUS, conn);
            Set<Integer> ids = new HashSet<>();
            while (resultSet.next()) {
                int id = resultSet.getInt("Process_Id");
                ids.add(id);
            }
            log.info("show binlog dump status: {}", ids);
            Assert.assertFalse(ids.isEmpty());
        }
    }

    @Test
    @SneakyThrows
    public void testShowBinlogDumpStatusForBinlogX() {
        if (usingBinlogX) {
            sendTokenAndWait(CheckParameter.builder().build());
            try (Connection conn = getPolardbxConnection()) {
                ResultSet resultSet = executeQuery(String.format(SHOW_BINLOG_DUMP_STATUS_WITH, "group1"), conn);
                Set<Integer> ids = new HashSet<>();
                while (resultSet.next()) {
                    int id = resultSet.getInt("Process_Id");
                    ids.add(id);
                }
                log.info("show binlog dump status with group1: {}", ids);
                Assert.assertFalse(ids.isEmpty());
            }
        }
    }
}
