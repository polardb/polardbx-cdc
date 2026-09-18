/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.replica;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * 校验实验室环境下，Dumper 未漏过滤匹配 server_id 的 DML 事件。
 * 双层过滤架构下（Dumper 过滤 DML，Replica 兜底过滤 DDL），
 * 若 Replica 端收到匹配 server_id 的 DML，说明 Dumper 漏过滤，
 * 会记录 REPLICA_SERVER_ID_FILTER_DML 事件。正常情况下该事件应不存在。
 *
 * @author zm
 */
@Slf4j
public class ServerIdFilterDmlLabEventTest extends RplBaseTestCase {
    private static final String QUERY_BINLOG_EVENT = "SELECT * FROM metaDB.binlog_lab_event where event_type = %s";

    @Test
    @SneakyThrows
    public void testServerIdFilterDmlLabEvent() {
        try (Connection pdxConnection = ConnectionManager.getInstance().getDruidCdcSyncDbConnection()) {
            ResultSet rs =
                JdbcUtil.executeQuery(
                    String.format(QUERY_BINLOG_EVENT, LabEventType.REPLICA_SERVER_ID_FILTER_DML.ordinal()),
                    pdxConnection);
            if (rs.next()) {
                String logEvent = rs.getString("params");
                Assert.fail("Dumper leaked DML with matching server_id to replica, lab event: " + logEvent);
            }
        }
    }
}
