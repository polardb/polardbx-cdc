/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.platform.dbstack;

import lombok.Data;

import java.util.List;

@Data
public class NsResponse {
    private Integer code;
    private String message;
    private List<NsServiceType> data;
}
