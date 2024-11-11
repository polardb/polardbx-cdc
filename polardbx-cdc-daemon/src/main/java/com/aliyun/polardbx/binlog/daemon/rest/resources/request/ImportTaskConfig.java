/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources.request;

import lombok.Data;

import java.util.List;

@Data
public class ImportTaskConfig {
    private ConnectionInfo srcConn;
    private List<ConnectionInfo> srcPhyConnList;
    private List<String> tableList;
    private String rules;
    private String dstDbName;
    private String srcDbName;
    private String rdsUid;
    private String rdsBid;
    private boolean isPrivate;
}
