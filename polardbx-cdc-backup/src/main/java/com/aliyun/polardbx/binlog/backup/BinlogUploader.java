/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.remote.io.IFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_MODE;
import static com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy.PART_SIZE;

/**
 * @author yudong
 * @since 2023/1/11
 */
public class BinlogUploader {
    private static final Logger logger = LoggerFactory.getLogger(BinlogUploader.class);
    /**
     * upload buffer
     */
    private final byte[] buffer;
    /**
     * 用于将本地binlog文件读到buffer中
     */
    private final IFileReader fetcher;
    /**
     * 上传到远端存储的binlog文件名
     */
    private final String remoteFileName;
    /**
     * 用于更新metrics
     */
    private final MetricsObserver observer;
    /**
     * 文件是否已经写完
     */
    private final BinlogOssRecord record;

    public BinlogUploader(IFileReader fetcher, String remoteFileName, MetricsObserver observer,
                          BinlogOssRecord record) {
        this.fetcher = fetcher;
        this.remoteFileName = remoteFileName;
        this.observer = observer;
        this.buffer = new byte[DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_UPLOAD_BUFFER_SIZE)];
        this.record = record;
    }

    public void upload() throws IOException {
        UPLOAD_MODE uploadMode = UPLOAD_MODE.valueOf(DynamicApplicationConfig.getString(BINLOG_BACKUP_UPLOAD_MODE));
        boolean supportMultiUpload = RemoteBinlogProxy.getInstance().supportMultiUpload();
        boolean shouldUseMultiMode = RemoteBinlogProxy.getInstance().needSwitchMultiUpload(fetcher.length());
        shouldUseMultiMode |= (uploadMode == UPLOAD_MODE.MULTI_PART && fetcher.isComplete()
            && record.getLogEnd() != null);

        if (supportMultiUpload && shouldUseMultiMode) {
            doMultiUpload();
        } else {
            doAppend();
        }
    }

    private void doAppend() throws IOException {
        logger.info("begin to append binlog:{} to remote", fetcher.getName());
        int len;
        final Appender appender = RemoteBinlogProxy.getInstance().providerAppender(remoteFileName);
        appender.begin();
        try {
            while ((len = fetcher.read(buffer)) > 0) {
                appender.append(buffer, len);
                observer.incrementUploadBytes(len);
            }
        } finally {
            fetcher.close();
        }

        appender.end();
    }

    /**
     * 文件大于4G时，切换为此模式
     */
    private void doMultiUpload() throws IOException {
        // 分片上传模式需要等该文件写入完成之后，根据文件大小计算出需要分片的个数
        while (!fetcher.isComplete()) {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                logger.warn("wait for file " + fetcher.getName() + " complete!");
            } catch (InterruptedException e) {
            }
        }

        logger.info("begin to multi upload binlog:{} to remote", fetcher.getName());
        long fileLength = fetcher.length();
        Appender multiUploader = RemoteBinlogProxy.getInstance().providerMultiAppender(remoteFileName,
            fileLength);
        int partCount = multiUploader.begin();
        byte[] buffer = new byte[PART_SIZE];
        for (int i = 0; i < partCount; i++) {
            int readLen = fetcher.read(buffer);
            multiUploader.append(buffer, readLen);
            observer.incrementUploadBytes(readLen);
        }
        multiUploader.end();
    }

    public enum UPLOAD_MODE {
        /**
         * 追加上传
         */
        APPEND,
        /**
         * 分片上传
         */
        MULTI_PART
    }
}
