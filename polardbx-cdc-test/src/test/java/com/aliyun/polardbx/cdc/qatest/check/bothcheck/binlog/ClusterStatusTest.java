/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.binlog;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ClusterStatusTest extends RplBaseTestCase {

    @SneakyThrows
    @Test
    public void testStatus() {
        Connection conn = getMetaConnection();
        List<String> urlList = new ArrayList<>();
        String urlFormat = "http://{0}:{1}/status";
        try {
            String sql = " select * from binlog_node_info";
            ResultSet rs = JdbcUtil.executeQuery(sql, conn);
            while (rs.next()) {
                String ip = rs.getString("ip");
                Integer port = rs.getInt("daemon_port");
                urlList.add(MessageFormat.format(urlFormat, ip, String.valueOf(port)));
            }
        } finally {
            conn.close();
        }

        for (String url : urlList) {
            boolean ok = false;
            long start = System.currentTimeMillis();
            String result = "";
            while (!ok) {
                try {
                    result = HttpHelper.doGet(url, null, null);
                    ok = result.equalsIgnoreCase("OK");
                } catch (Throwable t) {
                    log.error("check status error! ", t);
                }
                if (!ok) {
                    log.error("check status failed, will try again! result: " + result);
                    if (System.currentTimeMillis() - start > TimeUnit.MINUTES.toMillis(5)) {
                        throw new PolardbxException("check status timeout!");
                    } else {
                        Thread.sleep(1000);
                    }
                }
            }
        }
    }
}
