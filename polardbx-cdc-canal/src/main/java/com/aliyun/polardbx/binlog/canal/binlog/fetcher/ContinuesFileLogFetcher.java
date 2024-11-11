/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogDownloader;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTask;
import com.aliyun.polardbx.binlog.canal.exception.ConsumeOSSBinlogEndException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ContinuesFileLogFetcher extends LogFetcher {

    private static final Logger logger = LoggerFactory.getLogger(ContinuesFileLogFetcher.class);
    private static final long ALERT_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private FileLogFetcher fileLogFetcher;
    private String path;
    private BinlogFile binlogFile;
    private LinkedList<BinlogFile> binlogFileQueue;
    private boolean asyncDownload = false;
    private String storageInstanceId;

    public ContinuesFileLogFetcher(String storageInstanceId, FileLogFetcher fileLogFetcher, String path,
                                   BinlogFile binlogFile,
                                   LinkedList<BinlogFile> binlogFileQueue, boolean asyncDownload) {
        super(0);
        this.storageInstanceId = storageInstanceId;
        this.fileLogFetcher = fileLogFetcher;
        this.path = path;
        this.binlogFile = binlogFile;
        this.binlogFileQueue = binlogFileQueue;
        this.asyncDownload = asyncDownload;
        if (asyncDownload) {
            boolean find = false;
            for (BinlogFile bf : binlogFileQueue) {
                if (binlogFile == bf) {
                    find = true;
                }
                if (!find) {
                    continue;
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("add download binlog : " + bf.getLogname());
                }
                try {
                    BinlogDownloader.getInstance().addDownloadTask(storageInstanceId,
                        new DownloadTask(storageInstanceId, bf.getIntranetDownloadLink(),
                            path + File.separator + bf.getLogname()));
                } catch (ExecutionException e) {
                    throw new PolardbxException(e);
                }
            }

        }
    }

    @Override
    public boolean fetch() throws IOException {
        boolean readData = fileLogFetcher.fetch();
        if (readData) {
            return true;
        }
        File currentFilePath = new File(path + File.separator + binlogFile.getLogname());
        if (currentFilePath.exists()) {
            currentFilePath.delete();
        }
        int idx = binlogFileQueue.indexOf(binlogFile) + 1;
        if (idx >= binlogFileQueue.size()) {
            throw new ConsumeOSSBinlogEndException();
        }
        BinlogFile nextFile = binlogFileQueue.get(idx);
        logger.info(
            "last file " + binlogFile.getLogname() + " finish , rotate to new binlog file " + nextFile.getLogname());
        if (nextFile == null) {
            throw new ConsumeOSSBinlogEndException();
        }
        String nextFilePath = path + File.separator + nextFile.getLogname();
        if (!asyncDownload) {
            DownloadTask downloadTask =
                new DownloadTask(storageInstanceId, nextFile.getIntranetDownloadLink(), nextFilePath);
            downloadTask.exec();
        } else {
            File f = new File(nextFilePath);
            long lastAlertTimestampInMl = 0;
            do {
                if (f.exists()) {
                    break;
                }
                long now = System.currentTimeMillis();
                if (now - lastAlertTimestampInMl > ALERT_INTERVAL) {
                    logger.warn("wait for binlog : " + nextFilePath);
                    lastAlertTimestampInMl = now;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {

                }
            } while (true);
        }

        binlogFile = nextFile;
        fileLogFetcher = new FileLogFetcher();
        fileLogFetcher.open(nextFilePath, 0);
        return fileLogFetcher.fetch();
    }

    @Override
    public LogBuffer buffer() {
        return fileLogFetcher;
    }

    @Override
    public void close() throws IOException {
        fileLogFetcher.close();
        File currentFilePath = new File(path + File.separator + binlogFile.getLogname());
        if (currentFilePath.exists()) {
            currentFilePath.delete();
        }
    }
}
