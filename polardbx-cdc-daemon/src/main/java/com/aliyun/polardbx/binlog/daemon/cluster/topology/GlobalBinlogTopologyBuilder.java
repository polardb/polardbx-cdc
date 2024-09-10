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

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.BinlogTaskConfigStatus;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_FORCE_DOWNLOAD_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_DUMPER_SLAVE_MAX_MEM;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_DUMPER_WEIGHT;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_TASK_WEIGHT;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_USE_RELAY_TASK_THRESHOLD_WITH_DN_NUM;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class GlobalBinlogTopologyBuilder {
    private final String clusterId;

    public GlobalBinlogTopologyBuilder(String clusterId) {
        this.clusterId = clusterId;
    }

    /**
     * 给定一个容器列表，确定每个容器中运行哪些Task或Dumper
     */
    public Pair<Long, List<BinlogTaskConfig>> buildTopology(List<Container> containerList,
                                                            List<StorageInfo> storageInfoList,
                                                            String expectedStorageTso, long newVersion,
                                                            String dumperMasterNodeId, long serverId) {
        int containerCount = containerList.size();

        // (m * Dumper) + (1 * Final) + (n * Relay)
        List<List<StorageInfo>> relayStorageList = calcStorageListForRelayTask(containerCount, storageInfoList);
        List<BinlogTaskConfig> result = Lists.newArrayList();

        int dumperWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_DUMPER_WEIGHT);
        int taskWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_TASK_WEIGHT);
        int totalWeight = dumperWeight + taskWeight;
        int vcpu = containerList.get(0).getCapability().getVirCpu();
        int memUnit = containerList.get(0).getCapability().getFreeMemMb() / totalWeight;
        int memPerTask = relayStorageList.size() > 0 ? (memUnit * taskWeight) / 2 : memUnit * taskWeight;
        int memPerDumper = memUnit * dumperWeight;

        // build recover tso
        List<String> recoverInfo =
            RecoverTsoBuilder.buildRecoverInfo(CommonConstants.GROUP_NAME_GLOBAL, CommonConstants.STREAM_NAME_GLOBAL,
                expectedStorageTso);
        Map<String, String> recoverTsoMap = new HashMap<>(1);
        recoverTsoMap.put(CommonConstants.STREAM_NAME_GLOBAL, recoverInfo.get(0));
        Map<String, String> recoverFileNameMap = new HashMap<>(1);
        recoverFileNameMap.put(CommonConstants.STREAM_NAME_GLOBAL, recoverInfo.get(1));
        // 测试recover tso功能开关
        boolean forceRecover = isForceRecover();
        // 测试binlog下载功能开关
        boolean forceDownload = isForceDownload();

        // 为每个容器分配一个Dumper，并计算出每个Dumper的配置
        for (int i = 0; i < containerList.size(); i++) {
            ExecutionConfig tc = new ExecutionConfig();
            tc.setType(MergeSourceType.RPC.name());
            tc.setSources(Lists.newArrayList(TaskType.Final.name()));
            tc.setTso(expectedStorageTso);
            tc.setRecoverTsoMap(recoverTsoMap);
            tc.setRecoverFileNameMap(recoverFileNameMap);
            tc.setRecoverType(recoverInfo.get(2));
            tc.setForceRecover(forceRecover);
            tc.setForceDownload(forceDownload);
            tc.setTimestamp(System.currentTimeMillis());
            tc.setRuntimeVersion(newVersion);
            tc.setServerId(serverId);

            Container container = containerList.get(i);
            container.deductMem(memPerDumper);
            tc.setReservedMemMb(container.getCapability().getReservedMemMb());

            BinlogTaskConfig dumperConfig =
                makeTask((long) (i + 1), TaskType.Dumper, container, JSONObject.toJSONString(tc), newVersion);
            dumperConfig.setClusterId(clusterId);
            dumperConfig.setMem(memPerDumper);
            dumperConfig.setVcpu(vcpu);
            result.add(dumperConfig);
        }

        // 如果DN节点数目超过阈值，启用RelayTask，计算每个RelayTask的配置
        Container finalContainer = selectContainer4Final(containerList, dumperMasterNodeId);
        List<BinlogTaskConfig> relayTaskList = new ArrayList<>();
        if (!relayStorageList.isEmpty()) {
            AtomicLong index = new AtomicLong(0);
            Iterator<List<StorageInfo>> iterator = relayStorageList.iterator();
            for (Container container : containerList) {
                relayTaskList.add(buildRelayTask(clusterId, memPerTask, vcpu, expectedStorageTso,
                    newVersion, container, index, iterator.next(), serverId));
                if (container != finalContainer) {
                    relayTaskList.add(buildRelayTask(clusterId, memPerTask, vcpu, expectedStorageTso,
                        newVersion, container, index, iterator.next(), serverId));
                }
            }
            if (iterator.hasNext()) {
                throw new PolardbxException(
                    "dispatch storage to relay task error, remaining storage list is " + iterator.next());
            }
            result.addAll(relayTaskList);
        }

        // Final
        finalContainer.deductMem(memPerTask);
        ExecutionConfig config = new ExecutionConfig();
        config.setType(!relayTaskList.isEmpty() ? MergeSourceType.RPC.name() : MergeSourceType.BINLOG.name());
        if (!relayTaskList.isEmpty()) {
            config.setSources(relayTaskList.stream().map(BinlogTaskConfig::getTaskName).collect(Collectors.toList()));
        } else {
            config.setSources(storageInfoList.stream().map(StorageInfo::getStorageInstId).collect(Collectors.toList()));
        }
        config.setTso(expectedStorageTso);
        config.setRuntimeVersion(newVersion);
        config.setServerId(serverId);
        config.setReservedMemMb(finalContainer.getCapability().getReservedMemMb());
        BinlogTaskConfig finalConfig = makeTask(0L, TaskType.Final, finalContainer,
            JSONObject.toJSONString(config), newVersion);
        finalConfig.setClusterId(clusterId);
        finalConfig.setMem(memPerTask);
        finalConfig.setVcpu(vcpu);
        finalConfig.setStatus(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE);
        result.add(finalConfig);

        // rewrite dumper master memory
        // 如果dumper master和final task不在一个容器，则尝试调高dumper master的内存占用
        if (relayTaskList.size() <= 0 && !StringUtils.equals(dumperMasterNodeId, finalContainer.getContainerId())) {
            int newWeight = dumperWeight + taskWeight;
            Optional<BinlogTaskConfig> optional = result.stream().filter(t -> TaskType.Dumper.name().equals(t.getRole())
                && StringUtils.equals(t.getContainerId(), dumperMasterNodeId)).findFirst();
            if (optional.isPresent()) {
                optional.get().setMem(memUnit * newWeight);
                optional.get().setVcpu(containerList.get(0).getCapability().getVirCpu());
            }
        }

        // 如果dumper master和final task不在一个容器，则dumper salve和final是放在一个容器的，尝试调低dumper slave的内存
        // 如果dumper slave的内存大于设定的最大值，将多出的内存分配给task，task对内存的需求dumper要旺盛的多
        if (!StringUtils.equals(dumperMasterNodeId, finalContainer.getContainerId())) {
            Optional<BinlogTaskConfig> optional = result.stream().filter(t -> TaskType.Dumper.name().equals(t.getRole())
                && StringUtils.equals(t.getContainerId(), finalContainer.getContainerId())).findFirst();
            if (optional.isPresent()) {
                int slaveMem = optional.get().getMem();
                int finalMem = finalConfig.getMem();
                int maxSlaveMem = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_DUMPER_SLAVE_MAX_MEM);
                if (slaveMem > maxSlaveMem) {
                    int deduct = slaveMem - maxSlaveMem;
                    optional.get().setMem(maxSlaveMem);
                    finalConfig.setMem(finalMem + deduct);
                }
            }
        }
        return Pair.of(serverId, result);
    }

    private static BinlogTaskConfig buildRelayTask(String clusterId, int mem, int vcpu, String expectedStorageTso,
                                                   long newVersion, Container container, AtomicLong index,
                                                   List<StorageInfo> storageInfoList, long serverId) {
        container.deductMem(mem);
        ExecutionConfig config = new ExecutionConfig();
        config.setType(MergeSourceType.BINLOG.name());
        config.setSources(
            storageInfoList.stream().map(StorageInfo::getStorageInstId).collect(Collectors.toList()));
        config.setTso(expectedStorageTso);
        config.setServerId(serverId);
        config.setReservedMemMb(container.getCapability().getReservedMemMb());

        BinlogTaskConfig relayTaskConfig =
            makeTask(index.incrementAndGet(), TaskType.Relay, container, JSONObject.toJSONString(config), newVersion);
        relayTaskConfig.setClusterId(clusterId);
        relayTaskConfig.setMem(mem);
        relayTaskConfig.setVcpu(vcpu);
        relayTaskConfig.setStatus(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE);
        return relayTaskConfig;
    }

    private static BinlogTaskConfig makeTask(Long id, TaskType taskType, Container container, String ext,
                                             long version) {
        return BinlogTaskConfig.builder()
            .taskName(id == 0 ? taskType.name() : taskType.name() + "-" + id)
            .containerId(container.getContainerId())
            .ip(container.getIp())
            .port(container.holdPort())
            .config(ext)
            .role(taskType.name())
            .status(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE)
            .version(version)
            .build();
    }

    /**
     * 资源扣减
     */
    private static Container selectContainer4Final(List<Container> containers, String dumperMasterNode) {
        //将Task和DumperMaster分配到不同容器
        Collections.sort(containers);
        return containers.size() > 1 ?
            containers.stream().filter(c -> !c.getContainerId().equals(dumperMasterNode))
                .collect(Collectors.toList()).get(0) : containers.get(0);
    }

    private static boolean isForceRecover() {
        return DynamicApplicationConfig.getBoolean(TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED);
    }

    private static boolean isForceDownload() {
        return DynamicApplicationConfig.getBoolean(BINLOG_BACKUP_FORCE_DOWNLOAD_ENABLED);
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
     * 当DN节点数据超过阈值时，使用RelayTask
     * 计算是否需要使用RelayTask，以及如果使用RelayTask，每个RelayTask需要对接的DN节点
     */
    private List<List<StorageInfo>> calcStorageListForRelayTask(int cdcCount, List<StorageInfo> storageInfoList) {
        if (cdcCount == 1 || storageInfoList.size() < DynamicApplicationConfig
            .getInt(TOPOLOGY_USE_RELAY_TASK_THRESHOLD_WITH_DN_NUM)) {
            return Lists.newArrayList();
        } else {
            // 除Final所在的容器，每个容器分配两个RelayTask
            // Final所在的容器中分配一个RelayTask
            int relayTaskCount = (cdcCount - 1) * 2 + 1;
            if (storageInfoList.size() <= relayTaskCount) {
                return Lists.newArrayList();
            }

            List<List<StorageInfo>> relayTaskStorageList = new ArrayList<>(relayTaskCount);
            for (int i = 0; i < relayTaskCount; i++) {
                relayTaskStorageList.add(new ArrayList<>());
            }
            for (int i = 0; i < storageInfoList.size(); i++) {
                int index = i % relayTaskCount;
                relayTaskStorageList.get(index).add(storageInfoList.get(i));
            }
            return relayTaskStorageList;
        }
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
        GlobalBinlogTopologyBuilder storageCountStrategy = new GlobalBinlogTopologyBuilder("test");

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
