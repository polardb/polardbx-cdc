/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
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
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Random;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_BASE_PATH;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_SCHEMA_OBJECT_SWITCH;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;

/**
 * created by ziyang.lb
 **/
public class CdcSchemaStoreProvider implements SchemaObjectStoreProvider {
    private final static String SWITCH_ON = "ON";
    private final static String SWITCH_OFF = "OFF";
    private final static String SWITCH_RANDOM = "RANDOM";//for test
    private final static CdcSchemaStoreProvider INSTANCE = new CdcSchemaStoreProvider();
    private final RepoUnit repoUnit;

    private CdcSchemaStoreProvider() {
        this.repoUnit = buildRepoUnit();
    }

    public static CdcSchemaStoreProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public SchemaObjectStore get() {
        if (shouldPersist()) {
            return new PersistentSchemaStore(repoUnit);
        } else {
            return new SchemaObjectStoreInMemory();
        }
    }

    private boolean shouldPersist() {
        String sw = DynamicApplicationConfig.getString(META_PERSIST_SCHEMA_OBJECT_SWITCH);
        if (SWITCH_ON.equals(sw)) {
            return true;
        } else if (SWITCH_OFF.equals(sw)) {
            return false;
        } else if (SWITCH_RANDOM.equals(sw)) {
            return new Random().nextBoolean();
        } else {
            throw new PolardbxException("invalid schema object persist switch : " + sw);
        }
    }

    private RepoUnit buildRepoUnit() {
        String sw = DynamicApplicationConfig.getString(META_PERSIST_SCHEMA_OBJECT_SWITCH);
        if (SWITCH_OFF.equals(sw)) {
            return null;
        }

        try {
            String basePath = DynamicApplicationConfig.getString(META_PERSIST_BASE_PATH);
            if (!basePath.endsWith("/")) {
                basePath = basePath + "/";
            }

            FileUtils.forceMkdir(new File(basePath));
            FileUtils.cleanDirectory(new File(basePath));

            String taskName = DynamicApplicationConfig.getString(TASK_NAME);
            RepoUnit repoUnit = new RepoUnit(basePath + taskName, true, false);
            repoUnit.open();
            return repoUnit;
        } catch (Throwable t) {
            throw new PolardbxException("build repo unit failed", t);
        }
    }
}
