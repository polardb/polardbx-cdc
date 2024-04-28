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
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.binlog;

import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 校验binlog_oss_record表
 *
 * @author yudong
 * @since 2023/5/22 10:34
 **/
@Slf4j
public class BinlogRecordTest extends BaseTestCase {

    @SneakyThrows
    @Test
    public void testRecords() {
        List<String> querySql = new ArrayList<>();
        try (Connection conn = getMetaConnection()) {
            String formatSql =
                "select * from binlog_oss_record where group_id = '%s' and stream_id = '%s' and cluster_id = '%s' ";
            String sql = "select distinct group_id, stream_id, cluster_id from binlog_oss_record";
            ResultSet rs = JdbcUtil.executeQuery(sql, conn);
            while (rs.next()) {
                String groupId = rs.getString("group_id");
                String streamId = rs.getString("stream_id");
                String clusterId = rs.getString("cluster_id");
                querySql.add(String.format(formatSql, groupId, streamId, clusterId));
                log.info("get group_id: {}, stream_id: {}, cluster_id: {}", groupId, streamId, clusterId);
            }
        }

        try (Connection conn = getMetaConnection()) {
            for (String sql : querySql) {
                ResultSet rs = JdbcUtil.executeQuery(sql, conn);
                while (rs.next()) {
                    // 最后一条记录可能各项都为空
                    if (rs.isLast()) {
                        break;
                    }
                    int uploadStatus = rs.getInt("upload_status");
                    assertEquals("binlog file upload failed", BinlogUploadStatus.SUCCESS.getValue(), uploadStatus);
                    int purgeStatus = rs.getInt("purge_status");
                    assertEquals("binlog file is purged", BinlogPurgeStatus.UN_COMPLETE.getValue(), purgeStatus);
                    String uploadHost = rs.getString("upload_host");
                    assertNotNull("upload host is null", uploadHost);
                    Date logBegin = rs.getDate("log_begin");
                    assertNotNull("log begin is null", logBegin);
                    Date logEnd = rs.getDate("log_end");
                    assertNotNull("log end is null", logEnd);
                    long logSize = rs.getLong("log_size");
                    assertNotEquals("log size is 0", 0, logSize);
                    String lastTso = rs.getString("last_tso");
                    assertNotNull("last tso is null", lastTso);
                }
            }
        }
    }
}
