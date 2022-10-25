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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import com.aliyun.polardbx.rpl.taskmeta.PersistConfig;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.RocksDBException;
import org.rocksdb.util.ByteUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author shicai.xsc 2021/3/17 16:30
 * @since 5.0.0.0
 */
@Slf4j
public class Transaction {
    //常规属性
    private final Set<String> tables = new HashSet<>();
    private final List<DBMSEvent> events = new ArrayList<>();
    private boolean finished = false;
    private boolean prepared = false;
    private long eventSize;

    // 持久化相关
    private RepoUnit repoUnit;
    private boolean isPersisted;
    private String persistKeyPrefix;
    private long saveCursor;
    private long lastCheckTime;
    private Range currentRange;
    private List<Range> rangeList;
    private boolean alreadyIteration;
    private PersistConfig persistConfig;

    public Transaction() {
    }

    public Transaction(RepoUnit repoUnit, PersistConfig persistConfig) {
        this.repoUnit = repoUnit;
        this.persistConfig = persistConfig;
    }

    public void appendRowChange(DBMSEvent event) {
        checkOperation();
        appendInternal(event);
        DBMSRowChange rowChange = (DBMSRowChange) event;
        String fullTableName = rowChange.getSchema() + "." + rowChange.getTable();
        tables.add(fullTableName);
        eventSize++;
    }

    public void appendQueryLog(DBMSEvent event) {
        checkOperation();
        appendInternal(event);
        eventSize++;
    }

    public DBMSEvent peekFirst() {
        checkOperation();
        if (isPersisted) {
            return eventSize < 1 ? null : getByKey(persistKeyPrefix + "_" + StringUtils.leftPad(0 + "", 19, "0"));
        } else {
            return events.isEmpty() ? null : events.get(0);
        }
    }

    public DBMSEvent peekLast() {
        checkOperation();
        if (isPersisted) {
            return eventSize < 1 ? null :
                getByKey(persistKeyPrefix + "_" + StringUtils.leftPad((saveCursor - 1) + "", 19, "0"));
        } else {
            return events.isEmpty() ? null : events.get(events.size() - 1);
        }
    }

    public Set<String> getTables() {
        return tables;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public long getEventSize() {
        return eventSize;
    }

    public boolean isPersisted() {
        return isPersisted;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public void setPrepared(boolean prepared) {
        this.prepared = prepared;
    }

    // just call once
    public RangeIterator rangeIterator() {
        if (alreadyIteration) {
            throw new PolardbxException("can`t retrieve iteration again!!");
        }

        RangeIterator result;
        if (isPersisted) {
            result = new RangeIterator(rangeList.iterator());
        } else {
            Range range = new Range();
            range.events = this.events;
            result = new RangeIterator(Lists.newArrayList(range).iterator());
        }
        alreadyIteration = true;
        if (log.isDebugEnabled()) {
            log.debug("build range iterator for transaction, with persist key prefix " + persistKeyPrefix
                + " eventSize : " + eventSize);
        }
        return result;
    }

    private void checkOperation() {
        if (alreadyIteration) {
            throw new PolardbxException("this operation is not allowed, because has already iteration!");
        }
    }

    private DBMSEvent getByKey(String key) {
        try {
            byte[] value = repoUnit.get(ByteUtil.bytes(key));
            return SerializationUtils.deserialize(value);
        } catch (RocksDBException e) {
            throw new PolardbxException("get dbms event from rocks db failed!! with key " + key, e);
        }
    }

    private void appendInternal(DBMSEvent event) {
        if (persistConfig != null && persistConfig.isSupportPersist() && !isPersisted) {
            boolean shouldPersist = false;
            if (persistConfig.isForcePersist()) {
                shouldPersist = true;
            } else {
                if (System.currentTimeMillis() - lastCheckTime > persistConfig.getTransPersistCheckIntervalMs()) {
                    double oldRatio = JvmUtils.getOldUsedRatio();
                    if (oldRatio > persistConfig.getTransPersistMemoryThreshold()) {
                        shouldPersist = true;
                    }
                    lastCheckTime = System.currentTimeMillis();
                }
            }

            if (shouldPersist) {
                persistKeyPrefix = UUID.randomUUID().toString();
                log.info("persisting switch is opened for transaction, with persist key prefix " + persistKeyPrefix);
                rangeList = new LinkedList<>();
                persistAll();
                isPersisted = true;
            }
        }

        if (isPersisted) {
            persistOne(event);
        } else {
            events.add(event);
        }
    }

    private void persistAll() {
        events.forEach(this::persistOne);
        events.clear();
    }

    private void persistOne(DBMSEvent event) {
        if (currentRange == null) {
            currentRange = new Range();
            currentRange.start = saveCursor;
            rangeList.add(currentRange);
        }

        String key = persistKeyPrefix + "_" + StringUtils.leftPad(saveCursor + "", 19, "0");
        try {
            byte[] value = SerializationUtils.serialize(event);
            repoUnit.put(ByteUtil.bytes(key), value);

            currentRange.count.incrementAndGet();
            currentRange.byteSize.getAndAdd(value.length);
            if (currentRange.count.intValue() == persistConfig.getTransPersistRangeMaxItemSize()
                || currentRange.byteSize.intValue() >= persistConfig.getTransPersistRangeMaxByteSize()) {
                if (log.isDebugEnabled()) {
                    log.debug("range rotate, persist key prefix : " + persistKeyPrefix + " count :" + currentRange.count
                        .intValue() + " , byte size " + currentRange.byteSize.intValue());
                }
                currentRange = null;
            }
        } catch (RocksDBException e) {
            throw new PolardbxException("persist dbms event failed !!", e);
        }
        saveCursor++;
    }

    public class Range {
        private long start;
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicInteger byteSize = new AtomicInteger();
        private List<DBMSEvent> events;

        public List<DBMSEvent> getEvents() {
            if (events == null) {
                restore();
            }
            return events;
        }

        private void restore() {
            if (!isPersisted) {
                return;
            }

            String beginKey = persistKeyPrefix + "_" + StringUtils.leftPad(start + "", 19, "0");
            String endKey = persistKeyPrefix + "_" + StringUtils.leftPad(start + count.intValue() + "", 19, "0");
            byte[] beginKeyBytes = ByteUtil.bytes(beginKey);
            byte[] endKeyBytes = ByteUtil.bytes(endKey);
            List<Pair<byte[], byte[]>> pairList = repoUnit.getRange(beginKeyBytes, endKeyBytes, count.intValue());

            events = new LinkedList<>();
            pairList.forEach(p -> events.add(SerializationUtils.deserialize(p.getValue())));

            try {
                repoUnit.deleteRange(beginKeyBytes, endKeyBytes);
                repoUnit.compactRange(beginKeyBytes, endKeyBytes);
            } catch (RocksDBException e) {
                throw new PolardbxException("delete range data failed !", e);
            }
        }
    }

    public class RangeIterator {
        private final Iterator<Range> wrappedIterator;
        private Range currentRange;

        private RangeIterator(Iterator<Range> wrappedIterator) {
            this.wrappedIterator = wrappedIterator;
        }

        public boolean hasNext() {
            return wrappedIterator.hasNext();
        }

        public Range next() {
            if (currentRange != null) {
                wrappedIterator.remove();
            }
            currentRange = wrappedIterator.next();
            return currentRange;
        }
    }
}
