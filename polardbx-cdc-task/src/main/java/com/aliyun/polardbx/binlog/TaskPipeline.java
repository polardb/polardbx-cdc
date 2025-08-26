/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.collect.Collector;
import com.aliyun.polardbx.binlog.collect.LogEventCollector;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.MockParameter;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.BinlogExtractor;
import com.aliyun.polardbx.binlog.extractor.Extractor;
import com.aliyun.polardbx.binlog.extractor.ExtractorBuilder;
import com.aliyun.polardbx.binlog.extractor.MockExtractor;
import com.aliyun.polardbx.binlog.extractor.RpcExtractor;
import com.aliyun.polardbx.binlog.merge.DirectLogEventMerger;
import com.aliyun.polardbx.binlog.merge.LogEventMerger;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.merge.Merger;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.StorageFactory;
import com.aliyun.polardbx.binlog.transmit.ChunkMode;
import com.aliyun.polardbx.binlog.transmit.LogEventTransmitter;
import com.aliyun.polardbx.binlog.transmit.Transmitter;
import com.aliyun.polardbx.binlog.transmit.relay.RelayLogEventTransmitter;
import com.aliyun.polardbx.binlog.util.StorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_COLLECT_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_SOURCE_QUEUE_MAX_TOTAL_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_SOURCE_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMIT_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.ORIGIN_TSO;

@Slf4j
public class TaskPipeline {
    private final TaskConfigProvider taskConfigProvider;
    private final TaskRuntimeConfig runtimeConfig;
    private final String identifier;
    private final boolean useRelayLog;
    private final boolean useKWayMerge;
    private final String storageInstId;
    private final AtomicBoolean running;
    private String startTso;
    private Storage storage;
    private Merger merger;
    private Collector collector;
    private Transmitter transmitter;

    public TaskPipeline(String identifier, TaskConfigProvider taskConfigProvider, TaskRuntimeConfig runtimeConfig,
                        String startTso, boolean useRelayLog, boolean useKWayMerge, String storageInstId) {
        this.identifier = identifier;
        this.taskConfigProvider = taskConfigProvider;
        this.runtimeConfig = runtimeConfig;
        this.startTso = startTso;
        this.useRelayLog = useRelayLog;
        this.useKWayMerge = useKWayMerge;
        this.storageInstId = storageInstId;
        this.running = new AtomicBoolean(false);
    }

