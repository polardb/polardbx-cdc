/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSHeartbeatLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.error.PolardbxException;
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

    public static void main(String[] args) throws Exception {
        final String startFileName = System.getenv("file");
        final Long position = Long.parseLong(System.getenv("pos"));
        final String meta_host = System.getenv("meta_host");
        log.warn("meta_host:" + meta_host + ", startFileName:" + startFileName + ",pos:" + position);
        CdcClient cdcClient = new CdcClient(() -> {
            try {
                return DriverManager.getConnection(
                    "jdbc:mysql://" + meta_host
                        + "/polardbx_meta_db_polardbx?useUnicode=true&characterEncoding=UTF-8&useSSL=false",
                    "diamond", "diamond1qaz@2wsx");
            } catch (SQLException throwables) {
                throw new PolardbxException(throwables);
            }
        });
        cdcClient.setBinaryData();
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

    public void onHandle(CdcEventData cdcEventData) {
        if (!StringUtils.equalsIgnoreCase(binlogFile, cdcEventData.getBinlogFileName())) {
            log.info("process file : " + binlogFile + ":" + cdcEventData.getPosition());
        }
        position = cdcEventData.getPosition();
        binlogFile = cdcEventData.getBinlogFileName();
        DBMSEvent event = cdcEventData.getEvent();
        if (event instanceof DBMSTransactionBegin) {
            DBMSTransactionBegin begin = (DBMSTransactionBegin) event;
            log.info("begin @ : tso " + begin.getTso());
        } else if (event instanceof DBMSTransactionEnd) {
            DBMSTransactionEnd end = (DBMSTransactionEnd) event;
            log.info("commit @ :" + end.getTransactionId() + " tso " + end.getTso());
        } else if (event instanceof DBMSQueryLog) {
            DBMSQueryLog queryLog = (DBMSQueryLog) event;
            log.info("exec ddl : " + queryLog);
        }
        if (!(event instanceof DBMSHeartbeatLog)) {
            dataCount.incrementAndGet();
        }
    }
}
