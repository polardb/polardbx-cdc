/*
 * *
 *  * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 *  * All rights reserved.
 *  * <p>
 *  * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.polardbx.binlog.storage.util;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowDataHashCode;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEventMeta;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.util.HexUtil;
import com.google.protobuf.UnsafeByteOperations;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.RocksDBException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 用于比较两个 RowsLogEvent 之间是否存在相同行，并对相同行做"消消乐"处理。
 *
 * <h2>背景</h2>
 * <p>PolarDB-X 的 replace returning / insert ignore returning 优化会先向 DN 乐观地
 * 执行 REPLACE/INSERT IGNORE，再对误插入的数据下发 fix-delete 物理 SQL。由于 DN
 * 分片之间只有局部唯一约束，全局约束由 CN 维护，fix-delete 对应的 DELETE 事件在 binlog
 * 中天然落在对应 INSERT/UPDATE 的后面。下游 MySQL 直接消费这段 binlog 时，会先看到
 * INSERT，此时已有同 UK 值的数据存在，从而触发 duplicate key 报错。</p>
 *
 * <h2>解决策略（20260116 final 方案）</h2>
 * <p>CDC 在 {@link com.aliyun.polardbx.binlog.storage.TxnBuffer#addReturningEvent} 中
 * 对打了 returning 标记的 fix-delete 进行重排序，分三种情况：</p>
 * <ol>
 *   <li>fix-delete 的行在本逻辑 SQL 的 binlog 中<b>没有</b>对应的 INSERT/UPDATE：
 *       说明删除的是原表中已有数据，将 delete 提到本逻辑 SQL 所有行的最前面。</li>
 *   <li>fix-delete 的行与某个 WRITE_ROWS_EVENT 完全/部分匹配：
 *       说明删除的是本 batch 中刚插入的数据，与对应 INSERT 做"消消乐"（互相消除）。</li>
 *   <li>fix-delete 的行与某个 UPDATE_ROWS_EVENT 的 after 完全/部分匹配：
 *       说明删除的是 batch 中通过 UPDATE 原地写入的数据，将 delete 挪到该 UPDATE 的后面。</li>
 * </ol>
 *
 * <p>本工具类负责执行上述 "找相同行 → 截断/消除" 的核心逻辑，<b>会对传入 event 的
 * rawPayload 和 EventData 做原地修改</b>，调用方需注意此副作用。</p>
 *
 * <p>由于调用时机处于 TransactionBufferEventFilter 阶段（尚未到 Rebuild 阶段），
 * 不需要同步更新 {@code hashCode} 及 {@code primaryKey} 字段。</p>
 *
 * @author zm
 */
@Slf4j
public class RowsLogEventCompareUtil {

    /**
     * 比较 fix-delete event 与 insert/update event，找出相同行后：
     * <ul>
     *   <li>从较小的一方截去相同行（修改其 rawPayload + EventData）；</li>
     *   <li>返回两者的关系枚举，供 {@code addReturningEvent} 决策后续处理。</li>
     * </ul>
     *
     * <p>语义说明（以 event1=delete, event2=rowsEvent 为例）：</p>
     * <ul>
     *   <li>{@link RowsLogEventCompareCode#TOTALLY_EQUAL}：delete 与 insert 行完全一致，
     *       双方均已被消除（调用方负责从链表移除 insert，delete 本身也不再插入）。</li>
     *   <li>{@link RowsLogEventCompareCode#RIGHT_CONTAINS}：insert 行完全包含 delete 行，
     *       delete 的全部行均在 insert 中找到，insert 被截短，delete 不再存在。</li>
     *   <li>{@link RowsLogEventCompareCode#LEFT_CONTAINS}：delete 行完全包含 insert 行，
     *       insert 被全部消除，delete 中剩余行仍需继续向前比较。</li>
     *   <li>{@link RowsLogEventCompareCode#PARTLY_EQUAL}：部分相同，双方均被截短。</li>
     *   <li>{@link RowsLogEventCompareCode#TOTALLY_NOT_EQUAL}：无任何相同行，均不修改。</li>
     * </ul>
     *
     * @param delete fix-delete event（通常是 DELETE_ROWS_EVENT）
     * @param rowsEvent 与之比较的 INSERT 或 UPDATE event
     * @param decoder 用于懒解析 binlog payload 的解码器
     * @param context 解码上下文（含 TABLE_MAP 信息）
     * @return 两事件的包含关系枚举
     */
    public static RowsLogEventCompareCode compareEventAndRemoveEqualPart(TxnItemRef delete, TxnItemRef rowsEvent,
                                                                         LogDecoder decoder,
                                                                         LogContext context) {
        RowsLogEventCompareResult result = findDiffPartInRowsLogEvent(delete, rowsEvent, decoder, context);
        if (result.DiffPart1.isEmpty() && result.DiffPart2.isEmpty()) {
            // delete 与 insert 行完全相同，双方均消除
            return RowsLogEventCompareCode.TOTALLY_EQUAL;
        }
        if (result.DiffPart1.isEmpty()) {
            // insert 的行完全覆盖了 delete，截短 insert，delete 消除
            removeEqualPartInRowsLogEvent(result.DiffPart2, rowsEvent);
            return RowsLogEventCompareCode.RIGHT_CONTAINS;
        }
        if (result.DiffPart2.isEmpty()) {
            // delete 完全包含 insert 的行，insert 消除，delete 截短后继续
            removeEqualPartInRowsLogEvent(result.DiffPart1, delete);
            return RowsLogEventCompareCode.LEFT_CONTAINS;
        }
        if (!result.equalPart1.isEmpty()) {
            // 部分匹配，双方各自截去相同行
            removeEqualPartInRowsLogEvent(result.DiffPart2, rowsEvent);
            removeEqualPartInRowsLogEvent(result.DiffPart1, delete);
            return RowsLogEventCompareCode.PARTLY_EQUAL;
        }
        // 完全不同，无需修改
        return RowsLogEventCompareCode.TOTALLY_NOT_EQUAL;
    }

