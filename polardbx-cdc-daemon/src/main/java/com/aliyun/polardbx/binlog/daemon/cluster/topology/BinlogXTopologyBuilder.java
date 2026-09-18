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
import com.aliyun.polardbx.binlog.scheduler.StreamEntity;
import com.aliyun.polardbx.binlog.scheduler.StreamEntitySet;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.service.XStreamService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_SCHEDULE_DISPATCHER_ROCKSDB_RATIO;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_HASH_LEVEL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_FORCE_DOWNLOAD_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_DUMPER_WEIGHT;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_TASK_WEIGHT;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.RebalanceUtil.buildTaskNameIdentifier;
import static com.aliyun.polardbx.binlog.scheduler.model.Container.sortByResourceDesc;
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

    public TopologyEntity buildTopology(List<Container> containerList,
                                        List<StorageInfo> storageInfoList,
                                        ClusterSnapshot preClusterSnapshot,
                                        String expectedStorageTso,
                                        long newVersion,
                                        long newSubVersion,
                                        long serverId,
                                        String instructionId) {
        TopologyEntity topology = new TopologyEntity();

        // process stream for add/remove datanode
        Map<String, String> streamStorageMap = buildStreamStorageMap(storageInfoList,
            preClusterSnapshot, expectedStorageTso, instructionId);
        // build recover info
        prepareRecoverInfo(expectedStorageTso);
        // 测试recover tso功能开关
        boolean forceRecover = isForceRecover();
        // 测试binlog下载功能开关
        boolean forceDownload = isForceDownload();

        Map<String, StreamEntitySet> containerStreamMap = buildContainerStreamMap(preClusterSnapshot, containerList);
        List<BinlogTaskConfig> dumperList = buildDumpers(containerList, containerStreamMap, expectedStorageTso,
            newVersion, forceRecover, forceDownload, serverId, streamStorageMap, newSubVersion);
        List<BinlogTaskConfig> dispatcherList = buildDispatchers(containerList, storageInfoList,
            expectedStorageTso, newVersion, newSubVersion, serverId);

        buildUpstreamSources(dispatcherList, dumperList);

        topology.getTaskConfigs().addAll(dumperList);
        topology.getTaskConfigs().addAll(dispatcherList);
        topology.setStreamStorageMap(streamStorageMap);
        topology.setContainerStreamMap(containerStreamMap);

        BinlogTaskConfigMapper taskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        List<BinlogTaskConfig> preDumperList =
            taskConfigMapper.select(s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(clusterId))
                .and(BinlogTaskConfigDynamicSqlSupport.version, isEqualTo(preClusterSnapshot.getVersion()))
                .and(BinlogTaskConfigDynamicSqlSupport.role, isEqualTo(TaskType.DumperX.name())));
        compareDumperConfigAndReset(preDumperList, dumperList, forceRecover);

        topology.setServerId(serverId);
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

    private static BinlogTaskConfig createTaskConfig(long identifier,
                                                     TaskType taskType,
                                                     Container container,
                                                     String exeConfigStr,
                                                     long version,
                                                     long subVersion) {
        String taskName = taskType.name() + "-" + Math.abs(identifier);
        return BinlogTaskConfig.builder()
            .taskName(taskName)
            .containerId(container.getContainerId())
            .ip(container.getIp())
            .port(container.holdPort())
            .config(exeConfigStr)
            .role(taskType.name())
            .status(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE)
            .version(version)
            .subVersion(subVersion)
            .build();
    }

    private Map<String, StreamEntitySet> buildContainerStreamMap(ClusterSnapshot preClusterSnapshot,
                                                                 List<Container> containerList) {
        // DumperX，一个Container一个DumperX进程，如果流的个数小于Container的个数，则对应Container上不启动DumperX进程
        List<XStream> xStreamList = getXStreamsInCurrentCluster();
        if (CollectionUtils.isEmpty(preClusterSnapshot.getContainerStreamMap())) {
            return buildContainerStreamMapWithoutPrevious(containerList, xStreamList);
        } else {
            return buildContainerStreamMapWithPrevious(preClusterSnapshot, containerList, xStreamList);
        }
    }

    private List<BinlogTaskConfig> buildDumpers(List<Container> containerList,
                                                Map<String, StreamEntitySet> containerStreamMap,
                                                String expectedStorageTso,
                                                long newVersion,
                                                boolean forceRecover,
                                                boolean forceDownload,
                                                long serverId,
                                                Map<String, String> streamStorageMap,
                                                long newSubVersion) {
        List<BinlogTaskConfig> result = new ArrayList<>();
        int dumperWeight = getInt(TOPOLOGY_RESOURCE_DUMPER_WEIGHT);
        int taskWeight = getInt(TOPOLOGY_RESOURCE_TASK_WEIGHT);
        int totalWeight = dumperWeight + taskWeight;
        Map<String, Container> containerMap = containerList.stream().collect(
            Collectors.toMap(Container::getContainerId, Function.identity()));

        containerStreamMap.forEach((k, v) -> {
            if (v.isEmpty()) {
                return;
            }

            Container container = containerMap.get(k);
            int memUnit = containerMap.get(k).getCapability().getFreeMemMb() / totalWeight;
            int cpuUnit = containerMap.get(k).getCapability().getVirCpu() / totalWeight;
            Map<String, String> recoverTsoMap = new HashMap<>(v.size());
            Map<String, String> recoverFileMap = new HashMap<>(v.size());
            for (StreamEntity streamEntity : v) {
                String streamName = streamEntity.getStreamName();
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
            exeConfig.setStreamNameSet(v.stream().map(StreamEntity::getStreamName).collect(Collectors.toSet()));
            exeConfig.setRuntimeVersion(newVersion);
            exeConfig.setSubRuntimeVersion(newSubVersion);
            exeConfig.setServerId(serverId);
            exeConfig.setReservedMemMb(container.getCapability().getReservedMemMb());

            long identifier = buildTaskNameIdentifier(container.getContainerId(), container.getIp());
            BinlogTaskConfig taskConfig = createTaskConfig(identifier, TaskType.DumperX,
                container, JSONObject.toJSONString(exeConfig), newVersion, newSubVersion);
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

    Map<String, StreamEntitySet> buildContainerStreamMapWithoutPrevious(List<Container> containerList,
                                                                        List<XStream> xStreamList) {
        Map<String, StreamEntitySet> containerStreamMap = new HashMap<>();
        int containerCount = containerList.size();

        for (int i = 0; i < xStreamList.size(); i++) {
            int index = i % containerCount;
            String containerId = containerList.get(index).getContainerId();
            containerStreamMap.computeIfAbsent(containerId, k -> new StreamEntitySet());
            containerStreamMap.get(containerId).add(new StreamEntity(xStreamList.get(i).getStreamName()));
        }
        for (Container container : containerList) {
            containerStreamMap.computeIfAbsent(container.getContainerId(), k -> new StreamEntitySet());
        }

        return containerStreamMap;
    }

    Map<String, StreamEntitySet> buildContainerStreamMapWithPrevious(ClusterSnapshot preClusterSnapshot,
                                                                     List<Container> containerList,
                                                                     List<XStream> xStreamList) {
        Map<String, StreamEntitySet> preContainerStreamMap = preClusterSnapshot.getContainerStreamMap();

        Set<String> currentStreams = xStreamList.stream()
            .map(XStream::getStreamName).collect(Collectors.toSet());
        Set<String> currentContainers = containerList.stream()
            .map(Container::getContainerId).collect(Collectors.toSet());
        Set<String> previousStreams = preContainerStreamMap.values().stream()
            .flatMap(Set::stream).map(StreamEntity::getStreamName).collect(Collectors.toSet());
        Set<String> previousContainers = preContainerStreamMap.keySet();

        Set<String> addedContainers = currentContainers.stream()
            .filter(c -> !previousContainers.contains(c)).collect(Collectors.toSet());
        Set<String> removedContainers = previousContainers.stream()
            .filter(c -> !currentContainers.contains(c)).collect(Collectors.toSet());
        Set<String> addedStreams = currentStreams.stream()
            .filter(s -> !previousStreams.contains(s)).collect(Collectors.toSet());
        Set<String> removedStreams = previousStreams.stream()
            .filter(s -> !currentStreams.contains(s)).collect(Collectors.toSet());

        return rebalanceContainerStreamMap(preContainerStreamMap, currentStreams, currentContainers,
            addedContainers, removedContainers, addedStreams, removedStreams
        );
    }

    Map<String, StreamEntitySet> rebalanceContainerStreamMap(
        Map<String, StreamEntitySet> previousContainerStreamMap,
        Set<String> currentStreams,
        Set<String> currentContainers,
        Set<String> addedContainers,
        Set<String> removedContainers,
        Set<String> addedStreams,
        Set<String> removedStreams) {

        // 1. 确保所有当前容器都在映射中（即使没有分配流）
        Map<String, StreamEntitySet> newMap = new HashMap<>();
        for (String containerId : currentContainers) {
            newMap.computeIfAbsent(containerId, k -> new StreamEntitySet());
        }

        // 2. 处理保留的容器，移除已删除的流
        for (Map.Entry<String, StreamEntitySet> entry : previousContainerStreamMap.entrySet()) {
            String containerId = entry.getKey();
            Set<StreamEntity> preStreamEntities = entry.getValue();

            // 如果容器被删除，跳过（这些流需要重新分配）
            if (removedContainers.contains(containerId)) {
                continue;
            }

            // 如果容器仍然存在，保留它并移除已删除的流
            Set<StreamEntity> remainingStreams = preStreamEntities.stream()
                .filter(s -> !removedStreams.contains(s.getStreamName()))
                .filter(s -> currentStreams.contains(s.getStreamName())) // 确保流仍然存在
                .collect(Collectors.toSet());

            if (!remainingStreams.isEmpty() && currentContainers.contains(containerId)) {
                newMap.put(containerId, new StreamEntitySet(remainingStreams));
            }
        }

        // 3. 收集所有需要重新分配的流（包括新增的流和从被删除容器中移出的流）
        // 新增的流
        Set<String> streamsToReassign = new HashSet<>(addedStreams);
        // 从被删除容器中移出的流
        for (String removedContainer : removedContainers) {
            Set<String> streams = previousContainerStreamMap.get(removedContainer)
                .stream().map(StreamEntity::getStreamName).collect(Collectors.toSet());
            if (!streams.isEmpty()) {
                // 只保留仍然存在的流
                streams.stream()
                    .filter(currentStreams::contains)
                    .filter(s -> !removedStreams.contains(s))
                    .forEach(streamsToReassign::add);
            }
        }

        // 4. 如果还有可用容器，将需要重新分配的流按负载均衡方式分配到所有当前容器中
        if (!currentContainers.isEmpty() && !streamsToReassign.isEmpty()) {
            // 使用优先队列维护容器，按流数量升序排列
            PriorityQueue<String> containerQueue =
                new PriorityQueue<>(Comparator.comparingInt(c -> newMap.getOrDefault(c, new StreamEntitySet()).size()));
            containerQueue.addAll(currentContainers);

            List<String> streamList = new ArrayList<>(streamsToReassign);

            // 将流分配给当前负载最轻的容器
            for (String stream : streamList) {
                // 取出当前负载最轻的容器
                String containerId = containerQueue.poll();
                newMap.computeIfAbsent(containerId, k -> new StreamEntitySet()).add(new StreamEntity(stream));

                // 将容器重新放回队列（其优先级可能已改你变）
                containerQueue.offer(containerId);
            }
        }

        // 5. 检查是否需要在所有容器间进行全局重新平衡，不能排除上一轮存在不均衡的情况，因此只要size > 1，则进行全局重新平衡
        if (newMap.size() > 1) {
            // 创建容器列表，按流数量降序排列（流多的优先被移出）
            List<Map.Entry<String, StreamEntitySet>> containerEntries = newMap.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .collect(Collectors.toList());

            // 优化的负载均衡算法，尽量减少流的移动
            while (true) {
                // 重新排序容器（因为上一轮可能已经改变了流的分布）
                containerEntries.sort((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()));

                // 查找source容器（流数量最多的容器）
                Map.Entry<String, StreamEntitySet> sourceEntry = containerEntries.get(0);
                StreamEntitySet sourceStreams = sourceEntry.getValue();

                // 查找target容器（流数量最少的容器）
                Map.Entry<String, StreamEntitySet> targetEntry = containerEntries.get(containerEntries.size() - 1);
                StreamEntitySet targetStreams = targetEntry.getValue();

                // 如果源容器比目标容器多至少2个流，则移动一个流
                if (sourceStreams.size() - targetStreams.size() >= 2) {
                    // 取时间戳最大的流，即加入该容器最晚的流，优先进行移出
                    StreamEntity streamToMove = sourceStreams.getStreamEntityWithMaxTimestamp();
                    sourceStreams.remove(streamToMove);
                    targetStreams.add(new StreamEntity(streamToMove.getStreamName()));
                } else {
                    break;
                }
            }
        }

        return newMap;
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

    private List<BinlogTaskConfig> buildDispatchers(List<Container> containerList,
                                                    List<StorageInfo> storageInfoList,
                                                    String expectedStorageTso,
                                                    long newVersion,
                                                    long newSubVersion,
                                                    long serverId) {
        sortByResourceDesc(containerList);

        Map<String, Pair<BinlogTaskConfig, Container>> assignedTaskMap = new HashMap<>();
        for (Container container : containerList) {

            long mainIdentifier = buildTaskNameIdentifier(container.getContainerId(), container.getIp());
            int mem = container.getCapability().getFreeMemMb();
            int cpu = container.getCapability().getCpu();//没有绑核操作，暂时不需要资源隔离
            double rocksDbRatio = DynamicApplicationConfig.getDouble(BINLOGX_SCHEDULE_DISPATCHER_ROCKSDB_RATIO);
            mem = mem - Double.valueOf(mem * rocksDbRatio).intValue();//给rocksdb预留一些内存资源

            BinlogTaskConfig taskConfig = createTaskConfig(mainIdentifier, TaskType.Dispatcher,
                container, "", newVersion, newSubVersion);
            taskConfig.setClusterId(clusterId);
            taskConfig.setMem(mem);
            taskConfig.setVcpu(cpu);
            taskConfig.setStatus(BinlogTaskConfigStatus.ENABLE_AUTO_SCHEDULE);
            container.deductMem(mem);
            assignedTaskMap.put(mainIdentifier + "", Pair.of(taskConfig, container));

        }

        // storage的数量可能比dispatcher数量大，这里通过assignedExeConfigMap来去掉没有机会分配storage的dispatcher
        Map<String, ExecutionConfig> assignedExeConfigMap = new HashMap<>();
        ArrayList<String> taskMapKeyList = new ArrayList<>(assignedTaskMap.keySet());
        for (int i = 0; i < storageInfoList.size(); i++) {
            int index = i % taskMapKeyList.size();
            String indexKey = taskMapKeyList.get(index);
            ExecutionConfig executionConfig = assignedExeConfigMap.computeIfAbsent(indexKey, k -> {
                ExecutionConfig config = new ExecutionConfig();
                config.setType(MergeSourceType.BINLOG.name());
                config.setTso(expectedStorageTso);
                config.setRecoverTsoMap(this.recoverTsoMap);
                config.setRuntimeVersion(newVersion);
                config.setSubRuntimeVersion(newSubVersion);
                config.setSources(new ArrayList<>());
                config.setServerId(serverId);
                return config;
            });
            executionConfig.getSources().add(storageInfoList.get(i).getStorageInstId());
        }

        List<BinlogTaskConfig> result = new ArrayList<>();
        assignedExeConfigMap.forEach((k, v) -> {
            Pair<BinlogTaskConfig, Container> pair = assignedTaskMap.get(k);
            v.setReservedMemMb(pair.getValue().getCapability().getReservedMemMb());
            pair.getKey().setConfig(JSONObject.toJSONString(v));
            result.add(pair.getKey());
        });
        return result;
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
