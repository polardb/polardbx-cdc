/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.channel;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yudong
 * @since 2022/10/19
 **/
@Slf4j
public class LindormBinlogFileReadChannel extends AbstractBinlogFileReadChannel {
    private final AmazonS3 s3Client;
    private final String bucket;
    private final String fileName;

    public LindormBinlogFileReadChannel(AmazonS3 s3Client, String bucket, String fileName) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.fileName = fileName;
    }

    @Override
    public void implCloseChannel() {
        if (inputStream != null) {
            ((S3ObjectInputStream)inputStream).abort();
        }
        inputStream = null;
    }

    @Override
    protected void getRange(long startPosition) {
        if (startPosition < 0) {
            throw new IllegalArgumentException("invalid argument, start pos:" + startPosition);
        }

        S3Object object = getRangeHelper(startPosition);
        fileSize = object.getObjectMetadata().getInstanceLength();
        if (fileSize <= startPosition) {
            throw new IllegalArgumentException("file size:" + fileSize
                    + " is smaller than start pos:" + startPosition);
        }
        inputStream = object.getObjectContent();
        readBuffer = new RemoteBinlogFileReadBuffer(inputStream);
        position = startPosition;
    }

    private S3Object getRangeHelper(long startPosition) {
        GetObjectRequest req = new GetObjectRequest(bucket, fileName);
        req.setRange(startPosition);
        return s3Client.getObject(req);
    }
}
