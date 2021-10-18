/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.protobuf.ByteString;
import org.rocksdb.RocksDBException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by ziyang.lb
 **/
public class TxnItemRef implements Comparable<TxnItemRef> {

    private final TxnBuffer txnBuffer;
    private final String traceId;
    private final int eventType;
    private final Repository repository;
    private final AtomicBoolean persisted;
    private byte[] referenceKey;
    private volatile byte[] payload;
    private volatile ByteString byteStringPayload;
    private final int payloadSize;

    TxnItemRef(TxnBuffer txnBuffer, String traceId, int eventType, byte[] payload, ByteString byteStringPayload,
               Repository repository) {
        if (payload != null && byteStringPayload != null) {
            throw new IllegalStateException(
                "Both payload and byteStringPayload has not null value, with eventType is " +
                    eventType + " and traceId is " + traceId + "and txnKey is " + txnBuffer.getTxnKey());
        }
        if (payload == null && byteStringPayload == null) {
            throw new IllegalStateException("Both payload and byteStringPayload has null value, with eventType is " +
                eventType + " and traceId is " + traceId + "and txnKey is " + txnBuffer.getTxnKey());
        }

        this.txnBuffer = txnBuffer;
        this.traceId = traceId;
        this.eventType = eventType;
        this.payload = payload;
        this.byteStringPayload = byteStringPayload;
        this.payloadSize = payload != null ? payload.length : byteStringPayload.size();
        this.repository = repository;
        this.persisted = new AtomicBoolean(false);
    }

    void persist() throws RocksDBException {
        if (persisted.compareAndSet(false, true)) {
            referenceKey = txnBuffer.buildTxnItemRefKey();
            repository.put(referenceKey, payload != null ? payload : byteStringPayload.toByteArray());
            clearPayload();//尽快执行垃圾回收
        } else {
            throw new PolardbxException("Invalid status :duplicate persist operation, txn item has already persisted."
                + "TxnKey is : " + txnBuffer.getTxnKey() + " ,traceId is : " + traceId);
        }
    }

    void delete() throws RocksDBException {
        if (persisted.get()) {
            repository.del(referenceKey);
        }
    }

    public void clearPayload() {
        this.payload = null;
        this.byteStringPayload = null;
    }

    public String getTraceId() {
        return traceId;
    }

    public int getEventType() {
        return eventType;
    }

    public boolean isPersisted() {
        return persisted.get();
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    public ByteString getByteStringPayload() {
        if (byteStringPayload != null) {
            return byteStringPayload;
        } else {
            return ByteString.copyFrom(getPayload());
        }
    }

    // 如果是为了rpc通信，直接调用getByteStringPayload，在byteStringPayload不为空的时候，可以避免不必要的copy操作
    public byte[] getPayload() {
        if (persisted.get()) {
            try {
                return repository.get(referenceKey);
            } catch (RocksDBException e) {
                throw new PolardbxException("get payload from repository error.");
            }
        } else {
            if (payload != null) {
                return payload;
            } else if (byteStringPayload != null) {
                return byteStringPayload.toByteArray();
            } else {
                throw new IllegalStateException("Both payload and byteStringPayload is null.");
            }
        }
    }

    public void setPayload(byte[] payload) {
        clearPayload();
        if (persisted.get()) {
            try {
                repository.put(referenceKey, payload);
            } catch (RocksDBException e) {
                throw new PolardbxException("set payload error");
            }
        } else {
            this.payload = payload;
        }
    }

    TxnBuffer getTxnBuffer() {
        return txnBuffer;
    }

    @Override
    public int compareTo(TxnItemRef o) {
        return traceId.compareTo(o.getTraceId());
    }
}
