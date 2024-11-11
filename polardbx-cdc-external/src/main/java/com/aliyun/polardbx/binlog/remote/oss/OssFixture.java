/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.AppendObjectRequest;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.polardbx.binlog.remote.channel.OssBinlogFileReadChannel;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.Collections;

import static com.aliyun.polardbx.binlog.ConfigKeys.OSS_ACCESSKEY_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.OSS_ACCESSKEY_ID_SECRET;
import static com.aliyun.polardbx.binlog.ConfigKeys.OSS_BUCKET;
import static com.aliyun.polardbx.binlog.ConfigKeys.OSS_ENDPOINT;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * used for unit test
 *
 * @author yudong
 * @since 2023/8/25 18:21
 **/
public class OssFixture {
    private final OSS client;
    private final String bucket = getString(OSS_BUCKET);

    public OssFixture() {
        String endpoint = getString(OSS_ENDPOINT);
        String ak = getString(OSS_ACCESSKEY_ID);
        String sk = getString(OSS_ACCESSKEY_ID_SECRET);
        client = new OSSClientBuilder().build(endpoint, ak, sk);
    }

    @SneakyThrows
    public void read(String fileName, ByteBuffer buffer) {
        OSSObject object = client.getObject(new GetObjectRequest(bucket, fileName));
        InputStream inputStream = object.getObjectContent();
        inputStream.read(buffer.array());
    }

    @SneakyThrows
    public void write(String fileName, byte[] data) {
        InputStream inputStream = new ByteArrayInputStream(data);
        client.appendObject(new AppendObjectRequest(bucket, fileName, inputStream).withPosition(0L));
    }

    @SneakyThrows
    public void write(String fileName, FileInputStream inputStream) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, fileName, inputStream);
        client.putObject(putObjectRequest);
    }

    @SneakyThrows
    public void delete(String fileName) {
        client.deleteObjects(new DeleteObjectsRequest(bucket).withKeys(Collections.singletonList(fileName)));
    }

    @SneakyThrows
    public boolean exists(String fileName) {
        return client.doesObjectExist(bucket, fileName);
    }

    public Channel getChannel(String fileName) {
        return new OssBinlogFileReadChannel(client, bucket, fileName);
    }
}
