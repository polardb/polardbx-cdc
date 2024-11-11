/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.storage;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import org.apache.commons.io.FileUtils;
import org.rocksdb.RocksDB;

import java.io.File;
import java.io.IOException;

/**
 * created by ziyang.lb
 **/
public class RplStorage {
    private static volatile RepoUnit REPO_UNIT;
    private static final String TASK_NAME = DynamicApplicationConfig.getString(ConfigKeys.TASK_NAME);
    private static final String BASE_PATH = DynamicApplicationConfig.getString(ConfigKeys.RPL_PERSIST_BASE_PATH);
    private static final String TASK_PATH = BASE_PATH + TASK_NAME + "/";
    private static final String ROCKSDB_LIB_PATH = System.getProperty("java.io.tmpdir");

    public static void init() throws IOException {
        clearTempLibFiles();
        FileUtils.forceMkdir(new File(TASK_PATH));
        FileUtils.forceMkdir(new File(ROCKSDB_LIB_PATH));
        FileUtils.cleanDirectory(new File(TASK_PATH));
        RocksDB.loadLibrary();
    }

    public static RepoUnit getRepoUnit() {
        if (REPO_UNIT == null) {
            synchronized (RplStorage.class) {
                if (REPO_UNIT == null) {
                    try {
                        RepoUnit repoUnit = new RepoUnit(TASK_PATH, true, false, true);
                        repoUnit.open();
                        REPO_UNIT = repoUnit;
                    } catch (Throwable t) {
                        throw new PolardbxException("build repo unit failed", t);
                    }
                }
            }
        }
        return REPO_UNIT;
    }

    // RocksDB会在临时目录生成临时的lib文件，当通过kill命令的方式终止进程时，临时文件可以被释放掉
    // 但通过kill -9命令的方式终止进程时，临时文件不会被释放掉，此处做一下手动清理
    private static void clearTempLibFiles() {
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
