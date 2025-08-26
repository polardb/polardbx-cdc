/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverFileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.client.listener.IExceptionHandler;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class CdcClient {

    private static final Logger logger = LoggerFactory.getLogger(CdcClient.class);
    private AtomicBoolean started = new AtomicBoolean(false);
    private CdcClientParser cdcClientParser;
    private Thread parseThread;
    private IExceptionHandler exceptionHandler;
    private DumperDataSource dataSource;
    private MetaDbHelper metaDbHelper;
    private BinlogPosition startPosition;
    private int rowParseThreadNum = 4;
    private int ringBufferSize = 16384;
    private int flowControlWindow = 500 * 1024 * 1024;
    private boolean dryRun = false;

    public CdcClient(IMetaDBDataSourceProvider provider) {
        this(provider, true);
    }

    public CdcClient(IMetaDBDataSourceProvider provider, boolean useSyncProtocol) {
        metaDbHelper = new MetaDbHelper(provider);
        dataSource = new DumperDataSource(metaDbHelper, useSyncProtocol);
    }

    public void setExceptionHandler(IExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        this.dataSource.setExceptionHandler(exceptionHandler);
    }

    public void startAsync(IEventHandler handle) throws Exception {
        BinlogPosition position = dataSource.findStartPosition(flowControlWindow);
        this.startAsync(position.getFileName(), position.getPosition(), handle);
    }

    @Deprecated
    public void setBinaryData() {
        // do nothing
    }

    private void initConsumePosition(String binlogFileName, Long filePosition) {
        if (StringUtils.isBlank(binlogFileName) || filePosition == null) {
            throw new PolardbxException(
                "start position should not be null : file : " + binlogFileName + " : " + filePosition);
        }
        this.startPosition = new BinlogPosition(binlogFileName, filePosition, -1, -1);
    }

    private void startDump(String binlogFileName, IEventHandler handle) throws Exception {
        dataSource.initCharset();
        dataSource.reConnect(flowControlWindow);
        StreamObserverLogFetcher logFetcher = providerLogFetcher();
        dataSource.dump(new BinlogPosition(binlogFileName, 4, -1, -1), logFetcher);
        logger.info("dump start success!");
        cdcClientParser =
            new CdcClientParser(logFetcher, startPosition, handle, dataSource.getServerCharset(),
                exceptionHandler, ringBufferSize, rowParseThreadNum);
        cdcClientParser.setDryRun(dryRun);
        parseThread = new Thread(() -> {
            try {
                cdcClientParser.parser();
            } finally {
                dataSource.releaseChannel();
            }
        }, "parser-thread");
        parseThread.start();
    }

    public void setRowParseThreadNum(int rowParseThreadNum) {
        this.rowParseThreadNum = rowParseThreadNum;
    }

    public void setRingBufferSize(int ringBufferSize) {
        this.ringBufferSize = ringBufferSize;
    }

    public void setFlowControlWindow(int flowControlWindow) {
        this.flowControlWindow = flowControlWindow;
    }

    public void startAsync(String binlogFileName, Long filePosition, IEventHandler handle)
        throws Exception {
        if (handle == null) {
            throw new PolardbxException("not set event handle!");
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }

        initConsumePosition(binlogFileName, filePosition);
        startDump(binlogFileName, handle);
    }

    public boolean needTableMeta() {
        return false;
    }

    public StreamObserverLogFetcher providerLogFetcher() throws IOException {
        StreamObserverLogFetcher fetcher;
        if (dataSource.isUseSyncProtocol()) {
            fetcher = new StreamObserverFileLogFetcher();
        } else {
            fetcher = new StreamObserverLogFetcher();
        }

        fetcher.registerErrorHandle((t) -> {
            if (exceptionHandler != null) {
                exceptionHandler.handle(t);
            }
        });
        return fetcher;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public void shutdown() {
        started.set(false);
        if (dataSource != null) {
            dataSource.releaseChannel();
        }
        if (cdcClientParser != null) {
            cdcClientParser.stop();
        }

    }

    public LogPosition getLogPosition() {
        return cdcClientParser.getLogPosition();
    }

    /**
     * 设置需要关心的数据Set
     * 每个值都是db.table 的小写形式
     */
    public void setAcceptTable(Set<String> acceptTableSet) {
        cdcClientParser.setAcceptTable(acceptTableSet);
    }

    public void setIgnoreTable(Set<String> ignoreTableSet) {
        cdcClientParser.setIgnoreTable(ignoreTableSet);
    }

}
