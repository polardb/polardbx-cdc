/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.extern.slf4j.Slf4j;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_TRANSACTION_COMPRESSION_MAX_UNCOMPRESSED_SIZE;

/**
 * 由于大事务较少，不可能每个BatchEvent都给分配大内存，因此采用动态扩容的形式
 * 目前只有在压缩事务时可能会触发expand，其他行为应和ByteArray一致
 *
 * @author zm
 */
@Slf4j
public class AutoExpandByteArray extends ByteArray {
    private int maxExpandLength;

    public AutoExpandByteArray(byte[] data) {
        super(data);
        maxExpandLength = DynamicApplicationConfig.getInt(BINLOG_TRANSACTION_COMPRESSION_MAX_UNCOMPRESSED_SIZE);
    }

    /**
     * 在write超出限制时进行扩容
     */
    @Override
    public void write(byte b) {
        try {
            super.write(b);
        } catch (IllegalArgumentException e) {
            if (getPos() >= getLimit()) {
                expand();
                super.write(b);
            }
        }
    }

    /**
     * 每次扩容将容量翻倍
     */
    private void expand() {
        int targetLength = data.length << 1;
        if (targetLength > maxExpandLength) {
            throw new RuntimeException("Expand byte array exceed the maxExpandLength.");
        }
        log.info("start to expand byte array from {} to {}", data.length, targetLength);
        byte[] newData = new byte[targetLength];
        System.arraycopy(data, 0, newData, 0, data.length);
        this.data = newData;
        this.limit = data.length;
    }

}
