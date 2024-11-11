/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import lombok.Data;

import java.util.List;

@Data
public class ServiceDetail {
    private Long id;
    private String type;
    private String status;
    private List<TaskDetail> taskDetailList;
}
