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
package com.aliyun.polardbx.binlog.canal.core.model;

import com.aliyun.polardbx.binlog.canal.binlog.EventRepository;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;
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

    private DBMSEvent dbMessage;
    private BinlogPosition position;
    private DBMSXATransaction xaTransaction;

    private EventRepository repository;
    private long eventSize;
    private String persistKey;

    public MySQLDBMSEvent(DBMSEvent dbMessage, BinlogPosition position, long eventSize) {
        this.dbMessage = dbMessage;
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
        if (dbMessage == null) {
            throw new PolardbxException("can`t persist because dbMessage is null");
        }
        if (StringUtils.isNotBlank(persistKey)) {
            throw new PolardbxException("can`t persist again, current persist key is " + persistKey);
        }
        if (repository.isSupportPersist() && (repository.isForcePersist() ||
            eventSize >= repository.persistThreshold())) {
            persistKey = UUID.randomUUID().toString();
            try {
                repository.put(persistKey, SerializationUtils.serialize(dbMessage));
                dbMessage = null;
                log.info("mysql dbms event is persisted, with key " + persistKey);
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
            log.info("persisted mysql dbms event is released, with key " + persistKey);
            persistKey = null;
        } catch (Throwable e) {
            throw new PolardbxException("release persisted event failed !", e);
        }
    }

    public DBMSEvent getDbMessageWithEffect() {
        if (StringUtils.isNotBlank(persistKey)) {
            try {
                byte[] bytes = repository.get(persistKey);
                return SerializationUtils.deserialize(bytes);
            } catch (Throwable e) {
                throw new PolardbxException("restore from rocksdb failed!!", e);
            }
        } else {
            return dbMessage;
        }
    }

    public void setDbMessage(DBMSEvent dbMessage) {
        this.dbMessage = dbMessage;
    }

    public BinlogPosition getPosition() {
        return position;
    }

    public void setPosition(BinlogPosition position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "MySQLDBMSEvent [position=" + position + ", dbMessage=" + dbMessage + "]";
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
