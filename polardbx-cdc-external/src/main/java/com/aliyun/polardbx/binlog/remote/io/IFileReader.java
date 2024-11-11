/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.io;

import java.io.IOException;

public interface IFileReader {

    /**
     * 返回该binlog文件的长度
     *
     * @return file length
     */
    long length();

    /**
     * 返回该binlog文件的文件名
     *
     * @return file name
     */
    String getName();

    /**
     * 将binlog文件的一部分内容读到buffer中
     *
     * @param buffer 缓冲区
     * @return 本地读取的字节数
     */
    int read(byte[] buffer) throws IOException;

    /**
     * 检查该binlog是否已经完成了所有的写入
     */
    boolean isComplete();

    /**
     * 关闭io流
     */
    void close();
}
