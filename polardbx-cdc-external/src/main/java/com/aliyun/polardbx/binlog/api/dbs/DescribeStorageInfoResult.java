/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs;

import lombok.Data;

@Data
public class DescribeStorageInfoResult {
    private Integer HttpStatusCode;
    private String RequestId;
    private StorageEntity Data;
    private String DataJson;
    private Boolean Success;
    private String Code;
    private String Message;
}
