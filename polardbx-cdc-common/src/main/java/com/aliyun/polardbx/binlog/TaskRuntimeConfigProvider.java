/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.RpcParameter;
import com.aliyun.polardbx.binlog.domain.StorageContent;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.clusterId;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.tso;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class TaskRuntimeConfigProvider {
    private final String taskName;

    public TaskRuntimeConfigProvider(String taskName) {
        this.taskName = taskName;
    }

    public TaskRuntimeConfig getTaskRuntimeConfig() {
        TaskRuntimeConfig taskRuntimeConfig;

        BinlogTaskConfig binlogTaskConfig = getBinlogTaskConfig();
        ExecutionConfig executionConfig = buildExecutionConfig(binlogTaskConfig);

        taskRuntimeConfig = new TaskRuntimeConfig();
        taskRuntimeConfig.setExecutionConfig(executionConfig);
        taskRuntimeConfig.setId(binlogTaskConfig.getId());
        taskRuntimeConfig.setName(binlogTaskConfig.getTaskName());
        taskRuntimeConfig.setType(TaskType.valueOf(binlogTaskConfig.getRole()));
        taskRuntimeConfig.setServerPort(binlogTaskConfig.getPort());
        taskRuntimeConfig.setBinlogTaskConfig(binlogTaskConfig);

        MergeSourceType mergeSourceType = MergeSourceType.valueOf(executionConfig.getType());
        List<MergeSourceInfo> sourceInfos = new ArrayList<>();
        if (mergeSourceType == MergeSourceType.BINLOG) {
            AtomicInteger num = new AtomicInteger();
            executionConfig.getSources().forEach(p -> {
                MergeSourceInfo info = buildBinlogMergeSourceInfo(num.getAndIncrement(), p);
                sourceInfos.add(info);
            });
        } else if (mergeSourceType == MergeSourceType.RPC) {
            executionConfig.getSources().forEach(p -> {
                MergeSourceInfo info = buildRpcMergeSourceInfo(p);
                sourceInfos.add(info);
            });
        }

        taskRuntimeConfig.setMergeSourceInfos(sourceInfos);
        taskRuntimeConfig.setForceCompleteHbWindow(getStorageContent(executionConfig.getTso()).isRepaired());
        return taskRuntimeConfig;
    }

    public static MergeSourceInfo buildBinlogMergeSourceInfo(int seq, String storageInstId) {
        MergeSourceInfo info = new MergeSourceInfo();
        info.setId(String.format("%s-db-%s", seq, storageInstId));
        info.setType(MergeSourceType.BINLOG);

        BinlogParameter parameter = new BinlogParameter();
        parameter.setStorageInstId(storageInstId);
        info.setBinlogParameter(parameter);
        return info;
    }

    public static MergeSourceInfo buildRpcMergeSourceInfo(String sourceTaskName) {
        MergeSourceInfo info = new MergeSourceInfo();
        info.setId(String.format("merge-source-%s", sourceTaskName));
        info.setType(MergeSourceType.RPC);

        RpcParameter parameter = new RpcParameter();
        parameter.setTaskName(sourceTaskName);
        parameter.setDynamic(true);
        info.setRpcParameter(parameter);
        return info;
    }

    public BinlogTaskConfig getBinlogTaskConfig() {
        BinlogTaskConfigMapper mapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        Optional<BinlogTaskConfig> opTask = mapper
            .selectOne(s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId,
                    IsEqualTo.of(() -> DynamicApplicationConfig.getString(CLUSTER_ID)))
                .and(BinlogTaskConfigDynamicSqlSupport.taskName, IsEqualTo.of(() -> taskName)));
        if (!opTask.isPresent()) {
            throw new PolardbxException("task config is null, with taskName " + taskName);
        }
        return opTask.get();
    }

    StorageContent getStorageContent(String currentTso) {
        StorageHistoryInfoMapper storageHistoryMapper = SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
        List<StorageHistoryInfo> storageHistoryInfos =
            storageHistoryMapper.select(s -> s.where(tso, isEqualTo(currentTso))
                .and(clusterId, isEqualTo(DynamicApplicationConfig.getString(CLUSTER_ID))));
        if (storageHistoryInfos.isEmpty()) {
            throw new PolardbxException("can`t find storage info for tso " + currentTso);
        }
        return JSONObject.parseObject(storageHistoryInfos.get(0).getStorageContent(), StorageContent.class);
    }

    /**
     * 对runtimeVersion进行特殊处理，兼容老版本
     *
     * @return {@link ExecutionConfig }
     */
    ExecutionConfig buildExecutionConfig(BinlogTaskConfig binlogTaskConfig) {
        String taskConfigJson = binlogTaskConfig.getConfig();
        ExecutionConfig config = JSONObject.parseObject(taskConfigJson, ExecutionConfig.class);
        if (!StringUtils.contains(taskConfigJson, "runtimeVersion")) {
            config.setRuntimeVersion(binlogTaskConfig.getVersion());
        }
        return config;
    }
}
