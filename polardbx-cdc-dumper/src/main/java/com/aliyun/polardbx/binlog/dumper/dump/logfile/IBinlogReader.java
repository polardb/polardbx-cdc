/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.google.protobuf.ByteString;

/**
 * @author yudong
 * @since 2023/7/19 18:27
 **/
public interface IBinlogReader {
    /**
     * 初始化
     */
    void init();

    /**
     * 判断 binlog dump 请求是否合法
     */
    void valid();

    /**
     * 启动
     */
    void start();

    /**
     * 退出
     */
    void close();

    /**
     * 检查正在读取的文件的状态
     */
    boolean check();

    /**
     * 是否还有数据需要读取
     */
    boolean hasNext();

    /**
     * 返回下一个包
     */
    ByteString nextPack();
}