    /**
     * 将 fix-delete event 按照与 update event 的匹配情况拆成两份：
     * <ul>
     *   <li><b>不同部分</b>（key）：与 update event 不匹配的行，留在原 delete event 中，
     *       后续还需继续向前移动到 startIdx 位置（情况①）。</li>
     *   <li><b>相同部分</b>（value）：与 update event 的 after 行匹配的行，构成一个新的
     *       splitDelete event，插入到该 update event 的紧后方（情况③）。</li>
     * </ul>
     *
     * <p>若 delete 的所有行均在 update 中找到，则 key 返回 null，原 delete 彻底消除。</p>
     * <p>若无任何匹配，则 value 返回 null，delete 保持原样继续向前处理。</p>
     *
     * @param delete fix-delete event
     * @param rowsEvent 与之比较的 UPDATE_ROWS_EVENT
     * @param decoder binlog 解码器
     * @param context 解码上下文
     * @return {@link Pair#getKey()} = delete 不同部分（null 表示 delete 完全被消除）,
     * {@link Pair#getValue()} = delete 相同部分（null 表示无匹配）
     */
    public static Pair<TxnItemRef, TxnItemRef> splitDeleteEvent(TxnItemRef delete, TxnItemRef rowsEvent,
                                                                LogDecoder decoder,
                                                                LogContext context) {
        RowsLogEventCompareResult result = findDiffPartInRowsLogEvent(delete, rowsEvent, decoder, context);
        if (result.DiffPart1.isEmpty()) {
            // delete 的所有行均被 update 覆盖，原 delete 不再需要向前移动
            return Pair.of(null, delete);
        }
        if (result.equalPart1.isEmpty()) {
            // 完全不匹配，delete 不变
            return Pair.of(delete, null);
        }
        // 部分匹配：将 delete 拆成 diff 部分（留在原处向前移）和 equal 部分（插到 update 后）
        byte[] data = delete.getRawPayload();
        TxnItemRef splitDelete = TxnItemRef.buildTxnItemRefWithoutPayload(delete);

        // splitDelete 是新构造的 TxnItemRef，不持有主键，此处断言确保不误用
        assert (splitDelete.getPrimaryKey() == null);

        int payloadOffset = delete.getRowsLogEventMeta().getPayloadOffset();
        // diff 部分：delete 中与 update 不匹配的行，构成截短后的原 delete payload
        byte[] dataDiff = keepRowsFromPayload(data, result.DiffPart1, payloadOffset);
        // equal 部分：delete 中与 update after 匹配的行，构成 splitDelete 的 payload
        byte[] dataEqual = keepRowsFromPayload(data, result.equalPart1, payloadOffset);

        // 更新原 delete event 的 payload 和 meta
        if (delete.getRawPayloadOrigin() != null) {
            delete.setRawPayload(dataDiff);
        }
        delete.setEventData(
            delete.getEventData().toBuilder()
                .setPayload(UnsafeByteOperations.unsafeWrap(dataDiff))
                .build());
        delete.getRowsLogEventMeta().setRowDataHashCodes(result.DiffPart1);
        // 删减行后偏移量变化，必须刷新
        delete.getRowsLogEventMeta().refreshOffsets();

        // 构建 splitDelete（equal 部分）
        splitDelete.setRawPayload(dataEqual);
        splitDelete.setEventData(
            splitDelete.getEventData().toBuilder()
                .setPayload(UnsafeByteOperations.unsafeWrap(dataEqual))
                .build());

        // 若 TxnBuffer 已开启落盘，splitDelete 也需要持久化
        if (delete.getTxnBuffer().getEntity().shouldPersist) {
            try {
                splitDelete.persist();
            } catch (RocksDBException e) {
                throw new RuntimeException(
                    "persist txn item failed, txnBufferId: " + splitDelete.getTxnBuffer().getTxnBufferId()
                        + ",traceId: " + splitDelete.getTraceId(), e);
            }
        }
        // splitDelete 不会再参与比较，无需更新其 meta

        return Pair.of(delete, splitDelete);
    }

