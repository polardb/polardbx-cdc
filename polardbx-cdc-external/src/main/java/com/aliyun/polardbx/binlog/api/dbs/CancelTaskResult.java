/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs;

import lombok.Data;

@Data
public class CancelTaskResult {
    private Integer HttpStatusCode;
    private String RequestId;
    private Data Data;
    private Boolean Success;
    private String Code;
    private String Message;

    @lombok.Data
    public static class Data {
        private String Status;
        private String TaskId;
    }
}
