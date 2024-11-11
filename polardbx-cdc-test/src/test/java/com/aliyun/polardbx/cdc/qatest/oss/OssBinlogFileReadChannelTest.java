/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.remote.channel.OssBinlogFileReadChannel;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * @author yudong
 * @since 2022/9/30
 **/
@Slf4j
public class OssBinlogFileReadChannelTest extends BaseTest {
    private static OSS ossClient;
    private static String bucket;
    private static final String fileName = "binlog.000001";
    private static OssBinlogFileReadChannel ossChannel;
    private static FileChannel localChannel;

    @Before
    public void prepare() throws ExecutionException, RetryException {
        bucket = getString(ConfigKeys.OSS_BUCKET);
        String accessKeySecret = getString(ConfigKeys.OSS_ACCESSKEY_ID_SECRET);
        String accessKeyId = getString(ConfigKeys.OSS_ACCESSKEY_ID);
        String endPoint = getString(ConfigKeys.OSS_ENDPOINT);
        ossClient = new OSSClientBuilder().build(endPoint, accessKeyId, accessKeySecret);
        boolean exist = ossClient.doesBucketExist(bucket);
        Assert.assertTrue(exist);

        Retryer<Object> retryer = RetryerBuilder.newBuilder().retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(10)).build();
        retryer.call(() -> {
            prepareTestFile();
            return null;
        });
    }

    @AfterClass
    public static void cleanUp() {
        ossClient.deleteObject(bucket, fileName);
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
        localFile.deleteOnExit();

        FileOutputStream outputStream = new FileOutputStream(localFile, true);

        byte[] buffer = new byte[1024];
        // file size : 1K ~ 1M bytes
        int n = new Random().nextInt(1024) + 1;
        long nextPosition = 0L;
        for (int i = 0; i < n; i++) {
            new Random().nextBytes(buffer);
            outputStream.write(buffer);
        }
        int extra = new Random().nextInt(1024) + 1;
        byte[] extraBytes = new byte[extra];
        new Random().nextBytes(extraBytes);
        outputStream.write(extraBytes);
        outputStream.close();

        PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, fileName, localFile);
        ossClient.putObject(putObjectRequest);

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

        for (int i = 0; i < 10; i++) {
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
        for (int i = 0; i < 10; i++) {
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