    /**
     * 用 {@code remainingHashCodes}（保留行的 hashCode 列表）重建 event 的 payload，
     * 移除与另一方相同的行，并同步更新 EventData 和 meta 的 offset。
     *
     * @param remainingHashCodes 需要保留的行的 hashCode 列表（即不同部分）
     * @param event 需要被截短的 event（原地修改）
     */
    public static void removeEqualPartInRowsLogEvent(List<RowDataHashCode> remainingHashCodes, TxnItemRef event) {
        byte[] data = event.getRawPayload();
        if (!remainingHashCodes.isEmpty()) {
            event.getRowsLogEventMeta().setRowDataHashCodes(remainingHashCodes);
            // 重建仅保留 remainingHashCodes 对应行的 payload
            byte[] newData =
                keepRowsFromPayload(data, remainingHashCodes, event.getRowsLogEventMeta().getPayloadOffset());
            if (event.getRawPayloadOrigin() != null) {
                event.setRawPayload(newData);
            }
            event.setEventData(
                event.getEventData().toBuilder()
                    .setPayload(UnsafeByteOperations.unsafeWrap(newData))
                    .build());
            // payload 重建后行偏移量发生变化，必须刷新
            event.getRowsLogEventMeta().refreshOffsets();
        }
    }

    /**
     * 核心比较方法：逐行对比 event1 与 event2 的行数据，输出相同部分与不同部分。
     *
     * <p>对 UPDATE_ROWS_EVENT，只比较其 after 行（即 SET 之后的值），
     * before 行不参与匹配，因为 fix-delete 要删除的是更新后的数据。</p>
     *
     * <p>比较算法：双重循环，先用哈希值快速筛选候选匹配项，再逐字节确认，
     * 避免哈希碰撞导致误判。一旦找到匹配，立即从 diffPart 中移除并加入 equalPart，
     * 由于行与行之间一一对应，找到后直接 break 内层循环。</p>
     *
     * <p>注意：本方法<b>不修改</b> event 的 payload，仅返回分类结果，
     * 修改操作由 {@link #removeEqualPartInRowsLogEvent} 或 {@link #splitDeleteEvent} 完成。</p>
     *
     * @param event1 通常为 fix-delete event
     * @param event2 通常为 insert 或 update event
     * @param decoder binlog 解码器（用于懒初始化 meta）
     * @param context 解码上下文
     * @return 包含 equalPart1/equalPart2/DiffPart1/DiffPart2 的比较结果
     */
    public static RowsLogEventCompareResult findDiffPartInRowsLogEvent(TxnItemRef event1, TxnItemRef event2,
                                                                       LogDecoder decoder,
                                                                       LogContext context) {
        byte[] data1 = event1.getRawPayload();
        byte[] data2 = event2.getRawPayload();
        try {
            RowsLogEventMeta meta1 = prepareRowsLogEventMeta(event1, decoder, context);
            RowsLogEventMeta meta2 = prepareRowsLogEventMeta(event2, decoder, context);
            List<RowDataHashCode> hashCodes1 = meta1.getRowDataHashCodes();
            List<RowDataHashCode> hashCodes2 = meta2.getRowDataHashCodes();

            // diffPart 初始包含各自所有行，匹配到的行逐步移入 equalPart
            List<RowDataHashCode> diffPart1 = new ArrayList<>(hashCodes1);
            List<RowDataHashCode> diffPart2 = new ArrayList<>(hashCodes2);
            List<RowDataHashCode> equalPart1 = new ArrayList<>();
            List<RowDataHashCode> equalPart2 = new ArrayList<>();

            // 不同物理表的 event 不可能有相同行，快速返回
            if (meta1.getTableId() != meta2.getTableId()) {
                return new RowsLogEventCompareResult(equalPart1, equalPart2, diffPart1, diffPart2);
            }

            // 从后往前遍历 event1，以便 remove(i) 不影响前面元素的下标
            for (int i = hashCodes1.size() - 1; i >= 0; i--) {
                RowDataHashCode hashCode1 = hashCodes1.get(i);
                // UPDATE_ROWS_EVENT：取 after 行参与比较
                if (hashCode1.getAfter() != null) {
                    hashCode1 = hashCode1.getAfter();
                }

                // 在 diffPart2 中寻找与 hashCode1 匹配的行
                for (int j = 0; j < diffPart2.size(); j++) {
                    RowDataHashCode hashCode2 = diffPart2.get(j);
                    if (hashCode2.getAfter() != null) {
                        hashCode2 = hashCode2.getAfter();
                    }

                    if (hashCode1.getHashCode() == hashCode2.getHashCode()) {
                        // 哈希命中，逐字节确认（防止哈希碰撞）
                        int offset1 = meta1.getPayloadOffset() + hashCode1.getRowOffset();
                        int offset2 = meta2.getPayloadOffset() + hashCode2.getRowOffset();

                        boolean diff = false;
                        for (int k = 0; k < hashCode2.getLength(); k++) {
                            if (data1[offset1 + k] != data2[offset2 + k]) {
                                diff = true;
                                break;
                            }
                        }
                        if (!diff) {
                            // 逐字节确认相同，将两侧对应行从 diff 移入 equal
                            // 由于行与行一一对应，找到即可 break 内层循环
                            equalPart1.add(diffPart1.remove(i));
                            equalPart2.add(diffPart2.remove(j));
                            break;
                        }
                    }
                }
            }
            return new RowsLogEventCompareResult(equalPart1, equalPart2, diffPart1, diffPart2);
        } catch (Exception e) {
            log.error(
                "parse returning payload failed! delete event trace:{} insert event trace:{}, delete data:{}, insert data:{}",
                event1.getTraceId(), event2.getTraceId(), HexUtil.encodeHexStr(data1),
                HexUtil.encodeHexStr(data2));
            throw new RuntimeException("parse returning payload failed!", e);
        }
    }

