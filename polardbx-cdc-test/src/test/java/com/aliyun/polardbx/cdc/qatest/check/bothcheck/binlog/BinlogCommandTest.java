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

import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.executeQuery;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * @author yudong
 * @since 2023/5/22 10:34
 **/
@Slf4j
public class BinlogCommandTest extends BaseTestCase {
    private static final String SHOW_BINARY_LOGS = "show binary logs";
    private static final String SHOW_MASTER_LOGS = "show master logs";

    private static final String SHOW_BINARY_LOGS_WITH = "show binary logs with '%s'";
    private static final String SHOW_MASTER_LOGS_WITH = "show master logs with '%s'";

    private static final String SHOW_FULL_BINARY_LOGS = "show full binary logs";
    private static final String SHOW_FULL_MASTER_LOGS = "show full master logs";

    private static final String SHOW_FULL_BINARY_LOGS_WITH = "show full binary logs with '%s'";
    private static final String SHOW_FULL_MASTER_LOGS_WITH = "show full master logs with '%s'";

    private static final String SHOW_MASTER_STATUS = "show master status";
    private static final String SHOW_FULL_MASTER_STATUS = "show full master status";

    private static final String SHOW_MASTER_STATUS_WITH = "show master status with '%s'";
    private static final String SHOW_FULL_MASTER_STATUS_WITH = "show full master status with '%s'";

    private static final String SHOW_BINARY_STREAMS = "show binary streams";
    private static final String SHOW_BINARY_STREAMS_WITH = "show binary streams with '%s'";

