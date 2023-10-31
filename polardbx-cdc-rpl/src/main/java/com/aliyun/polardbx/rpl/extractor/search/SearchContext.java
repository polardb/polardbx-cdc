/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
}
