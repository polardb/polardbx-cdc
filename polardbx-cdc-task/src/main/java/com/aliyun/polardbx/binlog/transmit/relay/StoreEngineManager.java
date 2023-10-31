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
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.rocksdb.util.ByteUtil;

import java.io.File;
import java.util.Random;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_RELAY_ENGINE_TYPE;
import static com.aliyun.polardbx.binlog.Constants.RELAY_DATA_FORCE_CLEAN_FLAG;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class StoreEngineManager {
    private static final String ENGINE_META_PATH = "__meta__";
    private static final String MEAT_KEY_ENGINE_TYPE = "__meta_key_engine_type__";
    private static final String META_KEY_BASE_BATH = "__meta_key_base_path__";
    private static volatile EngineType ENGINE_TYPE;
    private static volatile RepoUnit META_REPO_UNIT;

    public static StoreEngine newInstance(String basePath, int streamSeq) {
        initMetRepoUnit(basePath);
        initEngineType();

        if (ENGINE_TYPE == EngineType.FILE) {
            return new RelayFileStoreEngine(META_REPO_UNIT, basePath + "/" + streamSeq, streamSeq);
        } else if (ENGINE_TYPE == EngineType.ROCKSDB) {
            return new RocksDBStoreEngine(META_REPO_UNIT, basePath + "/" + streamSeq, streamSeq);
        } else {
            throw new PolardbxException("invalid store engine type " + ENGINE_TYPE);
        }
    }

    @SneakyThrows
    public static synchronized void setForceCleanFlag() {
        String basePath = new String(META_REPO_UNIT.get(ByteUtil.bytes(META_KEY_BASE_BATH)));
        File flagFile = new File(basePath + "/" + RELAY_DATA_FORCE_CLEAN_FLAG);
        if (!flagFile.exists() && flagFile.createNewFile()) {
            log.info("flag file for force cleaning is created.");
        }
    }

    @SneakyThrows
    private static void initMetRepoUnit(String basePath) {
        if (META_REPO_UNIT == null) {
            synchronized (StoreEngineManager.class) {
                if (META_REPO_UNIT == null) {
                    RepoUnit repoUnit = new RepoUnit(basePath + "/" + ENGINE_META_PATH, false, false, false);
                    repoUnit.open();
                    repoUnit.put(ByteUtil.bytes(META_KEY_BASE_BATH), ByteUtil.bytes(basePath));
                    //进行完初始化操作之后，才能赋值，规避不安全的引用
                    META_REPO_UNIT = repoUnit;
                }
            }
        }
    }

    private static void initEngineType() {
        if (ENGINE_TYPE == null) {
            synchronized (StoreEngineManager.class) {
                if (ENGINE_TYPE == null) {
                    String type = DynamicApplicationConfig.getString(BINLOGX_TRANSMIT_RELAY_ENGINE_TYPE);
                    EngineType engineType = EngineType.valueOf(type);
                    if (engineType == EngineType.RANDOM) {
                        String value = getEngineType();
                        if (StringUtils.isBlank(value)) {
                            Random random = new Random();
                            engineType = random.nextBoolean() ? EngineType.FILE : EngineType.ROCKSDB;
                        } else {
                            engineType = EngineType.valueOf(value);
                        }
                    }

                    putEngineType(engineType);
                    ENGINE_TYPE = engineType;
                    log.info("init engine type is " + ENGINE_TYPE);
                }
            }
        }
    }

    @SneakyThrows
    private static void putEngineType(EngineType engineType) {
        META_REPO_UNIT.put(ByteUtil.bytes(MEAT_KEY_ENGINE_TYPE), ByteUtil.bytes(engineType.name()));
    }

    @SneakyThrows
    private static String getEngineType() {
        byte[] value = META_REPO_UNIT.get(ByteUtil.bytes(MEAT_KEY_ENGINE_TYPE));
        return value == null || value.length == 0 ? "" : new String(value);
    }
}
