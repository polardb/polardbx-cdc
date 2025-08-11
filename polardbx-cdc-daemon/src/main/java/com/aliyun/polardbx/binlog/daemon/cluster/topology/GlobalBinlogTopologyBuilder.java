/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
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
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_DUMPER_MASTER_MAX_RATIO;
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

        // build recover tso
        List<String> recoverInfo =
            RecoverTsoBuilder.buildRecoverInfo(CommonConstants.GROUP_NAME_GLOBAL, CommonConstants.STREAM_NAME_GLOBAL,
                expectedStorageTso);
        Map<String, String> recoverTsoMap = new HashMap<>(1);
        recoverTsoMap.put(CommonConstants.STREAM_NAME_GLOBAL, recoverInfo.get(0));
        Map<String, String> recoverFileNameMap = new HashMap<>(1);
        recoverFileNameMap.put(CommonConstants.STREAM_NAME_GLOBAL, recoverInfo.get(1));
        // 测试recover tso功能开关,这个开关在实验室是random的
        boolean forceRecover = isForceRecover();
        // 测试binlog下载功能开关
        boolean forceDownload = isForceDownload();

        // 为每个容器分配一个Dumper，并计算出每个Dumper的配置
        Container dumperMasterContainer = null;
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
            CpuMemoryItem cpuMemoryItem = calcCpuMemoryItem(container, relayStorageList.size());
            if (StringUtils.equals(dumperMasterNodeId, container.getContainerId())) {
                dumperMasterContainer = container;
            }

            container.deductMem(cpuMemoryItem.memPerDumper);
            tc.setReservedMemMb(container.getCapability().getReservedMemMb());

            BinlogTaskConfig dumperConfig =
                makeTask((long) (i + 1), TaskType.Dumper, container, JSONObject.toJSONString(tc), newVersion);
            dumperConfig.setClusterId(clusterId);
            dumperConfig.setMem(cpuMemoryItem.memPerDumper);
            dumperConfig.setVcpu(cpuMemoryItem.vcpu);
            result.add(dumperConfig);
        }

        // 如果DN节点数目超过阈值，启用RelayTask，计算每个RelayTask的配置
        Container finalContainer = selectContainer4Final(containerList, dumperMasterNodeId);
        List<BinlogTaskConfig> relayTaskList = new ArrayList<>();
        if (!relayStorageList.isEmpty()) {
            AtomicLong index = new AtomicLong(0);
            Iterator<List<StorageInfo>> iterator = relayStorageList.iterator();
            for (Container container : containerList) {
                CpuMemoryItem cpuMemoryItem = calcCpuMemoryItem(container, relayStorageList.size());

                relayTaskList.add(buildRelayTask(clusterId, cpuMemoryItem.memPerTask, cpuMemoryItem.vcpu,
                    expectedStorageTso, newVersion, container, index, iterator.next(), serverId));
                if (container != finalContainer) {
                    relayTaskList.add(buildRelayTask(clusterId, cpuMemoryItem.memPerTask, cpuMemoryItem.vcpu,
                        expectedStorageTso, newVersion, container, index, iterator.next(), serverId));
                }
            }
            if (iterator.hasNext()) {
                throw new PolardbxException(
                    "dispatch storage to relay task error, remaining storage list is " + iterator.next());
            }
            result.addAll(relayTaskList);
        }

        // Final
        CpuMemoryItem finalCpuMemoryItem = calcCpuMemoryItem(finalContainer, relayStorageList.size());
        finalContainer.deductMem(finalCpuMemoryItem.memPerTask);
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
        finalConfig.setMem(finalCpuMemoryItem.memPerTask);
        finalConfig.setVcpu(finalCpuMemoryItem.vcpu);
        finalConfig.setStatus(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE);
        result.add(finalConfig);

        // rewrite dumper master memory
        // 如果dumper master和final task不在一个容器，则尝试调高dumper master的内存占用
        if (relayTaskList.size() <= 0 && !StringUtils.equals(dumperMasterNodeId, finalContainer.getContainerId())) {
            int dumperWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_DUMPER_WEIGHT);
            int taskWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_TASK_WEIGHT);
            double masterMaxRatio = DynamicApplicationConfig.getDouble(TOPOLOGY_RESOURCE_DUMPER_MASTER_MAX_RATIO);
            int newWeight = dumperWeight + taskWeight;

            Optional<BinlogTaskConfig> optional = result.stream().filter(t -> TaskType.Dumper.name().equals(t.getRole())
                && StringUtils.equals(t.getContainerId(), dumperMasterNodeId)).findFirst();
            CpuMemoryItem cpuMemoryItem = calcCpuMemoryItem(dumperMasterContainer, relayStorageList.size());

            if (optional.isPresent()) {
                optional.get().setMem(Double.valueOf(cpuMemoryItem.memUnit * newWeight * masterMaxRatio).intValue());
                optional.get().setVcpu(dumperMasterContainer.getCapability().getVirCpu());
            }
        }

        // 如果dumper master和final task不在一个容器，则dumper salve和final是放在一个容器的，尝试调低dumper slave的内存
        // 如果dumper slave的内存大于设定的最大值，将多出的内存分配给task，task对内存的需求比dumper要旺盛的多
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

    private CpuMemoryItem calcCpuMemoryItem(Container container, int relayStorageSize) {
        int dumperWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_DUMPER_WEIGHT);
        int taskWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_TASK_WEIGHT);
        int totalWeight = dumperWeight + taskWeight;

        int vcpu = container.getCapability().getVirCpu();
        int memUnit = container.getCapability().getBaseAvailableMemMb() / totalWeight;
        int memPerTask = relayStorageSize > 0 ? (memUnit * taskWeight) / 2 : memUnit * taskWeight;
        int memPerDumper = memUnit * dumperWeight;

        return new CpuMemoryItem(vcpu, memUnit, memPerTask, memPerDumper);
    }

    @Data
    @AllArgsConstructor
    @ToString
    static class CpuMemoryItem {
        int vcpu;
        int memUnit;
        int memPerTask;
        int memPerDumper;
    }
}
