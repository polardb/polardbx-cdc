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
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;

import static com.aliyun.polardbx.binlog.ConfigKeys.MEM_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_CLEAN_WORKER_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_BASE_PATH;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_DELETE_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_NEW_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_TXNITEM_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_TXN_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_UNIT_COUNT;

/**
 * created by ziyang.lb
 **/
public class StorageFactory {
    private static final Storage INSTANCE = buildStorage();

    public static Storage getStorage() {
        return INSTANCE;
    }

    private static Storage buildStorage() {
        int memory = DynamicApplicationConfig.getInt(MEM_SIZE);
        int repoUnitCount = DynamicApplicationConfig.getInt(STORAGE_PERSIST_UNIT_COUNT);
        //内存小于15G时，repoUnitCount设定为1，保证rocksdb有足够内存空间，大于15G时用config文件默认配置
        if (memory < 15360) {
            repoUnitCount = 1;
        }
        return new LogEventStorage(new Repository(DynamicApplicationConfig.getBoolean(STORAGE_PERSIST_ENABLE),
            DynamicApplicationConfig.getString(STORAGE_PERSIST_BASE_PATH) + "/" + DynamicApplicationConfig
                .getString(ConfigKeys.TASK_NAME),
            PersistMode.valueOf(DynamicApplicationConfig.getString(STORAGE_PERSIST_MODE)),
            DynamicApplicationConfig.getDouble(STORAGE_PERSIST_NEW_THRESHOLD),
            DynamicApplicationConfig.getInt(STORAGE_PERSIST_TXN_THRESHOLD),
            DynamicApplicationConfig.getInt(STORAGE_PERSIST_TXNITEM_THRESHOLD),
            DeleteMode.valueOf(DynamicApplicationConfig.getString(STORAGE_PERSIST_DELETE_MODE)),
            repoUnitCount),
            DynamicApplicationConfig.getInt(STORAGE_CLEAN_WORKER_COUNT));

    }
}
