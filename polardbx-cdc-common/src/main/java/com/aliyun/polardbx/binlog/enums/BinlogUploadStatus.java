/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.enums;

/**
 * @author chengjin, yudong
 */
public enum BinlogUploadStatus {
    /**
     * 文件刚创建好，还没有进行上传
     */
    CREATE(0),
    /**
     * 文件正在上传中
     */
    UPLOADING(1),
    /**
     * 文件上传成功
     */
    SUCCESS(2),
    /**
     * 未使用远端存储，文件不进行上传
     */
    IGNORE(3);

    private final int value;

    BinlogUploadStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static BinlogUploadStatus fromValue(int value) {
        for (BinlogUploadStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("unexpected binlog upload status value:" + value);
    }

    public boolean uploadFinished() {
        return value == SUCCESS.value || value == IGNORE.value;
    }
}