    private static final String EXIST_STREAM_NAME = "group1_stream_0";
    private static final String NOT_EXIST_STREAM_NAME = "group2_stream_0";

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    @SneakyThrows
    public void testShowBinaryLogsForGlobalBinlog() {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_BINARY_LOGS, conn);
            while (resultSet.next()) {
                String fileName = resultSet.getString("LOG_NAME");
                long fileSize = resultSet.getLong("FILE_SIZE");
                Assert.assertFalse("show binary logs get empty file name!", StringUtils.isEmpty(fileName));
                Assert.assertNotEquals("show binary logs get 0 file size!", 0, fileSize);
            }
        }

        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_MASTER_LOGS, conn);
            while (resultSet.next()) {
                String fileName = resultSet.getString("LOG_NAME");
                long fileSize = resultSet.getLong("FILE_SIZE");
                Assert.assertFalse("show master logs get empty file name!", StringUtils.isEmpty(fileName));
                Assert.assertNotEquals("show master logs get 0 file size!", 0, fileSize);
            }
        }
    }

    @Test
    @SneakyThrows
    public void testShowFullBinaryLogsForGlobalBinlog() {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_FULL_BINARY_LOGS, conn);

            boolean hasResult = false;
            while (resultSet.next()) {
                hasResult = true;

                String fileName = resultSet.getString("LOG_NAME");
                Assert.assertFalse("show full binary logs get empty file name!", StringUtils.isEmpty(fileName));
                if (!resultSet.isLast()) {
                    long fileSize = resultSet.getLong("FILE_SIZE");
                    Assert.assertNotEquals("show full binary logs get 0 file size!", 0, fileSize);

                    Date createTime = resultSet.getDate("CREATE_TIME");
                    Assert.assertNotNull("show full binary logs get empty create_time!", createTime);
                    Date lastModifyTime = resultSet.getDate("LAST_MODIFY_TIME");
                    Assert.assertNotNull("show full binary logs get empty last_modify_time!", lastModifyTime);
                    Date firstEventTime = resultSet.getDate("FIRST_EVENT_TIME");
                    Assert.assertNotNull("show full binary logs get empty first_event_time!", firstEventTime);
                    Date lastEventTime = resultSet.getDate("LAST_EVENT_TIME");
                    Assert.assertNotNull("show full binary logs get empty last_event_time!", lastEventTime);
                    String lastTso = resultSet.getString("LAST_TSO");
                    Assert.assertFalse("show full binary logs get empty last_tso!", StringUtils.isEmpty(lastTso));
                    String uploadStatus = resultSet.getString("UPLOAD_STATUS");
                    Assert.assertFalse("show full binary logs get empty upload_status!",
                        StringUtils.isEmpty(uploadStatus));
                    String fileLocation = resultSet.getString("FILE_LOCATION");
                    Assert.assertFalse("show full binary logs get empty file_location!",
                        StringUtils.isEmpty(fileLocation));
                }
            }

            Assert.assertTrue("show full binary logs returns no data!", hasResult);
        }

        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_FULL_MASTER_LOGS, conn);

            boolean hasResult = false;
            while (resultSet.next()) {
                hasResult = true;

                String fileName = resultSet.getString("LOG_NAME");
                Assert.assertFalse("show full master logs get empty file name!", StringUtils.isEmpty(fileName));
                if (!resultSet.isLast()) {
                    long fileSize = resultSet.getLong("FILE_SIZE");
                    Assert.assertNotEquals("show full master logs get 0 file size!", 0, fileSize);

                    Date createTime = resultSet.getDate("CREATE_TIME");
                    Assert.assertNotNull("show full master logs get empty create_time!", createTime);
                    Date lastModifyTime = resultSet.getDate("LAST_MODIFY_TIME");
                    Assert.assertNotNull("show full master logs get empty last_modify_time!", lastModifyTime);
                    Date firstEventTime = resultSet.getDate("FIRST_EVENT_TIME");
                    Assert.assertNotNull("show full master logs get empty first_event_time!", firstEventTime);
                    Date lastEventTime = resultSet.getDate("LAST_EVENT_TIME");
                    Assert.assertNotNull("show full master logs get empty last_event_time!", lastEventTime);
                    String lastTso = resultSet.getString("LAST_TSO");
                    Assert.assertFalse("show full master logs get empty last_tso!", StringUtils.isEmpty(lastTso));
                    String uploadStatus = resultSet.getString("UPLOAD_STATUS");
                    Assert.assertFalse("show full master logs get empty upload_status!",
                        StringUtils.isEmpty(uploadStatus));
                    String fileLocation = resultSet.getString("FILE_LOCATION");
                    Assert.assertFalse("show full master logs get empty file_location!",
                        StringUtils.isEmpty(fileLocation));
                }
            }

            Assert.assertTrue("show full master logs returns no data!", hasResult);
        }
    }

    @Test
    @SneakyThrows
    public void testShowMasterStatusForGlobalBinlog() {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_MASTER_STATUS, conn);
            while (resultSet.next()) {
                String fileName = resultSet.getString("FILE");
                Assert.assertFalse("show master status get empty file name!", StringUtils.isEmpty(fileName));
                long position = resultSet.getLong("POSITION");
                Assert.assertNotEquals("show master status get zero position!", 0, position);
            }
        }
    }

    @Test
    @SneakyThrows
    public void testShowFullMasterStatusForGlobalBinlog() {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_FULL_MASTER_STATUS, conn);
            while (resultSet.next()) {
                String fileName = resultSet.getString("FILE");
                Assert.assertFalse("show full master status get empty file name!", StringUtils.isEmpty(fileName));
                long position = resultSet.getLong("POSITION");
                Assert.assertNotEquals("show full master status get zero position!", 0, position);
                String lastTso = resultSet.getString("LASTTSO");
                Assert.assertFalse("show full master status get empty last_tso!", StringUtils.isEmpty(lastTso));
                long delayMs = resultSet.getLong("DELAYTIMEMS");
                int avgRevEps = resultSet.getInt("AVGREVEPS");
                int avgRevBps = resultSet.getInt("AVGREVEPS");
                int avgWriteEps = resultSet.getInt("AVGWRITEEPS");
                int avgWriteBps = resultSet.getInt("AVGWRITEBPS");
                int avgWriteTps = resultSet.getInt("AVGWRITETPS");
                int avgUploadBps = resultSet.getInt("AVGUPLOADBPS");
                int avgDumpBps = resultSet.getInt("AVGDUMPBPS");
            }
        }
    }

    // SHOW BINLOG EVENTS
    //   [IN 'log_name']
    //   [FROM pos]
    //   [LIMIT [offset,] row_count]
    @Test
    @SneakyThrows
    public void testShowBinlogEventsForGlobalBinlog() {
        try (Connection conn = getPolardbxConnection()) {
            executeQueryHelper("show binlog events", conn);
            executeQueryHelper("show binlog events in 'binlog.000001'", conn);
            executeQueryHelper("show binlog events from 4", conn);
            executeQueryHelper("show binlog events limit 1", conn);
            executeQueryHelper("show binlog events limit 1,1", conn);
            executeQueryHelper("show binlog events in 'binlog.000001' from 4", conn);
            executeQueryHelper("show binlog events in 'binlog.000001' limit 1", conn);
            executeQueryHelper("show binlog events in 'binlog.000001' limit 1,1", conn);
            executeQueryHelper("show binlog events in 'binlog.000001' from 4 limit 1", conn);
            executeQueryHelper("show binlog events in 'binlog.000001' from 4 limit 1,1", conn);
            executeQueryHelper("show binlog events in 'binlog.000001' from 4 limit 1", conn);
            executeQueryHelper("show binlog events in 'binlog.000001' from 4 limit 1,1", conn);
            executeQueryHelper("show binlog events from 4 limit 1", conn);
            executeQueryHelper("show binlog events from 4 limit 1,1", conn);
        }
    }

    private void executeQueryHelper(String sql, Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement();
            ResultSet resultSet = stmt.executeQuery(sql)) {
        }
    }

    @Test
    @SneakyThrows
    public void testShowBinaryStreams() {
        if (usingBinlogX) {
            try (Connection conn = getPolardbxConnection()) {
                ResultSet resultSet = executeQuery(SHOW_BINARY_STREAMS, conn);
                while (resultSet.next()) {
                    String groupName = resultSet.getString("GROUP");
                    String streamName = resultSet.getString("STREAM");
                    String fileName = resultSet.getString("FILE");
                    long position = resultSet.getLong("POSITION");
                    Assert.assertFalse("show binary streams get empty group name!", StringUtils.isEmpty(groupName));
                    Assert.assertFalse("show binary streams get empty stream name!", StringUtils.isEmpty(streamName));
                    Assert.assertFalse("show binary streams get empty file name!", StringUtils.isEmpty(fileName));
                    Assert.assertNotEquals("show binary streams get zero position!", 0, position);
                }
            }

            // 多流实验室写死了group name为group1
            String sql1 = String.format(SHOW_BINARY_STREAMS_WITH, "group1");
            try (Connection conn = getPolardbxConnection();
                ResultSet resultSet = executeQuery(sql1, conn)) {
                int streamCount = 0;
                while (resultSet.next()) {
                    streamCount++;

                    String groupName = resultSet.getString("GROUP");
                    String streamName = resultSet.getString("STREAM");
                    String fileName = resultSet.getString("FILE");
                    long position = resultSet.getLong("POSITION");
                    Assert.assertEquals("group1", groupName);
                    Assert.assertFalse("show binary streams get empty stream name!", StringUtils.isEmpty(streamName));
                    Assert.assertFalse("show binary streams get empty file name!", StringUtils.isEmpty(fileName));
                    Assert.assertNotEquals("show binary streams get zero position!", 0, position);
                }

                Assert.assertEquals(3, streamCount);
            }

            String notExistGroupName = "group2";
            String sql2 = String.format(SHOW_BINARY_STREAMS_WITH, notExistGroupName);
            try (Connection conn = getPolardbxConnection();
                ResultSet resultSet = executeQuery(sql2, conn)) {
                Assert.assertFalse(resultSet.next());
            }
        }
    }

    @Test
    @SneakyThrows
    public void testShowBinaryLogsForBinlogX() {
        if (usingBinlogX) {
            String sql1 = String.format(SHOW_BINARY_LOGS_WITH, EXIST_STREAM_NAME);
            try (Connection conn = getPolardbxConnection()) {
                ResultSet resultSet = executeQuery(sql1, conn);
                while (resultSet.next()) {
                    String fileName = resultSet.getString("LOG_NAME");
                    long fileSize = resultSet.getLong("FILE_SIZE");
                    Assert.assertFalse("show binary logs with get empty file name!", StringUtils.isEmpty(fileName));
                    Assert.assertNotEquals("show binary logs with get 0 file size!", 0, fileSize);
                }
            }

            String sql2 = String.format(SHOW_MASTER_LOGS_WITH, EXIST_STREAM_NAME);
            try (Connection conn = getPolardbxConnection()) {
                ResultSet resultSet = executeQuery(sql2, conn);
                while (resultSet.next()) {
                    String fileName = resultSet.getString("LOG_NAME");
                    long fileSize = resultSet.getLong("FILE_SIZE");
                    Assert.assertFalse("show master logs with get empty file name!", StringUtils.isEmpty(fileName));
                    Assert.assertNotEquals("show master logs with get 0 file size!", 0, fileSize);
                }
            }
        }
    }

    @Test
    public void testShowBinaryLogsForBinlogX_err() throws SQLException {
        if (usingBinlogX) {
            String sql1 = String.format(SHOW_BINARY_LOGS_WITH, NOT_EXIST_STREAM_NAME);
            try (Connection conn = getPolardbxConnection();
                Statement stmt = conn.createStatement()) {

                exception.expect(SQLException.class);
                stmt.executeQuery(sql1);
            }
        }
    }

    @Test
    @SneakyThrows
    public void testShowFullBinaryLogsForBinlogX() {
        if (usingBinlogX) {
            String sql1 = String.format(SHOW_FULL_BINARY_LOGS_WITH, EXIST_STREAM_NAME);

            try (Connection conn = getPolardbxConnection()) {
                ResultSet resultSet = executeQuery(sql1, conn);

                boolean hasResult = false;
                while (resultSet.next()) {
                    hasResult = true;

                    String fileName = resultSet.getString("LOG_NAME");
                    Assert.assertTrue("binlog file name not starts with stream name!",
                        StringUtils.startsWith(fileName, EXIST_STREAM_NAME));
                    long fileSize = resultSet.getLong("FILE_SIZE");
                    Assert.assertNotEquals("show full binary logs with get 0 file size!", 0, fileSize);
                    if (!resultSet.isLast()) {
                        Date createTime = resultSet.getDate("CREATE_TIME");
                        Assert.assertNotNull("show full binary logs with get empty create_time!", createTime);
                        Date lastModifyTime = resultSet.getDate("LAST_MODIFY_TIME");
                        Assert.assertNotNull("show full binary logs with get empty last_modify_time!", lastModifyTime);
                        Date firstEventTime = resultSet.getDate("FIRST_EVENT_TIME");
                        Assert.assertNotNull("show full binary logs with get empty first_event_time!", firstEventTime);
                        Date lastEventTime = resultSet.getDate("LAST_EVENT_TIME");
                        Assert.assertNotNull("show full binary logs with get empty last_event_time!", lastEventTime);
                        String lastTso = resultSet.getString("LAST_TSO");
                        Assert.assertFalse("show full binary logs with get empty last_tso!",
                            StringUtils.isEmpty(lastTso));
                        String uploadStatus = resultSet.getString("UPLOAD_STATUS");
                        Assert.assertFalse("show full binary logs with get empty upload_status!",
                            StringUtils.isEmpty(uploadStatus));
                        String fileLocation = resultSet.getString("FILE_LOCATION");
                        Assert.assertFalse("show full binary logs with get empty file_location!",
                            StringUtils.isEmpty(fileLocation));
                    }
                }

                Assert.assertTrue("show full binary logs with returns no data!", hasResult);
            }

            String sql2 = String.format(SHOW_FULL_MASTER_LOGS_WITH, EXIST_STREAM_NAME);
            try (Connection conn = getPolardbxConnection()) {
                ResultSet resultSet = executeQuery(sql2, conn);

                boolean hasResult = false;
                while (resultSet.next()) {
                    hasResult = true;

                    String fileName = resultSet.getString("LOG_NAME");
                    Assert.assertTrue("binlog file name not starts with stream name!",
                        StringUtils.startsWith(fileName, EXIST_STREAM_NAME));
                    long fileSize = resultSet.getLong("FILE_SIZE");
                    Assert.assertNotEquals("show full master logs with get 0 file size!", 0, fileSize);
                    if (!resultSet.isLast()) {
                        Date createTime = resultSet.getDate("CREATE_TIME");
                        Assert.assertNotNull("show full master logs with get empty create_time!", createTime);
                        Date lastModifyTime = resultSet.getDate("LAST_MODIFY_TIME");
                        Assert.assertNotNull("show full master logs with get empty last_modify_time!", lastModifyTime);
                        Date firstEventTime = resultSet.getDate("FIRST_EVENT_TIME");
                        Assert.assertNotNull("show full master logs with get empty first_event_time!", firstEventTime);
                        Date lastEventTime = resultSet.getDate("LAST_EVENT_TIME");
                        Assert.assertNotNull("show full master logs with get empty last_event_time!", lastEventTime);
                        String lastTso = resultSet.getString("LAST_TSO");
                        Assert.assertFalse("show full master logs with get empty last_tso!",
                            StringUtils.isEmpty(lastTso));
                        String uploadStatus = resultSet.getString("UPLOAD_STATUS");
                        Assert.assertFalse("show full master logs with get empty upload_status!",
                            StringUtils.isEmpty(uploadStatus));
                        String fileLocation = resultSet.getString("FILE_LOCATION");
                        Assert.assertFalse("show full master logs with get empty file_location!",
                            StringUtils.isEmpty(fileLocation));
                    }
                }

                Assert.assertTrue("show full master logs with returns no data!", hasResult);
            }
        }
    }

    @Test
    @SneakyThrows
    public void testShowMasterStatusForBinlogX() {
        if (usingBinlogX) {
            String sql = String.format(SHOW_MASTER_STATUS_WITH, EXIST_STREAM_NAME);
            try (Connection conn = getPolardbxConnection()) {
                ResultSet resultSet = executeQuery(sql, conn);
                while (resultSet.next()) {
                    String fileName = resultSet.getString("FILE");
                    Assert.assertTrue("show master status with get binlog file name not starts with stream name!",
                        StringUtils.startsWith(fileName, EXIST_STREAM_NAME));
                    long position = resultSet.getLong("POSITION");
                    Assert.assertNotEquals("show master status with get zero position!", 0, position);
                }
            }
        }
    }

    @Test
    @SneakyThrows
    public void testShowFullMasterStatusForBinlogX() {
        if (usingBinlogX) {
            String sql = String.format(SHOW_FULL_MASTER_STATUS_WITH, EXIST_STREAM_NAME);
            try (Connection conn = getPolardbxConnection()) {
                ResultSet resultSet = executeQuery(sql, conn);
                while (resultSet.next()) {
                    String fileName = resultSet.getString("FILE");
                    Assert.assertTrue("show full master status get binlog file name not starts with stream name!",
                        StringUtils.startsWith(fileName, EXIST_STREAM_NAME));
                    long position = resultSet.getLong("POSITION");
                    Assert.assertNotEquals("show full master status get zero position!", 0, position);
                    String lastTso = resultSet.getString("LASTTSO");
                    Assert.assertFalse("show full master status get empty last_tso!", StringUtils.isEmpty(lastTso));
                    long delayMs = resultSet.getLong("DELAYTIMEMS");
                    int avgRevEps = resultSet.getInt("AVGREVEPS");
                    int avgRevBps = resultSet.getInt("AVGREVEPS");
                    int avgWriteEps = resultSet.getInt("AVGWRITEEPS");
                    int avgWriteBps = resultSet.getInt("AVGWRITEBPS");
                    int avgWriteTps = resultSet.getInt("AVGWRITETPS");
                    int avgUploadBps = resultSet.getInt("AVGUPLOADBPS");
                    int avgDumpBps = resultSet.getInt("AVGDUMPBPS");
                }
            }
        }
    }

    @Test
    @SneakyThrows
    public void testShowBinlogEventsForBinlogX() {
        if (usingBinlogX) {
            try (Connection conn = getPolardbxConnection()) {
                executeQueryHelper("show binlog events with 'group1_stream_0'", conn);
                executeQueryHelper("show binlog events with 'group1_stream_0' in 'group1_stream_0_binlog.000001'",
                    conn);
                executeQueryHelper("show binlog events with 'group1_stream_0' from 4", conn);
                executeQueryHelper("show binlog events with 'group1_stream_0' limit 1", conn);
                executeQueryHelper("show binlog events with 'group1_stream_0' limit 1,1", conn);
                executeQueryHelper(
                    "show binlog events with 'group1_stream_0' in 'group1_stream_0_binlog.000001' from 4", conn);
                executeQueryHelper(
                    "show binlog events with 'group1_stream_0' in 'group1_stream_0_binlog.000001' limit 1", conn);
                executeQueryHelper(
                    "show binlog events with 'group1_stream_0' in 'group1_stream_0_binlog.000001' limit 1,1", conn);
                executeQueryHelper(
                    "show binlog events with 'group1_stream_0' in 'group1_stream_0_binlog.000001' from 4 limit 1",
                    conn);
                executeQueryHelper(
                    "show binlog events with 'group1_stream_0' in 'group1_stream_0_binlog.000001' from 4 limit 1,1",
                    conn);
                executeQueryHelper(
                    "show binlog events with 'group1_stream_0' in 'group1_stream_0_binlog.000001' from 4 limit 1",
                    conn);
                executeQueryHelper(
                    "show binlog events with 'group1_stream_0' in 'group1_stream_0_binlog.000001' from 4 limit 1,1",
                    conn);
                executeQueryHelper("show binlog events with 'group1_stream_0' from 4 limit 1", conn);
                executeQueryHelper("show binlog events with 'group1_stream_0' from 4 limit 1,1", conn);
            }
        }
    }

    // set之后可能查不出来，不会立即生效，暂时不测了
//    @Test
//    @SneakyThrows
//    public void testSetCdcVariable() {
//        try (Connection conn = getPolardbxConnection()) {
//            executeQuery("set @@global.cdc_foo=bar", conn);
//        }
//
//        try (Connection conn = getPolardbxConnection();
//            Statement stmt = conn.createStatement();
//            ResultSet resultSet = stmt.executeQuery("select @@global.cdc_foo")) {
//            if (resultSet.next()) {
//                String value = resultSet.getString(1);
//                Assert.assertEquals("bar", value);
//            } else {
//                throw new UnexpectedException("cannot select foo!");
//            }
//        }
//    }

//    private Retryer<Object> buildRetryer() {
//        return RetryerBuilder.newBuilder().retryIfException()
//            .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
//            .withStopStrategy(StopStrategies.stopAfterAttempt(600)).build();
//    }

}
