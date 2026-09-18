/*
 * *
 *  * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 *  * All rights reserved.
 *  * <p>
 *  * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.polardbx.binlog.canal.binlog.event;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个 RowsLogEvent 的元数据快照，记录该 event 在 binlog payload 中的布局信息及每行数据的哈希。
 *
 * <p>在 replace returning / insert ignore returning 优化的 fix-delete 重排序流程中，
 * CDC 需要对同一逻辑 SQL 产生的多个 RowsLogEvent 进行"消消乐"比较，即：
 * 找出 fix-delete event 与已有 insert/update event 之间的相同行并做相应处理。
 * 本类作为比较的中间缓存，避免每次重复解析 binlog 字节流。</p>
 *
 * <p>生命周期：由 {@link com.aliyun.polardbx.binlog.storage.TxnItemRef#buildRowsMeta} 构造，
 * 存放在对应 {@code TxnItemRef} 的 {@code rowsLogEventMeta} 字段；
 * 每次对 event 的行数据做删减后，需调用 {@link #refreshOffsets()} 重新校准偏移量。</p>
 */
public class RowsLogEventMeta {

    /**
     * 对应 TABLE_MAP_EVENT 分配的物理表 id，用于快速判断两个 event 是否属于同一张物理表。
     * 不同物理分片之间，同一张逻辑表的 tableId 可能不同，因此 tableId 不匹配时可直接跳过比较。
     */
    @Getter
    private final long tableId;

    /**
     * binlog payload 中行数据的起始字节偏移（相对于 payload 起始位置）。
     * 即事件头（19 字节）+ post-header（含 table-id、flags、extra-data-length 等）的总长度。
     * {@link RowDataHashCode#rowOffset} 均相对此偏移计算。
     */
    @Getter
    private final int payloadOffset;

    /**
     * 当前 event 中每行数据的哈希码列表，顺序与 binlog 中行的物理顺序一致。
     * 在做"消消乐"时可直接操作此列表（移除已匹配的行），然后配合 {@link #refreshOffsets()}
     * 重建截短后的 payload 字节流。
     */
    @Getter
    @Setter
    List<RowDataHashCode> rowDataHashCodes = new ArrayList<>();

    /**
     * @param tableId TABLE_MAP_EVENT 中的物理表 id
     * @param payloadOffset 行数据在 payload 中的起始偏移
     */
    public RowsLogEventMeta(long tableId, int payloadOffset) {
        this.tableId = tableId;
        this.payloadOffset = payloadOffset;
    }

    /**
     * 在对 {@code rowDataHashCodes} 列表做删减（移除已匹配行）之后，
     * 重新计算列表中每个 {@link RowDataHashCode} 的 {@code rowOffset}。
     *
     * <p>删减行后 payload 字节流会被重新构建（compact），各行在新 payload 中的偏移
     * 与原始偏移不再相同，必须在调用 {@code keepRowsFromPayload} 之后立即调用本方法
     * 使 meta 与新 payload 保持一致，否则后续比较会因偏移错误导致数据损坏。</p>
     */
    public void refreshOffsets() {
        int currentPos = 0;
        for (RowDataHashCode rowDataHashCode : rowDataHashCodes) {
            rowDataHashCode.setRowOffset(currentPos);
            currentPos += rowDataHashCode.getLength();
            if (rowDataHashCode.getAfter() != null) {
                // UPDATE_ROWS_EVENT: before + after 行连续存储，after 紧跟 before 之后
                rowDataHashCode.getAfter().setRowOffset(currentPos);
                currentPos += rowDataHashCode.getAfter().getLength();
            }
        }
    }
}
