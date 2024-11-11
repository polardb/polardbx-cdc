/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.utils.CRC64;
import com.aliyun.oss.model.AppendObjectRequest;
import com.aliyun.oss.model.AppendObjectResult;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadResult;
import com.aliyun.oss.model.DownloadFileRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.ListVersionsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.OSSVersionSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.SimplifiedObjectMeta;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import com.aliyun.oss.model.VersionListing;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * @author yudong
 * @since 2022/8/9
 **/
@Slf4j
@Ignore
public class OssClientTest extends BaseTest {
    private static final String bucket = "yudong-bucket-bj";
    private static final String testPath = "oss-client-test";
    private static OSS ossClient;

    @Before
    public void prepare() {
        String accessKeySecret = getString(ConfigKeys.OSS_ACCESSKEY_ID_SECRET);
        String accessKeyId = getString(ConfigKeys.OSS_ACCESSKEY_ID);
        String endPoint = getString(ConfigKeys.OSS_ENDPOINT);
        ossClient = new OSSClientBuilder().build(endPoint, accessKeyId, accessKeySecret);
        boolean exist = ossClient.doesBucketExist(bucket);
        Assert.assertTrue(exist);
        clearOssDir(testPath);
    }

    @AfterClass
    public static void after() {
        clearOssDir(testPath);
        ossClient.shutdown();
    }

    @Test
    public void multiDownloadTest() throws Throwable {
        String testFileName = testPath + "/" + "multiDownloadTest";
        prepareTestFile(testFileName, 500 * 1024 * 1024);
        long startTime = System.currentTimeMillis();
        DownloadFileRequest downloadFileRequest =
            new DownloadFileRequest(getString(ConfigKeys.OSS_BUCKET), testFileName);
        downloadFileRequest.setDownloadFile("multiDownloadTest");
        downloadFileRequest.setPartSize(50 * 1024 * 1024);
        downloadFileRequest.setTaskNum(10);
        // downloadFileRequest.setEnableCheckpoint(true);
        ossClient.downloadFile(downloadFileRequest);
        long endTime = System.currentTimeMillis();
        int speed = (int) (500 * 1024 * 1024 / (endTime - startTime) / 1000);
        System.out.printf("download speed: %d bps", speed);
    }

    @Test
    public void simpleDownloadTest() {
        String testFileName = testPath + "/" + "simpleDownloadTest";
        prepareTestFile(testFileName, 500 * 1024 * 1024);
        long startTime = System.currentTimeMillis();
        ossClient.getObject(new GetObjectRequest(bucket, testFileName), new File("simpleDownloadTest"));
        long endTime = System.currentTimeMillis();
        int speed = (int) (500 * 1024 * 1024 / (endTime - startTime) / 1000);
        System.out.printf("download speed: %d bps", speed);
    }

    private void prepareTestFile(String ossFileName, long length) {
        // buffer size : 10M
        int bufferSize = 10 << 20;
        byte[] buffer = new byte[bufferSize];
        int numPart = (int) (length / bufferSize);
        int leftSize = (int) (length - numPart * bufferSize);
        long nextPosition = 0L;
        for (int i = 0; i < numPart; i++) {
            new Random().nextBytes(buffer);
            AppendObjectRequest request =
                new AppendObjectRequest(bucket, ossFileName, new ByteArrayInputStream(buffer));
            request.setPosition(nextPosition);
            AppendObjectResult result = ossClient.appendObject(request);
            nextPosition = result.getNextPosition();
        }
        byte[] leftBuffer = new byte[leftSize];
        new Random().nextBytes(leftBuffer);
        AppendObjectRequest request =
            new AppendObjectRequest(bucket, ossFileName, new ByteArrayInputStream(leftBuffer));
        request.setPosition(nextPosition);
        AppendObjectResult result = ossClient.appendObject(request);
        Assert.assertEquals(length, (long) result.getNextPosition());
    }

