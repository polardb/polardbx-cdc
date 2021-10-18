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

package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.cluster.function.TaskDistributionFunction;
import com.aliyun.polardbx.binlog.domain.BinlogTaskConfigStatus;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.TaskConfig;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_DUMPER_WEIGHT;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_TASK_WEIGHT;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class StorageCountStrategy implements TaskDistributionFunction {
    private static final Gson GSON = new GsonBuilder().create();

    private final String clusterId;
    private int threshold = 4;//单个task上承载的binlog流个数

    public StorageCountStrategy(String clusterId) {
        this.clusterId = clusterId;
    }

    public StorageCountStrategy(String clusterId, int threshold) {
        this.clusterId = clusterId;
        this.threshold = threshold;
    }

    @Override
    public List<BinlogTaskConfig> apply(List<Container> containerList, List<StorageInfo> storageInfoList,
                                        String expectedStorageTso, long newVersion, String dumperMasterNode) {

        int containerCount = containerList.size();
        int storageCount = storageInfoList.size();
        int relayTaskCount = 0;//短期不考虑实现Relay了

        // dumper + 1 * Final + relay
        int totalTaskCount = containerCount + 1 + relayTaskCount;

        List<BinlogTaskConfig> result = Lists.newArrayListWithCapacity(totalTaskCount);

        int dumperWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_DUMPER_WEIGHT);
        int taskWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_TASK_WEIGHT);
        int rasterize = dumperWeight + taskWeight;
        //int rasterize = rasterize(totalTaskCount, containerCount);

        int mem = containerList.get(0).getCapability().getFreeMemMb() / rasterize;
        int cpu = containerList.get(0).getCapability().getVirCpu() / rasterize;

        //Dumper
        for (int i = 0; i < containerList.size(); i++) {
            TaskConfig tc = new TaskConfig();
            tc.setType(MergeSourceType.RPC.name());
            tc.setSources(Lists.newArrayList(TaskType.Final.name()));
            tc.setTso(expectedStorageTso);
            Container container = containerList.get(i);
            container.deductMem(mem);
            BinlogTaskConfig config = makeTask((long) (i + 1), TaskType.Dumper, container,
                GSON.toJson(tc), newVersion);
            config.setClusterId(clusterId);
            config.setMem(mem * dumperWeight);
            config.setVcpu(cpu * dumperWeight);
            result.add(config);
        }

        //Final
        Container finalContainer = deduct(containerList, mem * 2, dumperMasterNode);
        TaskConfig finalTaskConfig = new TaskConfig();
        finalTaskConfig.setType(relayTaskCount == 0 ? MergeSourceType.BINLOG.name() : MergeSourceType.RPC.name());
        finalTaskConfig.setSources(relayTaskCount == 0 ?
            storageInfoList.stream().map(StorageInfo::getStorageInstId).collect(Collectors.toList())
            : relayTaskNames(relayTaskCount));
        finalTaskConfig.setTso(expectedStorageTso);
        BinlogTaskConfig finalConfig = makeTask(0L, TaskType.Final, finalContainer,
            GSON.toJson(finalTaskConfig), newVersion);
        finalConfig.setClusterId(clusterId);
        finalConfig.setMem(mem * taskWeight);
        finalConfig.setVcpu(cpu * taskWeight);
        finalConfig.setStatus(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE);
        result.add(finalConfig);

        if (relayTaskCount > 0) {
            int size = storageCount / relayTaskCount + (storageCount % relayTaskCount == 0 ? 0 : 1);
            List<List<StorageInfo>> partition = Lists.partition(storageInfoList, size);
            //Relay
            for (int i = 0; i < relayTaskCount; i++) {
                TaskConfig tc = new TaskConfig();
                tc.setType(MergeSourceType.BINLOG.name());
                List<String> storageConfigs = partition.get(i).stream().map(StorageInfo::getStorageInstId).collect(
                    Collectors.toList());
                tc.setSources(storageConfigs);
                tc.setTso(expectedStorageTso);
                Container container = deduct(containerList, mem, dumperMasterNode);
                BinlogTaskConfig config = makeTask((long) (i + 1), TaskType.Relay, container,
                    GSON.toJson(tc), newVersion);
                config.setClusterId(clusterId);
                config.setMem(mem);
                config.setVcpu(cpu);
                result.add(config);
            }
        }

        return result;
    }

    private static BinlogTaskConfig makeTask(Long id, TaskType taskType, Container container, String ext,
                                             long version) {
        BinlogTaskConfig config = BinlogTaskConfig.builder()
            .taskName(id == 0 ? taskType.name() : taskType.name() + "-" + id)
            .containerId(container.getContainerId())
            .ip(container.getNodeHttpAddress())
            .port(container.holdPort())
            .config(ext)
            .role(taskType.name())
            .status(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE)
            .version(version)
            .build();
        return config;
    }

    private static List<String> relayTaskNames(int taskCount) {
        List<String> result = Lists.newArrayListWithCapacity(taskCount);
        for (int i = 0; i < taskCount; i++) {
            result.add(TaskType.Relay.name() + "-" + (i + 1));
        }
        return result;
    }

    /**
     * 资源扣减
     */
    private static Container deduct(List<Container> containers, int mem, String dumperMasterNode) {
        //将Task和DumperMaster分配到不同容器，目前我们暂时还不考虑RelayTask，所以先这么简单的实现，TODO
        Collections.sort(containers);
        Container container = containers.size() > 1 ?
            containers.stream().filter(c -> !c.getContainerId().equals(dumperMasterNode))
                .collect(Collectors.toList()).get(0) : containers.get(0);
        container.deductMem(mem);
        return container;
    }

    /**
     * 容器栅格化（Final，Dumper2倍Relay 内存配比）
     *
     * @param tc 任务个数
     * @param nc 容器个数
     * @return 栅格化比例
     */
    public int rasterize(int tc, int nc) {
        //机器富余
        int count;
        if (tc <= nc) {
            log.debug("{} {} {}", tc, nc, 2);
            count = 2;
        } else {
            if (nc < 2) {
                //只有一台机器 dumper(2)+final(2)+relay
                count = tc <= 3 ? tc * 2 : 2 * 2 + 1 * 2 + (tc - 2 - 1);
                log.debug("<2  {} {} {}", tc, nc, count);
            } else {
                //剩余的relay转化为何dumper，relay规格相等的任务
                int left = tc - 3 - (nc == 2 ? 1 : (nc - 3)) * 2;
                int append = (left + nc - 1) / nc;
                count = (nc == 2 ? 4 : 2) + append;
                log.debug(">=3 {} {} {}", tc, nc, count);
            }
        }
        return count;
    }

    /**
     * 容器栅格化（Final:Dumper:Relay = x:y:z ）
     *
     * @param tc 任务个数
     * @param nc 容器个数
     * @param x Dumper内存配比
     * @param y Final内存配比
     * @param z Relay内存配比
     * @return 栅格化比例
     */
    public int rasterize(int tc, int nc, int x, int y, int z) {
        assert (nc | tc) > 0;
        //机器富余
        int count;
        if (tc <= nc) {
            log.debug("    {} {} {}", tc, nc, 2);
            count = x;
        } else {
            int xy = Math.max(x, y);
            int xyz = Math.max(xy, z);
            int left, append;
            switch (nc) {
            case 1:
                count = tc <= 3 ? tc * 2 : 2 * x + 1 * y + (tc - 2 - 1) * z;
                log.debug("<2  {} {} {}", tc, nc, count);
                break;
            case 2:
                left = xy <= z ? (tc - 2 - 1 - 1) * z : (tc - 2 - 1 - y / z) * z;
                append = left <= 0 ? 0 : (left + nc - 1) / nc;
                count = x + y + append;
                log.debug("==2 {} {} {}", tc, nc, count);
                break;
            default:
                left = xy <= z ? (tc - nc) * z :
                    (tc - 3 - Math.abs(x - y) / z - (nc - 3) * xy / z) * z;
                append = left <= 0 ? 0 : (left + nc - 1) / nc;
                count = xyz + append;
                log.debug(">=3 {} {} {}", tc, nc, count);
                break;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        StorageCountStrategy storageCountStrategy = new StorageCountStrategy("test");

        log.debug("=============1=============");
        storageCountStrategy.rasterize(1, 1);
        storageCountStrategy.rasterize(2, 1);
        storageCountStrategy.rasterize(3, 1);
        storageCountStrategy.rasterize(4, 1);
        storageCountStrategy.rasterize(5, 1);
        storageCountStrategy.rasterize(6, 1);
        storageCountStrategy.rasterize(7, 1);

        log.debug("=============x=============");
        storageCountStrategy.rasterize(1, 1, 2, 2, 1);
        storageCountStrategy.rasterize(2, 1, 2, 2, 1);
        storageCountStrategy.rasterize(3, 1, 2, 2, 1);
        storageCountStrategy.rasterize(4, 1, 2, 2, 1);
        storageCountStrategy.rasterize(5, 1, 2, 2, 1);
        storageCountStrategy.rasterize(6, 1, 2, 2, 1);
        storageCountStrategy.rasterize(7, 1, 2, 2, 1);

        log.debug("=============2=============");
        storageCountStrategy.rasterize(1, 2);
        storageCountStrategy.rasterize(2, 2);
        storageCountStrategy.rasterize(3, 2);
        storageCountStrategy.rasterize(4, 2);
        storageCountStrategy.rasterize(5, 2);
        storageCountStrategy.rasterize(6, 2);
        storageCountStrategy.rasterize(7, 2);
        storageCountStrategy.rasterize(8, 2);
        storageCountStrategy.rasterize(9, 2);
        log.debug("=============x=============");
        storageCountStrategy.rasterize(1, 2, 2, 2, 1);
        storageCountStrategy.rasterize(2, 2, 2, 2, 1);
        storageCountStrategy.rasterize(3, 2, 2, 2, 1);
        storageCountStrategy.rasterize(4, 2, 2, 2, 1);
        storageCountStrategy.rasterize(5, 2, 2, 2, 1);
        storageCountStrategy.rasterize(6, 2, 2, 2, 1);
        storageCountStrategy.rasterize(7, 2, 2, 2, 1);
        storageCountStrategy.rasterize(8, 2, 2, 2, 1);
        storageCountStrategy.rasterize(9, 2, 2, 2, 1);
        log.debug("=============3=============");
        storageCountStrategy.rasterize(2, 3);
        storageCountStrategy.rasterize(3, 3);
        storageCountStrategy.rasterize(4, 3);
        storageCountStrategy.rasterize(5, 3);
        storageCountStrategy.rasterize(6, 3);
        storageCountStrategy.rasterize(7, 3);
        log.debug("=============x=============");
        storageCountStrategy.rasterize(2, 3, 2, 2, 1);
        storageCountStrategy.rasterize(3, 3, 2, 2, 1);
        storageCountStrategy.rasterize(4, 3, 2, 2, 1);
        storageCountStrategy.rasterize(5, 3, 2, 2, 1);
        storageCountStrategy.rasterize(6, 3, 2, 2, 1);
        storageCountStrategy.rasterize(7, 3, 2, 2, 1);
        log.debug("=============4=============");
        storageCountStrategy.rasterize(3, 4);
        storageCountStrategy.rasterize(4, 4);
        storageCountStrategy.rasterize(5, 4);
        storageCountStrategy.rasterize(6, 4);
        storageCountStrategy.rasterize(7, 4);
        storageCountStrategy.rasterize(8, 4);
        storageCountStrategy.rasterize(9, 4);
        storageCountStrategy.rasterize(10, 4);
        log.debug("=============x=============");
        storageCountStrategy.rasterize(3, 4, 2, 2, 1);
        storageCountStrategy.rasterize(4, 4, 2, 2, 1);
        storageCountStrategy.rasterize(5, 4, 2, 2, 1);
        storageCountStrategy.rasterize(6, 4, 2, 2, 1);
        storageCountStrategy.rasterize(7, 4, 2, 2, 1);
        storageCountStrategy.rasterize(8, 4, 2, 2, 1);
        storageCountStrategy.rasterize(9, 4, 2, 2, 1);
        storageCountStrategy.rasterize(10, 4, 2, 2, 1);

    }
}
