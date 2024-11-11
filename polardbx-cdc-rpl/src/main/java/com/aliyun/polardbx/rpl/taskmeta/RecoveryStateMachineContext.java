/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

import java.util.Date;

/**
 * created by ziyang.lb
 **/
@Data
public class RecoveryStateMachineContext {
    private String fileStorageType;
    private String fileDirectory;
    private Long sqlCounter;
    private String downloadUrl;
    private Date expireTime;
    private int combineTaskInjectTroubleCount;
}
