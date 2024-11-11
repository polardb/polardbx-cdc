/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import java.io.File;

/**
 * binlog文件监听器
 *
 * @author chengjin
 */
public interface IBinlogListener {
    /**
     * 当binlog文件创建时，调用此方法
     *
     * @param file binlog文件
     */
    void onCreateFile(File file);

    /**
     * 当binlog文件发生rotate时，调用此方法
     *
     * @param currentFile 当前binlog文件
     * @param nextFile 下一个binlog文件
     */
    void onRotateFile(File currentFile, String nextFile);

    /**
     * 当binlog文件结束时，调用此方法
     *
     * @param file binlog文件
     * @param binlogEndInfo binlog文件结束信息
     */
    void onFinishFile(File file, BinlogEndInfo binlogEndInfo);

    /**
     * 当binlog文件被删除时，调用此方法
     *
     * @param file binlog文件
     */
    void onDeleteFile(File file);
}
