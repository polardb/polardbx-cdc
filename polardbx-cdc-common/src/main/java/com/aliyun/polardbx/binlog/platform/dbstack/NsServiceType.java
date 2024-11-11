/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.platform.dbstack;

import lombok.Data;

import java.util.Date;

@Data
public class NsServiceType {
    private Long id;
    private Long serviceTypeId;
    private String serviceTypeName;
    private String name;
    private String description;
    private String versionCode;
    private String bizType;
    private Integer status;
    private String protocol;
    private Integer gwCrossDomain;
    private Integer priority;
    private Integer locationId;
    private String customConfig;
    private String gwSubDomain;
    private String gwUpstream;
    private Date gmtCreated;
    private Date gmtModified;
    private String tags;
    private String locationCode;
    private String registries;
}
