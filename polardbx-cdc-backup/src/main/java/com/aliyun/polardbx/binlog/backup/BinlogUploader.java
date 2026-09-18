/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.remote.io.IFileReader;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_MODE;
import static com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy.PART_SIZE;

/**
 * @author yudong
 * @since 2023/1/11
 */
@Slf4j
public class BinlogUploader {
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
    private final long checkInterval;

    public BinlogUploader(IFileReader fetcher, String remoteFileName, MetricsObserver observer,
                          BinlogOssRecord record) {
        this.fetcher = fetcher;
        this.remoteFileName = remoteFileName;
        this.observer = observer;
        this.buffer = new byte[DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_UPLOAD_BUFFER_SIZE)];
        this.record = record;
        this.checkInterval = DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_BACKUP_UPLOAD_CHECK_FILE_COMPLETE_MS);
    }

    public void upload() throws IOException, InterruptedException {
        UPLOAD_MODE uploadMode = UPLOAD_MODE.valueOf(DynamicApplicationConfig.getString(BINLOG_BACKUP_UPLOAD_MODE));
        boolean supportMultiUpload = RemoteBinlogProxy.getInstance().supportMultiUpload();
        boolean shouldUseMultiMode = RemoteBinlogProxy.getInstance().needSwitchMultiUpload(fetcher.length());
        shouldUseMultiMode |= (uploadMode == UPLOAD_MODE.MULTI_PART && fetcher.isComplete()
            && record.getLogEnd() != null);
        shouldUseMultiMode |= RemoteBinlogProxy.getInstance().isS3();

        if (supportMultiUpload && shouldUseMultiMode) {
            doMultiUpload();
        } else {
            doAppend();
        }
    }

    void doAppend() throws IOException, InterruptedException {
        log.info("## begin to append binlog:{} to remote {}", fetcher.getName(), remoteFileName);

        int len;
        final Appender appender = RemoteBinlogProxy.getInstance().providerAppender(remoteFileName);
        appender.begin();
        try {
            while ((len = fetcher.read(buffer)) > 0) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("upload thread is interrupted");
                }

                appender.append(buffer, len);
                observer.incrementUploadBytes(len);
            }
        } finally {
            fetcher.close();
        }

        appender.end();
        log.info("## append finished binlog:{} to remote {}", fetcher.getName(), remoteFileName);
    }

    /**
     * 文件大于4G或远程存储是S3时，切换为此模式
     */
    void doMultiUpload() throws IOException, InterruptedException {
        log.info("## begin to multi upload binlog:{} to remote.", fetcher.getName());

        // 分片上传模式需要等该文件写入完成之后，根据文件大小计算出需要分片的个数
        // 之前isComplete()函数内部字符串比较10ms一次可能有性能问题，因此内部实现换成了int比较
        while (!fetcher.isComplete()) {
            if (Thread.interrupted()) {
                throw new InterruptedException("upload thread is interrupted");
            }
            Thread.sleep(checkInterval);
            log.warn("wait for binlog file " + fetcher.getName() + " complete!");
        }

        long fileLength = fetcher.length();
        Appender multiUploader = RemoteBinlogProxy.getInstance().providerMultiAppender(remoteFileName, fileLength);
        int partCount = multiUploader.begin();
        byte[] buffer = new byte[PART_SIZE];
        for (int i = 0; i < partCount; i++) {
            int readLen = fetcher.read(buffer);
            multiUploader.append(buffer, readLen);
            observer.incrementUploadBytes(readLen);
        }
        multiUploader.end();
        log.info("## multi upload finished binlog:{} to remote {}", fetcher.getName(), remoteFileName);
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
