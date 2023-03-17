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
