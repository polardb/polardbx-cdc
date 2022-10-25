/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.action;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.io.IDataFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_MODE;
import static com.aliyun.polardbx.binlog.RemoteBinlogProxy.PART_SIZE;

public class Uploader {

    private static final Logger logger = LoggerFactory.getLogger(Uploader.class);

    private final byte[] buffer;
    private final IDataFetcher fetcher;

    public Uploader(IDataFetcher fetcher) {
        this.fetcher = fetcher;
        this.buffer = new byte[DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_UPLOAD_BUFFER_SIZE)];
    }

    public void upload() throws IOException {
        try {
            cleanFile();
        } catch (Exception e) {

        }

        UPLOAD_MODE uploadMode = UPLOAD_MODE.valueOf(DynamicApplicationConfig.getString(BINLOG_BACKUP_UPLOAD_MODE));
        boolean dynamicUseMultiMode = RemoteBinlogProxy.getInstance().needSwitchMultilUpload(fetcher.availableLength());

        if (uploadMode == UPLOAD_MODE.APPEND && !dynamicUseMultiMode) {
            doAppend();
        } else {
            doMultiUpload();
        }
    }

    private void cleanFile() {
        RemoteBinlogProxy.getInstance().delete(fetcher.binlogName());
    }

    private void doAppend() throws IOException {
        int len = 0;
        final Appender appender = RemoteBinlogProxy.getInstance().providerAppender(fetcher.binlogName());
        appender.begin();
        try {
            while ((len = fetcher.next(buffer)) > 0) {
                appender.append(buffer, len);
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
        // 计算文件有多少个分片。
        while (!fetcher.completeFile()) {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                logger.warn("wait for file " + fetcher.binlogName() + " complete!");
            } catch (InterruptedException e) {
            }
        }
        long fileLength = fetcher.availableLength();
        Appender multiUploader = RemoteBinlogProxy.getInstance().providerMultiAppender(fetcher.binlogName(),
            fileLength);
        int partCount = multiUploader.begin();
        logger.info("begin to multi upload binlog : " + fetcher.binlogName());
        byte[] buffer = new byte[PART_SIZE];
        // 遍历分片上传。
        for (int i = 0; i < partCount; i++) {
            int readLen = fetcher.next(buffer);
            multiUploader.append(buffer, readLen);
        }
        multiUploader.end();
    }

    public void doAction() throws IOException {
        upload();
    }

    public enum UPLOAD_MODE {
        APPEND, MULTI_PART
    }

}
