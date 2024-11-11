/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import java.util.LinkedList;
import java.util.List;

/**
 * created by ziyang.lb
 **/
public class MergeItemChunk {
    private final LinkedList<MergeItem> items = new LinkedList<>();

    public void addMergeItem(MergeItem item) {
        this.items.add(item);
    }

    public void addMergeItems(List<MergeItem> items) {
        this.items.addAll(items);
    }

    public List<MergeItem> items() {
        return this.items;
    }
}
