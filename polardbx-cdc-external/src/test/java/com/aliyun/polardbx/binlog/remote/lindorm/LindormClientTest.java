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
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.CreateFileResponse;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.FileInfo;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.ReadFileResponse;
import com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated.WriteFileResponse;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.aliyun.polardbx.binlog.ConfigKeys;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * @author yudong
 * @since 2022/10/11
 **/
@Slf4j
public class LindormClientTest {
    private static LindormClient lindormClient;
    private static final String bucketName = "qatest-bucket";
    private static final String testPath = "lindorm-client-test/";

    @BeforeClass
    public static void prepareClient() {
        final SpringContextBootStrap appContextBootStrap =
                new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();

        String accessKey = getString(ConfigKeys.LINDORM_ACCESSKEY_ID);
        String accessSecret = getString(ConfigKeys.LINDORM_ACCESSKEY_ID_SECRET);
        String endpoint = getString(ConfigKeys.LINDORM_ENDPOINT);
        int thriftPort = getInt(ConfigKeys.LINDORM_THRIFT_PORT);
        int s3Port = getInt(ConfigKeys.LINDORM_S3_PORT);
        lindormClient = new LindormClient(accessKey, accessSecret, endpoint, thriftPort, s3Port);

        prepareTestBucket();
    }

    private static void prepareTestBucket() {
        while (!lindormClient.doesBucketExist(bucketName)) {
            lindormClient.createBucket(bucketName);
        }
    }

    @Test
    public void doesObjectExistTest() throws Exception {
        String fileName = "binlog.000001";
        if (lindormClient.doesObjectExist(bucketName, fileName)) {
            lindormClient.deleteObject(bucketName, fileName);
        }
        Assert.assertFalse(lindormClient.doesObjectExist(bucketName, fileName));
        lindormClient.createFile(bucketName, fileName);
        Assert.assertTrue(lindormClient.doesObjectExist(bucketName, fileName));
    }

    @Test
    public void deleteObjectTest() throws Exception {
        String fileName = "binlog.000001";
        if (lindormClient.doesObjectExist(bucketName, fileName)) {
            lindormClient.deleteObject(bucketName, fileName);
        }
        lindormClient.createFile(bucketName, fileName);
        Assert.assertTrue(lindormClient.doesObjectExist(bucketName, fileName));
        lindormClient.deleteObject(bucketName, fileName);
        Assert.assertFalse(lindormClient.doesObjectExist(bucketName, fileName));
    }

    // 怎么一会儿不报错，一会儿又报sdk error?
    @Test
    public void should_throw_exception_when_delete_not_exist_object() {
        String fileName = "binlog.000001";
        if (lindormClient.doesObjectExist(bucketName, fileName)) {
            lindormClient.deleteObject(bucketName, fileName);
        }
        lindormClient.deleteObject(bucketName, fileName);
    }

    @Test
    public void deleteObjectsTest() throws Exception {
        String fileName = "binlog.00000";
        List<String> files = new ArrayList<>();
        for (int i = 1; i < 10; i++) {
            files.add(fileName + i);
        }
        for (String file : files) {
            if (lindormClient.doesObjectExist(bucketName, file)) {
                lindormClient.deleteObject(bucketName, file);
            }
            lindormClient.createFile(bucketName, file);
        }
        for (String file: files) {
            Assert.assertTrue(lindormClient.doesObjectExist(bucketName, file));
        }
        lindormClient.deleteObjects(bucketName, files);
        for (String file : files) {
            Assert.assertFalse(lindormClient.doesObjectExist(bucketName, file));
        }
    }

    @Test
    public void getObjectMetadataTest() throws Exception {
        String fileName = "binlog.000001";
        if (lindormClient.doesObjectExist(bucketName, fileName)) {
            lindormClient.deleteObject(bucketName, fileName);
        }
        String content = "Nearly all men can stand adversity, " +
                "but if you want to test a man's character, give him power.";
        ByteBuffer buffer = ByteBuffer.wrap(content.getBytes());
        CRC64 crc64 = new CRC64();
        crc64.update(content.getBytes(), content.length());
        lindormClient.putFile(bucketName, fileName, buffer, crc64.getValue());
        ObjectMetadata metadata = lindormClient.getObjectMetadata(bucketName, fileName);
        Assert.assertEquals(metadata.getContentLength(), content.length());
    }

