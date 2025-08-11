/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.exception.ConsumeOSSBinlogEndException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;

public class ContinuesURLLogFetcher extends LogFetcher {

    private static Logger logger = LoggerFactory.getLogger(ContinuesURLLogFetcher.class);
    private URLLogFetcher urlLogFetcher;
    private BinlogFile binlogFile;
    private final LinkedList<BinlogFile> binlogFileQueue;
    private final String storageInstanceId;
    private final ExecutorService executorService;

    public ContinuesURLLogFetcher(String storageInstanceId, URLLogFetcher urlLogFetcher, BinlogFile binlogFile,
                                  LinkedList<BinlogFile> binlogFileQueue, ExecutorService executorService) {
        super(0);
        this.storageInstanceId = storageInstanceId;
        this.urlLogFetcher = urlLogFetcher;
        this.binlogFile = binlogFile;
        this.binlogFileQueue = binlogFileQueue;
        this.executorService = executorService;
    }

    public void setLogger(Logger logger) {
        ContinuesURLLogFetcher.logger = logger;
    }

    @Override
    public boolean fetch() throws IOException {
        boolean readData = urlLogFetcher.fetch();
        if (readData) {
            return true;
        }

        logger.info("finish read {} size : {}", binlogFile, urlLogFetcher.readSize());

        urlLogFetcher.close();
        urlLogFetcher = null;
        int idx = binlogFileQueue.indexOf(binlogFile) + 1;
        if (idx >= binlogFileQueue.size()) {
            throw new ConsumeOSSBinlogEndException();
        }
        BinlogFile nextFile = binlogFileQueue.get(idx);
        logger.info("last file {} finish , rotate to new binlog file {} with link : {}", binlogFile.getLogname(),
            nextFile.getLogname(), nextFile.getDownloadLink());

        binlogFile = nextFile;
        urlLogFetcher = LogFetcherFactory.createURLLogFetcher(storageInstanceId, binlogFile.getLogname());
        urlLogFetcher.open(nextFile.getIntranetDownloadLink(), nextFile.getFileSize(), executorService);
        return urlLogFetcher.fetch();
    }

    @Override
    public LogBuffer buffer() {
        return urlLogFetcher;
    }

    @Override
    public void close() throws IOException {
        if (urlLogFetcher == null) {
            return;
        }
        urlLogFetcher.close();
        urlLogFetcher = null;
    }
}
