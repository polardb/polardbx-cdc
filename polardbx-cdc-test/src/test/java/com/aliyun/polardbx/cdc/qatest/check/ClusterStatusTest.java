/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.cdc.qatest.check;

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