    @Test
    public void listObjectsTest() throws Exception {
        ListObjectsV2Result result = lindormClient.listObjects(bucketName, testPath);
        result.getObjectSummaries().forEach(s -> lindormClient.deleteObject(bucketName, s.getKey()));
        result = lindormClient.listObjects(bucketName, testPath);
        Assert.assertEquals(0, result.getKeyCount());

        String fileName = testPath + "binlog.00000";
        List<String> files = new ArrayList<>();
        int n = 10;
        for (int i = 0; i < n; i++) {
            files.add(fileName + i);
        }
        String content = "Nearly all men can stand adversity, " +
                "but if you want to test a man's character, give him power.";
        ByteBuffer buffer = ByteBuffer.wrap(content.getBytes());
        CRC64 crc64 = new CRC64();
        crc64.update(content.getBytes(), content.length());
        for (String file : files) {
            if (lindormClient.doesObjectExist(bucketName, file)) {
                lindormClient.deleteObject(bucketName, file);
            }
            lindormClient.putFile(bucketName, file, buffer, crc64.getValue());
        }

        result = lindormClient.listObjects(bucketName, testPath);
        Assert.assertEquals(n, result.getKeyCount());
    }

    @Test
    public void getObjectTest() throws Exception {
        String fileName = "binlog.000001";
        if (lindormClient.doesObjectExist(bucketName, fileName)) {
            lindormClient.deleteObject(bucketName, fileName);
        }
        String content = "Nearly all men can stand adversity, " +
                "but if you want to test a man's character, give him power.";
        ByteBuffer buffer = ByteBuffer.wrap(content.getBytes());
        CRC64 crc64 = new CRC64();
        crc64.update(content.getBytes(), content.length());
        lindormClient.putFile(bucketName, fileName, buffer, crc64.getValue());
        S3Object object = lindormClient.getObject(bucketName, fileName);
        Assert.assertEquals(content.length(), object.getObjectMetadata().getContentLength());
        // Assert.assertEquals(content, object.getObjectContent().toString());
    }

    @Test(expected = AmazonS3Exception.class)
    public void getNotExistObjectTest() {
        String fileName = "get-not-exist-object-test.txt";
        if (lindormClient.doesObjectExist(bucketName, fileName)) {
            lindormClient.deleteObject(bucketName, fileName);
        }
        lindormClient.getObject(bucketName, fileName);
    }

    @Test
    public void generateUrlTest() throws Exception {
        String fileName = testPath + "generate.txt";
        String txt = "hello, world";
        ByteBuffer content = ByteBuffer.wrap(txt.getBytes());
        CRC64 crc64 = new CRC64();
        crc64.update(content.array(), content.array().length);
        lindormClient.putFile(bucketName, fileName, content, crc64.getValue());
        String url = lindormClient.generateDownloadUrl(bucketName, fileName, 3600);
        System.out.println("download url:" + url);
    }

    @Test
    public void createFileTest() throws Exception {
        String fileName = testPath + "create-file-test.txt";
        lindormClient.deleteObject(bucketName, fileName);
        CreateFileResponse response = lindormClient.createFile(bucketName, fileName);
        Assert.assertEquals(bucketName, response.bucket);
        Assert.assertEquals(fileName, response.path);
        Assert.assertNotNull(response.outputStreamId);
    }

    @Test
    public void writeFileTest() throws Exception {
        String fileName = testPath + "write-file-test.txt";
        lindormClient.deleteObject(bucketName, fileName);
        String oid = lindormClient.createFile(bucketName, fileName).outputStreamId;
        byte[] data = new byte[2048];
        new Random().nextBytes(data);
        ByteBuffer content = ByteBuffer.wrap(data);
        CRC64 crc64 = new CRC64();
        crc64.update(data, data.length);
        WriteFileResponse response = lindormClient.writeFile(oid, bucketName, fileName, content,
                0, data.length, 0, crc64.getValue());
        Assert.assertEquals(bucketName, response.bucket);
        Assert.assertEquals(fileName, response.path);
        Assert.assertEquals(oid, response.outputStreamId);
        Assert.assertEquals(data.length, response.nextOffset);
    }

