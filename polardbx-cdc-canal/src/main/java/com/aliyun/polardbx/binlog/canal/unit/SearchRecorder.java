/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.unit;

import lombok.Data;

import java.util.HashSet;

@Data
public class SearchRecorder {
    private Long tid;
    private String fileName;
    private long position;
    private long timestamp;
    private String storageName;
    private boolean local = true;
    private long size;
    private boolean finish;
    private HashSet<String> unCommitXidSet = new HashSet<>();
    private HashSet<String> needStartXidSet = new HashSet<>();
    private long searchTime = -1;

    public SearchRecorder(String storageName) {
        this.storageName = storageName;
        this.tid = Thread.currentThread().getId();
        SearchRecorderMetrics.addSearchRecorder(this);
    }
}
