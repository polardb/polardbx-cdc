/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * 检查binlog_lab_event中有无sync point相关的异常记录
 *
 * @author yudong
 * @since 2024/5/10 15:59
 **/
public class SyncPointLabEventTest extends BaseTestCase {

    // 暂时忽略，太多了
    @SneakyThrows
    @Test
    @Ignore
    public void checkCommitWithSeq() {
        Assert.assertEquals(0, countEventByType(LabEventType.SYNC_POINT_COMMIT_WITHOUT_SEQ));
    }

    @SneakyThrows
    @Test
    public void checkLocalSeqAfterSyncPoint() {
        Assert.assertEquals(0, countEventByType(LabEventType.SYNC_POINT_UNEXPECTED_LOCAL_SEQ));
    }

    private int countEventByType(LabEventType type) throws Exception {
        final String sql = "select count(*) from binlog_lab_event where event_type = "
            + type.ordinal();
        try (Connection conn = getMetaConnection()) {
            ResultSet rs = JdbcUtil.executeQuery(sql, conn);
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                return 0;
            }
        }
    }

}