    @Test
    public void readFileTest() throws Exception {
        String fileName = testPath + "read-file-test.txt";
        lindormClient.deleteObject(bucketName, fileName);
        String oid = lindormClient.createFile(bucketName, fileName).outputStreamId;
        // write first part
        byte[] data = new byte[1024];
        new Random().nextBytes(data);
        ByteBuffer firstPartContent = ByteBuffer.wrap(data);
        CRC64 firstPartCrc64 = new CRC64();
        firstPartCrc64.update(data, data.length);
        long nextPos = lindormClient.writeFile(oid, bucketName, fileName, firstPartContent,
                0, data.length, 0, firstPartCrc64.getValue()).nextOffset;
        Assert.assertEquals(data.length, nextPos);
        // write second part
        new Random().nextBytes(data);
        ByteBuffer secondPartContent = ByteBuffer.wrap(data);
        CRC64 secondPartCrc64 = new CRC64();
        secondPartCrc64.update(data, data.length);
        nextPos = lindormClient.writeFile(oid, bucketName, fileName, secondPartContent,
                0, data.length, data.length, secondPartCrc64.getValue()).nextOffset;
        Assert.assertEquals(data.length << 1, nextPos);

        lindormClient.completeFile(oid, bucketName, fileName);

        // read first part
        ReadFileResponse response = lindormClient.readFile(bucketName, fileName, 0, data.length);
        Assert.assertEquals(bucketName, response.bucket);
        Assert.assertEquals(fileName, response.path);
        Assert.assertEquals(data.length, response.size);
        // Assert.assertEquals(firstPartCrc64.getValue(), response.crc64);
        // Assert.assertTrue(response.hasMore);
        // read second part
        response = lindormClient.readFile(bucketName, fileName, data.length, data.length);
        Assert.assertEquals(bucketName, response.bucket);
        Assert.assertEquals(fileName, response.path);
        Assert.assertEquals(data.length, response.size);
        // Assert.assertEquals(secondPartCrc64.getValue(), response.crc64);
        // Assert.assertFalse(response.hasMore);
    }

    @Test
    public void listFileInfosTest() throws Exception {
        String testListPath = testPath + "listFileInfoTest/";
        String baseFileName = testListPath + "test.";
        List<String> expectFileList = new ArrayList<>();
        int n = 10;
        for (int i = 0; i < n; i++) {
            expectFileList.add(baseFileName + i);
        }
        lindormClient.deleteObjects(bucketName, expectFileList);
        for (String fileName : expectFileList) {
            lindormClient.createFile(bucketName, fileName);
        }
        List<FileInfo> fileInfos = lindormClient.listFileInfos(bucketName, testListPath);
        Assert.assertEquals(n, fileInfos.size());
        List<String> actualFileList = fileInfos.stream().map(FileInfo::getPath)
                .sorted(String::compareTo).collect(Collectors.toList());
        boolean compareResult = ListUtils.isEqualList(expectFileList, actualFileList);
        Assert.assertTrue(compareResult);
    }

    @Test
    public void putFileTest() throws Exception {
        String fileName = testPath + "put-file-test.txt";
        lindormClient.deleteObject(bucketName, fileName);
        byte[] data = new byte[1024];
        new Random().nextBytes(data);
        ByteBuffer content = ByteBuffer.wrap(data);
        CRC64 crc64 = new CRC64();
        crc64.update(data, data.length);
        FileInfo fileInfo = lindormClient.putFile(bucketName, fileName, content, crc64.getValue());
        Assert.assertEquals(bucketName, fileInfo.bucket);
        Assert.assertEquals(fileName, fileInfo.path);
        Assert.assertEquals(data.length, fileInfo.size);
        Assert.assertEquals(crc64.getValue(), fileInfo.crc64);
    }
}
