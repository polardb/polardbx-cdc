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

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnsafeByteOperations;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.RocksDBException;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ziyang.lb
 **/
public class TxnItemRef implements Comparable<TxnItemRef>, Serializable {
    public static final AtomicLong CURRENT_TXN_ITEM_COUNT = new AtomicLong(0);
    public static final AtomicLong CURRENT_TXN_ITEM_PERSISTED_COUNT = new AtomicLong(0);

    private transient TxnBuffer txnBuffer;
    private transient EventData eventData;
    private String traceId;
    private int eventType;
    private boolean shouldClearRowsQuery;
    private int subKeySeq;
    private boolean restored;
    private int hashKey;
    private List<byte[]> primaryKey;

    public TxnItemRef() {
    }

    public TxnItemRef(TxnBuffer txnBuffer, String traceId, String rowsQuery, int eventType, byte[] payload,
                      String schema, String table, int hashKey, List<byte[]> primaryKey) {
        checkPayload(payload);
        this.txnBuffer = txnBuffer;
        this.traceId = traceId.intern();
        this.shouldClearRowsQuery = false;
        this.eventType = eventType;
        this.subKeySeq = -1;
        this.hashKey = hashKey;
        this.primaryKey = primaryKey;
        this.eventData = EventData.newBuilder()
            .setRowsQuery(rowsQuery == null ? "" : rowsQuery)
            .setSchemaName(schema == null ? "" : schema)
            .setTableName(table == null ? "" : table)
            .setPayload(UnsafeByteOperations.unsafeWrap(payload)).build();

        CURRENT_TXN_ITEM_COUNT.incrementAndGet();
    }

    void persist() throws RocksDBException {
        if (!isPersisted()) {
            Pair<Integer, byte[]> pair = txnBuffer.buildNewTxnItemRefKey();
            subKeySeq = pair.getLeft();
            txnBuffer.getRepoUnit().put(pair.getRight(), eventData.toByteArray());
            clearEventData();//尽快执行垃圾回收
        } else {
            throw new PolardbxException("Invalid status :duplicate persist operation, txn item has already persisted."
                + "TxnKey is : " + txnBuffer.getTxnKey() + " ,traceId is : " + traceId);
        }
        CURRENT_TXN_ITEM_PERSISTED_COUNT.incrementAndGet();
    }

    public void delete() throws RocksDBException {
        if (isPersisted()) {
            byte[] key = txnBuffer.buildTxnItemRefKeyWithSubSequence(subKeySeq);
            txnBuffer.getRepoUnit().delete(key);
        }
        CURRENT_TXN_ITEM_COUNT.decrementAndGet();
        if (isPersisted()) {
            CURRENT_TXN_ITEM_PERSISTED_COUNT.decrementAndGet();
        }
    }

    public void clearEventData() {
        this.eventData = null;
    }

    public String getTraceId() {
        return traceId;
    }

    public int getEventType() {
        return eventType;
    }

    public boolean isPersisted() {
        return subKeySeq != -1;
    }

    public void clearRowsQuery() {
        this.shouldClearRowsQuery = true;
        if (this.eventData != null) {
            this.eventData = eventData.toBuilder().setRowsQuery("").build();
        }
    }

    // 如果是为了rpc通信，直接调用getByteStringPayload，在byteStringPayload不为空的时候，可以避免不必要的copy操作
    public EventData getEventData() {
        if (isPersisted() && !restored) {
            try {
                byte[] key = txnBuffer.buildTxnItemRefKeyWithSubSequence(subKeySeq);
                byte[] value = txnBuffer.getRepoUnit().get(key);
                return parseOne(value);
            } catch (RocksDBException e) {
                throw new PolardbxException("get payload from repository error.");
            }
        } else {
            if (eventData != null) {
                return eventData;
            } else {
                throw new IllegalStateException(String.format("event data is null, isPersist variable is %s,"
                    + " restore variable is %s.", isPersisted(), restored));
            }
        }
    }

    public void setEventData(EventData eventData) {
        clearEventData();
        if (isPersisted()) {
            try {
                byte[] key = txnBuffer.buildTxnItemRefKeyWithSubSequence(subKeySeq);
                txnBuffer.getRepoUnit().put(key, eventData.toByteArray());
            } catch (RocksDBException e) {
                throw new PolardbxException("set payload error", e);
            }
        } else {
            this.eventData = eventData;
        }
    }

    public void restore(byte[] key, byte[] value) {
        byte[] actualKey = txnBuffer.buildTxnItemRefKeyWithSubSequence(subKeySeq);
        if (!Arrays.equals(key, actualKey)) {
            throw new PolardbxException("input key is different from actual key, input key is : " + new String(key)
                + ", actual key is :" + new String(actualKey));
        }
        this.eventData = parseOne(value);
        this.restored = true;
    }

    private EventData parseOne(byte[] data) {
        EventData eventData;
        try {
            eventData = EventData.parseFrom(data);
            if (shouldClearRowsQuery) {
                eventData = eventData.toBuilder().setRowsQuery("").build();
            }
            return eventData;
        } catch (InvalidProtocolBufferException e) {
            throw new PolardbxException("parse error when restore txn item.", e);
        }
    }

    public void setTxnBuffer(TxnBuffer txnBuffer) {
        this.txnBuffer = txnBuffer;
    }

    TxnBuffer getTxnBuffer() {
        return txnBuffer;
    }

    int getSubKeySeq() {
        return subKeySeq;
    }

    public int getHashKey() {
        return hashKey;
    }

    public void setHashKey(int hashKey) {
        this.hashKey = hashKey;
    }

    public List<byte[]> getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(List<byte[]> primaryKey) {
        this.primaryKey = primaryKey;
    }

    private void checkPayload(byte[] payload) {
        if (payload == null) {
            throw new IllegalStateException(
                "Payload can`t be null, with eventType is " + eventType + " and traceId is " + traceId
                    + "and txnKey is " + txnBuffer.getTxnKey());
        }
    }

    @Override
    public int compareTo(TxnItemRef o) {
        return traceId.compareTo(o.getTraceId());
    }
}
