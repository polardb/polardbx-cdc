/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
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

import static com.aliyun.polardbx.binlog.util.RocksDBUtil.ROCKSDB_LIB_PATH;
import static com.aliyun.polardbx.binlog.util.RocksDBUtil.clearTempLibFiles;

/**
 * created by ziyang.lb
 **/
public class RplStorage {
    private static volatile RepoUnit REPO_UNIT;
    private static final String TASK_NAME = DynamicApplicationConfig.getString(ConfigKeys.TASK_NAME);
    private static final String BASE_PATH = DynamicApplicationConfig.getString(ConfigKeys.RPL_PERSIST_BASE_PATH);
    private static final String TASK_PATH = BASE_PATH + TASK_NAME + "/";

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

}
