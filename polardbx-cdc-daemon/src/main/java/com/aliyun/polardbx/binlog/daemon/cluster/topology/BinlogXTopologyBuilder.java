/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.service.XStreamService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_HASH_LEVEL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_FORCE_DOWNLOAD_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_DUMPER_WEIGHT;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_TASK_WEIGHT;
import static com.aliyun.polardbx.binlog.service.StorageHistoryService.saveStorageHistoryDetail;
import static com.aliyun.polardbx.binlog.service.XStreamService.buildAndSaveXStream;
import static com.aliyun.polardbx.binlog.service.XStreamService.getXStreamsInCurrentCluster;
import static com.aliyun.polardbx.binlog.service.XStreamService.markStreamAsPending;
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

    public Topology buildTopology(List<Container> containerList,
                                  List<StorageInfo> storageInfoList,
                                  String expectedStorageTso,
                                  long newVersion,
                                  ClusterSnapshot preClusterSnapshot,
                                  long serverId,
                                  String instructionId) {
        Topology topology = new Topology();

        // process stream for add/remove datanode
        Map<String, String> streamStorageMap = buildStreamStorageMap(storageInfoList,
            preClusterSnapshot, expectedStorageTso, instructionId);
        // build recover info
        prepareRecoverInfo(expectedStorageTso);
        // 测试recover tso功能开关
        boolean forceRecover = isForceRecover();
        // 测试binlog下载功能开关
        boolean forceDownload = isForceDownload();

        List<BinlogTaskConfig> dumperList = buildDumpers(containerList, expectedStorageTso, newVersion,
            forceRecover, forceDownload, serverId, streamStorageMap);
        List<BinlogTaskConfig> dispatcherList = buildDispatchers(containerList, storageInfoList,
            expectedStorageTso, newVersion, serverId);

        buildUpstreamSources(dispatcherList, dumperList);

        topology.getConfigList().addAll(dumperList);
        topology.getConfigList().addAll(dispatcherList);
        topology.setStreamStorageMap(streamStorageMap);

        BinlogTaskConfigMapper taskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        List<BinlogTaskConfig> preDumperList =
            taskConfigMapper.select(s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(clusterId))
                .and(BinlogTaskConfigDynamicSqlSupport.version, isEqualTo(preClusterSnapshot.getVersion()))
                .and(BinlogTaskConfigDynamicSqlSupport.role, isEqualTo(TaskType.DumperX.name())));
        compareDumperConfigAndReset(preDumperList, dumperList, forceRecover);

        topology.setServerID(serverId);
        return topology;
    }

    public static void buildUpstreamSources(List<BinlogTaskConfig> dispatcherList, List<BinlogTaskConfig> dumperList) {
        HashLevel hashLevel = HashLevel.from(DynamicApplicationConfig.getString(BINLOGX_TRANSMIT_HASH_LEVEL));
        Map<String, Integer> sourceUsageCount = new HashMap<>();

        dumperList.forEach(d -> {
            ExecutionConfig executionConfig = JSONObject.parseObject(d.getConfig(), ExecutionConfig.class);
            if (hashLevel == HashLevel.DATANODE) {
                List<String> sources = dispatcherList.stream()
                    .filter(i -> StringUtils.equalsIgnoreCase(d.getContainerId(), i.getContainerId()))
                    .map(BinlogTaskConfig::getTaskName).collect(Collectors.toList());

                if (sources.isEmpty()) {
                    String leastUsedDispatcher = dispatcherList.stream()
                        .min(Comparator.comparingInt(t -> sourceUsageCount.getOrDefault(t.getTaskName(), 0)))
                        .map(BinlogTaskConfig::getTaskName)
                        .orElseThrow(() -> new RuntimeException("No available dispatcher found"));

                    executionConfig.setSources(Collections.singletonList(leastUsedDispatcher));
                    sourceUsageCount.compute(leastUsedDispatcher, (k, v) -> (v == null) ? 1 : v + 1);
                } else {
                    executionConfig.setSources(sources);
                    sources.forEach(s -> sourceUsageCount.compute(s, (k, v) -> (v == null) ? 1 : v + 1));
                }
            } else {
                List<String> allSources = dispatcherList.stream()
                    .map(BinlogTaskConfig::getTaskName)
                    .collect(Collectors.toList());
                executionConfig.setSources(allSources);
                allSources.forEach(s -> sourceUsageCount.compute(s, (k, v) -> (v == null) ? 1 : v + 1));
            }

            d.setConfig(JSONObject.toJSONString(executionConfig));
        });
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
                                                long serverId, Map<String, String> streamStorageMap) {
        List<BinlogTaskConfig> result = new ArrayList<>();
        int dumperWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_DUMPER_WEIGHT);
        int taskWeight = DynamicApplicationConfig.getInt(TOPOLOGY_RESOURCE_TASK_WEIGHT);
        int rasterize = dumperWeight + taskWeight;
        List<XStream> xStreamList = getXStreamsInCurrentCluster();

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
            exeConfig.setReservedMemMb(container.getCapability().getReservedMemMb());

            long identifier = NumberUtils.isCreatable(container.getContainerId()) ?
                Long.parseLong(container.getContainerId()) : container.getContainerId().hashCode();
            BinlogTaskConfig taskConfig = createTask(identifier, TaskType.DumperX,
                container, JSONObject.toJSONString(exeConfig), newVersion);
            taskConfig.setClusterId(clusterId);
            taskConfig.setMem(dumperWeight * memUnit);
            taskConfig.setVcpu(dumperWeight * cpuUnit);
            taskConfig.setStatus(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE);
            container.deductMem(dumperWeight * memUnit);
            result.add(taskConfig);
        });

        if (HashLevel.getCurrentHashLevel() == HashLevel.DATANODE) {
            for (BinlogTaskConfig taskConfig : result) {
                ExecutionConfig executionConfig = JSONObject.parseObject(taskConfig.getConfig(), ExecutionConfig.class);
                executionConfig.setStreamStorageMap(streamStorageMap);
                taskConfig.setConfig(JSONObject.toJSONString(executionConfig));
            }
        }
        return result;
    }

    private Map<String, String> buildStreamStorageMap(List<StorageInfo> storageInfoList,
                                                      ClusterSnapshot preClusterSnapshot,
                                                      String expectedStorageTso,
                                                      String instructionId) {
        HashLevel hashLevel = HashLevel.from(DynamicApplicationConfig.getString(BINLOGX_TRANSMIT_HASH_LEVEL));
        if (hashLevel == HashLevel.DATANODE) {
            List<XStream> xStreamList = getXStreamsInCurrentCluster();
            Map<String, String> streamStorageMap = new HashMap<>(xStreamList.size());

            if (preClusterSnapshot.isOrigin()) {
                if (storageInfoList.size() != xStreamList.size()) {
                    throw new PolardbxException(
                        String.format("stream count %s is not equal to storage count %s",
                            xStreamList.size(), storageInfoList.size()));
                }

                for (XStream xStream : xStreamList) {
                    String streamName = xStream.getStreamName();
                    streamStorageMap.put(streamName, XStreamService.extractStorageInstId(streamName));
                }
            } else {
                if (CollectionUtils.isEmpty(preClusterSnapshot.getStreamStorageMap())) {
                    throw new PolardbxException("stream storage mapping info is empty in previous cluster snapshot!");
                }

                Map<String, String> preStreamStorageMap = preClusterSnapshot.getStreamStorageMap();
                Set<String> currentStorageSet = storageInfoList.stream()
                    .map(StorageInfo::getStorageInstId).collect(Collectors.toSet());
                Set<String> previousStorageSet = new HashSet<>(preStreamStorageMap.values());

                if (storageInfoList.size() != preStreamStorageMap.size()) {
                    if (storageInfoList.size() > preStreamStorageMap.size()) {
                        adjustStreamWhenAddStorage(currentStorageSet, previousStorageSet, storageInfoList,
                            preStreamStorageMap, expectedStorageTso, instructionId);
                    } else {
                        adjustStreamWhenRemoveStorage(currentStorageSet, previousStorageSet, preStreamStorageMap);
                    }
                } else {
                    boolean checkResult = currentStorageSet.equals(previousStorageSet);
                    if (!checkResult) {
                        throw new PolardbxException(
                            String.format("current storage set is different from previous stream storage set, %s, %s"
                                , currentStorageSet, previousStorageSet));
                    }
                }
                streamStorageMap = preStreamStorageMap;
            }
            return streamStorageMap;
        }

        return null;
    }

    void adjustStreamWhenAddStorage(Set<String> currentStorageSet, Set<String> previousStorageSet,
                                    List<StorageInfo> storageInfoList, Map<String, String> preStreamStorageMap,
                                    String expectedStorageTso, String instructionId) {
        boolean checkResult = currentStorageSet.containsAll(previousStorageSet);
        if (!checkResult) {
            throw new PolardbxException(
                String.format("current storage set not contains all previous stream storage set, %s ,%s"
                    , currentStorageSet, previousStorageSet));
        }

        TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
        for (StorageInfo storageInfo : storageInfoList) {
            if (!previousStorageSet.contains(storageInfo.getStorageInstId())) {
                transactionTemplate.execute((o) -> {
                    XStream xStream = buildAndSaveXStream(storageInfo, -1, expectedStorageTso);
                    saveStorageHistoryDetail(expectedStorageTso, xStream.getStreamName(), instructionId);
                    preStreamStorageMap.put(xStream.getStreamName(), storageInfo.getStorageInstId());
                    return null;
                });
            }
        }
    }

    void adjustStreamWhenRemoveStorage(Set<String> currentStorageSet, Set<String> previousStorageSet,
                                       Map<String, String> preStreamStorageMap) {
        boolean checkResult = previousStorageSet.containsAll(currentStorageSet);
        if (!checkResult) {
            throw new PolardbxException(
                String.format("previous stream storage set not contains all current storage set, %s ,%s"
                    , previousStorageSet, currentStorageSet));
        }

        preStreamStorageMap.entrySet().removeIf(entry -> {
            boolean isRemove = !currentStorageSet.contains(entry.getValue());
            if (isRemove) {
                markStreamAsPending(entry.getKey());
                log.info("stream is marked as pending, {}.", entry.getKey());
            }
            return isRemove;
        });
    }

    private void prepareRecoverInfo(String expectedStorageTso) {
        List<XStream> xStreamList = getXStreamsInCurrentCluster();
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

        Map<Integer, Pair<BinlogTaskConfig, Container>> assignedTaskMap = new HashMap<>();
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
                assignedTaskMap.put(taskSequence, Pair.of(taskConfig, container));
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
            Pair<BinlogTaskConfig, Container> pair = assignedTaskMap.get(k + 1);
            v.setReservedMemMb(pair.getValue().getCapability().getReservedMemMb());
            pair.getKey().setConfig(JSONObject.toJSONString(v));
            result.add(pair.getKey());
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
