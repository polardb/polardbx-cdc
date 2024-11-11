/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.proc;

import lombok.Data;

/**
 * Created by ziyang.lb
 */
@Data
public class ProcSnapshot {
    private long pid;
    long cpuUser;
    long cpuSys;
    long cpuTotal;
    double cpuPercent;
    long memSize;
    long startTime;
    long fdNum;
}
