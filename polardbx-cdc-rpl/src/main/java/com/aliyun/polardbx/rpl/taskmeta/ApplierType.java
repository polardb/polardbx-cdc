/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

/**
 * @author shicai.xsc 2021/5/18 14:58
 * @since 5.0.0.0
 */
public enum ApplierType {
    // 保持原 binlog 中的事务完整性
    TRANSACTION,

    // 按照原 binlog 中的 event 顺序执行，但不保证事务完整性
    SERIAL,

    TABLE_PARALLEL,

    // *compute causality to parallel by row*
    // 将一批 events 按照标识键 (identify columns, pk + uk + shard key) 拆分成多个队列，多个队列并行执行，
    // 如果某表 a.a event a.a.1 修改了 identify columns，某表 b.b event b.b.1 修改了 identify columns，
    // 则 a.a 表 a.a.1 之后所有 event 集 serialA 改为事务内串行，b.b 表 b.b.1 之后所有 event 集 serialB 改为事务内串行，
    // serialA 和 serialB 并行
    SPLIT,

    // 将一批 events 按照 fullTableName 拆分成多个队列，多个队列并行执行，每个队列事务内串行执行
    SPLIT_TRANSACTION,

    // *compute causality to parallel by row*
    // *compact same key changes*
    // *merge same table & action changes into batch*
    // 将一批 events 按照 fullTableName 拆分成多个队列，多个队列并行执行，
    // 每个队列内，接下来会做 3 步骤
    // 1. update 改写：
    // 每个队列内，将 update 转变成 delete + insert，故每个队列中都只存在 delete + insert
    // 2. merge：
    // 假如 table_1 的队列中存在 200 个 delete + 200 个 insert，而 applierConfig.mergeBatchSize == 100，
    // 则 merge 成 2 个 delete + 2 个 insert
    // 3. 并行执行：
    // 先并行执行完 2 个 delete，2 个 delete 都执行成功后，再并行执行 2 个 insert
    MERGE,

    JUST_EXTRACT,

    FULL_COPY,

    // SQL闪回
    RECOVERY
}