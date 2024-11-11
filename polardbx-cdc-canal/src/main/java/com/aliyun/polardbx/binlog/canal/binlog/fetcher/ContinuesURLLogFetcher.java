/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
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
import java.util.concurrent.TimeUnit;

public class ContinuesURLLogFetcher extends LogFetcher {

    private static final Logger logger = LoggerFactory.getLogger(ContinuesURLLogFetcher.class);
    private static final long ALERT_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private URLLogFetcher urlLogFetcher;
    private BinlogFile binlogFile;
    private LinkedList<BinlogFile> binlogFileQueue;
    private String storageInstanceId;

    public ContinuesURLLogFetcher(String storageInstanceId, URLLogFetcher urlLogFetcher,
                                  BinlogFile binlogFile,
                                  LinkedList<BinlogFile> binlogFileQueue) {
        super(0);
        this.storageInstanceId = storageInstanceId;
        this.urlLogFetcher = urlLogFetcher;
        this.binlogFile = binlogFile;
        this.binlogFileQueue = binlogFileQueue;
    }

    @Override
    public boolean fetch() throws IOException {
        boolean readData = urlLogFetcher.fetch();
        if (readData) {
            return true;
        }

        logger.info("finish read " + binlogFile + " size : " + urlLogFetcher.readSize());

        urlLogFetcher.close();
        urlLogFetcher = null;
        int idx = binlogFileQueue.indexOf(binlogFile) + 1;
        if (idx >= binlogFileQueue.size()) {
            throw new ConsumeOSSBinlogEndException();
        }
        BinlogFile nextFile = binlogFileQueue.get(idx);
        logger.info(
            "last file " + binlogFile.getLogname() + " finish , rotate to new binlog file " + nextFile.getLogname()
                + " with link : " + nextFile.getDownloadLink());

        binlogFile = nextFile;
        urlLogFetcher = new URLLogFetcher();
        urlLogFetcher.open(nextFile.getIntranetDownloadLink(), nextFile.getFileSize());
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
