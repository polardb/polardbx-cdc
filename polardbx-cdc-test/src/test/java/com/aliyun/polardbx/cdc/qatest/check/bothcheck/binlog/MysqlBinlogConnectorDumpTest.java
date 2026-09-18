/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */

package com.aliyun.polardbx.cdc.qatest.check.bothcheck.binlog;

import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.EventData;
import com.github.shyiko.mysql.binlog.event.FormatDescriptionEventData;
import com.github.shyiko.mysql.binlog.event.RotateEventData;
import com.github.shyiko.mysql.binlog.event.deserialization.ChecksumType;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class MysqlBinlogConnectorDumpTest extends BinlogDumpTest {
    private static final int nJobs = 1;
    private static final int nWorkers = 5;
    private static final int MAX_FILES_NUM = 20;
    private static final AtomicInteger succeedCount = new AtomicInteger(0);

    @Test
    @SneakyThrows
    public void testMysqlBinlogConnectorDump() {
        ThreadPoolExecutor workerPool =
            new ThreadPoolExecutor(nWorkers, nWorkers, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder().setNameFormat("binlog-connector-dump-worker-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        List<Future<?>> futures = new ArrayList<>();
        ConnectionManager manager = ConnectionManager.getInstance();
        String hostName = manager.getPolardbxAddress();
        int port = Integer.parseInt(manager.getPolardbxPort());
        String userName = manager.getPolardbxUser();
        String password = manager.getPolardbxPassword();
        AuthenticationInfo authenticationInfo =
            new AuthenticationInfo(new InetSocketAddress(hostName, port), userName, password);
        // set of (startBinlogFile, endBinlogFile)
        Set<Pair<String, String>> jobs = generateJobs(nJobs, MAX_FILES_NUM);
        jobs.forEach(pair -> {
            Future<?> future = workerPool.submit(() -> {
                mysqlBinlogConnectorDump(authenticationInfo, pair.getLeft(), pair.getRight());
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

        Assert.assertEquals(jobs.size(), succeedCount.get());
    }

    public void mysqlBinlogConnectorDump(AuthenticationInfo authenticationInfo, String start, String end) {
        mysqlBinlogConnectorDump(
            authenticationInfo.getAddress().getHostName(),
            authenticationInfo.getAddress().getPort(),
            authenticationInfo.getUsername(),
            authenticationInfo.getPassword(),
            start,
            end);
    }

    private void mysqlBinlogConnectorDump(String hostName, int port, String username, String password, String start,
                                          String end) {
        log.info("start dump from {} to {}", start, end);
        Retryer<Object> retryer = RetryerBuilder.newBuilder().retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(10, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(10)).build();
        // build client
        BinaryLogClient client = buildBinaryLogClient(hostName, port, username, password, start, 4L);
        // prepare event listener for client
        prepareEventListener(client, end);
        // connect client to polar x
        try {
            retryer.call(() -> {
                client.connect();
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException("binlog dump test failed after retry 10 times", e);
        }
        log.info("dump from {} to {} success!", start, end);
    }

    private BinaryLogClient buildBinaryLogClient(String hostName, int port, String username, String password,
                                                 String binlogFileName, long pos) {
        BinaryLogClient client = new BinaryLogClient(hostName, port, username, password);
        client.setBinlogFilename(binlogFileName);
        client.setBinlogPosition(pos);
        EventDeserializer eventDeserializer = new EventDeserializer();
        eventDeserializer.setCompatibilityMode(
            EventDeserializer.CompatibilityMode.DATE_AND_TIME_AS_LONG,
            EventDeserializer.CompatibilityMode.CHAR_AND_BINARY_AS_BYTE_ARRAY
        );
        client.setEventDeserializer(eventDeserializer);
        return client;
    }

    private void prepareEventListener(BinaryLogClient client, String fileEnd) {
        client.registerEventListener(event -> {
            EventData eventData = event.getData();
            if (eventData instanceof FormatDescriptionEventData) {
                FormatDescriptionEventData formatDescriptionEventData = (FormatDescriptionEventData) eventData;
                Assert.assertEquals(ChecksumType.CRC32, formatDescriptionEventData.getChecksumType());
            }
            if (eventData instanceof RotateEventData) {
                RotateEventData rotateEventData = (RotateEventData) eventData;
                String nextBinlog = rotateEventData.getBinlogFilename();
                if (nextBinlog.equals(fileEnd)) {
                    try {
                        succeedCount.incrementAndGet();
                        client.disconnect();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }
}
