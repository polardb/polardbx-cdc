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
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.domain.BinlogTaskConfigStatus;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_SCHEDULE_DISPATCHER_COUNT_PER_NODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_SCHEDULE_DISPATCHER_MEMORY_MIN;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_SCHEDULE_DISPATCHER_MEMORY_UNIT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_SCHEDULE_DISPATCHER_ROCKSDB_RATIO;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_FORCE_DOWNLOAD_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_DUMPER_WEIGHT;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_TASK_WEIGHT;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.getStreamConfig;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BinlogXTopologyBuilder {
    private final String clusterId;

    private Map<String, String> recoverTsoMap;
    private Map<String, String> recoverFileMap;
    private String recoverType;

    public BinlogXTopologyBuilder(String clusterId) {
        this.clusterId = clusterId;
    }

    public Pair<Long, List<BinlogTaskConfig>> buildTopology(List<Container> containerList,
                                                            List<StorageInfo> storageInfoList,
                                                            String expectedStorageTso, long newVersion,
                                                            ClusterSnapshot preClusterSnapshot, long serverId) {
        List<BinlogTaskConfig> result = Lists.newArrayList();

        prepareRecoverInfo(expectedStorageTso);
        // 测试recover tso功能开关
        boolean forceRecover = isForceRecover();
        // 测试binlog下载功能开关
        boolean forceDownload = isForceDownload();

        List<BinlogTaskConfig> dumperList = buildDumpers(containerList, expectedStorageTso, newVersion,
            forceRecover, forceDownload, serverId);
        List<BinlogTaskConfig> dispatcherList = buildDispatchers(containerList, storageInfoList,
            expectedStorageTso, newVersion, serverId);

        dumperList.forEach(d -> {
            ExecutionConfig executionConfig = JSONObject.parseObject(d.getConfig(), ExecutionConfig.class);
            executionConfig.setSources(dispatcherList.stream()
                .map(BinlogTaskConfig::getTaskName).collect(Collectors.toList()));
            d.setConfig(JSONObject.toJSONString(executionConfig));
        });

        result.addAll(dumperList);
        result.addAll(dispatcherList);

        BinlogTaskConfigMapper taskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        List<BinlogTaskConfig> preDumperList =
            taskConfigMapper.select(s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(clusterId))
                .and(BinlogTaskConfigDynamicSqlSupport.version, isEqualTo(preClusterSnapshot.getVersion()))
                .and(BinlogTaskConfigDynamicSqlSupport.role, isEqualTo(TaskType.DumperX.name())));
        compareDumperConfigAndReset(preDumperList, dumperList, forceRecover);

        return Pair.of(serverId, result);
    }

    private static BinlogTaskConfig createTask(long id, TaskType taskType, Container container, String exeConfigStr,
                                               long version) {
        return BinlogTaskConfig.builder()
            .taskName(taskType.name() + "-" + Math.abs(id))
            .containerId(container.getContainerId())
            .ip(container.getIp())
            .port(container.holdPort())
            .config(exeConfigStr)
            .role(taskType.name())
            .status(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE)
            .version(version)
            .build();
    }

    private List<BinlogTaskConfig> buildDumpers(List<Container> containerList, String expectedStorageTso,
                                                long newVersion, boolean forceRecover, boolean forceDownload,
                                                long serverId) {
        List<BinlogTaskConfig> result = new ArrayList<>();
        int dumperWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_DUMPER_WEIGHT);
        int taskWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_TASK_WEIGHT);
        int rasterize = dumperWeight + taskWeight;
        List<XStream> xStreamList = getStreamConfig();

        // DumperX，一个Container一个DumperX进程，如果流的个数小于Container的个数，则对应Container上不启动DumperX进程
        TreeMap<Integer, Set<String>> dumperxTopologyMap = new TreeMap<>();
        int containerCount = containerList.size();
        for (int i = 0; i < xStreamList.size(); i++) {
            int index = i % containerCount;
            dumperxTopologyMap.computeIfAbsent(index, k -> new HashSet<>());
            dumperxTopologyMap.get(index).add(xStreamList.get(i).getStreamName());
        }

        dumperxTopologyMap.forEach((k, v) -> {
            Container container = containerList.get(k);
            int memUnit = containerList.get(k).getCapability().getFreeMemMb() / rasterize;
            int cpuUnit = containerList.get(k).getCapability().getVirCpu() / rasterize;
            Map<String, String> recoverTsoMap = new HashMap<>(v.size());
            Map<String, String> recoverFileMap = new HashMap<>(v.size());
            for (String streamName : v) {
                recoverTsoMap.put(streamName, this.recoverTsoMap.get(streamName));
                recoverFileMap.put(streamName, this.recoverFileMap.get(streamName));
            }

            ExecutionConfig exeConfig = new ExecutionConfig();
            exeConfig.setType(MergeSourceType.RPC.name());
            exeConfig.setTso(expectedStorageTso);
            exeConfig.setRecoverTsoMap(recoverTsoMap);
            exeConfig.setRecoverFileNameMap(recoverFileMap);
            exeConfig.setRecoverType(recoverType);
            exeConfig.setForceRecover(forceRecover);
            exeConfig.setForceDownload(forceDownload);
            exeConfig.setTimestamp(System.currentTimeMillis());
            exeConfig.setStreamNameSet(v);
            exeConfig.setRuntimeVersion(newVersion);
            exeConfig.setServerId(serverId);

            BinlogTaskConfig taskConfig = createTask(container.getContainerId().hashCode(), TaskType.DumperX,
                container, JSONObject.toJSONString(exeConfig), newVersion);
            taskConfig.setClusterId(clusterId);
            taskConfig.setMem(dumperWeight * memUnit);
            taskConfig.setVcpu(dumperWeight * cpuUnit);
            taskConfig.setStatus(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE);
            container.deductMem(dumperWeight * memUnit);
            result.add(taskConfig);
        });

        return result;
    }

    private void prepareRecoverInfo(String expectedStorageTso) {
        List<XStream> xStreamList = getStreamConfig();
        recoverTsoMap = new HashMap<>(xStreamList.size());
        recoverFileMap = new HashMap<>(xStreamList.size());
        for (XStream xStream : xStreamList) {
            String groupName = xStream.getGroupName();
            String streamName = xStream.getStreamName();
            List<String> recoverInfo = RecoverTsoBuilder.buildRecoverInfo(groupName, streamName, expectedStorageTso);
            recoverTsoMap.put(streamName, recoverInfo.get(0));
            recoverFileMap.put(streamName, recoverInfo.get(1));
            recoverType = recoverInfo.get(2);
        }
    }

    private boolean isForceRecover() {
        return DynamicApplicationConfig.getBoolean(TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED);
    }

    private static boolean isForceDownload() {
        return DynamicApplicationConfig.getBoolean(BINLOG_BACKUP_FORCE_DOWNLOAD_ENABLED);
    }

    private List<BinlogTaskConfig> buildDispatchers(List<Container> containerList, List<StorageInfo> storageInfoList,
                                                    String expectedStorageTso, long newVersion, long serverId) {
        sortByFreeResourceDesc(containerList);
        int dispatcherMemUnit = DynamicApplicationConfig.getInt(BINLOGX_SCHEDULE_DISPATCHER_MEMORY_UNIT);
        int dispatcherMinMem = DynamicApplicationConfig.getInt(BINLOGX_SCHEDULE_DISPATCHER_MEMORY_MIN);

        Map<Container, Integer> assignedCountMap = new HashMap<>();
        int assignedTaskCountPerContainer = DynamicApplicationConfig.getInt(BINLOGX_SCHEDULE_DISPATCHER_COUNT_PER_NODE);
        while (true) {
            int totalTaskCount = 0;
            assignedCountMap.clear();

            for (Container container : containerList) {
                if (container.getCapability().getFreeMemMb() < dispatcherMinMem) {
                    continue;
                }

                int taskCount;
                if (assignedTaskCountPerContainer > 0) {
                    taskCount = assignedTaskCountPerContainer;
                } else {
                    int freeMemMb = container.getCapability().getFreeMemMb();
                    taskCount = Math.max(1, freeMemMb / dispatcherMemUnit);
                }
                totalTaskCount += taskCount;
                assignedCountMap.put(container, taskCount);
            }

            // 如果dispatcher task的数量比DN的数量还多，则尝试降低Task的数量，一个Container只分配一个Task
            // 如果已经是一个Container只分配了一个Task，但Task的数量还是比DN的数量多，则忽略，下面进行storage分配的时候会处理这种情况
            if (totalTaskCount > storageInfoList.size() && assignedCountMap.values().stream().anyMatch(i -> i > 1)) {
                assignedTaskCountPerContainer = 1;
            } else {
                break;
            }
        }

        Map<Integer, BinlogTaskConfig> assignedTaskMap = new HashMap<>();
        int taskSequence = 0;
        for (Container container : containerList) {
            if (!assignedCountMap.containsKey(container)) {
                continue;
            }
            int taskCount = assignedCountMap.get(container);

            int mem = container.getCapability().getFreeMemMb() / taskCount;
            int cpu = container.getCapability().getCpu();//没有绑核操作，暂时不需要资源隔离
            double rocksDbRatio = DynamicApplicationConfig.getDouble(BINLOGX_SCHEDULE_DISPATCHER_ROCKSDB_RATIO);
            mem = mem - Double.valueOf(mem * rocksDbRatio).intValue();//给rocksdb预留一些内存资源
            for (int i = 0; i < taskCount; i++) {
                taskSequence++;
                BinlogTaskConfig taskConfig = createTask(taskSequence, TaskType.Dispatcher, container, "", newVersion);
                taskConfig.setClusterId(clusterId);
                taskConfig.setMem(mem);
                taskConfig.setVcpu(cpu);
                taskConfig.setStatus(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE);
                container.deductMem(mem);
                assignedTaskMap.put(taskSequence, taskConfig);
            }
        }

        Map<Integer, ExecutionConfig> assignedExeConfigMap = new HashMap<>();
        for (int i = 0; i < storageInfoList.size(); i++) {
            int index = i % assignedTaskMap.size();
            ExecutionConfig executionConfig = assignedExeConfigMap.computeIfAbsent(index, k -> {
                ExecutionConfig config = new ExecutionConfig();
                config.setType(MergeSourceType.BINLOG.name());
                config.setTso(expectedStorageTso);
                config.setRecoverTsoMap(this.recoverTsoMap);
                config.setRuntimeVersion(newVersion);
                config.setSources(new ArrayList<>());
                config.setServerId(serverId);
                return config;
            });
            executionConfig.getSources().add(storageInfoList.get(i).getStorageInstId());
        }

        List<BinlogTaskConfig> result = new ArrayList<>();
        assignedExeConfigMap.forEach((k, v) -> {
            BinlogTaskConfig taskConfig = assignedTaskMap.get(k + 1);
            taskConfig.setConfig(JSONObject.toJSONString(v));
            result.add(taskConfig);
        });
        return result;
    }

    public void sortByFreeResourceDesc(List<Container> containerList) {
        containerList.sort((o1, o2) -> {
            int free1 = o1.getCapability().getFreeMemMb();
            int free2 = o2.getCapability().getFreeMemMb();
            if (free1 == free2) {
                return 0;
            } else if (free1 < free2) {
                return 1;
            } else {
                return -1;
            }
        });
    }

    private void compareDumperConfigAndReset(List<BinlogTaskConfig> preDumpers, List<BinlogTaskConfig> currentDumpers,
                                             boolean forceRecover) {
        if (forceRecover) {
            return;
        }

        Set<DumperTopologyItem> preItems = buildDumperTopologyItems(preDumpers);
        Set<DumperTopologyItem> currentItems = buildDumperTopologyItems(currentDumpers);
        if (preItems.equals(currentItems)) {
            currentDumpers.forEach(d -> {
                String configStr = d.getConfig();
                ExecutionConfig executionConfig = JSONObject.parseObject(configStr, ExecutionConfig.class);
                executionConfig.setNeedCleanBinlogOfPreVersion(false);
                d.setConfig(JSONObject.toJSONString(executionConfig));
            });
        }
    }

    private Set<DumperTopologyItem> buildDumperTopologyItems(List<BinlogTaskConfig> list) {
        Set<DumperTopologyItem> items = new HashSet<>();
        for (BinlogTaskConfig config : list) {
            DumperTopologyItem item = new DumperTopologyItem();
            item.setDumperName(config.getTaskName());
            item.setContainerId(config.getContainerId());
            String configStr = config.getConfig();
            ExecutionConfig executionConfig = JSONObject.parseObject(configStr, ExecutionConfig.class);
            item.setStreams(executionConfig.getStreamNameSet());
            items.add(item);
        }
        return items;
    }

    @Data
    static class DumperTopologyItem {
        private String dumperName;
        private String containerId;
        private Set<String> streams;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DumperTopologyItem that = (DumperTopologyItem) o;
            return dumperName.equals(that.dumperName) &&
                containerId.equals(that.containerId) &&
                streams.equals(that.streams);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dumperName, containerId, streams);
        }
    }
}
