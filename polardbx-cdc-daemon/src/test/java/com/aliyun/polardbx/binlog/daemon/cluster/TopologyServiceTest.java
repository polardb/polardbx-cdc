/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.GlobalBinlogTopologyService;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class TopologyServiceTest extends BaseTestWithGmsTables {

    @Test
    public void calculateTopology() throws Throwable {
        TableDataInitUtil.initNodeInfo(configProvider);

        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        GlobalBinlogTopologyService topologyService = new GlobalBinlogTopologyService(clusterId, "");
        topologyService.tryBuild();
    }
}
