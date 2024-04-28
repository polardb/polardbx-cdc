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
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.domain.BinlogTaskConfigStatus;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.ColumnarTaskConfig;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author wenki
 */
@Slf4j
public class ColumnarTopologyBuilder {
    private static final Gson GSON = new GsonBuilder().create();

    private final String clusterId;
    public static int totalMem;

    public ColumnarTopologyBuilder(String clusterId) {
        this.clusterId = clusterId;
    }

    public List<ColumnarTaskConfig> buildTopology(List<Container> containerList, long newVersion, String leaderIp) {
        List<ColumnarTaskConfig> result = Lists.newArrayList();

        int vcpu = containerList.get(0).getCapability().getVirCpu();
        int mem = containerList.get(0).getCapability().getFreeMemMbWithoutRatio();

        totalMem = calculateHeapMemory(mem);

        // Columnar
        int leaderCnt = 0;
        for (int i = 0; i < containerList.size(); i++) {
            Container container = containerList.get(i);
            container.deductMem(mem);
            String role = "Follower";
            if (container.getIp().equals(leaderIp) && leaderCnt == 0) {
                role = "Leader";
                leaderCnt++;
            }
            ColumnarTaskConfig columnarConfig = makeTask((long) (i + 1), TaskType.Columnar, container,
                "", role, newVersion);
            columnarConfig.setClusterId(clusterId);
            columnarConfig.setMem(mem);
            columnarConfig.setVcpu(vcpu);
            result.add(columnarConfig);
        }

        return result;
    }

    private static int calculateHeapMemory(int freeMem) {
        int heapMemory;
        if (freeMem < 2048) {
            heapMemory = 1024;
        } else if (freeMem <= 4096) {
            heapMemory = 2048;
        } else if (freeMem <= 8192) {
            heapMemory = 4096;
        } else if (freeMem <= 16384) {
            heapMemory = 10240;
        } else if (freeMem <= 32768) {
            heapMemory = 24576;
        } else if (freeMem <= 65536) {
            heapMemory = 51200;
        } else if (freeMem <= 131072) {
            heapMemory = 112640;
        } else {
            heapMemory = 122880;
        }
        return heapMemory;
    }

    private static ColumnarTaskConfig makeTask(Long id, TaskType taskType, Container container, String ext, String role,
                                               long version) {
        return ColumnarTaskConfig.builder()
            .taskName(id == 0 ? taskType.name() : taskType.name() + "-" + id)
            .containerId(container.getContainerId())
            .ip(container.getIp())
            .port(container.holdPort())
            .config(ext)
            .role(role)
            .status(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE)
            .version(version)
            .build();
    }
}
