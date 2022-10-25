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
package com.aliyun.polardbx.binlog.lindorm;

import com.aliyun.oss.common.utils.CRC64;
import com.aliyun.polardbx.binlog.IRemoteManager;
import com.aliyun.polardbx.binlog.action.Appender;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class LindormProvider implements IRemoteManager {

    private static final Logger logger = LoggerFactory.getLogger(LindormProvider.class);

    private LindormConfig config;

    private LindormClient client;

    public LindormProvider(LindormConfig config) {
        this.config = config;
        try {
            client = new LindormClient(config.getAccessKey(), config.getAccessSecret(), config.getIp(),
                config.getPort(), config.getDownloadPort(), config.getBucket());
            client.initBucket();
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
    }

    private String normalizeLindormFile(String fileName) {
        return "/" + config.getPolardbxInstance() + "/" + fileName;
    }

    @Override
    public void download(String fileName, String localPath) {
        try {
            client.downloadByThrift(normalizeLindormFile(fileName),
                new File(localPath + File.separator + fileName));
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
    }

    @Override
    public String getMd5(String fileName) {
        throw new UnsupportedOperationException("getMd5 method is not supported");
    }

    @Override
    public long getSize(String fileName) {
        throw new UnsupportedOperationException("getSize method is not supported");
    }

    @Override
    public void delete(String fileName) {
        String lindormFilePath = normalizeLindormFile(fileName);
        try {
            client.delete(lindormFilePath);
        } catch (Exception e) {
            logger.error("delete file failed,file name is " + lindormFilePath, e);
        }
    }

    @Override
    public void deleteAll(String prefix) {
        throw new UnsupportedOperationException("deleteAll method is not supported");
    }

    @Override
    public boolean isObjectsExistForPrefix(String pathPrefix) {
        throw new UnsupportedOperationException("isEmpty method is not supported");
    }

    @Override
    public List<String> listFiles(String path) {
        try {
            String lindormFilePath = normalizeLindormFile(path);
            return client.listFile(lindormFilePath);
        } catch (Exception e) {
            throw new PolardbxException("list file error", e);
        }
    }

    @Override
    public List<String> listBuckets() {
        throw new UnsupportedOperationException("listBuckets method is not supported");
    }

    @Override
    public byte[] getObjectData(String fileName) {
        throw new UnsupportedOperationException("getObjectData method is not supported");
    }

    @Override
    public Appender providerMultiAppender(String fileName, long fileLength) {
        return null;
    }

    @Override
    public Appender providerAppender(String fileName) {
        return new LindormAppender(client, normalizeLindormFile(fileName));
    }

    @Override
    public boolean useMultiAppender(long size) {
        return false;
    }

    @Override
    public String prepareDownloadLink(String fileName, long expireTimeInSec) {
        try {
            return client.generateUrl(normalizeLindormFile(fileName));
        } catch (Exception e) {
            throw new PolardbxException("generate url error.", e);
        }
    }

    public static class LindormAppender implements Appender {

        private LindormClient client;
        private CRC64 crc64;
        private String fileName;
        private String outputStreamId;
        private long fileOffset = 0;

        public LindormAppender(LindormClient client, String fileName) {
            this.client = client;
            this.fileName = fileName;
        }

        @Override
        public int begin() {
            crc64 = new CRC64();
            try {
                client.delete(this.fileName);
            } catch (Exception e) {
                // 先删除一下
                logger.error("delete lindorm file failed!", e);
            }
            try {
                outputStreamId = client.createFile(this.fileName);
            } catch (Exception e) {
                throw new PolardbxException(e);
            }
            return 0;
        }

        @Override
        public long append(byte[] buffer, int len) {
            CRC64 tmp = new CRC64(crc64.getValue());
            tmp.update(buffer, 0, len);
            try {
                fileOffset = client.upload(outputStreamId, fileName, buffer, len, tmp, fileOffset);
            } catch (Exception e) {
                throw new PolardbxException(e);
            }
            crc64 = tmp;
            return fileOffset;
        }

        @Override
        public void end() {
            try {
                client.completeUpload(outputStreamId, fileName);
            } catch (Exception e) {
                throw new PolardbxException(e);
            }
        }
    }

}
