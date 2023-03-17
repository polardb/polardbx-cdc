/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
