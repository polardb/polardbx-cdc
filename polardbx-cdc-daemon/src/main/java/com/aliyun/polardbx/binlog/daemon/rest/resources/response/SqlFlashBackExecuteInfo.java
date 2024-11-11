/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources.response;

import com.aliyun.polardbx.rpl.common.fsmutil.ServiceDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Created by ziyang.lb
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlFlashBackExecuteInfo {
    private Long fsmId;
    private String fsmState;
    private String fsmStatus;
    //OSS or LINDORM
    private String fileStorageType;
    private String filePathPrefix;
    private Long sqlCounter;
    private String downloadUrl;
    //单位：ms
    private long expireTime;
    private double progress;
    private List<ServiceDetail> serviceDetailList;
}
