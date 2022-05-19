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

package com.aliyun.polardbx.rpl.taskmeta;

/**
 * @author shicai.xsc 2021/5/18 14:58
 * @since 5.0.0.0
 */
public enum ApplierType {
    // 保持原 binlog 中的事务完整性
    TRANSACTION(1),

    // 按照原 binlog 中的 event 顺序执行，但不保证事务完整性
    SERIAL(10),

    // 将一批 events 按照标识键 (identify columns, pk + uk + shard key) 拆分成多个队列，多个队列并行执行，
    // 如果某表 a.a event a.a.1 修改了 identify columns，某表 b.b event b.b.1 修改了 identify columns，
    // 则 a.a 表 a.a.1 之后所有 event 集 serialA 改为事务内串行，b.b 表 b.b.1 之后所有 event 集 serialB 改为事务内串行，
    // serialA 和 serialB 并行
    SPLIT(20),

    // 将一批 events 按照 fullTableName 拆分成多个队列，多个队列并行执行，每个队列事务内串行执行
    SPLIT_TRANSACTION(30),

    // 将一批 events 按照 fullTableName 拆分成多个队列，多个队列并行执行，
    // 每个队列内，接下来会做 3 步骤
    // 1. update 改写：
    // 每个队列内，将 update 转变成 delete + insert，故每个队列中都只存在 delete + insert
    // 2. merge：
    // 假如 table_1 的队列中存在 200 个 delete + 200 个 insert，而 applierConfig.mergeBatchSize == 100，
    // 则 merge 成 2 个 delete + 2 个 insert
    // 3. 并行执行：
    // 先并行执行完 2 个 delete，2 个 delete 都执行成功后，再并行执行 2 个 insert
    MERGE(40),

    // 1，2 步骤和 MERGE 相同，不同的是 3 步骤
    // 3. 每个队列内，merge 成 2 个 delete + 2 个 insert 后，这 4 个 sql 会放在一个事务内串行执行
    // MERGE_TRANSACTION 比 MERGE 缺点：效率低
    // MERGE_TRANSACTION 比 MERGE 优点：不会出现中间状态
    // 即：原本是 update 1 to 2，如果使用 MERGE，可能出现中间状态: 1 被删除了，2 还未插入，用户对账会发现少了一条记录
    MERGE_TRANSACTION(50),

    JUST_EXTRACT(60);

    private int value;

    public int getValue() {
        return value;
    }

    ApplierType(int value) {
        this.value = value;
    }

    public static ApplierType from(int state) {
        for (ApplierType i : ApplierType.values()) {
            if (i.value == state) {
                return i;
            }
        }
        return null;
    }
}