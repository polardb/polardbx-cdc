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

import java.util.LinkedList;
import java.util.List;

public class RowBatch {

    @Getter
    private final List<DefaultRowChange> rowChanges;
    private boolean canMerge;

    public RowBatch() {
        this.rowChanges = new LinkedList<>();
        this.canMerge = true;
    }

    public void add(DefaultRowChange rowChange) {
        rowChanges.add(rowChange);
        if (rowChange.getAction() == DBMSAction.UPDATE) {
            canMerge = false;
        }
    }

    public boolean canMerge() {
        return canMerge & rowChanges.size() > 1;
    }

    public int size() {
        return rowChanges.size();
    }
}
