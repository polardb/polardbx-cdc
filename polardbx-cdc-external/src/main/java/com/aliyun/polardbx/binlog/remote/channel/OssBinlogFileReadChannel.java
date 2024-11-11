/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.channel;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
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
            throw new IllegalArgumentException("invalid start position:" + startPosition);
        }

        ossObject = getRangeHelper(startPosition);
        String contentRange = ossObject.getResponse().getHeaders().get("Content-Range");
        fileSize = getFileSize(contentRange);
        if (fileSize <= startPosition) {
            throw new IllegalArgumentException("oss file size:" + fileSize
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
            throw new OSSException("Close oss channel error!");
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

        try {
            return ossClient.getObject(request);
        } catch (Exception ossException) {
            log.error("get oss range with position:{} error", position, ossException);
            throw new OSSException("Get file content from oss error!");
        }
    }

    private long getFileSize(String contentRange) {
        if (contentRange == null) {
            return -1;
        }
        String[] strs = contentRange.split(" ");
        if (strs.length != 2 || !"bytes".equalsIgnoreCase(strs[0])) {
            log.error("unexpected content range str: {} ", Arrays.toString(strs));
            throw new OSSException("Get file size from oss error!");
        }
        String[] rangeAndSize = strs[1].split("/");
        return Integer.parseInt(rangeAndSize[1]);
    }
}
