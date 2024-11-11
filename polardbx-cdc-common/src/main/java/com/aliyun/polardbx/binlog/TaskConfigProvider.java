/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
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
public class TaskConfigProvider {
    private final String taskName;

    public TaskConfigProvider(String taskName) {
        this.taskName = taskName;
    }

    public TaskRuntimeConfig getTaskRuntimeConfig() {
        TaskRuntimeConfig taskRuntimeConfig;

        BinlogTaskConfigMapper mapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        Optional<BinlogTaskConfig> opTask = mapper
            .selectOne(s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId,
                    IsEqualTo.of(() -> DynamicApplicationConfig.getString(CLUSTER_ID)))
                .and(BinlogTaskConfigDynamicSqlSupport.taskName, IsEqualTo.of(() -> taskName)));
        if (!opTask.isPresent()) {
            throw new PolardbxException("task config is null");
        }
        BinlogTaskConfig task = opTask.get();
        ExecutionConfig config = JSONObject.parseObject(task.getConfig(), ExecutionConfig.class);

        taskRuntimeConfig = new TaskRuntimeConfig();
        taskRuntimeConfig.setId(task.getId());
        taskRuntimeConfig.setName(task.getTaskName());
        taskRuntimeConfig.setType(TaskType.valueOf(task.getRole()));
        taskRuntimeConfig.setServerPort(task.getPort());
        taskRuntimeConfig.setBinlogTaskConfig(task);

        MergeSourceType mergeSourceType = MergeSourceType.valueOf(config.getType());
        List<MergeSourceInfo> sourceInfos = new ArrayList<>();
        if (mergeSourceType == MergeSourceType.BINLOG) {
            AtomicInteger num = new AtomicInteger();

            config.getSources().forEach(p -> {
                MergeSourceInfo info = new MergeSourceInfo();
                info.setId(String.format("%s-db-%s", num.getAndIncrement(), p));
                info.setType(mergeSourceType);

                BinlogParameter parameter = new BinlogParameter();
                parameter.setStorageInstId(p);
                info.setBinlogParameter(parameter);

                sourceInfos.add(info);
            });
        } else if (mergeSourceType == MergeSourceType.RPC) {
            config.getSources().forEach(p -> {
                MergeSourceInfo info = new MergeSourceInfo();
                info.setId(String.format("merge-source-%s", p));
                info.setType(mergeSourceType);

                RpcParameter parameter = new RpcParameter();
                parameter.setTaskName(p);
                parameter.setDynamic(true);
                info.setRpcParameter(parameter);

                sourceInfos.add(info);
            });
        } else {
            throw new PolardbxException("invalid merge source type : " + mergeSourceType);
        }

        taskRuntimeConfig.setMergeSourceInfos(sourceInfos);
        taskRuntimeConfig.setForceCompleteHbWindow(getStorageContent(config.getTso()).isRepaired());
        return taskRuntimeConfig;
    }

    private StorageContent getStorageContent(String currentTso) {
        StorageHistoryInfoMapper storageHistoryMapper = SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
        List<StorageHistoryInfo> storageHistoryInfos =
            storageHistoryMapper.select(s -> s.where(tso, isEqualTo(currentTso))
                .and(clusterId, isEqualTo(DynamicApplicationConfig.getString(CLUSTER_ID))));
        if (storageHistoryInfos.isEmpty()) {
            throw new PolardbxException("can`t find storage info for tso " + currentTso);
        }
        log.info("storage list to consume for this task is " + storageHistoryInfos.get(0).getStorageContent());
        return JSONObject.parseObject(storageHistoryInfos.get(0).getStorageContent(), StorageContent.class);
    }
}
