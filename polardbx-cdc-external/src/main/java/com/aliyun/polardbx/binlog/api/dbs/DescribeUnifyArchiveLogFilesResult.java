/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs;

import lombok.Data;

@Data
public class DescribeUnifyArchiveLogFilesResult {
    private String HttpStatusCode;
    private String RequestId;
    private ArchiveLogPages Data;
    private String Success;
    private String Code;
    private String Message;
}
