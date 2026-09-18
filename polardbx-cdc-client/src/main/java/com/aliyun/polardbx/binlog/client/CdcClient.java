/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
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
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class CdcClient {

    protected static final Logger logger = LoggerFactory.getLogger(CdcClient.class);
    protected AtomicBoolean started = new AtomicBoolean(false);
    @Setter
    protected CdcClientParser cdcClientParser;
    protected Thread parseThread;
    protected IExceptionHandler exceptionHandler;
    protected DumperDataSource dataSource;
    protected MetaDbHelper metaDbHelper;
    protected BinlogPosition startPosition;
    protected int rowParseThreadNum = 4;
    protected int ringBufferSize = 16384;
    protected int flowControlWindow = 500 * 1024 * 1024;
    protected boolean dryRun = false;
    protected RBWaitStrategy waitStrategy = RBWaitStrategy.YIELD;
    protected volatile Set<String> allowOrIgnoreTables;
    protected volatile boolean whiteListMode = true;
    protected volatile boolean filterOptimizeEnabled = true;
    protected volatile boolean decode64Enabled = false;

    public CdcClient() {
    }

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

    protected void startDump(String binlogFileName, IEventHandler handle) throws Exception {
        dataSource.initCharset();
        dataSource.reConnect(flowControlWindow);
        StreamObserverLogFetcher logFetcher = providerLogFetcher();
        dataSource.dump(new BinlogPosition(binlogFileName, 4, -1, -1), logFetcher, new HashMap<>());
        logger.info("dump start success!");
        cdcClientParser =
            new CdcClientParser(logFetcher, startPosition, handle, dataSource.getServerCharset(),
                exceptionHandler, ringBufferSize, rowParseThreadNum);
        cdcClientParser.setDryRun(dryRun);
        cdcClientParser.setWaitStrategy(waitStrategy);
        cdcClientParser.setDecode64Enabled(decode64Enabled);
        if (this.allowOrIgnoreTables != null) {
            if (whiteListMode) {
                cdcClientParser.setAcceptTable(allowOrIgnoreTables);
            } else {
                cdcClientParser.setIgnoreTable(allowOrIgnoreTables);
            }
        }
        cdcClientParser.logBufferFilter.setEnabled(filterOptimizeEnabled);
        cdcClientParser.init();
        parseThread = new Thread(() -> {
            try {
                cdcClientParser.parse();
            } finally {
                dataSource.releaseChannel();
            }
        }, "parser-thread");
        parseThread.start();
    }

    public void setUseSleepWaitMode() {
        this.waitStrategy = RBWaitStrategy.SLEEP;
    }

    public void setUseBlockWaitMode() {
        this.waitStrategy = RBWaitStrategy.BLOCK;
    }

    public void setYieldWaitMode() {
        this.waitStrategy = RBWaitStrategy.YIELD;
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
        this.allowOrIgnoreTables = acceptTableSet;
        this.whiteListMode = true;
        if (cdcClientParser != null) {
            cdcClientParser.setAcceptTable(acceptTableSet);
        }
    }

    public void setIgnoreTable(Set<String> ignoreTableSet) {
        this.allowOrIgnoreTables = ignoreTableSet;
        this.whiteListMode = false;
        if (cdcClientParser != null) {
            cdcClientParser.setIgnoreTable(ignoreTableSet);
        }
    }

    public void setFilterOptimizeEnabled(boolean enabled) {
        this.filterOptimizeEnabled = enabled;
        if (cdcClientParser != null) {
            cdcClientParser.logBufferFilter.setEnabled(enabled);
        }
    }

    public void setDecode64Enabled(boolean enabled) {
        this.decode64Enabled = enabled;
        this.cdcClientParser.setDecode64Enabled(enabled);
    }

}
