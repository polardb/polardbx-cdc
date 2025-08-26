/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSHeartbeatLog;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class DryRunClient implements IEventHandler {

    private static AtomicLong dataCount = new AtomicLong(0);
    private static long lastPrintTime = System.currentTimeMillis();
    private static long INTERVAL = TimeUnit.SECONDS.toMillis(5);
    private static String binlogFile;
    private static long position;

    private static CountDownLatch latch = new CountDownLatch(1);

    public static class EmptyObserver implements StreamObserver<DumpStream> {

        @Override
        public void onNext(DumpStream value) {
            long total = dataCount.addAndGet(value.getPayload().size());
            long now = System.currentTimeMillis();
            if (now - lastPrintTime > INTERVAL) {
                log.info("receive tps : " + total * 1000 / (now - lastPrintTime));
                lastPrintTime = now;
                dataCount.set(0);
            }
        }

        @Override
        public void onError(Throwable t) {
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            latch.countDown();
        }
    }

    public static void justForNet(String meta_host, String metaDb_username, String metaDb_password,
                                  String startFileName, long position, boolean sync)
        throws Exception {
        MetaDbHelper metaDbHelper = new MetaDbHelper(() -> {
            try {
                return DriverManager.getConnection(
                    meta_host,
                    metaDb_username, metaDb_password);
            } catch (SQLException throwables) {
                throw new PolardbxException(throwables);
            }
        });
        DumperDataSource dumperDataSource = new DumperDataSource(metaDbHelper, sync);
        dumperDataSource.reConnect(500 * 1024 * 1024);
        dumperDataSource.dump(new BinlogPosition(startFileName, position, -1, -1), new EmptyObserver());
        latch.await();
    }

    public static void justForConsume(String meta_host, String metaDb_username, String metaDb_password,
                                      String startFileName, long position, boolean dryConsume, boolean sync)
        throws Exception {
        CdcClient cdcClient = new CdcClient(() -> {
            try {
                return DriverManager.getConnection(
                    meta_host,
                    metaDb_username, metaDb_password);
            } catch (SQLException throwables) {
                throw new PolardbxException(throwables);
            }
        }, sync);
        cdcClient.setBinaryData();
        cdcClient.setDryRun(dryConsume);
        cdcClient.setExceptionHandler(t -> {
            log.error("detected exception ： ", t);
            latch.countDown();
        });
        cdcClient.startAsync(startFileName, position, new DryRunClient());
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorService.schedule(() -> {
            long data = dataCount.get();
            long now = System.currentTimeMillis();
            log.info(
                "receive tps : " + (data * 1000 / (now - lastPrintTime)) + " log pos : " + binlogFile + ":" + position);
            lastPrintTime = now;
            dataCount.set(0);
        }, 5, TimeUnit.SECONDS);
        latch.await();
        executorService.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        final String startFileName = System.getenv("file");
        final Long position = Long.parseLong(System.getenv("pos"));
        final String meta_host = System.getenv("metaDb_url");
        final String metaDb_username = System.getenv("metaDb_username");
        final String metaDb_password = System.getenv("metaDb_password");
        final Boolean need_decode = Boolean.parseBoolean(System.getenv("need_decode"));
        final Boolean dryConsume = Boolean.parseBoolean(System.getenv("dry_consume"));
        final Boolean sync = Boolean.parseBoolean(System.getenv("sync"));
        final SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
        log.warn("meta_host:" + meta_host + ", startFileName:" + startFileName + ",pos:" + position + ", decode:"
            + need_decode + ", dry_consume:" + dryConsume);

        if (need_decode) {
            justForConsume(meta_host, metaDb_username, metaDb_password, startFileName, position, dryConsume, sync);
        } else {
            justForNet(meta_host, metaDb_username, metaDb_password, startFileName, position, sync);
        }

    }

    public void onHandle(CdcEventData cdcEventData) {
        if (!StringUtils.equalsIgnoreCase(binlogFile, cdcEventData.getBinlogFileName())) {
            log.info("process file : " + binlogFile + ":" + cdcEventData.getPosition());
        }
        position = cdcEventData.getPosition();
        binlogFile = cdcEventData.getBinlogFileName();
        DBMSEvent event = cdcEventData.getEvent();
        if (!(event instanceof DBMSHeartbeatLog)) {
            dataCount.incrementAndGet();
        }
    }
}
