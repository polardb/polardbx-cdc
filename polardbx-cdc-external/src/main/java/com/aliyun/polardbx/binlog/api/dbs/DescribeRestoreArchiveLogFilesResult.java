/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs;

import lombok.Data;

@Data
public class DescribeRestoreArchiveLogFilesResult {
    private int httpStatusCode;
    private String requestId;
    private Data data;
    private boolean success;
    private String code;
    private String message;

    @lombok.Data
    public static class Data {
        private ArchiveLogPages archiveLogInfo;
        private boolean restoreTimeValid;
        private String restoreMessage;
    }
}
