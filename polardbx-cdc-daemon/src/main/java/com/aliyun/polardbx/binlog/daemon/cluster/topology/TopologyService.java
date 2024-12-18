/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

/**
 * created by ziyang.lb
 **/
public interface TopologyService {

    /**
     * try build or rebuild topology config
     */
    void tryBuild() throws Throwable;
}
