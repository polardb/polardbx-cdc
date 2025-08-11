/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.channel;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.IOException;

@Slf4j
public class S3BinlogFileReadChannel extends AbstractBinlogFileReadChannel {
    private final S3Client s3;
    private final String bucket;
    private final String fileName;

    public S3BinlogFileReadChannel(S3Client s3, String bucket, String fileName) {
        this.s3 = s3;
        this.bucket = bucket;
        this.fileName = fileName;
    }

    /**
     * 给remote server发送请求，获得从start position开始的文件内容
     *
     * @param startPosition 文件开始位置
     */
    @Override
    public void getRange(long startPosition) {
        if (startPosition < 0) {
            throw new IllegalArgumentException("invalid start position:" + startPosition);
        }
        implCloseChannel();
        ResponseInputStream<GetObjectResponse> response = getRangeHelper(startPosition);
        fileSize = getFileSize();
        long contentLength = response.response().contentLength();
        log.info("get Range from {}, fileSize:{}, contentSize:{}", startPosition, fileSize, contentLength);

        if (fileSize <= startPosition) {
            throw new IllegalArgumentException("s3 file size:" + fileSize
                + " is smaller than start pos:" + startPosition);
        }

        inputStream = response;
        readBuffer = new RemoteBinlogFileReadBuffer(inputStream);
        position = startPosition;
    }

    /**
     * Closes this channel.
     *
     * <p> This method is invoked by the {@link #close close} method in order
     * to perform the actual work of closing the channel.  This method is only
     * invoked if the channel has not yet been closed, and it is never invoked
     * more than once.
     * </p>
     *
     * <p> An implementation of this method must arrange for any other thread
     * that is blocked in an I/O operation upon this channel to return
     * immediately, either by throwing an exception or by returning normally.
     * </p>
     *
     * @throws IOException If an I/O error occurs while closing the channel
     */
    @Override
    public void implCloseChannel() {
        if (inputStream != null) {
            ((ResponseInputStream) inputStream).abort();
        }
        inputStream = null;
    }

    private ResponseInputStream<GetObjectResponse> getRangeHelper(long position) {
        GetObjectRequest request =
            GetObjectRequest.builder().bucket(bucket).key(fileName).range(String.format("bytes=%s-", position)).build();
        return s3.getObject(request);
    }

    private long getFileSize() {
        HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket).key(fileName).build();
        HeadObjectResponse response = s3.headObject(request);
        return response.contentLength();
    }
}
