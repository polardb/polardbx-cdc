/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import lombok.Data;

@Data
public class TaskDetail {
    private Long taskId;
    private String type;
    private String status;
    private String physicalDbName;
    private String statistics;
    private String lastError;
    private Integer progress;
    private Long delay;
    private Long rps;
    private Long lastCommitSec;
}
