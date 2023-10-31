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

import com.aliyun.polardbx.binlog.util.RandomBoolean;
import com.aliyun.polardbx.binlog.util.Stopwatch;
import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * @author yudong
 * @since 2023/5/22 10:34
 **/
@Slf4j
public class BinlogCommandTest extends BaseTestCase {
    private static final String SHOW_BINARY_LOGS = "show binary logs";
    private static final String SHOW_BINARY_LOGS_WITH_STREAM = "show binary logs with '%s'";
    private static final String SHOW_MASTER_STATUS = "show master status";
    private static final String SHOW_MASTER_STATUS_WITH_STREAM = "show master status with '%s'";
    private static final String SHOW_BINARY_STREAMS = "show binary streams";
    private static final String SHOW_BINLOG_EVENTS = "show binlog events in '%s' from %s limit %s";
    private static final String SHOW_BINLOG_EVENTS_WITH_STREAM =
        "show binlog events with '%s' in '%s' from %s limit %s";

    @Test
    public void testShowBinaryLogs() {
        Retryer<Object> retryer = buildRetryer();

        if (usingBinlogX) {
            List<String> streamList = getStreamList();
            for (String stream : streamList) {
                try {
                    retryer.call(() -> {
                        testShowBinaryLogsForBinlogx(stream);
                        return null;
                    });
                } catch (Exception e) {
                    log.error("retry failed", e);
                    throw new RuntimeException(e);
                }
            }
        } else {
            try {
                retryer.call(() -> {
                    testShowBinaryLogsForGlobalBinlog();
                    return null;
                });
            } catch (Exception e) {
                log.error("retry failed", e);
                throw new RuntimeException(e);
            }
        }
    }

    private void testShowBinaryLogsForGlobalBinlog() throws Exception {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_BINARY_LOGS, conn);
            while (resultSet.next()) {
                String fileName = resultSet.getString("LOG_NAME");
                long fileSize = resultSet.getLong("FILE_SIZE");
                throwExceptionHelper(fileName == null, "file name is null");
                throwExceptionHelper(fileSize == 0, "file size is 0");
            }
        }
    }

    private void testShowBinaryLogsForBinlogx(String stream) throws Exception {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(String.format(SHOW_BINARY_LOGS_WITH_STREAM, stream), conn);
            while (resultSet.next()) {
                String fileName = resultSet.getString("LOG_NAME");
                long fileSize = resultSet.getLong("FILE_SIZE");
                throwExceptionHelper(fileName == null, "file name is null");
                throwExceptionHelper(fileSize == 0, "file size is 0");
            }
        }
    }

    @Test
    public void testShowMasterStatus() {
        Retryer<Object> retryer = buildRetryer();

        if (usingBinlogX) {
            List<String> streamList = getStreamList();
            for (String stream : streamList) {
                try {
                    retryer.call(() -> {
                        testShowMasterStatusForBinlogx(stream);
                        return null;
                    });
                } catch (Exception e) {
                    log.error("retry failed", e);
                    throw new RuntimeException(e);
                }
            }
        } else {
            try {
                retryer.call(() -> {
                    testShowMasterStatusForGlobalBinlog();
                    return null;
                });
            } catch (Exception e) {
                log.error("retry failed", e);
                throw new RuntimeException(e);
            }
        }
    }

    private void testShowMasterStatusForGlobalBinlog() throws Exception {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_MASTER_STATUS, conn);
            while (resultSet.next()) {
                String fileName = resultSet.getString("FILE");
                long fileSize = resultSet.getLong("POSITION");
                throwExceptionHelper(fileName == null, "file name is null");
                throwExceptionHelper(fileSize == 0, "file size is 0");
            }
        }
    }

    private void testShowMasterStatusForBinlogx(String stream) throws Exception {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(String.format(SHOW_MASTER_STATUS_WITH_STREAM, stream), conn);
            while (resultSet.next()) {
                String fileName = resultSet.getString("FILE");
                long fileSize = resultSet.getLong("POSITION");
                throwExceptionHelper(fileName == null, "file name is null");
                throwExceptionHelper(fileSize == 0, "file size is 0");
            }
        }
    }

    @Test
    public void testShowBinlogEvents() {
        Retryer<Object> retryer = buildRetryer();

        if (usingBinlogX) {
            List<String> streamList = getStreamList();
            for (String stream : streamList) {
                try {
                    retryer.call(() -> {
                        testShowBinlogEventsForBinlogx(stream);
                        return null;
                    });
                } catch (Exception e) {
                    log.error("retry failed", e);
                    throw new RuntimeException(e);
                }
            }
        } else {
            try {
                retryer.call(() -> {
                    testShowBinlogEventsForGlobalBinlog();
                    return null;
                });
            } catch (Exception e) {
                log.error("retry failed", e);
                throw new RuntimeException(e);
            }
        }
    }

    private void testShowBinlogEventsForGlobalBinlog() throws Exception {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_BINARY_LOGS, conn);
            RandomBoolean random = new RandomBoolean(10);
            while (resultSet.next()) {
                String fileName = resultSet.getString("LOG_NAME");
                long fileSize = resultSet.getLong("FILE_SIZE");
                throwExceptionHelper(fileName == null, "file name is null");
                throwExceptionHelper(fileSize == 0, "file size is 0");
                Stopwatch stopwatch = new Stopwatch();

                if (random.nextBoolean()) {
                    executeQuery(String.format(SHOW_BINLOG_EVENTS, fileName, "4", "2"), conn);
                    double elapsedTime = stopwatch.elapsedTime();
                    log.info("show binlog events of file {} cost {} s", fileName, elapsedTime);
                }
            }
        }
    }

    private void testShowBinlogEventsForBinlogx(String stream) throws Exception {
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(String.format(SHOW_BINARY_LOGS_WITH_STREAM, stream), conn);
            RandomBoolean random = new RandomBoolean(10);
            while (resultSet.next()) {
                String fileName = resultSet.getString("LOG_NAME");
                long fileSize = resultSet.getLong("FILE_SIZE");
                throwExceptionHelper(fileName == null, "file name is null");
                throwExceptionHelper(fileSize == 0, "file size is 0");

                if (random.nextBoolean()) {
                    Stopwatch stopwatch = new Stopwatch();
                    executeQuery(String.format(SHOW_BINLOG_EVENTS_WITH_STREAM, stream, fileName, "4", "2"), conn);
                    double elapsedTime = stopwatch.elapsedTime();
                    log.info("show binlog events of file {} cost {} s", fileName, elapsedTime);
                }
            }
        }
    }

    private List<String> getStreamList() {
        List<String> result = new ArrayList<>();
        if (!usingBinlogX) {
            return result;
        }

        Retryer<Object> retryer = buildRetryer();

        try {
            retryer.call(() -> {
                try (Connection conn = getPolardbxConnection()) {
                    ResultSet resultSet = executeQuery(SHOW_BINARY_STREAMS, conn);
                    while (resultSet.next()) {
                        String stream = resultSet.getString("STREAM");
                        result.add(stream);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.error("retry failed", e);
            throw new RuntimeException(e);
        }

        return result;
    }

    private Retryer<Object> buildRetryer() {
        return RetryerBuilder.newBuilder().retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(600)).build();
    }

    private ResultSet executeQuery(String sql, Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        return stmt.executeQuery(sql.trim());
    }

    private void throwExceptionHelper(boolean flag, String errorMsg) throws Exception {
        if (flag) {
            throw new Exception(errorMsg);
        }
    }
}
