/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.remote.io.IDataFetcher;
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
    private final IDataFetcher fetcher;
    /**
     * 上传到远端存储的binlog文件名
     */
    private final String remoteFileName;
    /**
     * 用于更新metrics
     */
    private final MetricsObserver observer;

    public BinlogUploader(IDataFetcher fetcher, String remoteFileName, MetricsObserver observer) {
        this.fetcher = fetcher;
        this.remoteFileName = remoteFileName;
        this.observer = observer;
        this.buffer = new byte[DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_UPLOAD_BUFFER_SIZE)];
    }

    public void upload() throws IOException {
        UPLOAD_MODE uploadMode = UPLOAD_MODE.valueOf(DynamicApplicationConfig.getString(BINLOG_BACKUP_UPLOAD_MODE));
        boolean dynamicUseMultiMode = RemoteBinlogProxy.getInstance().needSwitchMultiUpload(fetcher.availableLength());

        if (uploadMode == UPLOAD_MODE.APPEND && !dynamicUseMultiMode) {
            doAppend();
        } else {
            doMultiUpload();
        }
    }

    private void doAppend() throws IOException {
        logger.info("begin to append binlog:{} to remote", fetcher.binlogName());
        int len;
        final Appender appender = RemoteBinlogProxy.getInstance().providerAppender(remoteFileName);
        appender.begin();
        try {
            while ((len = fetcher.next(buffer)) > 0) {
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
        while (!fetcher.completeFile()) {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                logger.warn("wait for file " + fetcher.binlogName() + " complete!");
            } catch (InterruptedException e) {
            }
        }

        logger.info("begin to multi upload binlog:{} to remote", fetcher.binlogName());
        long fileLength = fetcher.availableLength();
        Appender multiUploader = RemoteBinlogProxy.getInstance().providerMultiAppender(remoteFileName,
            fileLength);
        int partCount = multiUploader.begin();
        byte[] buffer = new byte[PART_SIZE];
        for (int i = 0; i < partCount; i++) {
            int readLen = fetcher.next(buffer);
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
