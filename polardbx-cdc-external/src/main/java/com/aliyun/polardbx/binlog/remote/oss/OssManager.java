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
package com.aliyun.polardbx.binlog.remote.oss;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.utils.CRC64;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.AppendObjectRequest;
import com.aliyun.oss.model.AppendObjectResult;
import com.aliyun.oss.model.Bucket;
import com.aliyun.oss.model.BucketList;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadResult;
import com.aliyun.oss.model.DownloadFileRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.ListBucketsRequest;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.ListVersionsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.OSSVersionSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import com.aliyun.oss.model.VersionListing;
import com.aliyun.polardbx.binlog.BinlogFileUtil;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.remote.DownloadModeEnum;
import com.aliyun.polardbx.binlog.remote.IRemoteManager;
import com.aliyun.polardbx.binlog.util.LoopRetry;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_MAX_APPEND_FILE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_PART_SIZE;

/**
 * @author chengjin
 *
 * 传给OssManager方法的fileName是增加了group name和stream name
 * 前缀的文件名，而存储在oss上的文件名在此基础上又包装了一层
 * 见 BinlogFileUtil.buildRemoteFileFullName，增加了前缀 "polardbx_cdc/instance name/"
 * 所以，一个binlog文件在oss上的存储完整路径为：
 * polardbx_cdc/instance name/group/stream/file name
 */
public class OssManager implements IRemoteManager {

    private static final Logger logger = LoggerFactory.getLogger(OssManager.class);
    private static final int PART_SIZE = DynamicApplicationConfig.getInt(BINLOG_BACKUP_UPLOAD_PART_SIZE);
    private static final long MAX_APPEND_FILE_SIZE =
        1024 * 1024 * 1024 * DynamicApplicationConfig.getLong(BINLOG_BACKUP_UPLOAD_MAX_APPEND_FILE_SIZE);
    private final OssConfig ossConfig;

    public OssManager(OssConfig ossConfig) {
        this.ossConfig = ossConfig;
        this.config();
    }

