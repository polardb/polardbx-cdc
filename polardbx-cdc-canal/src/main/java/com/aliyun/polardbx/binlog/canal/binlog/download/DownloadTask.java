/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.download.action.DownloadActionFactory;
import com.aliyun.polardbx.binlog.canal.binlog.download.action.IDownloadAction;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class DownloadTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("rdsDownloadLogger");
    private final BinlogFile binlogFile;
    @Getter
    private final String localFilePath;
    private DownloadTaskListener listener;
    private final String storageInstanceId;

    public DownloadTask(String storageInstanceId, BinlogFile binlogFile, String localFilePath) {
        this.storageInstanceId = storageInstanceId;
        this.binlogFile = binlogFile;
        this.localFilePath = localFilePath;
    }


    public void exec() throws Exception {
        File localFile = new File(localFilePath);

        if (!localFile.exists()) {
            try {
                if (listener != null) {
                    listener.beginDownload(storageInstanceId);
                }
                long start = System.currentTimeMillis();
                IDownloadAction action = DownloadActionFactory.create();
                action.exec(storageInstanceId, localFilePath, binlogFile);
                long useTime = System.currentTimeMillis() - start;
                logger.info("download file {} success, use time: {}ms, use action : {}", localFilePath, useTime,
                    action.getClass().getSimpleName());
            } catch (Exception e) {
                try {
                    FileUtils.forceDelete(localFile);
                } catch (Exception ignored) {
                }
                throw e;
            } finally {
                if (listener != null) {
                    listener.endDownload(storageInstanceId);
                }
            }
        }
    }

    public void registerListener(DownloadTaskListener listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
        int max = 3;
        Throwable t = null;
        do {
            try {
                exec();
                t = null;
                break;
            } catch (Throwable e) {
                logger.error("download failed!" + binlogFile, e);
                t = e;
            }
        } while (max-- > 0);
        if (t != null && listener != null) {
            listener.catchException(t);
        }
    }
}
