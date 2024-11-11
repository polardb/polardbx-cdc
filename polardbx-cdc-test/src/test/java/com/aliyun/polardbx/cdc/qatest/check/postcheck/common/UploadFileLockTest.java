/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 检查上传文件后有没有正常释放锁
 *
 * @author zm
 */
@Slf4j
public class UploadFileLockTest extends RplBaseTestCase {
    private static final String QUERY_LAB_EVENT = "select * from binlog_lab_event where event_type = %s";
    private static final int MAX_ERROR_COUNT = 5;

    @Test
    public void checkLogEvent() throws SQLException {
        try (Connection c = ConnectionManager.getInstance().getDruidMetaConnection()) {
            ResultSet rs =
                JdbcUtil.executeQuery(String.format(QUERY_LAB_EVENT, LabEventType.UPLOAD_UNLOCK_FAIL.ordinal()), c);
            StringBuilder errMsgs = new StringBuilder("These files unlock failed during uploading\n");
            int errCount = 0;
            while (rs.next()) {
                String logEvent = rs.getString("params");
                errMsgs.append(logEvent);
                errMsgs.append("\n");
                errCount++;
            }
            Assert.assertTrue(errMsgs.toString(), errCount <= MAX_ERROR_COUNT);
        }
    }
}
