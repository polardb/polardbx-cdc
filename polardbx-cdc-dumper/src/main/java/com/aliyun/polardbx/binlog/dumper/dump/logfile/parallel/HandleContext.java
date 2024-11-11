/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileGenerator;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Data;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * created by ziyang.lb
 **/
@Data
public class HandleContext {
    private LogFileGenerator logFileGenerator;
    private volatile PolardbxException exception;
    private volatile long latestSinkSequence;
    private AtomicBoolean running;
}
