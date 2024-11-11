/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.pipeline;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.rocksdb.RocksDBException;
import org.rocksdb.util.ByteUtil;

import java.util.UUID;

/**
 * @author shicai.xsc 2020/11/30 15:15
 * @since 5.0.0.0
 */
@Slf4j
public class MessageEvent {

    private DBMSEvent dbmsEvent;

    private DBMSXATransaction xaTransaction;

    private RepoUnit repoUnit;

    private String persistKey;

    public MessageEvent() {

    }

    public MessageEvent(RepoUnit repoUnit) {
        this.repoUnit = repoUnit;
    }

    public void persist() {
        if (repoUnit == null) {
            return;
        }
        if (dbmsEvent == null) {
            throw new PolardbxException("can`t persist because dbms event is null");
        }
        if (StringUtils.isNotBlank(persistKey)) {
            throw new PolardbxException("can`t persist again, current persist key is " + persistKey);
        }

        persistKey = UUID.randomUUID().toString();
        try {
            repoUnit.put(ByteUtil.bytes(persistKey), SerializationUtils.serialize(dbmsEvent));
            dbmsEvent = null;
            if (log.isDebugEnabled()) {
                log.debug("message event is persisted, with key " + persistKey);
            }
        } catch (RocksDBException e) {
            throw new PolardbxException("persist failed !", e);
        }
    }

    public void tryRelease() {
        if (repoUnit == null) {
            return;
        }
        if (StringUtils.isBlank(persistKey)) {
            return;
        }
        try {
            repoUnit.delete(ByteUtil.bytes(persistKey));
            if (log.isDebugEnabled()) {
                log.debug("persisted mysql dbms event is released, with key " + persistKey);
            }
            persistKey = null;
        } catch (Throwable e) {
            throw new PolardbxException("release persisted event failed !", e);
        }
    }

    public DBMSEvent getDbmsEventDirect() {
        return dbmsEvent;
    }

    public DBMSEvent getDbmsEventEffective() {
        if (StringUtils.isNotBlank(persistKey)) {
            try {
                byte[] bytes = repoUnit.get(ByteUtil.bytes(persistKey));
                return SerializationUtils.deserialize(bytes);
            } catch (RocksDBException e) {
                throw new PolardbxException("restore dbms event from rocks db failed!!", e);
            }
        } else {
            return dbmsEvent;
        }
    }

    public void setDbmsEvent(DBMSEvent dbmsEvent) {
        this.dbmsEvent = dbmsEvent;
    }

    public DBMSXATransaction getXaTransaction() {
        return xaTransaction;
    }

    public void setXaTransaction(DBMSXATransaction xaTransaction) {
        this.xaTransaction = xaTransaction;
    }

    public RepoUnit getRepoUnit() {
        return repoUnit;
    }

    public void setRepoUnit(RepoUnit repoUnit) {
        this.repoUnit = repoUnit;
    }

    public String getPersistKey() {
        return persistKey;
    }

    public void setPersistKey(String persistKey) {
        this.persistKey = persistKey;
    }
}
