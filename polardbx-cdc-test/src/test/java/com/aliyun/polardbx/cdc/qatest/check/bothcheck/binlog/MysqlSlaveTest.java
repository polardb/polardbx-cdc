/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.binlog;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.getColumnNameList;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MysqlSlaveTest extends RplBaseTestCase {

    @Test
    public void checkSlaveStatus() {
        if (usingBinlogX) {
            checkWithRetry(getCdcSyncDbConnectionFirst(), "binlog-x-first");
            checkWithRetry(getCdcSyncDbConnectionSecond(), "binlog-x-second");
            checkWithRetry(getCdcSyncDbConnectionThird(), "binlog-x-third");
        } else {
            checkWithRetry(getCdcSyncDbConnection(), "global-binlog");
        }
    }

    @SneakyThrows
    private void checkWithRetry(Connection connection, String slaveType) {
        long startTime = System.currentTimeMillis();
        while (true) {
            try {
                check(connection, slaveType);
                break;
            } catch (Throwable t) {
                if (System.currentTimeMillis() - startTime > 120 * 1000) {
                    throw t;
                } else {
                    Thread.sleep(1000);
                }
            }
        }
    }

    @SneakyThrows
    private void check(Connection connection, String slaveType) {
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("show slave status");
            if (rs.next()) {
                String e1 = null;
                String e2 = null;
                List<String> columns = getColumnNameList(rs);
                List<Pair<String, String>> result = new ArrayList<>();

                for (String c : columns) {
                    String str = rs.getString(c);
                    result.add(Pair.of(c, str));
                    if (StringUtils.equalsIgnoreCase("Last_Error", c)) {
                        e1 = StringUtils.trim(str);
                    }
                    if (StringUtils.equalsIgnoreCase("Last_SQL_Error", c)) {
                        e2 = StringUtils.trim(str);
                    }
                }

                log.info("show slave status result for {} is \r\n {}", slaveType,
                    JSONObject.toJSONString(result, true));

                boolean hasError = StringUtils.isNotBlank(e1) || StringUtils.isNotBlank(e2);
                if (hasError) {
                    String workerStatus = queryWorkerStatus(connection);
                    log.error("slave error detected for {}, worker status: \r\n {}", slaveType, workerStatus);
                    String assertMsg = JSONObject.toJSONString(result, true)
                        + "\n\nreplication_applier_status_by_worker:\n" + workerStatus;
                    Assert.assertTrue(assertMsg, StringUtils.isBlank(e1));
                    Assert.assertTrue(assertMsg, StringUtils.isBlank(e2));
                }
            }
        }
    }

    /**
     * 查询 performance_schema.replication_applier_status_by_worker 获取详细的 worker 级别报错信息
     */
    @SneakyThrows
    private String queryWorkerStatus(Connection connection) {
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "select * from performance_schema.replication_applier_status_by_worker");
            List<String> columns = getColumnNameList(rs);
            List<List<Pair<String, String>>> rows = new ArrayList<>();
            while (rs.next()) {
                List<Pair<String, String>> row = new ArrayList<>();
                for (String c : columns) {
                    row.add(Pair.of(c, rs.getString(c)));
                }
                rows.add(row);
            }
            return JSONObject.toJSONString(rows, true);
        } catch (Exception e) {
            log.warn("failed to query replication_applier_status_by_worker", e);
            return "query failed: " + e.getMessage();
        }
    }
}
