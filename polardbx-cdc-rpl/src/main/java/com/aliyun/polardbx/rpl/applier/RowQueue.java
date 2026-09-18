/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Getter;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

@Getter
public class RowQueue {
    private boolean isCompleted = false;
    private LinkedList<DefaultRowChange> allRowChanges = new LinkedList<>();
    private LinkedList<DefaultRowChange> pureInsertRowChanges = new LinkedList<>();
    private LinkedList<DefaultRowChange> pureDeleteRowChanges = new LinkedList<>();
    private LinkedList<DefaultRowChange> holdingRowChanges = new LinkedList<>();

    public RowQueue() {
    }

    public RowQueue(List<DefaultRowChange> rowChange) {
        this.allRowChanges = new LinkedList<>(rowChange);
    }

    public void add(DefaultRowChange rowChange) {
        if (!isInsertOrDelete(rowChange)) {
            throw new PolardbxException("row queue only support insert or delete action when add single row change !");
        }
        addInsertOrDelete(rowChange);
    }

    public void addAll(List<DefaultRowChange> rowChange) {
        if (rowChange.size() == 1 && isInsertOrDelete(rowChange.get(0))) {
            addInsertOrDelete(rowChange.get(0));
        } else {
            holdingRowChanges.addAll(rowChange);
        }
    }

    private boolean isInsertOrDelete(DefaultRowChange rowChange) {
        return rowChange.getAction() == DBMSAction.INSERT || rowChange.getAction() == DBMSAction.DELETE;
    }

    private void addInsertOrDelete(DefaultRowChange rowChange) {
        if (rowChange.getAction() == DBMSAction.INSERT) {
            pureInsertRowChanges.add(rowChange);
        } else if (rowChange.getAction() == DBMSAction.DELETE) {
            pureDeleteRowChanges.add(rowChange);
        }
    }

    public void markCompleted() {
        if (!pureInsertRowChanges.isEmpty()) {
            allRowChanges.addAll(pureInsertRowChanges);
            pureInsertRowChanges = new LinkedList<>();
        }

        if (!pureDeleteRowChanges.isEmpty()) {
            allRowChanges.addAll(pureDeleteRowChanges);
            pureDeleteRowChanges = new LinkedList<>();
        }

        if (!holdingRowChanges.isEmpty()) {
            allRowChanges.addAll(holdingRowChanges);
            holdingRowChanges = new LinkedList<>();
        }

        isCompleted = true;
    }

    public int size() {
        if (isCompleted) {
            return allRowChanges.size();
        } else {
            return pureInsertRowChanges.size() + pureDeleteRowChanges.size() + holdingRowChanges.size();
        }
    }

    public void forEachApply(Consumer<RowBatch> consumer) {
        if (!isCompleted) {
            throw new PolardbxException("row queue is not completed!");
        }

        DefaultRowChange preRowChange = allRowChanges.get(0);
        String preSchema = preRowChange.getSchema();
        String preTable = preRowChange.getTable();
        DBMSAction preAction = preRowChange.getAction();

        RowBatch rowBatch = new RowBatch();
        rowBatch.add(preRowChange);

        for (int i = 1; i < allRowChanges.size(); i++) {
            DefaultRowChange rowChange = allRowChanges.get(i);
            if (rowChange.getAction() == DBMSAction.UPDATE) {
                if (rowBatch.canMerge()) {
                    callback(consumer, rowBatch);
                    rowBatch = new RowBatch();
                }
            } else {
                if (!preSchema.equals(rowChange.getSchema())
                    || !preTable.equals(rowChange.getTable())
                    || preAction != rowChange.getAction()) {

                    callback(consumer, rowBatch);
                    rowBatch = new RowBatch();
                }
            }

            rowBatch.add(rowChange);
            preSchema = rowChange.getSchema();
            preTable = rowChange.getTable();
            preAction = rowChange.getAction();
        }

        if (rowBatch.size() > 0) {
            callback(consumer, rowBatch);
        }
    }

    void callback(Consumer<RowBatch> consumer, RowBatch rowBatch) {
        consumer.accept(rowBatch);
    }
}
