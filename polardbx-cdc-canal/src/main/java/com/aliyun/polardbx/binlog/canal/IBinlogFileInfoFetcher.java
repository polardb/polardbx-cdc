/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import java.io.IOException;

/**
 * Binlog文件大小查询器
 * Created by ziyang.lb
 */
public interface IBinlogFileInfoFetcher {
    /**
     * 获取指定binlog文件的文件大小
     */
    long fetch(String fileName) throws IOException;

    /**
     * 查看指定的binlog文件是否存在
     */
    boolean isFileExisting(String fileName) throws IOException;
}