    /**
     * 懒初始化 event 的 {@link RowsLogEventMeta}：若尚未解析则解析 binlog payload 并构建 meta。
     *
     * @param event 目标 event
     * @param decoder binlog 解码器
     * @param context 解码上下文（必须已注入对应的 TABLE_MAP 信息）
     * @return 已初始化的 RowsLogEventMeta
     */
    public static RowsLogEventMeta prepareRowsLogEventMeta(TxnItemRef event, LogDecoder decoder, LogContext context)
        throws IOException {
        RowsLogEventMeta meta = event.getRowsLogEventMeta();
        if (meta == null) {
            byte[] data = event.getRawPayload();
            RowsLogEvent rowsEvent = (RowsLogEvent) decoder.decode(new LogBuffer(data, 0, data.length), context);
            meta = event.buildRowsMeta(rowsEvent);
        }
        return meta;
    }

    /**
     * 从原始 payload 中只保留 {@code rowsToRemaining} 指定的行，构建截短后的 payload 字节数组。
     *
     * <p>payload 的结构为：</p>
     * <pre>
     * [事件头 19B][post-header][payloadOffset 指示的行数据区][CRC32 4B]
     * </pre>
     * <p>本方法按如下顺序重新组装：</p>
     * <ol>
     *   <li>保留原始 payload 的前 {@code payloadOffset} 字节（header + post-header，不含行数据）；</li>
     *   <li>按 {@code rowsToRemaining} 的顺序，依次写入每行数据（对 UPDATE 还需写 after 行）；</li>
     *   <li>写入原始 CRC32（末尾 4 字节）；</li>
     *   <li>将新 payload 的总长度回填到 header 的 event_length 字段（偏移 9，4 字节小端）。</li>
     * </ol>
     *
     * @param payload 原始 event payload
     * @param rowsToRemaining 需要保留的行的 hashCode 列表（含偏移和长度信息）
     * @param payloadOffset 行数据在 payload 中的起始偏移
     * @return 仅包含指定行的新 payload 字节数组
     */
    private static byte[] keepRowsFromPayload(byte[] payload, List<RowDataHashCode> rowsToRemaining,
                                              int payloadOffset) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // 写 header + post-header（不包含行数据）
        outputStream.write(payload, 0, payloadOffset);

        for (RowDataHashCode hashCode : rowsToRemaining) {
            // 写当前行（INSERT/DELETE 的行，或 UPDATE 的 before 行）
            int offset = payloadOffset + hashCode.getRowOffset();
            outputStream.write(payload, offset, hashCode.getLength());

            if (hashCode.getAfter() != null) {
                // UPDATE_ROWS_EVENT：after 行紧跟 before 行之后
                RowDataHashCode after = hashCode.getAfter();
                offset = payloadOffset + after.getRowOffset();
                outputStream.write(payload, offset, after.getLength());
            }
        }

        // 保留原始 CRC32（末尾 4 字节），即使数据变了 CRC 也维持原样
        outputStream.write(payload, payload.length - 4, 4);

        // 回填 event_length 字段（header 偏移 9，占 4 字节，小端）
        byte[] data = outputStream.toByteArray();
        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(9);
        byteArray.writeLong(data.length, 4);

        return data;
    }
}
