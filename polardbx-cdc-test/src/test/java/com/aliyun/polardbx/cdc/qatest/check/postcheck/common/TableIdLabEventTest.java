/*
 * *
 *  * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 *  * All rights reserved.
 *  * <p>
 *  * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * 这个类是为了测试为啥在有的ddl在applyHistory后又apply一遍
 * 发现是由于applyHistory取出来的histories是<=搜索到的tso
 * 而后续消费binlog时，发送来的ddl是>=搜索到的tso
 * 中间有个等于的ddl是重复的，现已修复
 *
 * @author zm
 */
@Slf4j
@Ignore
public class TableIdLabEventTest {
    private static final String QUERY_BINLOG_EVENT = "SELECT * FROM binlog_lab_event where event_type = %s";

    @Test
    @SneakyThrows
    public void testDuplicateTableIdLabEvent() {
        try (Connection c = ConnectionManager.getInstance().getDruidMetaConnection()) {
            boolean error = false;
            ResultSet rs =
                JdbcUtil.executeQuery(
                    String.format(QUERY_BINLOG_EVENT, LabEventType.DUPLICATE_UPDATE_TABLE_ID.ordinal()), c);
            while (rs.next()) {
                // fileName:endInfoTso:SeekTso:version
                // or fileName:nextAbsolutePos:maxNextAbsolutePos 检测v2的死循环
                String logEvent = rs.getString("params");
                log.error(logEvent);
                error = true;
            }
            Assert.assertFalse("find allocate table id error!", error);
        }
    }
}
