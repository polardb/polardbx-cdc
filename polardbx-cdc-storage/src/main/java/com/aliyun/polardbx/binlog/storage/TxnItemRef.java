/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowDataHashCode;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEventMeta;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UnsafeByteOperations;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.RocksDBException;

import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ziyang.lb
 **/
@Slf4j
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
    @Setter
    private transient byte[] rawPayload;
    @Getter
    @Setter
    private int logicSqlId;
    @Getter
    @Setter
    private String partitionId;
    /**
     * 仅用于ROWS_LOG_EVENT,取其中每行的数据做CRC64产生的值
     */
    @Getter
    @Setter
    private transient RowsLogEventMeta rowsLogEventMeta;
    /**
     * 标记该 TxnItemRef 对应的 binlog event 是否为 replace returning / insert ignore returning
     * 优化产生的 fix-delete 或需要优先排序的 TABLE_MAP_EVENT。
     *
     * <p>fix-delete 场景：{@link TxnBuffer#doAdd} 中对 DELETE_ROWS_EVENT 打标，
     * 打标后会触发 {@link TxnBuffer#addReturningEvent} 的重排序逻辑。</p>
     *
     * <p>TABLE_MAP_EVENT 场景：当 fix-delete 与某个 UPDATE 行匹配时，
     * 对应的 TABLE_MAP_EVENT 也会被打标，以便在跨 DN 归并排序时该 TABLE_MAP 能够
     * 优先被取出，保证与其后的 split-delete 在排序时整体先行（见 {@link TxnItemRef#compareTo}）。</p>
     */
    @Setter
    private boolean returningEvent;

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
        this.rawPayload = payload;

        CURRENT_TXN_ITEM_COUNT.incrementAndGet();
    }

    public static TxnItemRef buildTxnItemRefWithoutPayload(TxnItemRef itemRef) {
        TxnItemRef txnItemRef = new TxnItemRef();
        txnItemRef.setTxnBuffer(itemRef.getTxnBuffer());
        txnItemRef.traceId = itemRef.getTraceId();
        txnItemRef.eventType = itemRef.getEventType();
        txnItemRef.subKeySeq = -1;
        txnItemRef.hashKey = itemRef.getHashKey();
        txnItemRef.primaryKey = itemRef.getPrimaryKey();
        txnItemRef.logicSqlId = itemRef.logicSqlId;
        txnItemRef.returningEvent = itemRef.returningEvent;
        txnItemRef.eventData = itemRef.getEventData();
        return txnItemRef;
    }

    public void persist() throws RocksDBException {
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
        this.rawPayload = null;
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

    public byte[] getRawPayload() {
        if (rawPayload == null) {
            return getEventData().getPayload().toByteArray();
        }
        return rawPayload;
    }

    public byte[] getRawPayloadOrigin() {
        return rawPayload;
    }

    public void setEventData(EventData eventData) {
        if (isPersisted()) {
            clearEventData();
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

    public TxnBuffer getTxnBuffer() {
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
        int ret = traceId.compareTo(o.getTraceId());
        if (ret == 0) {
            if (returningEvent) {
                return -1;
            } else if (o.returningEvent) {
                return 1;
            } else if (partitionId != null && o.partitionId != null) {
                ret = partitionId.compareTo(o.partitionId);
            }
        }
        return ret;
    }

    /**
     * 解析 binlog event 的 payload，为每一行数据构建 {@link RowsLogEventMeta}，
     * 包含行哈希（{@link RowDataHashCode}）和偏移信息，用于后续的 returning fix 比较。
     *
     * <p>对 UPDATE_ROWS_EVENT，每行会同时记录 before 和 after 两个 hashCode：
     * before 存储在 {@link RowDataHashCode} 本身，after 存储在其 {@code after} 字段。
     * 在 returning 冲突检测时，只取 after 行与 fix-delete 做匹配。</p>
     *
     * @param event 已解析的 RowsLogEvent（含 columnInfo 和行数据）
     * @return 构建完成的 RowsLogEventMeta，同时赋值给 {@code this.rowsLogEventMeta}
     * @throws RuntimeException 若 UPDATE_ROWS_EVENT 中 before 行后没有 after 行（数据损坏）
     */
    public RowsLogEventMeta buildRowsMeta(RowsLogEvent event) {
        RowsLogBuffer rowsLogBuffer = event.getOriginalRowsBuf("utf8");
        int eventType = event.getHeader().getType();
        BitSet columns = event.getColumns();
        TableMapLogEvent.ColumnInfo[] columnInfos = event.getTable().getColumnInfo();
        rowsLogEventMeta = new RowsLogEventMeta(event.getTableId(), event.getPayloadOffset());
        List<RowDataHashCode> hashCodes = rowsLogEventMeta.getRowDataHashCodes();

        RowDataHashCode hashCode = new RowDataHashCode(rowsLogBuffer.getBuffer().position());
        while (rowsLogBuffer.nextOneRow(columns)) {
            rowsLogBuffer.getNextOneRowHashCode(columnInfos, hashCode);
            hashCodes.add(hashCode);
            if (eventType == TableMapLogEvent.UPDATE_ROWS_EVENT || eventType == TableMapLogEvent.UPDATE_ROWS_EVENT_V1) {
                // UPDATE_ROWS_EVENT：before 行之后紧跟对应的 after 行
                RowDataHashCode afterHashCode =
                    new RowDataHashCode(rowsLogBuffer.getBuffer().position());
                if (rowsLogBuffer.nextOneRow(columns)) {
                    rowsLogBuffer.getNextOneRowHashCode(columnInfos, afterHashCode);
                    hashCode.setAfter(afterHashCode);
                } else {
                    throw new RuntimeException("update event has no after row! traceId: " + this.getTraceId());
                }
            }
            // 初始化下一行的 hashCode，rowOffset 从当前 buffer position 开始
            hashCode = new RowDataHashCode(rowsLogBuffer.getBuffer().position());
        }
        return rowsLogEventMeta;
    }

    /**
     * 清除 binlog event payload 中 flags 字段的 {@code STMT_END_F} 标志位。
     *
     * <p>当一个 UPDATE_ROWS_EVENT 后需要紧接着插入 split-delete event 时，
     * 原 UPDATE 不再是该 TABLE_MAP 对应的最后一个 DML event，
     * 必须清除其 {@code STMT_END_F} 标志，否则下游解析器会误认为该 event 已结束语句。</p>
     *
     * <p>payload 中 flags 字段位置：19（header）+ 6（table-id）= 25 字节偏移处，占 2 字节。</p>
     */
    public void unsetEndFlags() {
        EventData eventDataTmp = getEventData();
        byte[] payload = eventDataTmp.getPayload().toByteArray();
        ByteArray byteArray = new ByteArray(payload);
        // 跳过 19 字节 event header + 6 字节 table-id，到达 flags 字段
        // TODO(zm): 确定这里的table id一定长度为6
        byteArray.skip(25);
        int flags = byteArray.readInteger(2);
        // 清除 STMT_END_F（bit 0），其余 flags 保持不变
        flags &= ~RowsLogEvent.STMT_END_F;
        byteArray.setPos(byteArray.getPos() - 2);
        byteArray.writeLong(flags, 2);
        setEventData(eventDataTmp.toBuilder().setPayload(UnsafeByteOperations.unsafeWrap(payload)).build());
        if (rawPayload != null) {
            rawPayload = payload;
        }
    }
}