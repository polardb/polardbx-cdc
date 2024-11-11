/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

/**
 * @author shicai.xsc 2021/1/8 11:28
 * @since 5.0.0.0
 */
@Data
public class HostInfo {

    private boolean usePolarxPoolCN;
    private String host;
    private int port;
    private String userName;
    private String password;
    private String schema;
    private HostType type;
    private long serverId;

    public HostInfo() {
    }

    public HostInfo(String host, int port, String userName, String password, String schema, HostType type,
                    long serverId) {
        this.usePolarxPoolCN = false;
        this.host = host;
        this.port = port;
        this.userName = userName;
        this.password = password;
        this.schema = schema;
        this.type = type;
        this.serverId = serverId;
    }
}
