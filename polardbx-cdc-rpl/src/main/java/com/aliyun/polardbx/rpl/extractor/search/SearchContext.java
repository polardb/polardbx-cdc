/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.search;

import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;

public class SearchContext {
    private String currentSearchFile;
    private String currentTSO;
    private BinlogPosition resultPosition;
    private BinlogPosition lastPosition;
    private boolean lockPosition = false;
    private long fileSize;

    private boolean polarx = false;

    private int processEventCount;

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getCurrentSearchFile() {
        return currentSearchFile;
    }

    public void setCurrentSearchFile(String currentSearchFile) {
        this.currentSearchFile = currentSearchFile;
    }

    public String getCurrentTSO() {
        return currentTSO;
    }

    public void setCurrentTSO(String currentTSO) {
        this.currentTSO = currentTSO;
    }

    public void reset() {
        this.currentTSO = null;
        this.processEventCount = 0;
    }

    public BinlogPosition getResultPosition() {
        return resultPosition;
    }

    public void setResultPosition(BinlogPosition resultPosition) {
        this.resultPosition = resultPosition;
    }

    public boolean isLockPosition() {
        return lockPosition;
    }

    public void setLockPosition(boolean lockPosition) {
        this.lockPosition = lockPosition;
    }

    public int getProcessEventCount() {
        return processEventCount;
    }

    public void setProcessEventCount(int processEventCount) {
        this.processEventCount = processEventCount;
    }

    public BinlogPosition getLastPosition() {
        return lastPosition;
    }

    public void setLastPosition(BinlogPosition lastPosition) {
        this.lastPosition = lastPosition;
    }

    public void setPolarx(boolean polarx) {
        this.polarx = polarx;
    }
    public boolean isPolarx() {
        return polarx;
    }
}
