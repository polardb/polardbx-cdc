/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSHeartbeatLog;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.jdbc.PolarDbxCompatDriver;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 一个仅用于测试的CDC Client
 *
 * @author chengjin
 */
@Slf4j
public class DryRunClient implements IEventHandler {

    private static final PolarDbxCompatDriver JDBC_DRIVER = new PolarDbxCompatDriver();

    /**
     * 累计收到的事件数
     */
    private static final AtomicLong dataCount = new AtomicLong(0);
    /**
     * 周期内的字节吐出量
     */
    private static final AtomicLong syncBps = new AtomicLong(0);
    /**
     * 周期内的事件吐出量
     */
    private static final AtomicLong syncEps = new AtomicLong(0);
    private static long lastPrintTime = System.currentTimeMillis();
    private static final long INTERVAL = TimeUnit.SECONDS.toMillis(5);
    private static String binlogFile;
    private static long position;
    private final static int CDC_BINLOG_DOWNLOAD_WINDOW_SIZE = 3;
    private final static long CDC_BINLOG_DOWNLOAD_PART_SIZE = 10485760L;
    private final static int CDC_BINLOG_DOWNLOAD_PARALLELISM_PER_FILE = 5;
    private final static int COLUMNAR_CDC_CLIENT_RING_BUFFER_SIZE = 1048576;

    private static final CountDownLatch latch = new CountDownLatch(1);

    /**
     * 空观察者，仅统计收到的数据量，不做任何解析，用于测试纯网络性能。
     */
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

    /**
     * 纯网络层性能测试：仅从Dumper拉取binlog流，不做解码和解析。
     */
    public static void justForNet(String meta_host, String metaDb_username, String metaDb_password,
                                  String startFileName, long position, boolean sync)
        throws Exception {
        MetaDbHelper metaDbHelper = new MetaDbHelper(() -> {
            try {
                return connect(meta_host, metaDb_username, metaDb_password);
            } catch (SQLException throwables) {
                throw new PolardbxException(throwables);
            }
        });
        DumperDataSource dumperDataSource = new DumperDataSource(metaDbHelper, sync);
        dumperDataSource.reConnect(500 * 1024 * 1024);
        dumperDataSource.dump(new BinlogPosition(startFileName, position, -1, -1), new EmptyObserver(),
            new HashMap<>());
        latch.await();
    }

    /**
     * 解码层性能测试：拉取binlog流并解码，可选是否解析行事件。
     */
    public static void justForConsume(String meta_host, String metaDb_username, String metaDb_password,
                                      String startFileName, long position, boolean dryConsume, boolean sync)
        throws Exception {
        CdcClient cdcClient = new CdcClient(() -> {
            try {
                return connect(meta_host, metaDb_username, metaDb_password);
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

    /**
     * 列存客户端过滤性能测试：测试并行读取 + 表级过滤场景下的吐吐量。
     */
    public static void testColumnarFilter(String meta_host, String metaDb_username, String metaDb_password,
                                          String startFileName, long position, int parallelism, int parserThreads,
                                          boolean filterOptimizeEnabled, Set<String> allowTables) throws Exception {
        log.info("start to test ColumnarFilter with filterOptimizeEnabled:{}, allowTables:{}", filterOptimizeEnabled,
            allowTables);
        Consumer<Long> epsConsumer = syncEps::addAndGet;
        Consumer<Long> bpsConsumer = syncBps::addAndGet;

        ColumnarCdcClient cdcClient = new ColumnarCdcClient(() -> {
            try {
                return connect(meta_host, metaDb_username, metaDb_password);
            } catch (SQLException throwables) {
                throw new PolardbxException(throwables);
            }
        }, CDC_BINLOG_DOWNLOAD_WINDOW_SIZE, CDC_BINLOG_DOWNLOAD_PARALLELISM_PER_FILE, CDC_BINLOG_DOWNLOAD_PART_SIZE,
            parallelism,
            true, 64);
        cdcClient.setRingBufferSize(COLUMNAR_CDC_CLIENT_RING_BUFFER_SIZE);
        cdcClient.setExceptionHandler(t -> {
            log.error("detected exception ： ", t);
            latch.countDown();
        });
        cdcClient.setFilterOptimizeEnabled(filterOptimizeEnabled);
        cdcClient.setAcceptTable(allowTables);
        cdcClient.setAddBinlogEventSize(bpsConsumer);
        cdcClient.setAddBinlogEventThroughput(epsConsumer);
        cdcClient.setRowParseThreadNum(parserThreads);
        lastPrintTime = System.currentTimeMillis();

        cdcClient.startAsync(startFileName, position, new DryRunClient());

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(() -> {
            long bps = syncBps.get();
            long eps = syncEps.get();
            long now = System.currentTimeMillis();
            log.info("Eps:{}, Bps:{}", (eps * 1000) / (now - lastPrintTime),
                (bps * 1000) / (now - lastPrintTime));
            lastPrintTime = now;
            syncBps.set(0);
            syncEps.set(0);
        }, 1, 1, TimeUnit.SECONDS);
        latch.await();
        executorService.shutdownNow();
    }

    private static Connection connect(String url, String username, String password) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        return JDBC_DRIVER.connect(url, properties);
    }

    public static void main(String[] args) throws Exception {
        final String startFileName = System.getenv("file");
        final Long position = Long.parseLong(System.getenv("pos"));
        final String meta_host = System.getenv("metaDb_url");
        final String metaDb_username = System.getenv("metaDb_username");
        final String metaDb_password = System.getenv("metaDb_password");
        // 是否需要解码
        final Boolean need_decode = Boolean.parseBoolean(System.getenv("need_decode"));
        // 是否需要解析rows_event
        final Boolean dryConsume = Boolean.parseBoolean(System.getenv("dry_consume"));
        final Boolean sync = Boolean.parseBoolean(System.getenv("sync"));
        final Boolean filterOptimizeEnabled = Boolean.parseBoolean(System.getenv("filter"));
        final String allowTables = System.getenv("allow_tables");
        final int parallelism = Integer.parseInt(System.getenv("parallelism"));
        final int parserThreads = Integer.parseInt(System.getenv("parser_threads"));
        final SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
        log.warn("meta_host:" + meta_host + ", startFileName:" + startFileName + ",pos:" + position + ", decode:"
            + need_decode + ", dry_consume:" + dryConsume);

        if (StringUtils.isNotBlank(allowTables)) {
            Set<String> set = new HashSet<>();
            String[] names = allowTables.split(",");
            Collections.addAll(set, names);
            testColumnarFilter(meta_host, metaDb_username, metaDb_password, startFileName, position, parallelism,
                parserThreads,
                filterOptimizeEnabled, set);
        } else if (need_decode) {
            justForConsume(meta_host, metaDb_username, metaDb_password, startFileName, position, dryConsume, sync);
        } else {
            justForNet(meta_host, metaDb_username, metaDb_password, startFileName, position, sync);
        }

    }

    public void onHandle(CdcEventData cdcEventData) {
        position = cdcEventData.getPosition();
        binlogFile = cdcEventData.getBinlogFileName();
        DBMSEvent event = cdcEventData.getEvent();
        if (!(event instanceof DBMSHeartbeatLog)) {
            dataCount.incrementAndGet();
        }
    }
}
