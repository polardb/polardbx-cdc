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
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author yudong
 * @since 2022/9/14
 **/
@Slf4j
public class OssBinlogFileReadChannel extends AbstractBinlogFileReadChannel {
    private final OSS ossClient;
    private final String bucket;
    private final String fileName;
    private OSSObject ossObject;

    public OssBinlogFileReadChannel(OSS ossClient, String bucket, String fileName) {
        this.ossClient = ossClient;
        this.bucket = bucket;
        this.fileName = fileName;
    }

    @Override
    protected void getRange(long startPosition) {
        if (startPosition < 0) {
            throw new IllegalArgumentException("invalid argument, start pos:" + startPosition);
        }

        ossObject = getRangeHelper(startPosition);
        String contentRange = ossObject.getResponse().getHeaders().get("Content-Range");
        fileSize = getFileSize(contentRange);
        if (fileSize <= startPosition) {
            throw new IllegalArgumentException("file size:" + fileSize
                + " is smaller than start pos:" + startPosition);
        }
        inputStream = ossObject.getObjectContent();
        readBuffer = new RemoteBinlogFileReadBuffer(inputStream);
        position = startPosition;
    }

    @Override
    public void implCloseChannel() {
        try {
            if (ossObject != null) {
                ossObject.forcedClose();
            }
        } catch (IOException e) {
            log.error("close channel of file {} error", fileName, e);
            throw new RuntimeException(e);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private OSSObject getRangeHelper(long position) {
        GetObjectRequest request = new GetObjectRequest(bucket, fileName);
        String range = "bytes=" + position + "-";
        request.addHeader("Range", range);
        return ossClient.getObject(request);
    }

    private long getFileSize(String contentRange) {
        if (contentRange == null) {
            return -1;
        }
        String[] strs = contentRange.split(" ");
        if (strs.length != 2 || !"bytes".equalsIgnoreCase(strs[0])) {
            log.error("unexpected content range str: {} ", Arrays.toString(strs));
            throw new RuntimeException("unexpected oss content range str");
        }
        String[] rangeAndSize = strs[1].split("/");
        return Integer.parseInt(rangeAndSize[1]);
    }
}
