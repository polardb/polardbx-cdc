/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import org.apache.commons.io.FileUtils;

import java.io.File;

public class RocksDBUtil {
    public static final String ROCKSDB_LIB_PATH = System.getProperty("java.io.tmpdir");

    // RocksDB会在临时目录生成临时的lib文件，当通过kill命令的方式终止进程时，临时文件可以被释放掉
    // 但通过kill -9命令的方式终止进程时，临时文件不会被释放掉，此处做一下手动清理
    public static void clearTempLibFiles() {
        File directory = new File(ROCKSDB_LIB_PATH);
        if (directory.exists()) {
            File[] files = directory.listFiles((dir, name) ->
                name.startsWith("librocksdbjni") && name.endsWith(".so")
            );

            if (files != null && files.length > 0) {
                for (File file : files) {
                    FileUtils.deleteQuietly(file);
                }
            }
        }
    }
}
