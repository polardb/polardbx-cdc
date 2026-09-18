/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import lombok.Data;

/**
 * 记录 ROWS_LOG_EVENT 中某一行数据在 payload 字节数组中的位置、长度和内容哈希。
 *
 * <p>背景：在 replace returning / insert ignore returning 优化中，CDC 需要对 DN 产生的
 * binlog 中的 fix-delete 事件进行重排序（参见 {@code TxnBuffer#addReturningEvent}），
 * 以避免下游 MySQL 消费 binlog 时出现 UK 冲突报错。判断两个 RowsLogEvent 中是否存在
 * 相同行，需要逐行比对二进制数据，本类缓存了每行的哈希值与偏移信息以加速比较。</p>
 *
 * <p>注意：{@code rowOffset} 是相对于所在 RowsLogEvent 的 payload 起始处（payloadOffset 之后）
 * 的偏移量，并非原始 binlog 字节流的绝对偏移。</p>
 *
 * @author zm
 */
@Data
public class RowDataHashCode {
    /**
     * 当前行在 payload 中的起始偏移（相对于 payloadOffset 之后），
     * 每次调用 {@link RowsLogEventMeta#refreshOffsets()} 后会被重新计算。
     */
    private int rowOffset;

    /**
     * 当前行（含 null bitmap）的字节长度。
     * 对 UPDATE_ROWS_EVENT，此字段描述的是 before 行的长度。
     */
    private int length;

    /**
     * 当前行数据的 CRC64 哈希值，用于快速相等性预判。
     * 哈希碰撞时需回退到逐字节比较。
     */
    private long hashCode;

    /**
     * 仅 UPDATE_ROWS_EVENT 使用：after 行（即 SET 部分）的哈希与偏移信息。
     * INSERT/DELETE 事件该字段为 null。
     * 在 returning 冲突检测时，比较时只使用 after 行（即更新后的值）与 delete 行做匹配。
     */
    RowDataHashCode after;

    /**
     * @param rowOffset 当前行相对于 payloadOffset 的初始偏移
     */
    public RowDataHashCode(int rowOffset) {
        this.rowOffset = rowOffset;
    }
}
