/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.s3;

import com.aliyun.oss.common.utils.IOUtils;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.remote.CommonConfig;
import com.aliyun.polardbx.binlog.remote.DownloadModeEnum;
import com.aliyun.polardbx.binlog.remote.DownloadParameter;
import com.aliyun.polardbx.binlog.remote.IRemoteManager;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.LoopRetry;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.RetryableException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_DOWNLOAD_LINK_REMOVE_INTERNAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_MULTI_APPEND_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_PART_SIZE;

/**
 * @author zm
 */
@Slf4j
public class S3Manager implements IRemoteManager {
    private static final int PART_SIZE = DynamicApplicationConfig.getInt(BINLOG_BACKUP_UPLOAD_PART_SIZE);
    private static final long MAX_APPEND_FILE_SIZE =
        1024 * 1024 * 1024 * DynamicApplicationConfig.getLong(BINLOG_BACKUP_UPLOAD_MULTI_APPEND_THRESHOLD);
    private static final boolean DOWNLOAD_LINK_REMOVE_INTERNAL_ENABLED =
        DynamicApplicationConfig.getBoolean(BINLOG_BACKUP_DOWNLOAD_LINK_REMOVE_INTERNAL);
    public CommonConfig commonConfig;
    // S3 的预签名最长只能存在7天
    private final long MAX_URL_EXPIRED_TIME_SECONDS = 604800;
    public static final int KB = 1024;
    public static final int DEFAULT_BUFFER_SIZE = 8 * KB;

    public S3Manager(CommonConfig commonConfig) {
        this.commonConfig = commonConfig;
        findOrCreateBucket();
    }

