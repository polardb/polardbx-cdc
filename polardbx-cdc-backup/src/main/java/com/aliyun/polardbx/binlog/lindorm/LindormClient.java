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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.common.utils.CRC64;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.Authorization;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.Bucket;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.CompleteFileRequest;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.CreateBucketRequest;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.CreateFileRequest;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.CreateFileResponse;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.DeleteFileRequest;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.FileInfo;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.FlushFileRequest;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.LindormFileService;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.ListBucketsRequest;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.ListFileInfosRequest;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.ReadFileRequest;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.ReadFileResponse;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.WriteFileRequest;
import com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated.WriteFileResponse;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.apache.thrift.TConfiguration;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LindormClient {

    private TSocket tTransport;
    private LindormFileService.Client client;
    private String accessKey;
    private String accessSecret;
    private String bucket;
    private String ip;
    private int port;
    private int downloadPort;

    public LindormClient(String accessKey, String accessSecret, String ip, int port, int downloadPort, String bucket)
        throws TTransportException {
        TConfiguration config = new TConfiguration();
        config.setMaxMessageSize(1213486160);
        this.tTransport = new TSocket(config, ip, port);
        this.tTransport.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10));
        this.tTransport.setSocketTimeout((int) TimeUnit.SECONDS.toMillis(60));
        this.tTransport.setTimeout((int) TimeUnit.SECONDS.toMillis(60));
        this.tTransport.open();
        TBinaryProtocol protocol = new TBinaryProtocol(tTransport);
        this.client = new LindormFileService.Client(protocol);
        this.accessKey = accessKey;
        this.accessSecret = accessSecret;
        this.bucket = bucket;
        this.ip = ip;
        this.port = port;
        this.downloadPort = downloadPort;
    }

    private void checkConnect() {
        try {
            initBucket();
        } catch (Exception e) {
            try {
                tTransport.close();
                tTransport.open();
            } catch (TTransportException tTransportException) {

            }
        }
    }

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
        String sign = signature(accessKey, accessSecret, timestamp, action);
        Authorization auth = new Authorization(accessKey, sign, timestamp);
        auth.stsToken = stsToken;
        return auth;
    }

    private String signature(String accessKey, String accessSecret, long timestamp, String action)
        throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] hashedPassword = toSHA1(accessSecret.getBytes("utf-8"));
        byte[] akBytes = accessKey.getBytes("utf-8");
        byte[] encodedNameAndPass = toSHA1(merge(hashedPassword, akBytes));
        return hmacSHA1Signature(hashedPassword, encodedNameAndPass, timestamp, action);
    }

    private byte[] toSHA1(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest sha = null;
        sha = MessageDigest.getInstance("SHA");
        byte[] md5Bytes = sha.digest(bytes);
        return md5Bytes;
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
        byte[] bytes2 = hashedPassword;
        return xor(bytes1, bytes2);
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

    public void initBucket() throws Exception {
        ListBucketsRequest listBucketsRequest = new ListBucketsRequest(generateAuth("listBuckets", null));
        List<Bucket> bucketList = client.listBuckets(listBucketsRequest);
        for (Bucket bucket : bucketList) {
            if (bucket.getName().equals(this.bucket)) {
                return;
            }
        }
        CreateBucketRequest request = new CreateBucketRequest(bucket, generateAuth("createBucket", null));
        client.createBucket(request);
    }

    public List<String> listFile(String path) throws Exception {
        checkConnect();
        ListFileInfosRequest listFileInfosRequest =
            new ListFileInfosRequest(bucket, generateAuth("listFileInfos", null));
        listFileInfosRequest.setPathPrefix(path);
        List<FileInfo> files = client.listFileInfos(listFileInfosRequest);
        List<String> fileNameList = new ArrayList<>();
        for (FileInfo ff : files) {
            fileNameList.add(ff.getPath());
        }
        return fileNameList;
    }

    public void downloadByHttp(String filePath, File destFile) throws Exception {
        String response = generateUrl(filePath);
        JSONObject responseObj = JSON.parseObject(response);
        String downloadUrl = responseObj.getString("url");
        HttpHelper.download(downloadUrl, new FileOutputStream(destFile));
    }

    public void delete(String filePath) throws Exception {
        checkConnect();
        DeleteFileRequest deleteFileRequest =
            new DeleteFileRequest(bucket, Arrays.asList(filePath), generateAuth("deleteFile", null));
        client.deleteFile(deleteFileRequest);
    }

    public void downloadByThrift(String filePath, File destFile) throws Exception {
        checkConnect();
        FileOutputStream fis = new FileOutputStream(destFile);
        FileChannel fc = fis.getChannel();
        long readSize = 0;
        boolean hasMore = true;
        final int M2 = 20 * 1024 * 1024;
        int nextOffset = 0;

        try {
            while (hasMore) {
                ReadFileRequest readFileRequest =
                    new ReadFileRequest(bucket, filePath, nextOffset, M2, generateAuth("readFile", null));
                ReadFileResponse response = client.readFile(readFileRequest);
                ByteBuffer bb = response.bufferForResult();
                fc.write(bb);
                nextOffset += response.size;
                hasMore = response.hasMore;
            }
        } finally {
            fc.close();
        }

        System.out.println("total read size : " + readSize);
    }

    public String generateUrl(String path) throws Exception {
        String url = "http://" + ip + ":" + downloadPort + "/generatePreSignedUrl";
        long timestamp = System.currentTimeMillis();
        int expires = 3600;
        String sign = signature(accessKey, accessSecret, timestamp, "readFile");
        Map<String, String> params = new HashMap<>();
        params.put("bucketName", bucket);
        params.put("filePath", path);
        params.put("expires", expires + "");
        params.put("accessKeyId", accessKey);
        params.put("signature", sign);
        params.put("timestamp", timestamp + "");
        params.put("stsToken", "");
        String response = HttpHelper.doGet(url, params, null);
        return response;
    }

    public String createFile(String file) throws Exception {
        checkConnect();
        CreateFileRequest createFileRequest = new CreateFileRequest(bucket, file, generateAuth("createFile", null));
        CreateFileResponse response = client.createFile(createFileRequest);
        return response.outputStreamId;
    }

    public long upload(String outputStreamId, String file, byte[] buffer, int length, CRC64 crc64, long destOffset)
        throws Exception {
        checkConnect();
        WriteFileRequest writeFileRequest =
            new WriteFileRequest(outputStreamId, bucket, file, ByteBuffer.wrap(buffer, 0, length), 0,
                length, destOffset,
                crc64.getValue(),
                generateAuth("writeFile", null));
        WriteFileResponse writeFileResponse = client.writeFile(writeFileRequest);
        long fileOffset = writeFileResponse.nextOffset;
        FlushFileRequest flushFileRequest =
            new FlushFileRequest(outputStreamId, bucket, file, fileOffset, generateAuth("flushFile", null));
        client.flushFile(flushFileRequest);
        return fileOffset;
    }

    public void completeUpload(String outputStreamId, String file) throws Exception {
        checkConnect();
        CompleteFileRequest completeFileRequest =
            new CompleteFileRequest(outputStreamId, bucket, file, generateAuth("completeFile", null));
        client.completeFile(completeFileRequest);
    }

    public void uploadFile(String file) throws Exception {
        checkConnect();
        String outputStreamId = createFile(file);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        byte[] bytes = new byte[1024 * 1024 * 5];
        int len = 0;
        long fileOffset = 0;
        CRC64 crc64 = new CRC64();
        while ((len = bis.read(bytes)) != -1) {
            CRC64 tmpCrc = new CRC64(crc64.getValue());
            tmpCrc.update(bytes, 0, len);
            fileOffset = upload(outputStreamId, file, bytes, len, tmpCrc, fileOffset);
            crc64 = tmpCrc;
        }
//        completeUpload(outputStreamId, file);
    }

}
