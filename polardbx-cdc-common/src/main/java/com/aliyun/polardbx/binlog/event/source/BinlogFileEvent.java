/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.event.source;

import com.aliyun.polardbx.binlog.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BinlogFileEvent extends BaseEvent {

    private ActionType actionType;

    private String currentFileName;
    private String nextFileName;

    public static enum ActionType {
        CREATE,
        FINISH,
        ROTATE,
        DELETE
    }
}
