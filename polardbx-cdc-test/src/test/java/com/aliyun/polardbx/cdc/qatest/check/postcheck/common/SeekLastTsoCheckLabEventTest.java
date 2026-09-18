/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class SeekLastTsoCheckLabEventTest {
    private static final String QUERY_BINLOG_OSS_RECORD = "SELECT * FROM binlog_oss_record";
    private static final String QUERY_BINLOG_EVENT = "SELECT * FROM binlog_lab_event where event_type = %s";

    @Test
    @SneakyThrows
    public void testSeekLastTsoCheckLabEvent() {
        Set<String> finishedFiles = new HashSet<>();
        Set<String> checkedFiles = new HashSet<>();
        try (Connection c = ConnectionManager.getInstance().getDruidMetaConnection()) {
            ResultSet rs = JdbcUtil.executeQuery(QUERY_BINLOG_OSS_RECORD, c);
            while (rs.next()) {
                if (rs.getString("last_tso") != null) {
                    String name = rs.getString("binlog_file");
                    finishedFiles.add(name);
                }
            }
            boolean error = false;
            rs =
                JdbcUtil.executeQuery(String.format(QUERY_BINLOG_EVENT, LabEventType.SEEK_LAST_TSO_CHECK.ordinal()), c);
            log.info("the err msg may be fileName:endInfoTso:SeekTso:version");
            while (rs.next()) {
                // fileName:endInfoTso:SeekTso:version
                // or fileName:nextAbsolutePos:maxNextAbsolutePos 检测v2的死循环
                String logEvent = rs.getString("params");
                String[] msgs = logEvent.split(":");
                if (msgs.length > 1) {
                    error = true;
                    log.error("Find seek last tso error: {}", logEvent);
                }
                checkedFiles.add(msgs[0]);
            }
            for (String name : finishedFiles) {
                if (!checkedFiles.contains(name)) {
                    error = true;
                    log.error("file {} not check!", name);
                }
            }
            Assert.assertFalse("find seek last tso error!", error);
        }
    }
}
