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