    private void config() {
        ListBucketsRequest request = new ListBucketsRequest(ossConfig.bucketName, null, null);
        OSS oss = getOssClient();
        do {
            BucketList bucketObj = oss.listBuckets(request);
            List<Bucket> bucketList = bucketObj.getBucketList();
            boolean find = false;
            for (Bucket bucket : bucketList) {
                if (bucket.getName().equalsIgnoreCase(ossConfig.bucketName)) {
                    logger.info("success find bucket : " + ossConfig.bucketName);
                    find = true;
                    break;
                }
            }
            if (find) {
                break;
            }
            try {
                logger.info("not found bucket : " + ossConfig.bucketName + " , will try to create!");
                oss.createBucket(ossConfig.bucketName);
            } catch (Exception e) {
                logger.error("create bucket error, will retry!", e);
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.BINLOG_OSS_BACKUP_BUCKET_NOT_FOUND_WARNING, ossConfig.bucketName);
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException interruptedException) {
                    logger.error("create bucket failed and thread interrupt!", e);
                    break;
                }
            }
        } while (true);

    }

    public String getBucket() {
        return ossConfig.bucketName;
    }

    public OSS getOssClient() {
        return getOssClient(false);
    }

    private OSS getOssClient(boolean removeInternal) {
        String url = ossConfig.endpoint;
        if (removeInternal) {
            url = url.replaceAll("-internal", "");
        }
        return new OSSClientBuilder().build(url, ossConfig.accessKeyId, ossConfig.accessKeySecret);
    }

    @Override
    public String getMd5(String fileName) {
        OSS ossClient = getOssClient();
        String ossFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, ossConfig.polardbxInstance);
        OSSObject ossObject = ossClient.getObject(ossConfig.bucketName, ossFileName);
        ossClient.shutdown();
        return ossObject.getObjectMetadata().getETag();
    }

    @Override
    public long getSize(String fileName) {
        OSS ossClient = getOssClient();
        String ossFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, ossConfig.polardbxInstance);
        OSSObject ossObject = ossClient.getObject(ossConfig.bucketName, ossFileName);
        try {
            ossObject.close();
        } catch (Exception e) {

        }
        ossClient.shutdown();
        return ossObject.getObjectMetadata().getContentLength();
    }

    @Override
    public void delete(String binlogFile) {
        OSS ossClient = getOssClient();
        String ossFileName = BinlogFileUtil.buildRemoteFileFullName(binlogFile, ossConfig.polardbxInstance);
        // 删除文件。如需删除文件夹，请将ObjectName设置为对应的文件夹名称。如果文件夹非空，则需要将文件夹下的所有object删除后才能删除该文件夹。
        ossClient.deleteObject(ossConfig.bucketName, ossFileName);
        ossClient.shutdown();
    }

    @Override
    public void deleteAll(String prefix) {
        List<String> fileList = listFiles(prefix);
        fileList.forEach(f -> {
            delete(prefix + f);
            logger.info("file {} is deleted from remote.", f);
        });

        List<OSSVersionSummary> versionSummaryList = listVersions(prefix);
        versionSummaryList.forEach(v -> {
            deleteVersion(v);
            logger.info("version {} is deleted from remote.", v);
        });
    }

    @Override
    public boolean isObjectsExistForPrefix(String pathPrefix) {
        List<String> fileList = listFiles(pathPrefix);
        return !fileList.isEmpty();
    }

    @Override
    public List<String> listFiles(String path) {
        return listObjects(path);
    }

    @Override
    public Appender providerMultiAppender(String fileName, long fileLength) {
        return new MultiUploader(fileName, fileLength, this);
    }

    @Override
    public Appender providerAppender(String fileName) {
        return new OSSAppender(fileName, this);
    }

    @Override
    public boolean useMultiAppender(long size) {
        return size >= MAX_APPEND_FILE_SIZE;
    }

    @Override
    public String prepareDownloadLink(String fileName, long expireTimeInSec) {
        OSS oss = getOssClient(true);
        long expires = System.currentTimeMillis() + expireTimeInSec * 1000;
        return oss.generatePresignedUrl(ossConfig.bucketName,
            BinlogFileUtil.buildRemoteFileFullName(fileName, ossConfig.polardbxInstance), new Date(expires),
            HttpMethod.GET).toString();
    }

    @Override
    public void download(String fileName, String localPath) {
        DownloadModeEnum downloadMode =
            DownloadModeEnum.valueOf(DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DOWNLOAD_MODE));
        if (downloadMode == DownloadModeEnum.PARALLEL) {
            parallelDownload(fileName, localPath);
        } else {
            serialDownload(fileName, localPath);
        }
    }

    private void serialDownload(String fileName, String localPath) {
        OSS client = getOssClient();
        String ossFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, ossConfig.polardbxInstance);
        try {
            client.getObject(new GetObjectRequest(ossConfig.bucketName, ossFileName), new File(localPath, fileName));
        } finally {
            client.shutdown();
        }
    }

    private void parallelDownload(String fileName, String localPath) {
        OSS client = getOssClient();
        String ossFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, ossConfig.polardbxInstance);
        Long partSize = DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_DOWNLOAD_PART_SIZE);
        Integer taskNum = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_DOWNLOAD_MAX_THREAD_NUM);
        DownloadFileRequest downloadFileRequest = new DownloadFileRequest(ossConfig.bucketName, ossFileName);
        downloadFileRequest.setDownloadFile(new File(localPath, fileName).getPath());
        downloadFileRequest.setPartSize(partSize);
        downloadFileRequest.setTaskNum(taskNum);
        try {
            client.downloadFile(downloadFileRequest);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            client.shutdown();
        }
    }

    private List<String> listObjects(String prefix) {
        String ossFilePrefix = BinlogFileUtil.buildRemoteFileFullName(prefix, ossConfig.polardbxInstance);
        List<String> objectList = new ArrayList<>();
        String continuationToken = null;

        OSS ossClient = getOssClient();

        while (true) {
            ListObjectsV2Request request = new ListObjectsV2Request();
            request.setBucketName(ossConfig.getBucketName());
            request.setPrefix(ossFilePrefix);
            request.setContinuationToken(continuationToken);
            ListObjectsV2Result objectListing = ossClient.listObjectsV2(request);
            List<OSSObjectSummary> ossObjectSummaryList = objectListing.getObjectSummaries();
            for (OSSObjectSummary objectSummary : ossObjectSummaryList) {
                String key = objectSummary.getKey();
                objectList.add(key.substring(key.lastIndexOf('/') + 1));
            }
            if (objectListing.isTruncated()) {
                continuationToken = objectListing.getNextContinuationToken();
            } else {
                break;
            }
        }

        ossClient.shutdown();
        return objectList;
    }

    private void deleteVersion(OSSVersionSummary versionSummary) {
        OSS ossClient = getOssClient();
        ossClient.deleteVersion(ossConfig.getBucketName(), versionSummary.getKey(), versionSummary.getVersionId());
        ossClient.shutdown();
    }

    private List<OSSVersionSummary> listVersions(String prefix) {
        String ossFilePrefix = BinlogFileUtil.buildRemoteFileFullName(prefix, ossConfig.polardbxInstance);
        List<OSSVersionSummary> result = new ArrayList<>();
        String nextVersionIdMarker = null;
        String nextKeyMarker = null;

        OSS ossClient = getOssClient();
        while (true) {
            ListVersionsRequest request = new ListVersionsRequest(ossConfig.getBucketName(), ossFilePrefix,
                nextKeyMarker, nextVersionIdMarker, null, null);
            VersionListing versionListing = ossClient.listVersions(request);
            result.addAll(versionListing.getVersionSummaries());

            if (versionListing.isTruncated()) {
                nextVersionIdMarker = versionListing.getNextVersionIdMarker();
                nextKeyMarker = versionListing.getNextKeyMarker();
            } else {
                break;
            }
        }
        ossClient.shutdown();
        return result;
    }

    @Override
    public byte[] getObjectData(String fileName) {
        String ossFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, ossConfig.polardbxInstance);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        OSS ossClient = getOssClient();
        LoopRetry loopRetry = new LoopRetry(new LoopRetry.SleepIntervalStrategy(500)) {
            @Override
            public boolean retry() {
                try {
                    OSSObject ossObject = ossClient.getObject(ossConfig.getBucketName(), ossFileName);
                    InputStream inputStream = ossObject.getObjectContent();
                    byte[] cache = new byte[512];
                    int n;
                    while ((n = inputStream.read(cache)) != -1) {
                        byteArrayOutputStream.write(cache, 0, n);
                    }
                } catch (Exception e) {
                    logger.error("get object" + fileName + "error, will retry", e);
                    return false;
                }
                return true;
            }
        };
        if (!loopRetry.loop(new AtomicInteger(10))) {
            throw new RuntimeException("get oss object failed!");
        }

        ossClient.shutdown();
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public List<String> listBuckets() {
        OSS ossClient = getOssClient();
        return ossClient.listBuckets().stream().map(Bucket::getName).collect(Collectors.toList());
    }

    public static class OSSAppender implements Appender {

        private OSS oss;
        private long nextPosition = 0;
        private OssConfig ossConfig;
        private String fileName;
        private OssManager provider;
        private CRC64 crc64;

        public OSSAppender(String fileName, OssManager provider) {
            this.fileName = fileName;
            this.provider = provider;
        }

        @Override
        public int begin() {
            oss = provider.getOssClient();
            this.ossConfig = provider.ossConfig;
            crc64 = new CRC64();
            return 1;
        }

        @Override
        public long append(byte[] buffer, int len) {
            ObjectMetadata meta = new ObjectMetadata();
            // 指定上传的内容类型。
            meta.setContentType("text/plain");

            // 通过AppendObjectRequest设置多个参数。
            AppendObjectRequest appendObjectRequest = new AppendObjectRequest(ossConfig.bucketName,
                BinlogFileUtil.buildRemoteFileFullName(fileName, ossConfig.polardbxInstance),
                new ByteArrayInputStream(buffer, 0, len),
                meta);

            // 第一次追加。
            // 设置文件的追加位置。
            appendObjectRequest.setPosition(nextPosition);
            do {
                try {
                    crc64.update(buffer, len);
                    AppendObjectResult appendObjectResult = oss.appendObject(appendObjectRequest);
                    String crcStr = appendObjectResult.getObjectCRC();
                    if (crc64.getValue() != NumberUtils.createBigInteger(crcStr).longValue()) {
                        throw new PolardbxException("check : " + fileName + " crc failed!");
                    }
                    nextPosition = appendObjectResult.getNextPosition();
                    break;
                } catch (OSSException oe) {
                    // 到这里说明aksk 有问题，或者 多个客户端同时上传了，需要退出，等待调度
                    logger.error("append failed, will terminal upload " + fileName, oe);
                    throw oe;
                } catch (ClientException ce) {
                    logger.error("append failed , will retry ", ce);
                    try {
                        Thread.sleep(1L);
                    } catch (Exception e1) {
                    }
                }
            } while (true);
            return nextPosition;
        }

        @Override
        public void end() {
            oss.shutdown();
        }
    }

    public static class MultiUploader implements Appender {

        private String binlogFileName;
        private OSS oss;
        private long fileLength;
        private OssConfig ossConfig;
        private String uploadId;
        private List<PartETag> partETags;
        private int partCount;
        private int partNum = 0;
        private boolean success = false;
        private OssManager provider;
        private long currentAppendSize;
        private ByteArrayOutputStream bos = new ByteArrayOutputStream(PART_SIZE);

        public MultiUploader(String binlogFileName, long fileLength, OssManager provider) {
            this.binlogFileName = binlogFileName;
            this.fileLength = fileLength;
            this.provider = provider;
            this.ossConfig = provider.ossConfig;
        }

        @Override
        public int begin() {
            this.oss = provider.getOssClient();
            // 创建InitiateMultipartUploadRequest对象。
            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(ossConfig.bucketName,
                BinlogFileUtil.buildRemoteFileFullName(binlogFileName, ossConfig.polardbxInstance));

            // 如果需要在初始化分片时设置文件存储类型，请参考以下示例代码。
            //        ObjectMetadata metadata = new ObjectMetadata();
            //        // metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.Standard.toString());
            //        metadata.setHeader(OSSHeaders.OSS_, StorageClass.Standard.toString());
            //        request.setObjectMetadata(metadata);

            // 初始化分片。
            InitiateMultipartUploadResult upresult = oss.initiateMultipartUpload(request);
            // 返回uploadId，它是分片上传事件的唯一标识，您可以根据这个uploadId发起相关的操作，如取消分片上传、查询分片上传等。
            this.uploadId = upresult.getUploadId();

            // partETags是PartETag的集合。PartETag由分片的ETag和分片号组成。
            this.partETags = new ArrayList<PartETag>();
            // 计算文件有多少个分片。
            this.partCount = (int) (fileLength / PART_SIZE);
            if (fileLength % PART_SIZE != 0) {
                this.partCount++;
            }
            return this.partCount;
        }

        @Override
        public long append(byte[] buffer, int readLen) {
            if (partNum == partCount) {
                throw new PolardbxException(
                    String.format("partNum is already equal to partCount, can`t continue to append, partCount %s.",
                        partCount));
            }

            int remainDataLen = readLen;
            int writeOffset = 0;
            while (remainDataLen > 0) {
                int limit = PART_SIZE - bos.size();
                if (limit == 0) {
                    innerAppend(bos.toByteArray(), PART_SIZE);
                    bos.reset();
                    continue;
                }
                int writeSize = Math.min(limit, remainDataLen);
                bos.write(buffer, writeOffset, writeSize);
                writeOffset += writeSize;
                remainDataLen -= writeSize;
            }
            currentAppendSize += readLen;
            if (bos.size() > 0 && (partNum + 1 == partCount)) {
                innerAppend(bos.toByteArray(), bos.size());
                bos.reset();
                if (currentAppendSize != fileLength) {
                    throw new PolardbxException(
                        String.format(
                            "partNum has equal to partCount, but currentAppend size %s is not equal to fileLength %s.",
                            currentAppendSize, fileLength));
                }
            }
            return readLen;
        }

        private long innerAppend(byte[] buffer, int readLen) {
            // 跳过已经上传的分片。
            UploadPartRequest uploadPartRequest = new UploadPartRequest();
            uploadPartRequest.setBucketName(ossConfig.bucketName);
            uploadPartRequest.setKey(
                BinlogFileUtil.buildRemoteFileFullName(binlogFileName, ossConfig.polardbxInstance));
            uploadPartRequest.setUploadId(uploadId);
            uploadPartRequest.setInputStream(new ByteArrayInputStream(buffer, 0, readLen));
            //            uploadPartRequest.setProgressListener(new UploadObjectProgressListener());
            long curPartSize = (partNum + 1 == partCount) ? (fileLength - (long) partNum * PART_SIZE) : PART_SIZE;
            // 设置分片大小。除了最后一个分片没有大小限制，其他的分片最小为100 KB。
            uploadPartRequest.setPartSize(curPartSize);
            // 设置分片号。每一个上传的分片都有一个分片号，取值范围是1~10000，如果超出这个范围，OSS将返回InvalidArgument的错误码。
            uploadPartRequest.setPartNumber(partNum + 1);
            do {
                try {
                    CRC64 crc64 = new CRC64(buffer, readLen);
                    // 每个分片不需要按顺序上传，甚至可以在不同客户端上传，OSS会按照分片号排序组成完整的文件。
                    UploadPartResult uploadPartResult = oss.uploadPart(uploadPartRequest);
                    if (uploadPartResult.getPartETag().getPartCRC() != crc64.getValue()) {
                        throw new PolardbxException("check : " + binlogFileName + " crc failed!");
                    }
                    // 每次上传分片之后，OSS的返回结果包含PartETag。PartETag将被保存在partETags中。
                    {
                        partETags.add(uploadPartResult.getPartETag());
                    }
                    partNum++;
                    break;
                } catch (OSSException e) {
                    logger.error("upload failed, will terminal upload " + binlogFileName, e);
                    try {
                        AbortMultipartUploadRequest abortMultipartUploadRequest = new AbortMultipartUploadRequest(
                            ossConfig.bucketName,
                            binlogFileName,
                            uploadId);
                        oss.abortMultipartUpload(abortMultipartUploadRequest);
                    } catch (Exception se) {
                    }
                    throw e;
                } catch (ClientException e) {
                    logger.error("upload failed , will retry ", e);
                    try {
                        Thread.sleep(1L);
                    } catch (Exception e1) {
                    }
                }
            } while (true);
            return 0L;
        }

        public boolean isSuccess() {
            return this.success;
        }

        @Override
        public void end() {
            if (partCount != partNum) {
                logger.error("partCount is not equal to partNum, partCount {}, parNum {}", partCount, partNum);
                throw new PolardbxException(
                    String.format("partCount is not equal to partNum, partCount %s, parNum %s.", partCount, partNum));
            }
            // 创建CompleteMultipartUploadRequest对象。
            // 在执行完成分片上传操作时，需要提供所有有效的partETags。OSS收到提交的partETags后，会逐一验证每个分片的有效性。当所有的数据分片验证通过后，OSS将把这些分片组合成一个完整的文件。
            CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(ossConfig.bucketName,
                    BinlogFileUtil.buildRemoteFileFullName(binlogFileName, ossConfig.polardbxInstance),
                    uploadId,
                    partETags);

            // 完成上传。
            CompleteMultipartUploadResult completeMultipartUploadResult = oss.completeMultipartUpload(
                completeMultipartUploadRequest);
            this.success = true;
            // 关闭OSSClient。
            oss.shutdown();
        }
    }
}
