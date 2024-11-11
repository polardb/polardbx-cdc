/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core;

import com.aliyun.polardbx.binlog.canal.DefaultBinlogFileInfoFetcher;
import com.aliyun.polardbx.binlog.canal.IBinlogFileInfoFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.OssConnection;
import com.aliyun.polardbx.binlog.canal.core.handle.EventHandle;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.unit.SearchRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public class BinlogEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BinlogEventProcessor.class);
    private EventHandle handle;
    private LogFetcher fetcher;
    private String binlogFileName;
    private ServerCharactorSet serverCharactorSet;
    private IBinlogFileInfoFetcher binlogFileSizeFetcher;
    private boolean run;
    private Long serverId;
    private LogPosition lastLogPosition;
    private int binlogChecksum = LogEvent.BINLOG_CHECKSUM_ALG_OFF;

    private boolean serverIdMatch = false;

    private SearchRecorder searchRecorder;

    public EventHandle getHandle() {
        return handle;
    }

    public void setHandle(EventHandle handle) {
        this.handle = handle;
    }

    public void init(ErosaConnection connection, String binlogFileName, long position, boolean search,
                     ServerCharactorSet serverCharactorSet, Long serverId, int binlogChecksum)
        throws IOException {
        init(connection, binlogFileName, position, search, serverCharactorSet, serverId, binlogChecksum, false);
    }

    public void init(ErosaConnection connection, String binlogFileName, long position, boolean search,
                     ServerCharactorSet serverCharactorSet, Long serverId, int binlogChecksum, boolean test)
        throws IOException {
        connection.connect();
        if (this.fetcher != null) {
            this.fetcher.close();
            this.fetcher = null;
        }
        this.serverId = serverId;
        this.fetcher = connection.providerFetcher(binlogFileName, position, search);
        if (connection instanceof OssConnection) {
            this.binlogFileName = ((OssConnection) connection).getLastConnectFile();
        }
        this.binlogChecksum = binlogChecksum;
        this.binlogFileName = binlogFileName;
        this.serverCharactorSet = serverCharactorSet;
        if (!test) {
            this.binlogFileSizeFetcher = new DefaultBinlogFileInfoFetcher(connection);
        }
    }

    public void start() throws Exception {
        run = true;
        handle.onStart();
        doStart();
    }

    public String currentFileName() {
        return binlogFileName;
    }

    public void resetNextLogPosition(String fileName) {
        this.lastLogPosition = new LogPosition(fileName, 0);
    }

    public void restore(ErosaConnection connection) throws IOException {
        logger.info("restore connect with : " + lastLogPosition);
        connection.reconnect();
        binlogChecksum = ((MysqlConnection) connection).loadBinlogChecksum();
        connection.reconnect();
        fetcher = connection.providerFetcher(lastLogPosition.getFileName(), lastLogPosition.getPosition(), false);
        if (binlogFileSizeFetcher != null) {
            binlogFileSizeFetcher = new DefaultBinlogFileInfoFetcher(connection);
        }
        try {
            doStart();
        } finally {
            fetcher.close();
            handle.onEnd();
        }
    }

    public void setSearchRecorder(SearchRecorder searchRecorder) {
        this.searchRecorder = searchRecorder;
    }

    private void doStart() throws IOException {
        LogDecoder decoder = new LogDecoder();
        Set<Integer> ie = handle.interestEvents();
        for (Integer flag : ie) {
            decoder.handle(flag);
        }
        decoder.setBinlogFileSizeFetcher(binlogFileSizeFetcher);
        LogContext context = new LogContext();
        LogPosition logPosition = new LogPosition(binlogFileName, 0);
        context.setLogPosition(logPosition);
        context.setServerCharactorSet(serverCharactorSet);
        context.setFormatDescription(new FormatDescriptionLogEvent(4, binlogChecksum));
        while (run && fetcher.fetch()) {
            LogEvent event = decoder.decode(fetcher.buffer(), context);

            if (event == null) {
                // 如果是文件中读取数据，可能读取的不是一个完整的binlog文件
                continue;
            }
            if (event.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT && serverId != null) {
                this.serverIdMatch = serverId == event.getHeader().getServerId();
                this.binlogFileName = logPosition.getFileName();
            }
            handle.handle(event, context.getLogPosition());
            lastLogPosition = context.getLogPosition();
            if (searchRecorder != null) {
                searchRecorder.setPosition(event.getLogPos());
                searchRecorder.setTimestamp(event.getWhen());
            }
            if (handle.interrupt()) {
                logger.warn(" handler interrupt");
                break;
            }
        }
        logger.warn("event process or end run : " + run + ", log position " + context.getLogPosition());
        fetcher.close();
    }

    public boolean isServerIdMatch() {
        return this.serverIdMatch;
    }

    public void stop() {
        if (!run) {
            return;
        }
        if (handle != null) {
            handle.onEnd();
        }
        run = false;
    }
}
