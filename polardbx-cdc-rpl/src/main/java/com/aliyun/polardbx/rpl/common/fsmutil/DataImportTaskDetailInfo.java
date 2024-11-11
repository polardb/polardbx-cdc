/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import lombok.Data;

import java.util.List;

@Data
public class DataImportTaskDetailInfo {
    private Long fsmId;
    private String fsmState;
    private String fsmStatus;
    private List<ServiceDetail> serviceDetailList;
}
