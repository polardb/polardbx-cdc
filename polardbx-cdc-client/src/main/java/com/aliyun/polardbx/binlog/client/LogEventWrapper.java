/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LogEventWrapper {
    private int id;
    private LogEvent logEvent;
    private List<DBMSEvent> dbmsEventList;
    private LogPosition logPosition;
    private String traceInfo;

    @Override
    public String toString() {
        return "LogEventWrapper{" +
            "id=" + id +
            ", logPosition=" + logPosition +
            '}';
    }
}
