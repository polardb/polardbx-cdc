/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.dump;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;

public interface SinkFunction {

    public boolean sink(LogEvent event, LogPosition logPosition) throws CanalParseException, TableIdNotFoundException;
}
