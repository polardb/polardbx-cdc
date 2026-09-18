/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import lombok.Getter;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Getter
public class StagingRowQueue {
    private Map<String, List<DefaultRowChange>> pureInsertRowChanges = new HashMap<>();
    private Map<String, List<DefaultRowChange>> pureDeleteRowChanges = new HashMap<>();
    private Map<String, Map<RowKey, List<DefaultRowChange>>> holdingRowChanges = new HashMap<>();

    private int pureInsertRowChangeSize;
    private int pureDeleteRowChangeSize;
    private int holdingRowChangeSize;

    public void addAll(String fullTbName, RowKey rowKey, List<DefaultRowChange> rowChange) {
        if (rowChange.size() == 1 && isInsertOrDelete(rowChange.get(0))) {
            addInsertOrDelete(fullTbName, rowChange.get(0));
        } else {
            holdingRowChanges.computeIfAbsent(fullTbName, k -> new HashMap<>()).put(rowKey, rowChange);
            holdingRowChangeSize += rowChange.size();
        }
    }

    public void clearPureInsertRowChanges() {
        pureInsertRowChanges = null;
        pureInsertRowChangeSize = 0;
    }

    public void clearPureDeleteRowChanges() {
        pureDeleteRowChanges = null;
        pureDeleteRowChangeSize = 0;
    }

    public void clearHoldingRowChanges() {
        holdingRowChanges = null;
        holdingRowChangeSize = 0;
    }

    boolean isInsertOrDelete(DefaultRowChange rowChange) {
        return rowChange.getAction() == DBMSAction.INSERT || rowChange.getAction() == DBMSAction.DELETE;
    }

    void addInsertOrDelete(String fullTbName, DefaultRowChange rowChange) {
        if (rowChange.getAction() == DBMSAction.INSERT) {
            pureInsertRowChanges.computeIfAbsent(fullTbName, k -> new LinkedList<>()).add(rowChange);
            pureInsertRowChangeSize++;
        } else if (rowChange.getAction() == DBMSAction.DELETE) {
            pureDeleteRowChanges.computeIfAbsent(fullTbName, k -> new LinkedList<>()).add(rowChange);
            pureDeleteRowChangeSize++;
        }
    }
}