    public void dump(DumpRequest request, TxnOutputStream<DumpReply> outputStream) throws InterruptedException {
        if (!running.get()) {
            throw new PolardbxException("task pipeline is not running, for dump request " + request);
        }
        if (!transmitter.checkTSO(request.getTso(), outputStream, true)) {
            throw new PolardbxException("can`t find binlog for tso " + request.getTso());
        }
        transmitter.dump(request.getTso(), outputStream);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            init();

            //启动顺序，按照依赖关系编排，不可随意更改
            this.storage.start();
            this.transmitter.start();
            this.collector.start();
            this.merger.start();
            log.info("task pipeline started for " + identifier);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            //停止顺序，按照依赖关系编排，不可随意更改
            if (this.merger != null) {
                this.merger.stop();
            }
            if (this.collector != null) {
                this.collector.stop();
            }
            if (transmitter != null) {
                this.transmitter.stop();
            }
            if (storage != null) {
                this.storage.stop();
            }
            log.info("task pipeline stopped for " + identifier);
        }
    }

    public void restart() {
        log.info("restarting task pipeline begin: with tso {}, identifier {}.", startTso, identifier);
        this.stop();
        this.start();
        log.info("restarting task pipeline end: with tso {}, identifier {}.", startTso, identifier);
    }

    void init() {
        checkValid();
        this.storage = StorageFactory.createStorage(identifier, !useKWayMerge);
        this.transmitter = buildTransmitter();
        this.collector = buildCollector();
        this.merger = buildMerger();
        this.buildMergeSources();
        this.tryUpdateSourcesList();
    }

    int calcMergeSourceQueueSize() {
        int defaultSize = getInt(TASK_MERGE_SOURCE_QUEUE_SIZE);
        int maxTotalSize = getInt(TASK_MERGE_SOURCE_QUEUE_MAX_TOTAL_SIZE);
        int mergeSourceSize = runtimeConfig.getMergeSourceInfos().size();
        double calcSize = maxTotalSize / ((double) mergeSourceSize);
        return Math.min(defaultSize, new Double(calcSize).intValue());
    }

    void checkValid() {
        String expectedStorageTso = StorageUtil.buildExpectedStorageTso(startTso);
        String runtimeStorageTso = runtimeConfig.getExecutionConfig().getTso();

        if (!useRelayLog && !StringUtils.equals(expectedStorageTso, runtimeStorageTso)) {
            log.error("The runtime storage tso {} is inconsistent with the expected storage tso {}, will retry.",
                runtimeStorageTso, expectedStorageTso);
            throw new PolardbxException(
                "The runtime storage tso " + runtimeStorageTso + " is inconsistent with the expected storage tso "
                    + expectedStorageTso);
        }
    }

    void buildMergeSources() {
        String rdsBinlogPath = getString(TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR) + File.separator + getString(TASK_NAME);
        if (useKWayMerge) {
            this.runtimeConfig.getMergeSourceInfos()
                .forEach(i -> merger.addMergeSource(buildOneMergeSource(i, rdsBinlogPath)));
        } else {
            MergeSourceInfo mergeSourceInfo = taskConfigProvider.buildBinlogMergeSourceInfo(0, storageInstId);
            merger.addMergeSource(buildOneMergeSource(mergeSourceInfo, rdsBinlogPath));
        }
    }

    MergeSource buildOneMergeSource(MergeSourceInfo i, String rdsBinlogPath) {
        MergeSource mergeSource = new MergeSource(i.getId(),
            new ArrayBlockingQueue<>(calcMergeSourceQueueSize()), storage);
        mergeSource.setStartTSO(startTso);
        mergeSource.setExtractor(buildExtractor(i, mergeSource, rdsBinlogPath));
        return mergeSource;
    }

    Extractor buildExtractor(MergeSourceInfo mergeSourceInfo, MergeSource mergeSource, String rdsBinlogPath) {
        Extractor extractor;
        if (mergeSourceInfo.getType() == MergeSourceType.BINLOG) {
            extractor = buildBinlogExtractor(mergeSourceInfo, mergeSource, rdsBinlogPath);
        } else if (mergeSourceInfo.getType() == MergeSourceType.RPC) {
            extractor = buildRpcExtractor(mergeSourceInfo, mergeSource);
        } else if (mergeSourceInfo.getType() == MergeSourceType.MOCK) {
            extractor = buildMockExtractor(mergeSourceInfo, mergeSource);
        } else {
            throw new PolardbxException("invalid merge source type :" + mergeSourceInfo.getType());
        }
        return extractor;
    }

    Extractor buildBinlogExtractor(MergeSourceInfo mergeSourceInfo, MergeSource mergeSource,
                                   String rdsBinlogPath) {
        return ExtractorBuilder.buildExtractor(mergeSourceInfo.getBinlogParameter(), storage, mergeSource,
            rdsBinlogPath, runtimeConfig.getExecutionConfig().getServerIdWithCompatibility(), useRelayLog);
    }

    Extractor buildRpcExtractor(MergeSourceInfo mergeSourceInfo, MergeSource mergeSource) {
        RpcExtractor extractor = new RpcExtractor(mergeSource, storage);
        extractor.setRpcParameter(mergeSourceInfo.getRpcParameter());
        return extractor;
    }

    Extractor buildMockExtractor(MergeSourceInfo mergeSourceInfo, MergeSource mergeSource) {
        MockParameter param = mergeSourceInfo.getMockParameter();
        return new MockExtractor(param.getTxnType(), param.getDmlCount(), param.getEventSiz(),
            param.isUseBuffer(), param.getPartitionId(), mergeSource, storage);
    }

    Transmitter buildTransmitter() {
        Transmitter transmitter;
        if (useRelayLog) {
            transmitter = new RelayLogEventTransmitter(storage,
                runtimeConfig.getBinlogTaskConfig().getVersion(), extractRecoverTsoMapFromTaskConfig());
            startTso = ((RelayLogEventTransmitter) transmitter).getStartTso();
        } else {
            transmitter = new LogEventTransmitter(runtimeConfig.getType() == TaskType.Relay,
                getInt(TASK_TRANSMIT_QUEUE_SIZE),
                storage,
                ChunkMode.valueOf(getString(ConfigKeys.TASK_TRANSMIT_CHUNK_MODE)),
                getInt(ConfigKeys.TASK_TRANSMIT_CHUNK_ITEM_SIZE),
                getInt(ConfigKeys.TASK_TRANSMIT_MAX_MESSAGE_SIZE),
                getBoolean(ConfigKeys.TASK_TRANSMIT_DRY_RUN),
                startTso);
        }
        return transmitter;
    }

    Map<String, String> extractRecoverTsoMapFromTaskConfig() {
        ExecutionConfig taskConfig = runtimeConfig.getExecutionConfig();
        return taskConfig.getRecoverTsoMap();
    }

    Collector buildCollector() {
        return new LogEventCollector(storage,
            transmitter,
            getInt(TASK_COLLECT_QUEUE_SIZE),
            getBoolean(ConfigKeys.TASK_MERGE_XA_WITHOUT_TSO),
            canPreBuildMessage(),
            runtimeConfig.getType() == TaskType.Relay);
    }

    boolean canPreBuildMessage() {
        return runtimeConfig.getType() == TaskType.Relay || runtimeConfig.getType() == TaskType.Final || (
            runtimeConfig.getType() == TaskType.Dispatcher && !useRelayLog);
    }

    Merger buildMerger() {
        if (useKWayMerge) {
            String expectedStorageTso = StorageUtil.buildExpectedStorageTso(startTso);
            LogEventMerger result = new LogEventMerger(
                collector,
                getBoolean(ConfigKeys.TASK_MERGE_XA_WITHOUT_TSO),
                startTso,
                getBoolean(ConfigKeys.TASK_MERGE_DRY_RUN),
                getInt(ConfigKeys.TASK_MERGE_DRY_RUN_MODE),
                storage,
                StringUtils.equals(expectedStorageTso, ORIGIN_TSO) ? null : expectedStorageTso);
            result.addHeartBeatWindowAware(collector);
            result.setForceCompleteHbWindow(runtimeConfig.isForceCompleteHbWindow());
            return result;
        } else {
            return new DirectLogEventMerger(collector);
        }
    }

    void tryUpdateSourcesList() {
        if (useKWayMerge) {
            List<String> sourcesList = merger.getMergeSources().values().stream()
                .filter(s -> s.getExtractor() instanceof BinlogExtractor)
                .map(s -> ((BinlogExtractor) s.getExtractor()).getStorageInstId())
                .collect(Collectors.toList());

            if (sourcesList.isEmpty()) {
                return;
            }

            if (sourcesList.size() != merger.getMergeSources().size()) {
                throw new PolardbxException("all extractor must be binlog extractor!");
            }

            BinlogTaskInfoMapper taskInfoMapper = SpringContextHolder.getObject(BinlogTaskInfoMapper.class);
            Optional<BinlogTaskInfo> optionalTaskInfo = taskInfoMapper.selectOne(
                s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId,
                        SqlBuilder.isEqualTo(getString(CLUSTER_ID)))
                    .and(BinlogTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(runtimeConfig.getName())));
            if (optionalTaskInfo.isPresent()) {
                BinlogTaskInfo taskInfo = optionalTaskInfo.get();
                taskInfo.setSourcesList(JSONObject.toJSONString(sourcesList));
                taskInfoMapper.updateByPrimaryKey(taskInfo);
            } else {
                log.error("Task not found in db, taskName: {}", runtimeConfig.getName());
            }
        }
    }
}
