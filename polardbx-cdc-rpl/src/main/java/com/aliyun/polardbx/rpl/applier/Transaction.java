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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.taskmeta.PersistConfig;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.RocksDBException;
import org.rocksdb.util.ByteUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction.DELETE;
import static com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction.INSERT;
import static com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction.UPDATE;

/**
 * @author shicai.xsc 2021/3/17 16:30
 * @since 5.0.0.0
 */
@Slf4j
public class Transaction {

    private static final int DESERIALIZE_PARALLELISM = DynamicApplicationConfig
        .getInt(ConfigKeys.RPL_ROCKSDB_DESERIALIZE_PARALLELISM);
    private static final ExecutorService executorForDeserialize = ThreadPoolUtil
        .createExecutorWithFixedNum(DESERIALIZE_PARALLELISM, "deserializer");
    private static final ExecutorService executorForCompact = ThreadPoolUtil
        .createExecutorWithFixedNum(1, "compactor");

    //常规属性
    private final Set<String> tables = new HashSet<>();
    private final List<DBMSEvent> events = new ArrayList<>();
    private boolean finished = false;
    private boolean prepared = false;
    private long eventCount;

    @Getter
    private long insertCount;
    @Getter
    private long updateCount;
    @Getter
    private long deleteCount;
    @Getter
    private long byteSize;

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

    public Transaction(RepoUnit repoUnit, PersistConfig persistConfig) {
        this.repoUnit = repoUnit;
        this.persistConfig = persistConfig;
    }

    public void appendRowChange(DBMSEvent event) {
        checkOperation();
        appendInternal(event);
        DefaultRowChange rowChange = (DefaultRowChange) event;
        String fullTableName = rowChange.getSchema() + "." + rowChange.getTable();
        tables.add(fullTableName);
        eventCount++;

        if (event.getAction() == INSERT) {
            insertCount += rowChange.getRowSize();
        } else if (event.getAction() == UPDATE) {
            updateCount += rowChange.getRowSize();
        } else if (event.getAction() == DELETE) {
            deleteCount += rowChange.getRowSize();
        }
        byteSize += rowChange.getEventSize();
    }

    public void appendQueryLog(DBMSEvent event) {
        checkOperation();
        appendInternal(event);
        eventCount++;
    }

    public void close() {
        if (isPersisted && rangeList != null) {
            rangeList.forEach(Range::close);
        }
    }

    public DBMSEvent peekFirst() {
        if (isPersisted) {
            return eventCount < 1 ? null : getByKey(persistKeyPrefix + "_" + StringUtils.leftPad(0 + "", 19, "0"));
        } else {
            return events.isEmpty() ? null : events.get(0);
        }
    }

    public DBMSEvent peekLast() {
        if (isPersisted) {
            return eventCount < 1 ? null :
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

    public long getEventCount() {
        return eventCount;
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

    public synchronized RangeIterator rangeIterator() {
        RangeIterator result;
        if (isPersisted) {
            result = new RangeIterator(
                Lists.newArrayList(rangeList.stream().map(Range::copy).collect(Collectors.toList())).iterator());
        } else {
            Range range = new Range();
            range.events = Lists.newArrayList(this.events);
            result = new RangeIterator(Lists.newArrayList(range).iterator());
        }
        alreadyIteration = true;
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
        private AtomicInteger count = new AtomicInteger(0);
        private AtomicInteger byteSize = new AtomicInteger();
        private AtomicBoolean closed = new AtomicBoolean();
        private List<DBMSEvent> events;

        public List<DBMSEvent> getEvents() {
            if (closed.get()) {
                throw new PolardbxException(
                    "transaction range has closed!!, start key {" + start + "}, count {" + count.get() + "}.");
            }

            if (events == null) {
                restore();
            }
            return Collections.unmodifiableList(events);
        }

        private void restore() {
            try {
                if (!isPersisted) {
                    return;
                }

                String beginKey = persistKeyPrefix + "_" + StringUtils.leftPad(start + "", 19, "0");
                String endKey = persistKeyPrefix + "_" + StringUtils.leftPad(start + count.intValue() + "", 19, "0");
                byte[] beginKeyBytes = ByteUtil.bytes(beginKey);
                byte[] endKeyBytes = ByteUtil.bytes(endKey);
                List<Pair<byte[], byte[]>> pairList = repoUnit.getRange(beginKeyBytes, endKeyBytes, count.intValue());

                events = new LinkedList<>();
                ArrayList<Future<DBMSEvent>> futures = new ArrayList<>(2000);
                for (Pair<byte[], byte[]> one : pairList) {
                    futures.add(executorForDeserialize.submit(() -> SerializationUtils.deserialize(one.getValue())));
                }
                for (Future<DBMSEvent> future : futures) {
                    events.add(future.get());
                }

                log.info("transaction range is restored, tranId {}, start key {}, count {}.",
                    persistKeyPrefix, start, count);
            } catch (InterruptedException | ExecutionException e) {
                throw new PolardbxException("restore range data failed !", e);
            }
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) {
                log.warn("transaction range has already closed, tranId {}, start key {}, count {}.",
                    persistKeyPrefix, start, count.get());
                return;
            }

            try {
                String beginKey = persistKeyPrefix + "_" + StringUtils.leftPad(start + "", 19, "0");
                String endKey =
                    persistKeyPrefix + "_" + StringUtils.leftPad(start + count.intValue() + "", 19, "0");
                byte[] beginKeyBytes = ByteUtil.bytes(beginKey);
                byte[] endKeyBytes = ByteUtil.bytes(endKey);

                repoUnit.deleteRange(beginKeyBytes, endKeyBytes);
                executorForCompact.submit(() -> {
                        try {
                            repoUnit.compactRange(beginKeyBytes, endKeyBytes);
                        } catch (RocksDBException e) {
                            log.error("compact range failed !", e);
                        }
                    }
                );
                log.info("transaction range is closed, tranId {}, start key {}, count {}.",
                    persistKeyPrefix, start, count.get());
            } catch (Throwable t) {
                log.error("transaction range close failed, tranId {}, start key {}, count {}.",
                    persistKeyPrefix, start, count.get(), t);
            }
        }

        private Range copy() {
            Range range = new Range();
            range.start = this.start;
            range.count = new AtomicInteger(count.get());
            range.byteSize = new AtomicInteger(byteSize.get());
            range.closed = new AtomicBoolean(closed.get());
            range.events = this.events;
            return range;
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
                if (isPersisted) {
                    log.info("transaction range is removed from iterator, tranId {}, start key {}, count {}.",
                        persistKeyPrefix, currentRange.start, currentRange.count);
                }
            }
            currentRange = wrappedIterator.next();
            return currentRange;
        }
    }
}
