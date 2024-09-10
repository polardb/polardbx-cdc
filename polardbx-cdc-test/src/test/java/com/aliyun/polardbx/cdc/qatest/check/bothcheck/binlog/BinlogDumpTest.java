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

import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.executeQuery;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * @author yudong
 * @since 2023/8/3 16:04
 **/
@Slf4j
public class BinlogDumpTest extends BaseTestCase {
    private static final String SHOW_BINARY_LOGS = "show binary logs";
    private static final String SHOW_BINARY_LOGS_WITH_STREAM = "show binary logs with '%s'";
    private static final String SHOW_BINARY_STREAMS = "show binary streams";
    private static final int nJobs = 10;
    private static final int nWorkers = 5;
    private static final int MAX_FILES_NUM = 20;

    @SneakyThrows
    @Test
    public void testBinlogDump() {
        ThreadPoolExecutor workerPool =
            new ThreadPoolExecutor(nWorkers, nWorkers, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder().setNameFormat("binlog-dump-worker-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        Set<Pair<String, String>> jobs = generateJobs();
        List<Future<?>> futures = new ArrayList<>();
        jobs.forEach(pair -> {
            Future<?> future = workerPool.submit(() -> {
                executeJob(pair.getLeft(), pair.getRight());
            });
            futures.add(future);
        });

        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Pair: start file, end file
     */
    private Set<Pair<String, String>> generateJobs() {
        Set<Pair<String, String>> res = new HashSet<>();
        List<String> allFiles = getBinlogFiles();
        if (allFiles.size() <= 1) {
            return res;
        }
        Random random = new Random();
        for (int i = 0; i < nJobs; i++) {
            int start = random.nextInt(allFiles.size() - 1);
            int end = start + Math.min(random.nextInt(allFiles.size() - start), MAX_FILES_NUM);
            res.add(Pair.of(allFiles.get(start), allFiles.get(end)));
        }

        // 测试dump请求的filename为空的情况
        if (!usingBinlogX) { // filename为空请求单流的第一个文件
            res.add(Pair.of("", allFiles.get(1)));
        }

        log.info("all dump jobs:{}", res);

        return res;
    }

    @SneakyThrows
    private void executeJob(String start, String end) {
        Retryer<Object> retryer = RetryerBuilder.newBuilder().retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(10, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(10)).build();
        try {
            retryer.call(() -> {
                MysqlConnection polarxConn = null;
                try {
                    polarxConn = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
                    polarxConn.connect();
                    polarxConn.dump(start, 4L, null, (event, logPosition) -> {
                        if (event instanceof RotateLogEvent) {
                            log.info("receive rotate event, current file name:{}", logPosition.getFileName());
                            return !logPosition.getFileName().equals(end);
                        }
                        return true;
                    });
                } catch (Exception e) {
                    throw new Exception(e);
                } finally {
                    if (polarxConn != null) {
                        polarxConn.disconnect();
                    }
                }
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException("binlog dump test failed after retry 10 times", e);
        }
    }

    @SneakyThrows
    private List<String> getBinlogFiles() {
        List<String> res = new ArrayList<>();
        String querySql =
            usingBinlogX ? String.format(SHOW_BINARY_LOGS_WITH_STREAM, getStreamName()) : SHOW_BINARY_LOGS;
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(querySql, conn);
            while (resultSet.next()) {
                // 最后一个文件正在产生，dump过程很可能等不到rotate event
                if (resultSet.isLast()) {
                    break;
                }
                res.add(resultSet.getString("LOG_NAME"));
            }
            return res;
        }
    }

    @SneakyThrows
    private String getStreamName() {
        String res = null;
        try (Connection conn = getPolardbxConnection()) {
            ResultSet resultSet = executeQuery(SHOW_BINARY_STREAMS, conn);
            while (resultSet.next()) {
                res = resultSet.getString("STREAM");
                if (new Random().nextBoolean()) {
                    return resultSet.getString("STREAM");
                }
            }
        }
        return res;
    }

}
