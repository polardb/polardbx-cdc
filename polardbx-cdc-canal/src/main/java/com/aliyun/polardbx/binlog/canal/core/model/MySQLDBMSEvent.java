/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.model;

import com.aliyun.polardbx.binlog.canal.binlog.EventRepository;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

/**
 * mysql对象对应的DBMSEvent
 *
 * @author agapple 2017年5月11日 下午5:32:59
 * @since 3.2.4
 */
@Slf4j
public class MySQLDBMSEvent {

    private DBMSEvent dbmsEventPayload;
    private BinlogPosition position;
    private DBMSXATransaction xaTransaction;
    private EventRepository repository;
    private long eventSize;
    private String persistKey;

    public MySQLDBMSEvent(DBMSEvent dbmsEventPayload, BinlogPosition position, long eventSize) {
        this.dbmsEventPayload = dbmsEventPayload;
        this.position = position;
        this.eventSize = eventSize;
    }

    public boolean isPersisted() {
        return StringUtils.isNotBlank(persistKey);
    }

    public void tryPersist() {
        if (repository == null) {
            return;
        }
        if (dbmsEventPayload == null) {
            throw new PolardbxException("can`t persist because dbms event payload is null");
        }
        if (StringUtils.isNotBlank(persistKey)) {
            throw new PolardbxException("can`t persist again, current persist key is " + persistKey);
        }
        if (repository.isSupportPersist() && (repository.isForcePersist() ||
            eventSize >= repository.persistThreshold())) {
            persistKey = UUID.randomUUID().toString();
            try {
                repository.put(persistKey, SerializationUtils.serialize(dbmsEventPayload));
                dbmsEventPayload = null;
                if (log.isDebugEnabled()) {
                    log.debug("mysql dbms event is persisted, with key " + persistKey);
                }
                StatMetrics.getInstance().addPersistEventCount(1);
            } catch (Throwable e) {
                throw new PolardbxException("persist failed !", e);
            }
        }
    }

    public void tryRelease() {
        if (repository == null) {
            return;
        }
        if (StringUtils.isBlank(persistKey)) {
            return;
        }
        try {
            repository.delete(persistKey);
            if (log.isDebugEnabled()) {
                log.debug("persisted mysql dbms event is released, with key " + persistKey);
            }
            persistKey = null;
            StatMetrics.getInstance().deletePersistEventCount(1);
        } catch (Throwable e) {
            throw new PolardbxException("release persisted event failed !", e);
        }
    }

    public DBMSEvent getDbmsEventPayload() {
        if (StringUtils.isNotBlank(persistKey)) {
            try {
                byte[] bytes = repository.get(persistKey);
                return SerializationUtils.deserialize(bytes);
            } catch (Throwable e) {
                throw new PolardbxException("restore from rocksdb failed!!", e);
            }
        } else {
            return dbmsEventPayload;
        }
    }

    public void setDbmsEventPayload(DBMSEvent dbmsEventPayload) {
        this.dbmsEventPayload = dbmsEventPayload;
    }

    public BinlogPosition getPosition() {
        return position;
    }

    public void setPosition(BinlogPosition position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "MySQLDBMSEvent [position=" + position + ", dbmsEventPayload=" + dbmsEventPayload + "]";
    }

    public DBMSXATransaction getXaTransaction() {
        return xaTransaction;
    }

    public void setXaTransaction(DBMSXATransaction xaTransaction) {
        this.xaTransaction = xaTransaction;
    }

    public long getEventSize() {
        return eventSize;
    }

    public void setEventSize(long eventSize) {
        this.eventSize = eventSize;
    }

    public EventRepository getRepository() {
        return repository;
    }

    public void setRepository(EventRepository repository) {
        this.repository = repository;
    }

}