    public void findOrCreateBucket() {
        S3Client s3 = getS3Client();
        do {
            if (Thread.interrupted()) {
                throw new RuntimeException("thread interrupted in create bucket " + commonConfig.bucketName);
            }
            List<Bucket> bucketList = s3.listBuckets().buckets();
            boolean find = false;
            for (Bucket bucket : bucketList) {
                if (bucket.name().equalsIgnoreCase(commonConfig.bucketName)) {
                    log.info("success find bucket : " + commonConfig.bucketName);
                    find = true;
                    break;
                }
            }
            if (find) {
                break;
            }
            try {
                log.info("not found bucket : " + commonConfig.bucketName + " , will try to create!");
                CreateBucketRequest request = CreateBucketRequest.builder()
                    .bucket(commonConfig.bucketName)
                    .build();
                s3.createBucket(request);
            } catch (Exception e) {
                log.error("create bucket error, will retry!", e);
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.BINLOG_OSS_BACKUP_BUCKET_NOT_FOUND_WARNING, commonConfig.bucketName);
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException interruptedException) {
                    log.error("create bucket failed and thread interrupt!", e);
                    break;
                }
            }
        } while (true);
    }

    public S3Client getS3Client() {
        AwsCredentials awsCredentials =
            AwsBasicCredentials.create(commonConfig.accessKeyId, commonConfig.accessKeySecret);
        return S3Client.builder()
            .endpointOverride(URI.create("http://" + commonConfig.endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
            .region(Region.of(commonConfig.regionId))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build())
            .build();
    }

    public String getBucket() {
        return commonConfig.bucketName;
    }

    public S3Client getS3ClientVirtualPath() {
        AwsCredentials awsCredentials =
            AwsBasicCredentials.create(commonConfig.accessKeyId, commonConfig.accessKeySecret);
        return S3Client.builder()
            .endpointOverride(URI.create("http://" + commonConfig.endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
            .region(Region.of(commonConfig.regionId))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(false)
                .chunkedEncodingEnabled(false)
                .build())
            .build();
    }

    public S3Presigner getS3Presigner() {
        String url = commonConfig.endpoint;

        if (DOWNLOAD_LINK_REMOVE_INTERNAL_ENABLED && url.contains("-internal")) {
            url = url.replaceAll("-internal", "");
        }

        AwsCredentials awsCredentials =
            AwsBasicCredentials.create(commonConfig.accessKeyId, commonConfig.accessKeySecret);
        return S3Presigner.builder()
            .endpointOverride(URI.create("http://" + url))
            .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
            .region(Region.of(commonConfig.regionId))
            .build();
    }

    @Override
    public void download(String fileName, String localPath, DownloadParameter downloadParameter) throws Throwable {
        DownloadModeEnum downloadMode = downloadParameter.getDownloadMode();
        if (downloadMode == DownloadModeEnum.PARALLEL) {
            parallelDownloadByRangeSingle(fileName, localPath, downloadParameter);
        } else {
            serialDownload(fileName, localPath);
        }
    }

    private void serialDownload(String fileName, String localPath) {
        try (S3Client s3Client = getS3ClientVirtualPath()) {
            String ossFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance);
            Path localFile = Paths.get(new File(localPath, fileName).getPath());
            GetObjectRequest request =
                GetObjectRequest.builder().bucket(commonConfig.bucketName).key(ossFileName).build();
            s3Client.getObject(request, localFile);
        } catch (SdkClientException e) {
            log.error("Error downloading file from S3: ", e);
            throw e;
        }
    }

    public void parallelDownloadByRangeSingle(String keyName, String localPath, DownloadParameter param) {
        S3Client s3Client = getS3ClientVirtualPath();
        // 获取文件元信息
        long fileSize = this.getSize(keyName);
        log.info("prepare download fileName: {}, fileSize: {} bytes, downloadParam: {}", keyName, fileSize,
            param);

        // 定义分片大小
        long partSize = param.getParallelPartSize();
        int totalParts = (int) Math.ceil((double) fileSize / partSize);
        boolean renamed = false;

        // 创建线程池
        ExecutorService executor = new ThreadPoolExecutor(
            param.getParallelism(),
            param.getParallelism(),
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("s3-binlog-download-thread-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        List<Future<Void>> futures = new ArrayList<>();
        // 创建临时文件
        File tempFile = new File(localPath, keyName + ".tmp");

        try {
            tempFile.createNewFile();
            for (int i = 0; i < totalParts; i++) {
                long start = i * partSize;
                long end = Math.min(start + partSize - 1, fileSize - 1);

                // 提交下载任务
                String remoteFileName =
                    BinlogFileUtil.buildRemoteFileFullName(keyName, commonConfig.polardbxInstance);
                Callable<Void> task =
                    new DownloadPartTask(s3Client, commonConfig.bucketName, remoteFileName, start, end, tempFile);
                futures.add(executor.submit(task));
            }

            // 等待所有任务完成
            for (Future<Void> future : futures) {
                future.get(); // 阻塞直到任务完成
            }

            // rename 临时文件
            renamed = tempFile.renameTo(new File(localPath, keyName));
            System.out.println("File downloaded and renamed successfully.");
        } catch (Exception e) {
            log.error("Error in parallel downloading file", e);
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
            // 清理临时文件
            if (!renamed) {
                tempFile.delete();
            }
            s3Client.close();
        }
    }

    static class DownloadPartTask implements Callable<Void> {
        private final S3Client s3;
        private final String bucketName;
        private final String remoteFileName;
        private final long start;
        private final long end;
        private final File tempFile;

        public DownloadPartTask(S3Client s3, String bucketName, String remoteFileName, long start, long end,
                                File tempFile) {
            this.s3 = s3;
            this.bucketName = bucketName;
            this.remoteFileName = remoteFileName;
            this.start = start;
            this.end = end;
            this.tempFile = tempFile;
        }

        @Override
        public Void call() throws Exception {
            // 构造分片下载请求
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(remoteFileName)
                .range(String.format("bytes=%s-%s", start, end))
                .build();
            Retryer<Void> retryer = RetryerBuilder.<Void>newBuilder()
                .retryIfException()
                .withWaitStrategy(WaitStrategies.fixedWait(2000, TimeUnit.MILLISECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(30))
                .build();

            // 下载分片
            // 参考了com.aliyun.oss.internal.OSSDownloadOperation.Task.call
            retryer.call(() -> {
                log.info("Try Download part from " + start + " to " + end + " into " + tempFile.getName());
                ResponseInputStream<GetObjectResponse> response = s3.getObject(request);
                try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
                    raf.seek(start);
                    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                    int bytesRead = 0;
                    while ((bytesRead = IOUtils.readNBytes(response, buffer, 0, buffer.length)) > 0) {
                        raf.write(buffer, 0, bytesRead);
                    }
                } catch (Exception e) {
                    log.error("download error from {} to {} into {}", start, end, tempFile.getName());
                    response.close();
                    throw e;
                }
                return null;
            });

            log.info("Downloaded part from " + start + " to " + end + " into " + tempFile.getName());
            return null;
        }
    }

    /**
     * 如果对象是由 PutObject、PostObject 或 CopyObject 操作创建或通过 AWS Management Console创建，
     * 并且该对象还是纯文本或使用具有 Amazon S3 托管式密钥的服务器端加密（SSE-S3）进行加密的，则该对象的 ETag 将是其对象数据的 MD5 摘要。
     * 如果对象是由 PutObject、PostObject 或 CopyObject 操作创建或通过 AWS Management Console创建，
     * 并且该对象使用客户提供的密钥 (SSE-C) 或 AWS Key Management Service (AWS KMS) 密钥 (SSE-KMS) 通过服务器端加密进行加密，则该对象的 ETag 将不是其对象数据的 MD5 摘要。
     * 如果对象由分段上传过程或 UploadPartCopy 操作创建，则无论使用哪种加密方法，对象的 ETag 都不是 MD5 摘要。
     * 如果对象大于 16 MB，则 AWS Management Console会作为分段上传来上传或复制该对象，因此 ETag 不是 MD5 摘要。
     */
    @Override
    public String getMd5(String fileName) {
        S3Client s3Client = getS3ClientVirtualPath();
        String remoteFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance);
        HeadObjectRequest headObjectRequest =
            HeadObjectRequest.builder().key(remoteFileName).bucket(commonConfig.bucketName).build();
        String md5 = s3Client.headObject(headObjectRequest).eTag();
        s3Client.close();
        return md5;
    }

    @Override
    public long getSize(String fileName) {
        S3Client s3Client = getS3ClientVirtualPath();
        String remoteFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance);
        HeadObjectRequest headObjectRequest =
            HeadObjectRequest.builder().key(remoteFileName).bucket(commonConfig.bucketName).build();
        long size = s3Client.headObject(headObjectRequest).contentLength();
        s3Client.close();
        return size;
    }

    @Override
    public void delete(String fileName) {
        log.info("try delete {} from remote ...", fileName);
        S3Client s3Client = getS3ClientVirtualPath();
        fileName = BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance);
        DeleteObjectRequest req = DeleteObjectRequest.builder().bucket(commonConfig.bucketName).key(fileName).build();
        s3Client.deleteObject(req);
        s3Client.close();
    }

    @Override
    public void deleteAll(String prefix) {
        S3Client s3Client = getS3ClientVirtualPath();
        try {
            String remotePrefix = BinlogFileUtil.buildRemoteFileFullName(prefix, commonConfig.polardbxInstance);
            ListObjectsV2Request listObjectsRequest = ListObjectsV2Request.builder()
                .bucket(commonConfig.bucketName)
                .prefix(remotePrefix)
                .build();
            ListObjectsV2Iterable response = s3Client.listObjectsV2Paginator(listObjectsRequest);
            response.forEach(page -> {
                List<S3Object> objects = page.contents();
                for (S3Object s3 : objects) {
                    DeleteObjectRequest req =
                        DeleteObjectRequest.builder().bucket(commonConfig.bucketName).key(s3.key()).build();
                    s3Client.deleteObject(req);
                    log.info("Deleted file: " + s3.key());
                }
            });

            List<ObjectVersion> versionSummaryList = listVersions(prefix);
            versionSummaryList.forEach(v -> {
                deleteVersion(v);
                log.info("version {} is deleted from remote.", v);
            });
        } catch (S3Exception e) {
            log.error("Error deleting objects from S3: ", e);
        }
    }

    public List<ObjectVersion> listVersions(String prefix) {
        List<ObjectVersion> result = new ArrayList<>();
        try {
            if (!getBucketVersioningEnabled()) {
                return result;
            }

            String remoteFilePrefix = BinlogFileUtil.buildRemoteFileFullName(prefix, commonConfig.polardbxInstance);
            S3Client s3 = getS3ClientVirtualPath();
            ListObjectVersionsRequest req =
                ListObjectVersionsRequest.builder()
                    .bucket(commonConfig.bucketName)
                    .prefix(remoteFilePrefix)
                    .build();
            do {
                ListObjectVersionsResponse response = s3.listObjectVersions(req);
                result.addAll(response.versions());
                if (response.isTruncated()) {
                    req = req.toBuilder().keyMarker(response.nextKeyMarker())
                        .versionIdMarker(response.nextVersionIdMarker()).build();
                } else {
                    req = null;
                }
            } while (req != null);
            s3.close();
        } catch (S3Exception e) {
            if (e.awsErrorDetails().errorCode().equalsIgnoreCase("InvalidBucketState")) {
                log.warn("getBucketVersioning is not supported in this environment!", e);
            } else {
                throw e;
            }
        }

        return result;
    }

    public boolean getBucketVersioningEnabled() {
        try {
            S3Client s3 = getS3ClientVirtualPath();
            GetBucketVersioningRequest getBucketVersioningRequest = GetBucketVersioningRequest.builder()
                .bucket(commonConfig.bucketName).build();
            GetBucketVersioningResponse res = s3.getBucketVersioning(getBucketVersioningRequest);
            s3.close();
            return res != null && res.status() != null && res.status().equals(BucketVersioningStatus.ENABLED);
        } catch (S3Exception e) {
            if (e.awsErrorDetails().errorCode().equalsIgnoreCase("InvalidBucketState")) {
                log.warn("getBucketVersioning is not supported in this environment!", e);
                return false;
            } else {
                throw e;
            }
        }
    }

    public void deleteVersion(ObjectVersion versionSummary) {
        S3Client s3 = getS3ClientVirtualPath();
        DeleteObjectRequest deleteObjectRequest =
            DeleteObjectRequest.builder()
                .bucket(commonConfig.bucketName)
                .key(versionSummary.key())
                .versionId(versionSummary.versionId())
                .build();
        s3.deleteObject(deleteObjectRequest);
        s3.close();
    }

    @Override
    public boolean isObjectsExistForPrefix(String pathPrefix) {
        List<String> fileList = listFiles(pathPrefix);
        return !fileList.isEmpty();
    }

    @Override
    public List<String> listFiles(String path) {
        S3Client s3Client = getS3ClientVirtualPath();
        List<String> objectList = new ArrayList<>();
        String remoteFilePrefix = BinlogFileUtil.buildRemoteFileFullName(path, commonConfig.polardbxInstance);
        ListObjectsV2Request request =
            ListObjectsV2Request.builder().bucket(commonConfig.bucketName).prefix(remoteFilePrefix).build();
        ListObjectsV2Iterable response = s3Client.listObjectsV2Paginator(request);
        response.forEach(page -> {
            List<S3Object> s3Objects = page.contents();
            for (S3Object object : s3Objects) {
                String key = object.key();
                objectList.add(key.substring(key.lastIndexOf('/') + 1));
            }
        });
        s3Client.close();
        return objectList;
    }

    @Override
    public byte[] getObjectData(String fileName) {
        String ossFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance);
        S3Client s3Client = getS3ClientVirtualPath();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        LoopRetry loopRetry = new LoopRetry(new LoopRetry.SleepIntervalStrategy(500)) {
            @Override
            public boolean retry() {
                try {
                    byteArrayOutputStream.reset();
                    GetObjectRequest getObjectRequest =
                        GetObjectRequest.builder()
                            .bucket(commonConfig.bucketName)
                            .key(ossFileName)
                            .build();
                    ResponseInputStream<GetObjectResponse> res = s3Client.getObject(getObjectRequest);
                    byte[] cache = new byte[512];
                    int n;
                    while ((n = res.read(cache)) != -1) {
                        byteArrayOutputStream.write(cache, 0, n);
                    }
                } catch (Exception e) {
                    log.error("get object {} error. will retry", fileName, e);
                    return false;
                }
                return true;
            }
        };
        if (!loopRetry.loop(new AtomicInteger(10))) {
            throw new RuntimeException("get s3 object failed!");
        }
        s3Client.close();
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public Appender providerMultiAppender(String fileName, long fileLength) {
        return new MultiUploader(fileName, fileLength, this);
    }

    @Override
    public Appender providerAppender(String fileName) {
        return new S3Appender(fileName, this);
    }

    @Override
    public boolean supportMultiAppend() {
        return true;
    }

    @Override
    public boolean useMultiAppender(long size) {
        return size >= MAX_APPEND_FILE_SIZE;
    }

    @Override
    public String prepareDownloadLink(String fileName, long expireTimeInSec) {
        if (expireTimeInSec > MAX_URL_EXPIRED_TIME_SECONDS) {
            expireTimeInSec = MAX_URL_EXPIRED_TIME_SECONDS;
        }
        String remoteFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance);
        try (S3Presigner s3Presigner = getS3Presigner()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(commonConfig.bucketName)
                .key(remoteFileName)
                .build();
            GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expireTimeInSec))
                .getObjectRequest(getObjectRequest)
                .build();
            PresignedGetObjectRequest presignedResponse = s3Presigner.presignGetObject(getObjectPresignRequest);
            presignedResponse.url();
            return presignedResponse.url().toString();
        }
    }

    @Override
    public List<String> listBuckets() {
        S3Client s3Client = getS3Client();
        return s3Client.listBuckets().buckets().stream().map(Bucket::name).collect(Collectors.toList());
    }

    /**
     * @author zm
     * 必须指出：S3不支持追加写，这个追加写类使用分片上传的方法进行了模拟
     * 但分片存在上限，最大只能有10000个，所以不适用于实时上传的场景.
     */
    public static class S3Appender implements Appender {

        private S3Client s3;
        private long nextPosition = 0;
        private CommonConfig commonConfig;
        private final String fileName;
        private final S3Manager provider;
        private List<CompletedPart> completedParts;
        private String uploadId;
        private int partNum = 1;

        public S3Appender(String fileName, S3Manager provider) {
            this.fileName = fileName;
            this.provider = provider;
        }

        @Override
        public int begin() {
            s3 = provider.getS3ClientVirtualPath();
            completedParts = new ArrayList<>();
            this.commonConfig = provider.commonConfig;
            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(commonConfig.bucketName)
                .key(BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance))
                .build();
            CreateMultipartUploadResponse response = s3.createMultipartUpload(request);
            uploadId = response.uploadId();
            return 1;
        }

        /**
         * S3仅支持对特定类型存储桶(S3 Express One Zone)追加写，但也有1w次追加的限制，因此决定还是用分段写的方式实现追加写
         * <a href="https://aws.amazon.com/cn/about-aws/whats-new/2024/11/amazon-s3-express-one-zone-append-data-object/">...</a>
         * <a href="https://docs.aws.amazon.com/zh_cn/AmazonS3/latest/userguide/directory-buckets-objects-append.html">参考连接</a>
         * 另外，S3会自动进行校验
         * <a href="https://docs.aws.amazon.com/zh_cn/AmazonS3/latest/userguide/checking-object-integrity.html">S3校验</a>
         *
         * @return long
         */
        @Override
        public long append(byte[] buffer, int len) {
            String remoteFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance);

            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(commonConfig.bucketName)
                .key(remoteFileName)
                .uploadId(uploadId)
                .partNumber(partNum)
                .build();

            Retryer<UploadPartResponse> retryer = RetryerBuilder.<UploadPartResponse>newBuilder()
                .retryIfException()
                .withWaitStrategy(WaitStrategies.fixedWait(1000, TimeUnit.MILLISECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .build();

            try {
                UploadPartResponse res =
                    retryer.call(() -> s3.uploadPart(uploadPartRequest, RequestBody.fromBytes(buffer)));
                CompletedPart part = CompletedPart.builder()
                    .partNumber(partNum)
                    .eTag(res.eTag())
                    .build();
                completedParts.add(part);
            } catch (Exception e) {
                log.error("Upload failed, will terminate upload {}, partNum {}", fileName, partNum);
                AbortMultipartUploadRequest abortMultipartUploadRequest =
                    AbortMultipartUploadRequest.builder().uploadId(uploadId).bucket(commonConfig.bucketName)
                        .key(BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance))
                        .build();
                s3.abortMultipartUpload(abortMultipartUploadRequest);
                throw new RuntimeException("upload failed", e);
            }
            partNum++;
            return nextPosition += len;
        }

        @Override
        public void end() {
            completeMultiUpload();
            s3.close();
        }

        private void completeMultiUpload() {
            String remoteFileName = BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance);
            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();
            CompleteMultipartUploadRequest completeMultipartUploadRequest = CompleteMultipartUploadRequest.builder()
                .bucket(commonConfig.bucketName)
                .uploadId(uploadId)
                .key(remoteFileName)
                .multipartUpload(completedMultipartUpload)
                .build();
            s3.completeMultipartUpload(completeMultipartUploadRequest);
        }
    }

    public static class MultiUploader implements Appender {

        private final String fileName;
        private S3Client s3;
        private final long fileLength;
        private final CommonConfig commonConfig;
        @Setter
        @Getter
        private String uploadId;
        private List<CompletedPart> completedParts;
        private int partCount;
        private int partNum = 0;
        private boolean success = false;
        private final S3Manager provider;
        private long currentAppendSize;
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream(PART_SIZE);

        public MultiUploader(String fileName, long fileLength, S3Manager provider) {
            this.fileName = fileName;
            this.fileLength = fileLength;
            this.provider = provider;
            this.commonConfig = provider.commonConfig;
        }

        @Override
        public int begin() {
            this.s3 = provider.getS3ClientVirtualPath();
            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(commonConfig.bucketName)
                .key(BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance))
                .build();
            CreateMultipartUploadResponse response = s3.createMultipartUpload(request);
            this.uploadId = response.uploadId();
            this.completedParts = new ArrayList<>();
            this.partCount = (int) (fileLength / PART_SIZE);
            if (fileLength % PART_SIZE != 0) {
                this.partCount++;
            }
            return this.partCount;
        }

        @Override
        public long append(byte[] buffer, int readLen) {
            if (partNum == partCount) {
                throw new RuntimeException(
                    "partNum is already equal to partCount, can't continue to append, partCount " + partCount);
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
                    abort();
                    throw new RuntimeException(
                        "partNum has equal to partCount, but currentAppend size " + currentAppendSize
                            + " is not equal to fileLength " + fileLength);
                }
            }
            return readLen;
        }

        private long innerAppend(byte[] buffer, int readLen) {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(commonConfig.bucketName)
                .key(BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance))
                .uploadId(uploadId)
                .partNumber(partNum + 1)
                .build();
            do {
                if (Thread.interrupted()) {
                    abort();
                    throw new RuntimeException("upload " + fileName + " interrupted");
                }
                try {
                    UploadPartResponse uploadPartResult =
                        s3.uploadPart(uploadPartRequest, RequestBody.fromBytes(buffer));
                    CompletedPart part = CompletedPart.builder()
                        .partNumber(partNum + 1)
                        .eTag(uploadPartResult.eTag())
                        .build();
                    completedParts.add(part);
                    partNum++;
                    break;
                } catch (S3Exception e) {
                    log.error("Upload failed, will terminate upload {}, partCount:{}, partNum:{}, bufferSize:{}",
                        fileName, partCount, partNum, buffer.length, e);
                    abort();
                    throw e;
                } catch (RetryableException e) {
                    log.error("Upload failed, will retry", e);
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
                abort();
                log.error("partCount is not equal to partNum, partCount {}, parNum {}", partCount, partNum);
                throw new RuntimeException(
                    "partCount is not equal to partNum, partCount " + partCount + ", parNum " + partNum);
            }
            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();

            CompleteMultipartUploadRequest completeMultipartUploadRequest = CompleteMultipartUploadRequest.builder()
                .bucket(commonConfig.bucketName)
                .uploadId(uploadId)
                .key(BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance))
                .multipartUpload(completedMultipartUpload)
                .build();

            s3.completeMultipartUpload(completeMultipartUploadRequest);
            this.success = true;
            s3.close();
        }

        public void abort() {
            try {
                AbortMultipartUploadRequest abortMultipartUploadRequest =
                    AbortMultipartUploadRequest.builder().uploadId(uploadId).bucket(commonConfig.bucketName)
                        .key(BinlogFileUtil.buildRemoteFileFullName(fileName, commonConfig.polardbxInstance))
                        .build();
                s3.abortMultipartUpload(abortMultipartUploadRequest);
            } catch (Exception ignored) {
            }
        }
    }
}