    @Test
    public void simpleAppendCrcCheckTest() {
        String fileName = "crc64-test.txt";
        String ossFileName = getFullPath(fileName);
        byte[] buffer = new byte[1024 * 1024];
        CRC64 crc64 = new CRC64();
        long nextPosition = 0L;

        for (int i = 0; i < 3; i++) {
            new Random().nextBytes(buffer);
            CRC64 partCrc = new CRC64(buffer, buffer.length);
            crc64.update(buffer, buffer.length);
            AppendObjectRequest request =
                new AppendObjectRequest(
                    bucket, ossFileName,
                    new ByteArrayInputStream(buffer));
            request.setPosition(nextPosition);
            AppendObjectResult result = ossClient.appendObject(request);
            nextPosition = result.getNextPosition();
            log.info("part CRC(calculate):{}", partCrc.getValue());
            log.info("client CRC:{}", result.getClientCRC());
            log.info("server CRC:{}", result.getServerCRC());
            log.info("object CRC:{}", result.getObjectCRC());
            log.info("local calculate CRC:{}", crc64.getValue());
        }

        ObjectMetadata meta = ossClient.getObjectMetadata(bucket, ossFileName);
        log.info("object meta.serverCRC:{}", meta.getServerCRC());
    }

    @Test
    public void multiAppendCrcCheckTest() {
        String fileName = "crc64-multi-check-test.txt";
        String ossFileName = getFullPath(fileName);
        byte[] buffer = new byte[1024 * 1024];
        CRC64 crc64 = new CRC64();
        List<PartETag> partETags = new ArrayList<>();

        InitiateMultipartUploadRequest initRequest =
            new InitiateMultipartUploadRequest(bucket, ossFileName);
        InitiateMultipartUploadResult initResult =
            ossClient.initiateMultipartUpload(initRequest);
        String uploadId = initResult.getUploadId();

        for (int i = 0; i < 3; i++) {
            new Random().nextBytes(buffer);
            CRC64 partCrc = new CRC64(buffer, buffer.length);
            crc64.update(buffer, buffer.length);

            UploadPartRequest request = new UploadPartRequest();
            request.setBucketName(bucket);
            request.setKey(ossFileName);
            request.setUploadId(uploadId);
            request.setInputStream(new ByteArrayInputStream(buffer, 0, buffer.length));
            request.setPartSize(buffer.length);
            request.setPartNumber(i + 1);
            UploadPartResult result = ossClient.uploadPart(request);
            partETags.add(result.getPartETag());

            log.info("part CRC(calculate):{}", partCrc.getValue());
            log.info("part CRC(OSS return):{}", result.getPartETag().getPartCRC());
            log.info("client CRC:{}", result.getClientCRC());
            log.info("server CRC:{}", result.getServerCRC());
            log.info("total CRC:{}", crc64.getValue());
        }
        CompleteMultipartUploadRequest completeRequest =
            new CompleteMultipartUploadRequest(bucket, ossFileName, uploadId, partETags);
        CompleteMultipartUploadResult completeResult =
            ossClient.completeMultipartUpload(completeRequest);
        log.info("complete CRC:{}", completeResult.getServerCRC());
    }

    @Test
    public void streamUploadTest() throws NoSuchAlgorithmException {
        String fileName = "stream-upload-test.txt";
        String content = "hello,world";
        String ossFileName = getFullPath(fileName);
        if (doesFileExist(ossFileName)) {
            deleteOssFile(ossFileName);
        }
        simpleUploadByteArray(ossFileName, content);
        checkMeta(ossFileName, content);
    }

    @Test
    public void fileUploadTest() throws IOException, NoSuchAlgorithmException {
        String fileName = "file-upload-test.txt";
        String content = "hello,world";
        String ossFileName = getFullPath(fileName);
        if (doesFileExist(ossFileName)) {
            deleteOssFile(ossFileName);
        }
        prepareLocalFile(fileName, content);
        simpleUploadLocalFile(fileName, ossFileName);
        checkMeta(ossFileName, content);
        deleteLocalFile(fileName);
    }

    @Test
    public void appendUploadTest() {
        String fileName = "append-upload-test.txt";
        String ossFileName = getFullPath(fileName);
        String content1 = "Man was born free, and everywhere he is in chains.";
        String content2 = "All the splendor in the world is not worth a good friend.";
        String content3 = "If a man deceives me once, shame on him; if twice,shame on me.";
        long nextPos = 0L;
        CRC64 crc64 = new CRC64();

        nextPos = appendUpload(ossFileName, content1, nextPos, crc64);
        nextPos = appendUpload(ossFileName, content2, nextPos, crc64);
        nextPos = appendUpload(ossFileName, content3, nextPos, crc64);

        boolean exist = doesFileExist(ossFileName);
        Assert.assertTrue(exist);
        SimplifiedObjectMeta meta = ossClient.getSimplifiedObjectMeta(bucket, ossFileName);
        long actualSize = meta.getSize();
        Assert.assertEquals(nextPos, actualSize);
    }

