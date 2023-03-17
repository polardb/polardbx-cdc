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
package com.aliyun.polardbx.binlog.remote.lindorm;

import com.aliyun.oss.common.utils.CRC64;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.remote.IRemoteManager;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.FileInfo;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author yudong
 * @since 2022.10.27
 */
public class LindormManager implements IRemoteManager {
    private static final Logger logger = LoggerFactory.getLogger(LindormManager.class);
    private final LindormConfig lindormConfig;

    public LindormManager(LindormConfig config) {
        this.lindormConfig = config;
        this.config();
    }

    private void config() {
        boolean success = false;
        String bucket = getBucket();
        do {
            try (LindormClient client = getLindormClient()) {
                if (client.doesBucketExist(bucket)) {
                    logger.info("bucket {} create success", bucket);
                    success = true;
                } else {
                    logger.info("try to create bucket {}", bucket);
                    client.createBucket(bucket);
                }
            } catch (Exception e) {
                logger.error("create bucket error, will try later", e);
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException interruptedException) {
                    logger.info("create bucket failed and thread interrupt!", e);
                }
            }

        } while (!success);
    }

    public String getBucket() {
        return lindormConfig.getBucket();
    }

    public AmazonS3 getS3Client() {
        return getLindormClient().getS3Client();
    }

    private LindormClient getLindormClient() {
        return new LindormClient(lindormConfig.getAccessKey(),
                lindormConfig.getAccessSecret(), lindormConfig.getIp(),
                lindormConfig.getThriftPort(), lindormConfig.getS3Port());
    }

    @Override
    public List<String> listBuckets() {
        try (LindormClient client = getLindormClient()) {
            return client.listBuckets().stream().map(Bucket::getName).collect(Collectors.toList());
        }
    }

    /** get crc64 of file content */
    @Override
    public String getMd5(String fileName) {
        try (LindormClient client = getLindormClient()) {
            String lindormFileName = getLindormFileName(fileName);
            FileInfo fileInfo = client.getFileInfo(getBucket(), lindormFileName);
            return String.valueOf(fileInfo.getCrc64());
        } catch (Exception e) {
            logger.error("get checksum of {} error", fileName, e);
            return null;
        }
    }

    @Override
    public long getSize(String fileName) {
        try (LindormClient client = getLindormClient()) {
            String lindormFileName = getLindormFileName(fileName);
            ObjectMetadata metaData = client.getObjectMetadata(getBucket(), lindormFileName);
            return metaData.getInstanceLength();
        }
    }

    @Override
    public void delete(String fileName) {
        try (LindormClient client = getLindormClient()) {
            String lindormFileName = getLindormFileName(fileName);
            logger.info("try to delete file {} from lindorm", lindormFileName);
            client.deleteObject(getBucket(), lindormFileName);
            logger.info("success to delete file {} from lindorm", lindormFileName);
        }
    }

    /** return pure file name list */
    @Override
    public List<String> listFiles(String path) {
        try (LindormClient client = getLindormClient()) {
            List<String> res = new ArrayList<>();
            String lindormFilePrefix = getLindormFileName(path);
            ListObjectsV2Result result = client.listObjects(getBucket(), lindormFilePrefix);
            result.getObjectSummaries().forEach(s3ObjectSummary -> {
                String fullPath = s3ObjectSummary.getKey();
                res.add(fullPath.substring(fullPath.lastIndexOf('/') + 1));
            });
            return res;
        }
    }

    @Override
    public void deleteAll(String path) {
        List<String> fileList = listFiles(path);
        fileList.forEach(fileName -> {
            delete(path + fileName);
            logger.info("file {} is deleted from remote", fileName);
        });
    }

    @Override
    public boolean isObjectsExistForPrefix(String path) {
        List<String> fileList = listFiles(path);
        return !fileList.isEmpty();
    }

    @Override
    public String prepareDownloadLink(String fileName, long expireTimeInSec) {
        try (LindormClient client = getLindormClient()) {
            String lindormFileName = getLindormFileName(fileName);
            return client.generateDownloadUrl(getBucket(), lindormFileName, expireTimeInSec);
        } catch (Exception e) {
            logger.error("prepare download link for {} error", fileName, e);
            return null;
        }
    }

    @Override
    public void download(String fileName, String localPath) {
        boolean success = true;
        do {
            try (LindormClient client = getLindormClient()) {
                String lindormFileName = getLindormFileName(fileName);
                logger.info("begin to download lindorm file: {} to local path: {}", lindormFileName, localPath);
                S3Object object = client.getObject(getBucket(), lindormFileName);
                FileOutputStream fos = new FileOutputStream(new File(localPath, fileName));
                S3ObjectInputStream input = object.getObjectContent();
                IOUtils.copy(input, fos);
            } catch (Exception e) {
                success = false;
                logger.error("download file {} error, will try again!", fileName, e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interruptedException) {
                    logger.error("interruption during sleep", interruptedException);
                    break;
                }
            }
        } while (!success);
        logger.info("success to download file {} to local", fileName);
    }

    @Override
    public byte[] getObjectData(String fileName) {
        try (LindormClient client = getLindormClient()) {
            String lindormFileName = getLindormFileName(fileName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            S3Object object = client.getObject(getBucket(), lindormFileName);
            try (S3ObjectInputStream input = object.getObjectContent()) {
                IOUtils.copy(input, baos);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            logger.error("get object data of {} error", fileName, e);
            return null;
        }
    }

    @Override
    public boolean useMultiAppender(long size) {
        return false;
    }

    @Override
    public Appender providerAppender(String fileName) {
        return new ThriftAppender(fileName, this);
    }

    @Override
    public Appender providerMultiAppender(String fileName, long fileLength) {
        return null;
    }

    public String getLindormFileName(String fileName) {
        return lindormConfig.getPolardbxInstance() + "/" + fileName;
    }

    public static class ThriftAppender implements Appender {
        private final String fileName;
        private String lindormFileName;
        private final LindormManager provider;
        private LindormClient client;
        private CRC64 crc64;
        private String outputStreamId;
        private long nextOffset = 0;

        public ThriftAppender(String fileName, LindormManager provider) {
            this.fileName = fileName;
            this.provider = provider;
        }

        @Override
        public int begin() {
            try {
                client = provider.getLindormClient();
                lindormFileName = provider.getLindormFileName(fileName);
                logger.info("begin to append {} to lindorm", lindormFileName);
                crc64 = new CRC64();
                outputStreamId = client.createFile(provider.getBucket(), lindormFileName).getOutputStreamId();
                return 0;
            } catch (Exception e) {
                logger.error("lindorm appender begin error", e);
                return -1;
            }
        }

        @Override
        public long append(byte[] data, int len) {
            try {
                crc64.update(data, len);
                // todo @yudong 可能能够使用direct buffer优化
                ByteBuffer buffer = ByteBuffer.wrap(data, 0, len);
                nextOffset = client.writeFile(outputStreamId, provider.getBucket(),
                        lindormFileName, buffer, 0, len, nextOffset, crc64.getValue()).getNextOffset();
                return nextOffset;
            } catch (Exception e) {
                logger.error("lindorm append file {} error", lindormFileName, e);
                return -1;
            }
        }

        @Override
        public void end() {
            try {
                client.completeFile(outputStreamId, provider.getBucket(), lindormFileName);
                logger.info("success to append file {} to lindorm", lindormFileName);
            } catch (Exception e) {
                logger.error("lindorm write complete file {} error", fileName, e);
            }
        }
    }
}
