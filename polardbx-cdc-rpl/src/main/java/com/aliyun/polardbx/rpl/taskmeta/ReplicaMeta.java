/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;
import org.apache.commons.lang3.tuple.MutableTriple;

import java.util.List;

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
     * applier参数
     */
    ApplierType applierType = ApplierType.SPLIT;

    boolean compareAll;

    /*
     * 只在 ConflictStrategy.OVERWRITE 下起作用
     */
    boolean insertOnUpdateMiss;

    ConflictStrategy conflictStrategy = ConflictStrategy.OVERWRITE;

    /*
     * 控制是否同步ddl
     * enable ddl: default true
     */
    boolean enableDdl = true;
    String streamGroup;

    /*
     * 源为2.0时该参数控制是否采用快照解析
     * enableSrcLogicalMetaSnapshot: default true
     */
    boolean enableSrcLogicalMetaSnapshot = true;

    /*
     * dynamic cn
     */
    boolean enableDynamicMasterHost;
    List<MutableTriple<String, Integer, String>> masterHostList;
    String masterInstId;
}