    @Test
    public void listFilesTest() {
        String testDir = "list-files-test";
        int testFileNumber = 10;
        List<String> expectFileList = new ArrayList<>();
        for (int i = 0; i < testFileNumber; i++) {
            expectFileList.add(testDir + "/test-file-" + i);
        }
        String content = "hello,world";
        for (String fileName : expectFileList) {
            String ossFileName = getFullPath(fileName);
            simpleUploadByteArray(ossFileName, content);
        }

        List<String> actualFileList = listFiles(testPath + "/" + testDir);
        Collections.sort(actualFileList);
        Assert.assertEquals(expectFileList, actualFileList);
    }

    @Test
    public void listVersionsTest() {
        String testDir = "list-versions-test";
        int testFileNumber = 10;
        List<String> expectFileList = new ArrayList<>();
        for (int i = 0; i < testFileNumber; i++) {
            expectFileList.add(testDir + "/test-file-" + i);
        }
        String content = "hello,world";
        for (String fileName : expectFileList) {
            String ossFileName = getFullPath(fileName);
            simpleUploadByteArray(ossFileName, content);
        }

        List<OSSVersionSummary> versions = listVersions(testPath + "/" + testDir);
        List<String> actualFileList = new ArrayList<>();
        for (OSSVersionSummary ossVersion : versions) {
            String ossFileName = ossVersion.getKey();
            actualFileList.add(getPartPath(ossFileName));
        }
        Collections.sort(actualFileList);
        Assert.assertEquals(expectFileList, actualFileList);
    }

    @Test
    public void deleteVersionTest() {
        String fileName = "delete-version-test.txt";
        String content = "hello,world";
        String ossFileName = getFullPath(fileName);
        if (doesFileExist(ossFileName)) {
            deleteOssFile(ossFileName);
        }
        simpleUploadByteArray(ossFileName, content);

        List<OSSVersionSummary> versions = listVersions(ossFileName);
        Assert.assertEquals(1, versions.size());
        versions.forEach(v -> {
            ossClient.deleteVersion(bucket, v.getKey(), v.getVersionId());
        });
        Assert.assertFalse(doesFileExist(ossFileName));
    }

    @Test
    public void multipartUploadTest() throws NoSuchAlgorithmException, IOException {
        String fileName = "multi-part-upload-test.txt";
        String ossFileName = getFullPath(fileName);
        long partSize = 1024 * 1024;
        int partCount = 3;

        // generate 3MB bytes data and write it to test file
        byte[] bytes = new byte[(int) (partSize * partCount)];
        new Random().nextBytes(bytes);
        FileUtils.writeByteArrayToFile(new File(fileName), bytes);
        File file = new File(fileName);
        long fileLength = file.length();

        // init part
        InitiateMultipartUploadRequest initRequest =
            new InitiateMultipartUploadRequest(bucket, ossFileName);
        InitiateMultipartUploadResult initResult =
            ossClient.initiateMultipartUpload(initRequest);
        String uploadId = initResult.getUploadId();
        List<PartETag> partETags = new ArrayList<>();

        // upload part
        for (int i = 0; i < partCount; i++) {
            long startPos = i * partSize;
            long curPartSize = (i + 1 == partCount) ? (fileLength - startPos) : partSize;
            InputStream inputStream = new FileInputStream(file);
            // skip already upload part
            inputStream.skip(startPos);

            UploadPartRequest upRequest = new UploadPartRequest();
            upRequest.setBucketName(bucket);
            upRequest.setKey(ossFileName);
            upRequest.setUploadId(uploadId);
            upRequest.setInputStream(inputStream);
            upRequest.setPartSize(partSize);
            upRequest.setPartNumber(i + 1);

            UploadPartResult upResult = ossClient.uploadPart(upRequest);
            partETags.add(upResult.getPartETag());
        }

        // complete upload part
        CompleteMultipartUploadRequest completeRequest =
            new CompleteMultipartUploadRequest(bucket, ossFileName, uploadId, partETags);
        CompleteMultipartUploadResult completeResult =
            ossClient.completeMultipartUpload(completeRequest);

        // check meta
        boolean exist = doesFileExist(ossFileName);
        Assert.assertTrue(exist);
        SimplifiedObjectMeta meta = ossClient.getSimplifiedObjectMeta(bucket, ossFileName);
        long size = meta.getSize();
        Assert.assertEquals(size, fileLength);

        file.delete();
    }

