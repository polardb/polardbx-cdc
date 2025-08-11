/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.ConfigKeys;

import java.io.File;

import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_CLEAN_WORKER_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_BASE_PATH;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_DELETE_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_NEW_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_TXNITEM_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_TXN_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_UNIT_COUNT;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getDouble;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * created by ziyang.lb
 **/
public class StorageFactory {

    public static Storage createStorage(String identifier, boolean oneStoragePerDn) {
        return buildStorage(identifier, oneStoragePerDn);
    }

    private static Storage buildStorage(String identifier, boolean oneStoragePerDn) {
        int repoUnitCount = oneStoragePerDn ? 1 : getInt(STORAGE_PERSIST_UNIT_COUNT);
        int cleanWorkerCount = oneStoragePerDn ? 1 : getInt(STORAGE_CLEAN_WORKER_COUNT);
        String persistPath = getString(STORAGE_PERSIST_BASE_PATH) + File.pathSeparator +
            getString(ConfigKeys.TASK_NAME) + File.pathSeparator + identifier;

        Repository repository = new Repository(getBoolean(STORAGE_PERSIST_ENABLE),
            persistPath,
            PersistMode.valueOf(getString(STORAGE_PERSIST_MODE)),
            getDouble(STORAGE_PERSIST_NEW_THRESHOLD),
            getInt(STORAGE_PERSIST_TXN_THRESHOLD),
            getInt(STORAGE_PERSIST_TXNITEM_THRESHOLD),
            DeleteMode.valueOf(getString(STORAGE_PERSIST_DELETE_MODE)),
            repoUnitCount);
        return new LogEventStorage(identifier, repository, cleanWorkerCount);
    }
}
