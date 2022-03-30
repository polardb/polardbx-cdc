/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
