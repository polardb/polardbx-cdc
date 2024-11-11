/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.event.source;

import com.aliyun.polardbx.binlog.event.BaseEvent;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author yanfenglin
 */
@Data
@AllArgsConstructor
public class LatestFileCursorChangeEvent extends BaseEvent {
    private BinlogCursor cursor;
}
