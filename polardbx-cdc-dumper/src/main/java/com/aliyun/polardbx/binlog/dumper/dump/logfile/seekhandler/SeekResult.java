/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler;

import com.aliyun.polardbx.binlog.domain.MarkInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
public class SeekResult {
    private String binlogFile;
    private String position;
    private String lastTso;
    private Byte lastEventType;
    private Long lastEventTimestamp;
    private Long maxTableId;
    @Getter
    @Setter
    private Long lastXid;
    private MarkInfo markInfo;

    public SeekResult(String lastTso, Byte lastEventType, Long lastEventTimestamp) {
        this.lastTso = lastTso;
        this.lastEventType = lastEventType;
        this.lastEventTimestamp = lastEventTimestamp;
    }

    public String getBinlogFile() {
        return binlogFile;
    }

    public void setBinlogFile(String binlogFile) {
        this.binlogFile = binlogFile;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getLastTso() {
        return lastTso;
    }

    public void setLastTso(String lastTso) {
        this.lastTso = lastTso;
    }

    public Byte getLastEventType() {
        return lastEventType;
    }

    public void setLastEventType(Byte lastEventType) {
        this.lastEventType = lastEventType;
    }

    public Long getLastEventTimestamp() {
        return lastEventTimestamp;
    }

    public MarkInfo getMarkInfo() {
        return markInfo;
    }

    public void setLastEventTimestamp(Long lastEventTimestamp) {
        this.lastEventTimestamp = lastEventTimestamp;
    }

    public Long getMaxTableId() {
        return maxTableId;
    }

    public void setMaxTableId(Long maxTableId) {
        this.maxTableId = maxTableId;
    }

    @Override
    public String toString() {
        return "SeekResult{" +
            "lastTso='" + lastTso + '\'' +
            ", lastEventType=" + lastEventType +
            ", lastEventTimestamp=" + lastEventTimestamp +
            ", maxTableId=" + maxTableId +
            ", lastXid=" + lastXid;
    }
}