    @Test
    public void streamDownloadTest() {
        String fileName = "stream-download-test.txt";
        String content = "hello,world";
        String ossFileName = getFullPath(fileName);
        if (doesFileExist(ossFileName)) {
            deleteOssFile(ossFileName);
        }
        simpleUploadByteArray(ossFileName, content);

        OSSObject ossObject = ossClient.getObject(bucket, ossFileName);
    }

    private static String getFullPath(String pureName) {
        return testPath + "/" + pureName;
    }

    private static String getPartPath(String OssFileName) {
        int begin = OssFileName.indexOf('/') + 1;
        return OssFileName.substring(begin);
    }

    private boolean doesFileExist(String fileName) {
        return ossClient.doesObjectExist(bucket, fileName);
    }

    /**
     * ETag (entity tag) 在每个Object生成的时候被创建，用于标识一个Object的内容。
     * 对于Put Object请求创建的Object，ETag值是其内容的MD5值；
     * 对于其他方式创建的Object，ETag值是基于一定计算规则生成的唯一值，但不是其内容的MD5值。
     * ETag值可以用于检查Object内容是否发生变化。
     */
    private void checkMeta(String ossFileName, String content) throws NoSuchAlgorithmException {
        boolean exist = doesFileExist(ossFileName);
        Assert.assertTrue(exist);
        SimplifiedObjectMeta meta = ossClient.getSimplifiedObjectMeta(bucket, ossFileName);
        long size = meta.getSize();
        Assert.assertEquals(size, content.length());
    }

    private void simpleUploadByteArray(String fileName, String content) {
        PutObjectRequest request = new PutObjectRequest(bucket, fileName,
            new ByteArrayInputStream(content.getBytes()));
        ossClient.putObject(request);
    }

    private void simpleUploadLocalFile(String localFile, String ossFile) {
        PutObjectRequest request = new PutObjectRequest(bucket, ossFile, new File(localFile));
        ossClient.putObject(request);
    }

    private long appendUpload(String fileName, String content, long position, CRC64 crc64) {
        crc64.update(content.getBytes(), content.getBytes().length);
        long expect = crc64.getValue();

        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentType("text/plain");
        AppendObjectRequest request = new AppendObjectRequest(bucket, fileName,
            new ByteArrayInputStream(content.getBytes()),
            meta);
        request.setPosition(position);
        AppendObjectResult result = ossClient.appendObject(request);

        String crcStr = result.getObjectCRC();
        long actual = NumberUtils.createBigInteger(crcStr).longValue();
        Assert.assertEquals(expect, actual);
        return result.getNextPosition();
    }

    private static void deleteOssFile(String fileName) {
        ossClient.deleteObject(bucket, fileName);
    }

    private static void clearOssDir(String path) {
        List<String> fileList = listFiles(path);
        for (String fileName : fileList) {
            String ossFileName = getFullPath(fileName);
            deleteOssFile(ossFileName);
        }
    }

    private void prepareLocalFile(String fileName, String content) throws IOException {
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }
        file.createNewFile();
        FileWriter fw = new FileWriter(file);
        fw.write(content);
        fw.close();
    }

    private void deleteLocalFile(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }
    }

    private static List<String> listFiles(String path) {
        ListObjectsV2Request request = new ListObjectsV2Request(bucket);
        request.setPrefix(path);
        ListObjectsV2Result result = ossClient.listObjectsV2(request);
        List<String> fileList = new ArrayList<>();
        for (OSSObjectSummary objectSummary : result.getObjectSummaries()) {
            String ossFileName = objectSummary.getKey();
            fileList.add(getPartPath(ossFileName));
        }
        return fileList;
    }

    List<OSSVersionSummary> listVersions(String prefix) {
        List<OSSVersionSummary> versions = new ArrayList<>();
        String nextKeyMarker = null;
        String nextVersionMarker = null;
        VersionListing versionListing;

        do {
            ListVersionsRequest listVersionsRequest = new ListVersionsRequest()
                .withBucketName(bucket)
                .withKeyMarker(nextKeyMarker)
                .withVersionIdMarker(nextVersionMarker)
                .withPrefix(prefix);
            versionListing = ossClient.listVersions(listVersionsRequest);
            versions.addAll(versionListing.getVersionSummaries());
            nextKeyMarker = versionListing.getNextKeyMarker();
            nextVersionMarker = versionListing.getNextVersionIdMarker();
        } while (versionListing.isTruncated());

        return versions;
    }
}
