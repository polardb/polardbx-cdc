/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

/**
 * @author yudong
 */
@Data
public class RecoveryMeta {
    private String host;
    private int port;
    private String user;
    private String pwd;

    private String schema;
    private boolean isMirror;
    private long startTimestamp;
    private long endTimestamp;
    private String sqlType;
    private String traceId;
    private String table;

    private int binlogFilesCountPerTask;
    private boolean injectTrouble;
}
