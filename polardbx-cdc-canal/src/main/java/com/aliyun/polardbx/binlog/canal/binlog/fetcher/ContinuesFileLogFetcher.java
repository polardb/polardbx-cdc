/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTask;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTaskFactory;
import com.aliyun.polardbx.binlog.canal.binlog.download.StorageDownloader;
import com.aliyun.polardbx.binlog.canal.exception.ConsumeOSSBinlogEndException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class ContinuesFileLogFetcher extends LogFetcher {

    private static Logger logger = LoggerFactory.getLogger(ContinuesFileLogFetcher.class);
    private static final long ALERT_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private FileLogFetcher fileLogFetcher;
    private final String path;
    private BinlogFile binlogFile;
    private final LinkedList<BinlogFile> binlogFileQueue;
    private final String storageInstanceId;
    private final StorageDownloader downloader;

    public ContinuesFileLogFetcher(String storageInstanceId, FileLogFetcher fileLogFetcher, String path,
                                   BinlogFile binlogFile, LinkedList<BinlogFile> binlogFileQueue,
                                   StorageDownloader downloader) {
        super(0);
        this.storageInstanceId = storageInstanceId;
        this.fileLogFetcher = fileLogFetcher;
        this.path = path;
        this.binlogFile = binlogFile;
        this.binlogFileQueue = binlogFileQueue;
        this.downloader = downloader;
    }

    public void setLogger(Logger logger) {
        ContinuesFileLogFetcher.logger = logger;
    }

    @Override
    public boolean fetch() throws IOException {
        boolean readData = fileLogFetcher.fetch();
        if (readData) {
            return true;
        }
        File currentFilePath = new File(path + File.separator + binlogFile.getLogname());
        if (currentFilePath.exists()) {
            FileUtils.forceDelete(currentFilePath);
        }
        int idx = binlogFileQueue.indexOf(binlogFile) + 1;
        if (idx >= binlogFileQueue.size()) {
            logger.info("last file {} finish, will trigger oss end and try direct consume!", binlogFile.getLogname());
            throw new ConsumeOSSBinlogEndException();
        }
        BinlogFile nextFile = binlogFileQueue.get(idx);
        logger.info("last file {} finish , rotate to new binlog file {}", binlogFile.getLogname(),
            nextFile.getLogname());
        String nextFilePath = path + File.separator + nextFile.getLogname();
        if (downloader == null) {
            DownloadTask downloadTask =
                DownloadTaskFactory.createDownloadTask(storageInstanceId, nextFile,
                    nextFilePath);
            try {
                downloadTask.exec();
            } catch (Exception e) {
                throw new PolardbxException("download file " + nextFilePath + " failed!", e);
            }
        } else {
            File f = new File(nextFilePath);
            long lastAlertTimestampInMl = 0;
            while (!f.exists()) {
                if (downloader.getException() != null) {
                    throw new PolardbxException("download file " + nextFilePath + " failed!",
                        downloader.getException());
                }
                long now = System.currentTimeMillis();
                if (now - lastAlertTimestampInMl > ALERT_INTERVAL) {
                    logger.warn("wait for binlog : {}", nextFilePath);
                    lastAlertTimestampInMl = now;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new PolardbxException("wait for binlog " + nextFilePath + " failed!", e);
                }
            }
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
