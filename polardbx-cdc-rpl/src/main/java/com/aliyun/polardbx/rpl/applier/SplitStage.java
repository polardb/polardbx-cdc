/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SplitStage {
    private final boolean isMultiStage;
    private final Map<String, Map<RowKey, List<DefaultRowChange>>> allSplitRowChanges;
    private final Map<String, List<DefaultRowChange>> allSerialRowChanges;
    private int parallelRowCount;
    private int allRowCount;

    public SplitStage(boolean isMultiStage) {
        this.isMultiStage = isMultiStage;
        this.allSplitRowChanges = new HashMap<>();
        this.allSerialRowChanges = new HashMap<>();
    }

    public void addRowChange4SerialApply(String fullTbName, DefaultRowChange rowChange) {
        if (isMultiStage && allSerialRowChanges.containsKey(fullTbName)) {
            throw new PolardbxException("duplicate serial row change for table " + fullTbName);
        }

        List<DefaultRowChange> tbSerialRowChanges =
            allSerialRowChanges.computeIfAbsent(fullTbName, k -> new ArrayList<>());
        tbSerialRowChanges.add(rowChange);

        allRowCount++;
    }

    public void addRowChange4ParallelApply(String fullTbName, DefaultRowChange rowChange, List<Integer> pkColumns) {

        // 注意：不能按照 RowKey 去进行压缩，因为同一个key可能并不指向同一行数据
        RowKey key = new RowKey(rowChange, pkColumns);
        Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges =
            allSplitRowChanges.computeIfAbsent(fullTbName, k -> new HashMap<>());

        // 保证同一个 key 的变更按照顺序排列
        List<DefaultRowChange> tbKeyRowChanges = tbSplitRowChanges.computeIfAbsent(key, k -> new ArrayList<>());
        tbKeyRowChanges.add(rowChange);

        parallelRowCount++;
        allRowCount++;
    }
}
