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
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

/**
 * @author shicai.xsc 2021/2/3 21:51
 * @since 5.0.0.0
 */
@Data
public class ReplicaMeta {

    String channel;

    String masterHost;
    int masterPort;
    String masterUser;
    String masterPassword;
    HostType masterType;
    String position;
    /*
     * 写入server id
     */
    String serverId;
    String ignoreServerIds;

    String doDb;
    String ignoreDb;
    String doTable;
    String ignoreTable;
    String wildDoTable;
    String wildIgnoreTable;
    String rewriteDb;
    String extra;
    boolean imageMode;
    String skipTso;
    String skipUntilTso;

    String clusterId;

    /*
     * 多流新增参数
     * enable ddl: default true
     */
    boolean enableDdl = true;
    boolean enableDynamicMasterHost;
    String streamGroup;
}
