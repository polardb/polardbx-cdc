/*
 * *
 *  * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 *  * All rights reserved.
 *  * <p>
 *  * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.polardbx.binlog.storage.util;

import com.aliyun.polardbx.binlog.canal.binlog.event.RowDataHashCode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * {@link RowsLogEventCompareUtil#findDiffPartInRowsLogEvent} 的比较结果载体。
 *
 * <p>对两个 RowsLogEvent（通常为一个 delete event 和一个 insert/update event）逐行比较后，
 * 将行分为"相同部分"（equalPart）和"不同部分"（DiffPart）分别存储，
 * 供调用方决定如何对这两个 event 做截断或消除操作。</p>
 *
 * <p>命名约定：后缀 1 对应入参 event1（通常为 fix-delete），后缀 2 对应 event2（insert/update）。</p>
 */
@Data
@AllArgsConstructor
public class RowsLogEventCompareResult {
    /**
     * event1 中与 event2 存在相同行的部分（按 event1 视角的 hashCode 列表）。
     * 若为空则说明两者完全没有交集，或 event1 的所有行均不同。
     */
    List<RowDataHashCode> equalPart1;

    /**
     * event2 中与 event1 存在相同行的部分（按 event2 视角的 hashCode 列表）。
     * equalPart1 与 equalPart2 一一对应，长度始终相等。
     */
    List<RowDataHashCode> equalPart2;

    /**
     * event1 中与 event2 完全不同的行（即 event1 独有的行）。
     * 若为空则说明 event1 的所有行在 event2 中均有对应（RIGHT_CONTAINS 或 TOTALLY_EQUAL）。
     */
    List<RowDataHashCode> DiffPart1;

    /**
     * event2 中与 event1 完全不同的行（即 event2 独有的行）。
     * 若为空则说明 event2 的所有行在 event1 中均有对应（LEFT_CONTAINS 或 TOTALLY_EQUAL）。
     */
    List<RowDataHashCode> DiffPart2;
}
