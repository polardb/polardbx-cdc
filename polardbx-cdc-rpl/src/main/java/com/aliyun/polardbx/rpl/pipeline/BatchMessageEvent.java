/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.pipeline;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class BatchMessageEvent {
    private List<DBMSEvent> dbmsEvents;
    private Timestamp extractTimestamp;
    private String position;
}
