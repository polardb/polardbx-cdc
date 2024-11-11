/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.storage;

import com.aliyun.polardbx.binlog.canal.binlog.EventRepository;
import com.aliyun.polardbx.rpl.taskmeta.PersistConfig;
import org.rocksdb.util.ByteUtil;

/**
 * created by ziyang.lb
 **/
public class RplEventRepository implements EventRepository {

    private final PersistConfig persistConfig;

    public RplEventRepository(PersistConfig persistConfig) {
        this.persistConfig = persistConfig;
    }

    @Override
    public boolean isSupportPersist() {
        return persistConfig.isSupportPersist();
    }

    @Override
    public boolean isForcePersist() {
        return persistConfig.isForcePersist();
    }

    @Override
    public long persistThreshold() {
        return persistConfig.getEventPersistThreshold();
    }

    @Override
    public void put(String key, byte[] value) throws Throwable {
        RplStorage.getRepoUnit().put(ByteUtil.bytes(key), value);
    }

    @Override
    public byte[] get(String key) throws Throwable {
        return RplStorage.getRepoUnit().get(ByteUtil.bytes(key));
    }

    @Override
    public void delete(String key) throws Throwable {
        RplStorage.getRepoUnit().delete(ByteUtil.bytes(key));
    }
}
