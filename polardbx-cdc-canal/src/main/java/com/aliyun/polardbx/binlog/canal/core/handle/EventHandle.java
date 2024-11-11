/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;

import java.util.Set;

public interface EventHandle {
    boolean interrupt();

    Set<Integer> interestEvents();

    void onStart();

    void handle(LogEvent event, LogPosition position);

    void onEnd();
}
