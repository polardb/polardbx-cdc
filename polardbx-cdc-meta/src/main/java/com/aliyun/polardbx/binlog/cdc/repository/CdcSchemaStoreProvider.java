/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.repository;

import com.alibaba.polardbx.druid.sql.repository.SchemaObjectStore;
import com.alibaba.polardbx.druid.sql.repository.SchemaObjectStoreInMemory;
import com.alibaba.polardbx.druid.sql.repository.SchemaObjectStoreProvider;
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import org.apache.commons.io.FileUtils;

import java.io.File;

import static com.aliyun.polardbx.binlog.ConfigKeys.IS_REPLICA;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_BASE_PATH;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PERSIST_SCHEMA_META_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;

/**
 * created by ziyang.lb
 **/
public class CdcSchemaStoreProvider implements SchemaObjectStoreProvider {

    private static final CdcSchemaStoreProvider INSTANCE = new CdcSchemaStoreProvider();
    private volatile RepoUnit repoUnit;

    private CdcSchemaStoreProvider() {
    }

    public static CdcSchemaStoreProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public SchemaObjectStore get() {
        if (getMetaPersistEnabled()) {
            return new PersistentSchemaStore(getRepoUnit());
        } else {
            return new SchemaObjectStoreInMemory();
        }
    }

    private RepoUnit getRepoUnit() {
        if (repoUnit != null) {
            return repoUnit;
        }
        synchronized (this) {
            if (repoUnit == null) {
                repoUnit = buildRepoUnit();
            }
        }
        return repoUnit;
    }

    private RepoUnit buildRepoUnit() {
        try {
            String basePath = DynamicApplicationConfig.getString(META_PERSIST_BASE_PATH);
            if (!basePath.endsWith("/")) {
                basePath = basePath + "/";
            }

            String taskName = DynamicApplicationConfig.getString(TASK_NAME);
            String taskPath = basePath + DynamicApplicationConfig.getClusterType() + "/" + taskName;

            FileUtils.forceMkdir(new File(taskPath));
            FileUtils.cleanDirectory(new File(taskPath));

            RepoUnit repoUnit = new RepoUnit(taskPath, true, false, true);
            repoUnit.open();
            return repoUnit;
        } catch (Throwable t) {
            throw new PolardbxException("build repo unit failed", t);
        }
    }

    private boolean getMetaPersistEnabled() {
        if (CommonConstants.TRUE.equals(System.getProperty(IS_REPLICA))) {
            return DynamicApplicationConfig.getBoolean(RPL_PERSIST_SCHEMA_META_ENABLED);
        } else {
            return DynamicApplicationConfig.getBoolean(META_PERSIST_ENABLED);
        }
    }
}
