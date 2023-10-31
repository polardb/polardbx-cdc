/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private final RepoUnit repoUnit;

    private CdcSchemaStoreProvider() {
        this.repoUnit = buildRepoUnit();
    }

    public static CdcSchemaStoreProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public SchemaObjectStore get() {
        if (getMetaPersistEnabled()) {
            return new PersistentSchemaStore(repoUnit);
        } else {
            return new SchemaObjectStoreInMemory();
        }
    }

    private RepoUnit buildRepoUnit() {
        if (!getMetaPersistEnabled()) {
            return null;
        }

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
