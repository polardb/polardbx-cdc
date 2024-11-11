/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class DownloadTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("rdsDownloadLogger");
    private String downloadLink;
    private String localFilePath;
    private DownloadTaskListener listener;
    private String storageInstanceId;

    public DownloadTask(String storageInstanceId, String downloadLink, String localFilePath) {
        this.storageInstanceId = storageInstanceId;
        this.downloadLink = downloadLink;
        this.localFilePath = localFilePath;
    }

    public String getLocalFilePath() {
        return localFilePath;
    }

    public void exec() {
        File localFile = new File(localFilePath);
        if (listener != null) {
            listener.beginDownload(storageInstanceId);
        }
        if (!localFile.exists()) {
            try {
                HttpHelper.download(downloadLink, localFilePath);
            } catch (Exception e) {
                try {
                    localFile.delete();
                } catch (Exception d) {
                }
                throw new PolardbxException(e);
            }
        }
        if (listener != null) {
            listener.endDownload(storageInstanceId);
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
                logger.error("download failed!" + downloadLink, e);
                t = e;
            }
        } while (max-- > 0);
        if (t != null && listener != null) {
            listener.catchException(t);
        }
    }
}
