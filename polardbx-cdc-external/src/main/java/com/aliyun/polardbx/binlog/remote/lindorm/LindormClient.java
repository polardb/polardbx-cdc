/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.lindorm;

import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.Authorization;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.CompleteFileRequest;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.CreateFileRequest;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.CreateFileResponse;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.FileInfo;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.FlushFileRequest;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.FlushFileResponse;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.GetFileInfoRequest;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.LindormFileService;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.ListFileInfosRequest;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.PutFileRequest;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.ReadFileRequest;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.ReadFileResponse;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.WriteFileRequest;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.WriteFileResponse;
import com.amazonaws.HttpMethod;
import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.internal.SkipMd5CheckStrategy;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TConfiguration;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author yudong
 * @since 2022.10.27
 */
@Slf4j
public class LindormClient implements AutoCloseable {
    private final TSocket tTransport;
    private final LindormFileService.Client thriftClient;
    private final AmazonS3 s3Client;
    private final String accessKey;
    private final String accessSecret;

    public LindormClient(String accessKey, String accessSecret, String endPoint, int thriftPort, int s3Port) {
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;

        try {
            // config thrift client
            TConfiguration config = new TConfiguration();
            config.setMaxMessageSize(1213486160);
            tTransport = new TSocket(config, endPoint, thriftPort);
            tTransport.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10));
            tTransport.setSocketTimeout((int) TimeUnit.SECONDS.toMillis(60));
            tTransport.setTimeout((int) TimeUnit.SECONDS.toMillis(60));
            TBinaryProtocol protocol = new TBinaryProtocol(tTransport);
            this.thriftClient = new LindormFileService.Client(protocol);
            tTransport.open();
        } catch (TTransportException e) {
            throw new RuntimeException(e);
        }

        // config s3 client
        String s3Endpoint = "http://" + endPoint + ":" + s3Port;
        this.s3Client = AmazonS3ClientBuilder.standard().
                withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(s3Endpoint, null)).
                withPathStyleAccessEnabled(true).
                withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, accessSecret))).
                build();
        System.setProperty(SkipMd5CheckStrategy.DISABLE_GET_OBJECT_MD5_VALIDATION_PROPERTY, "true");
    }

    @Override
    public void close() {
        tTransport.close();
        s3Client.shutdown();
    }

    public AmazonS3 getS3Client() {
        return s3Client;
    }

    // ============== bucket related methods ==============

    public boolean doesBucketExist(String bucketName) {
        return s3Client.doesBucketExistV2(bucketName);
    }

    public void createBucket(String bucketName) {
        s3Client.createBucket(bucketName);
    }

    public List<Bucket> listBuckets() {
        return s3Client.listBuckets();
    }

    public void deleteBucket(String bucketName) {
        s3Client.deleteBucket(bucketName);
    }

    // ============== object related methods ==============

    public boolean doesObjectExist(String bucketName, String fileName) {
        log.info("does object exist of file {}, in bucket {}", fileName, bucketName);
        return s3Client.doesObjectExist(bucketName, fileName);
    }

    public void deleteObject(String bucketName, String fileName) {
        log.info("delete object of file {}, in bucket {}", fileName, bucketName);
        s3Client.deleteObject(bucketName, fileName);
    }

    public void deleteObjects(String bucketName, List<String> fileNames) {
        List<DeleteObjectsRequest.KeyVersion> keys = new ArrayList<>();
        fileNames.forEach(fileName -> {
            keys.add(new DeleteObjectsRequest.KeyVersion(fileName));
        });
        DeleteObjectsRequest req = new DeleteObjectsRequest(bucketName).
                withKeys(keys);
        DeleteObjectsResult res = s3Client.deleteObjects(req);
        int successfulDeletes = res.getDeletedObjects().size();
        log.info(successfulDeletes + " objects successfully deleted.");
    }

    public ObjectMetadata getObjectMetadata(String bucketName, String fileName) {
        log.info("get object meta data of file: {}, in bucket: {}", fileName, bucketName);
        return s3Client.getObjectMetadata(bucketName, fileName);
    }


    public ListObjectsV2Result listObjects(String bucketName, String path) {
        log.info("list objects in path: {}, bucket: {}", path, bucketName);
        return s3Client.listObjectsV2(bucketName, path);
    }

    public S3Object getObject(String bucketName, String fileName) {
        log.info("get object of file: {} in bucket: {}", fileName, bucketName);
        return s3Client.getObject(bucketName, fileName);
    }

    public String generateDownloadUrl(String bucketName, String fileName, long expireTimeInSec) {
        Date expiration = new Date();
        long expTimeMillis = Instant.now().toEpochMilli();
        expTimeMillis += expireTimeInSec * 1000;
        expiration.setTime(expTimeMillis);
        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, fileName).
                        withMethod(HttpMethod.GET).
                        withExpiration(expiration);
        URL url = s3Client.generatePresignedUrl(generatePresignedUrlRequest);
        return url.toString();
    }

    // ============== thrift related method =================

    /**
     * 创建文件,返回文件流ID,根据文件流ID可以持续向文件中写入内容,可以实现append和断点续传能力
     */
    public CreateFileResponse createFile(String bucketName, String fileName) throws Exception {
        CreateFileRequest req = new CreateFileRequest(bucketName, fileName, generateAuth("createFile", null));
        return thriftClient.createFile(req);
    }

    /**
     * 向创建成功的文件中写入数据
     * 同一个文件同一时间只支持一个客户端写入
     * 数据写入成功后需要调用flush写入的数据才对用户可见
     */
    public WriteFileResponse writeFile(String oid, String bucketName, String fileName,
                          ByteBuffer src, int srcOffset, int srcLength,
                          long fileOffset, long fileCrc64) throws  Exception {
        WriteFileRequest req = new WriteFileRequest(oid, bucketName, fileName,
                src, srcOffset, srcLength, fileOffset, fileCrc64, generateAuth("writeFile", null));
        return thriftClient.writeFile(req);
    }

    /**
     * 写入的数据需要调用flushFile之后才对外可见
     */
    public FlushFileResponse flushFile(String oid, String bucketName, String fileName, long offset) throws Exception {
        FlushFileRequest req = new FlushFileRequest(oid, bucketName, fileName, offset, generateAuth("flushFile", null));
        return thriftClient.flushFile(req);
    }

    /**
     * 完成文件写入,关闭文件流,关闭后全部数据对用户可见
     */
    public FileInfo completeFile(String oid, String bucketName, String fileName) throws Exception {
        CompleteFileRequest req = new CompleteFileRequest(oid, bucketName,
                fileName, generateAuth("completeFile", null));
        return thriftClient.completeFile(req);
    }

    /**
     * 读取文件,可以每次读取文件一部分方便进行断点下载
     */
    public ReadFileResponse readFile(String bucketName, String fileName, long offset, int size) throws Exception {
        ReadFileRequest req = new ReadFileRequest(bucketName, fileName, offset, size, generateAuth("readFile", null));
        return thriftClient.readFile(req);
    }

    /**
     * 对于小文件(< 10MB)使用putFile,可以一次性直接上传
     */
    public FileInfo putFile(String bucketName, String fileName, ByteBuffer content, long crc64) throws Exception {
        PutFileRequest req = new PutFileRequest(bucketName, fileName, content, crc64, generateAuth("putFile", null));
        return thriftClient.putFile(req);
    }

    /**
     * 获取指定文件信息
     */
    public FileInfo getFileInfo(String bucketName, String fileName) throws Exception {
        GetFileInfoRequest req = new GetFileInfoRequest(bucketName, fileName, generateAuth("getFileInfo", null));
        return thriftClient.getFileInfo(req);
    }

    /**
     * 批量查询文件信息
     */
    public List<FileInfo> listFileInfos(String bucketName, String pathPrefix) throws Exception {
        ListFileInfosRequest req = new ListFileInfosRequest(bucketName, generateAuth("listFileInfos", null));
        req.setPathPrefix(pathPrefix);
        return thriftClient.listFileInfos(req);
    }

    // ================ auth related method ==================

    private String toHex(byte[] bytearray) {
        String hexString = "0123456789abcdef";
        int numChars = bytearray.length * 2;
        StringBuilder hex = new StringBuilder();
        int i = 0;
        while (i < numChars) {
            byte d = bytearray[i / 2];
            hex.append(hexString.charAt((d >> 4) & 0x0F));
            hex.append(hexString.charAt(d & 0x0F));
            i += 2;
        }
        return hex.toString();
    }

    private Authorization generateAuth(String action, String stsToken)
            throws Exception {
        long timestamp = System.currentTimeMillis();
        String sign = signature(timestamp, action);
        Authorization auth = new Authorization(accessKey, sign, timestamp);
        auth.stsToken = stsToken;
        return auth;
    }

    private String signature(long timestamp, String action)
            throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] hashedPassword = toSHA1(accessSecret.getBytes("utf-8"));
        byte[] akBytes = accessKey.getBytes("utf-8");
        byte[] encodedNameAndPass = toSHA1(merge(hashedPassword, akBytes));
        return hmacSHA1Signature(hashedPassword, encodedNameAndPass, timestamp, action);
    }

    private byte[] toSHA1(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest sha = null;
        sha = MessageDigest.getInstance("SHA");
        return sha.digest(bytes);
    }

    private byte[] merge(byte[]... args) {
        int len = 0;
        for (byte[] a : args) {
            len += a.length;
        }
        byte[] data = new byte[len];
        int pos = 0;
        for (byte[] a : args) {
            System.arraycopy(a, 0, data, pos, a.length);
            pos += a.length;
        }
        return data;
    }

    private String hmacSHA1Signature(byte[] hashedPassword, byte[] encodedNameAndPass, long timestamp, String action)
            throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] bytes1 = toSHA1(merge((timestamp + "").getBytes("utf-8"), action.getBytes("utf-8"), encodedNameAndPass));
        return xor(bytes1, hashedPassword);
    }

    private String xor(byte[] bytes1, byte[] bytes2) {
        byte[] longbytes = bytes1;
        byte[] shortbytes = bytes2;
        if (bytes1.length < bytes2.length) {
            longbytes = bytes2;
            shortbytes = bytes1;
        }

        byte[] xorBytes = new byte[longbytes.length];
        int i = 0;
        while (i < shortbytes.length) {
            xorBytes[i] = (byte) (shortbytes[i] ^ longbytes[i]);
            i = i + 1;
        }

        while (i < longbytes.length) {
            xorBytes[i] = longbytes[i];
            i = i + 1;
        }

        return toHex(xorBytes);
    }
}
