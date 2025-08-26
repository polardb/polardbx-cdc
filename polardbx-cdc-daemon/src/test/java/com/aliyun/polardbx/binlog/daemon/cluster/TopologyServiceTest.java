/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.GlobalBinlogTopologyService;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.daemon.cluster.TableDataMocker.mockNodeInfoList;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Ignore
public class TopologyServiceTest extends BaseTest {

    @Test
    public void calculateTopology() throws Throwable {
        mockConfig(CLUSTER_ID, "cluster_id_test");
        List<NodeInfo> nodeInfoList = mockNodeInfoList();

        try (MockedStatic<SpringContextHolder> holder = mockStatic(SpringContextHolder.class, CALLS_REAL_METHODS)) {
            NodeInfoMapper nodeInfoMapper = mock(NodeInfoMapper.class);
            when(nodeInfoMapper.select(any())).thenReturn(nodeInfoList);
            when(getObject(NodeInfoMapper.class)).thenReturn(nodeInfoMapper);

            String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            GlobalBinlogTopologyService topologyService = new GlobalBinlogTopologyService(clusterId, "");
            topologyService.tryBuild();
        }
    }
}
