/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metadata.MetaGenerator;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.rpc.TxnMessageProvider;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.util.DNStorageSqlExecutor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_ENGINE_AUTO_START;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;

/**
 * Created by ziyang.lb
 * Task Engine: Task模块的内核，负责组装各个组件
 **/
@Slf4j
public class TaskEngine implements TxnMessageProvider {

    private final static String MAIN_PIPELINE = "MAIN_PIPELINE";

    private final MetaGenerator metaGenerator;
    @Setter
    private TaskRuntimeConfig taskRuntimeConfig;
    @Getter
    private final ConcurrentHashMap<String, TaskPipeline> taskPipelines;
    private final AtomicBoolean running;

    public TaskEngine(TaskRuntimeConfig taskRuntimeConfig) {
        this.metaGenerator = new MetaGenerator();
        this.taskPipelines = new ConcurrentHashMap<>();
        this.taskRuntimeConfig = taskRuntimeConfig;
        this.running = new AtomicBoolean(false);
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            tryTriggerMetaStart();
            tryStartPipeline();
            log.info("task engine started.");
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("task engine stopped.");
        }
    }

    void tryTriggerMetaStart() {
        if (!metaGenerator.exists()) {
            flushLogs();
        }
        metaGenerator.tryStart();
    }

    void flushLogs() {
        // TODO @承谨，应该flush所有DN的binlog，而不仅仅是当前Task负责的DN
        List<String> storageList = taskRuntimeConfig.getMergeSourceInfos().stream()
            .map(MergeSourceInfo::getBinlogParameter)
            .map(BinlogParameter::getStorageInstId)
            .collect(Collectors.toList());
        for (String s : storageList) {
            DNStorageSqlExecutor storageSqlExecutor = new DNStorageSqlExecutor(s);
            storageSqlExecutor.tryFlushDnBinlog();
        }
    }

    public void tryStartPipeline() {
        if (getBoolean(TASK_ENGINE_AUTO_START) || dumpFromRelayLog()) {
            TaskPipeline mainPipeline = taskPipelines.computeIfAbsent(MAIN_PIPELINE,
                k -> new TaskPipeline(MAIN_PIPELINE, taskRuntimeConfig, "", true, true, null));
            mainPipeline.start();
        }
    }

    @Override
    public void dump(DumpRequest request, TxnOutputStream<DumpReply> outputStream) throws InterruptedException {
        if (!dumpFromRelayLog()) {
            restart(request);
        }
        Pair<String, TaskPipeline> pair = getTaskPipeline(request, false);
        try {
            pair.getValue().dump(request, outputStream);
        } finally {
            if (!dumpFromRelayLog()) {
                taskPipelines.remove(pair.getKey());
                pair.getValue().stop();
            }
        }
    }

    public synchronized void restart(DumpRequest request) {
        TaskPipeline taskPipeline = getTaskPipeline(request, true).getRight();
        if (taskPipeline != null) {
            taskPipeline.stop();
        }
        taskPipeline = createTaskPipeline(request);
        taskPipeline.start();
    }

    Pair<String, TaskPipeline> getTaskPipeline(DumpRequest request, boolean remove) {
        String key = buildPipelineKey(request);
        return remove ? Pair.of(key, taskPipelines.remove(key)) : Pair.of(key, taskPipelines.get(key));
    }

    TaskPipeline createTaskPipeline(DumpRequest request) {
        if (taskRuntimeConfig.getType() != TaskType.Dispatcher && StringUtils.isNotBlank(request.getStorageInstId())) {
            throw new PolardbxException("TaskEngine only support dispatcher task with storageInstId.");
        }

        String key = buildPipelineKey(request);
        String identifier = StringUtils.isBlank(request.getStorageInstId()) ?
            MAIN_PIPELINE : request.getStorageInstId();
        TaskPipeline taskPipeline = new TaskPipeline(identifier, taskRuntimeConfig,
            request.getTso(), dumpFromRelayLog(), useKWayMerge(request), request.getStorageInstId());
        taskPipelines.put(key, taskPipeline);
        return taskPipeline;
    }

    boolean dumpFromRelayLog() {
        return taskRuntimeConfig.getType() == TaskType.Dispatcher
            && HashLevel.getCurrentHashLevel() != HashLevel.DATANODE;
    }

    boolean useKWayMerge(DumpRequest request) {
        return dumpFromRelayLog() || StringUtils.isBlank(request.getStorageInstId());
    }

    String buildPipelineKey(DumpRequest request) {
        return StringUtils.isNotBlank(request.getStorageInstId()) ? request.getStorageInstId() : MAIN_PIPELINE;
    }

}
