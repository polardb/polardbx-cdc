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
package com.aliyun.polardbx.binlog.remote.channel;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.AppendObjectRequest;
import com.aliyun.oss.model.AppendObjectResult;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;
import java.util.zip.CRC32;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * @author yudong
 * @since 2022/9/30
 **/
@Slf4j
public class OssBinlogFileReadChannelTest {
    private static OSS ossClient;
    private static String bucket;
    private static final String fileName = "binlog.000001";
    private static OssBinlogFileReadChannel ossChannel;
    private static FileChannel localChannel;

    @BeforeClass
    public static void prepare() throws IOException {
        // prepare oss client
        final SpringContextBootStrap appContextBootStrap =
            new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();

        bucket = getString(ConfigKeys.OSS_BUCKET);
        String accessKeySecret = getString(ConfigKeys.OSS_ACCESSKEY_ID_SECRET);
        String accessKeyId = getString(ConfigKeys.OSS_ACCESSKEY_ID);
        String endPoint = getString(ConfigKeys.OSS_ENDPOINT);
        ossClient = new OSSClientBuilder().build(endPoint, accessKeyId, accessKeySecret);
        boolean exist = ossClient.doesBucketExist(bucket);
        Assert.assertTrue(exist);

        prepareTestFile();
    }

    @AfterClass
    public static void cleanUp() {
        ossClient.deleteObject(bucket, fileName);
        new File(fileName).delete();
    }

    private static void prepareTestFile() throws IOException {
        if (ossClient.doesObjectExist(bucket, fileName)) {
            ossClient.deleteObject(bucket, fileName);
        }
        File localFile = new File(fileName);
        if (localFile.exists()) {
            localFile.delete();
        }
        localFile.createNewFile();

        FileOutputStream outputStream = new FileOutputStream(localFile, true);

        byte[] buffer = new byte[1024];
        // file size : 1K ~ 1M bytes
        int n = new Random().nextInt(1024) + 1;
        long nextPosition = 0L;
        for (int i = 0; i < n; i++) {
            new Random().nextBytes(buffer);
            outputStream.write(buffer);
            AppendObjectRequest request = new AppendObjectRequest(bucket, fileName,
                new ByteArrayInputStream(buffer));
            request.setPosition(nextPosition);
            AppendObjectResult result = ossClient.appendObject(request);
            nextPosition = result.getNextPosition();
        }

        int extra = new Random().nextInt(1024) + 1;
        byte[] extraBytes = new byte[extra];
        new Random().nextBytes(extraBytes);
        outputStream.write(extraBytes);
        outputStream.close();
        AppendObjectRequest request = new AppendObjectRequest(bucket, fileName,
            new ByteArrayInputStream(extraBytes));
        request.setPosition(nextPosition);
        AppendObjectResult result = ossClient.appendObject(request);
        result.getNextPosition();

        ossChannel = new OssBinlogFileReadChannel(ossClient, bucket, fileName);
        FileInputStream is = new FileInputStream(fileName);
        localChannel = is.getChannel();
    }

    @Test
    public void testSize() throws IOException {
        long actual = ossChannel.size();
        long expect = localChannel.size();
        Assert.assertEquals(expect, actual);
    }

    @Test
    public void testPosition() throws IOException {
        long fileSize = localChannel.size();
        long pos = (long) (Math.random() * fileSize);

        ossChannel.position(pos);
        localChannel.position(pos);
        Assert.assertEquals(localChannel.position(), ossChannel.position());
        ByteBuffer buffer1 = ByteBuffer.allocate(1024);
        ByteBuffer buffer2 = ByteBuffer.allocate(1024);
        int readLen1;
        int readLen2;
        CRC32 localCrc = new CRC32();
        CRC32 ossCrc = new CRC32();
        while ((readLen1 = localChannel.read(buffer1)) > 0) {
            readLen2 = ossChannel.read(buffer2);
            Assert.assertEquals(readLen1, readLen2);
            buffer1.flip();
            buffer2.flip();
            localCrc.update(buffer1.array(), 0, buffer1.limit());
            ossCrc.update(buffer2.array(), 0, buffer2.limit());
            Assert.assertEquals(localCrc.getValue(), ossCrc.getValue());
        }
        Assert.assertEquals(-1, ossChannel.read(buffer2));
    }

    @Test
    public void testReadWithPos() throws IOException {
        // 测试从某个指定的位置读取一次

        long fileSize = localChannel.size();
        ByteBuffer buffer1 = ByteBuffer.allocate(1024);
        ByteBuffer buffer2 = ByteBuffer.allocate(1024);
        int readLen1;
        int readLen2;
        CRC32 localCrc = new CRC32();
        CRC32 ossCrc = new CRC32();

        // 随机测试100次
        for (int i = 0; i < 100; i++) {
            long pos = (long) (Math.random() * fileSize);
            readLen1 = localChannel.read(buffer1, pos);
            readLen2 = ossChannel.read(buffer2, pos);
            Assert.assertEquals(readLen1, readLen2);
            buffer1.flip();
            buffer2.flip();
            localCrc.update(buffer1.array(), 0, buffer1.limit());
            ossCrc.update(buffer2.array(), 0, buffer2.limit());
            Assert.assertEquals(localCrc.getValue(), ossCrc.getValue());
        }
    }

    @Test
    public void testRead() throws IOException {
        // 测试从头至尾读取一个文件

        localChannel.position(4L);
        ossChannel.position(4L);
        ByteBuffer buffer1 = ByteBuffer.allocate(1024);
        ByteBuffer buffer2 = ByteBuffer.allocate(1024);
        int readLen1;
        int readLen2;
        CRC32 localCrc = new CRC32();
        CRC32 ossCrc = new CRC32();
        while ((readLen1 = localChannel.read(buffer1)) > 0) {
            readLen2 = ossChannel.read(buffer2);
            Assert.assertEquals(readLen1, readLen2);
            buffer1.flip();
            buffer2.flip();
            localCrc.update(buffer1.array(), 0, buffer1.limit());
            ossCrc.update(buffer2.array(), 0, buffer2.limit());
            Assert.assertEquals(localCrc.getValue(), ossCrc.getValue());
        }
        Assert.assertEquals(-1, ossChannel.read(buffer2));
    }

    @Test
    public void testReadWithArbitrarySizeBuffer() throws IOException {
        // 随机测试100次
        for (int i = 0; i < 100; i++) {
            int bufferSize = 1024 + new Random().nextInt(1024);
            localChannel.position(4L);
            ossChannel.position(4L);
            ByteBuffer buffer1 = ByteBuffer.allocate(bufferSize);
            ByteBuffer buffer2 = ByteBuffer.allocate(bufferSize);
            int readLen1;
            int readLen2;
            CRC32 localCrc = new CRC32();
            CRC32 ossCrc = new CRC32();
            while ((readLen1 = localChannel.read(buffer1)) > 0) {
                readLen2 = ossChannel.read(buffer2);
                Assert.assertEquals(readLen1, readLen2);
                buffer1.flip();
                buffer2.flip();
                localCrc.update(buffer1.array(), 0, buffer1.limit());
                ossCrc.update(buffer2.array(), 0, buffer2.limit());
                Assert.assertEquals(localCrc.getValue(), ossCrc.getValue());
            }
            Assert.assertEquals(-1, ossChannel.read(buffer2));
        }
    }
}
