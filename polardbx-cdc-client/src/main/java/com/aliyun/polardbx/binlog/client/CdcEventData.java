/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;

public class CdcEventData {
    private final String binlogFileName;
    private final long position;
    private final DBMSEvent event;

    public CdcEventData(String binlogFileName, long position, DBMSEvent event) {
        this.binlogFileName = binlogFileName;
        this.position = position;
        this.event = event;
    }

    public String getBinlogFileName() {
        return binlogFileName;
    }

    public long getPosition() {
        return position;
    }

    public DBMSEvent getEvent() {
        return event;
    }
}
