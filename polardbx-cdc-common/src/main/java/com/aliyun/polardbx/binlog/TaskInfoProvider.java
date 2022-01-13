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

package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.RpcParameter;
import com.aliyun.polardbx.binlog.domain.StorageContent;
import com.aliyun.polardbx.binlog.domain.TaskInfo;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.model.TaskConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.tso;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class TaskInfoProvider {
    private final static Gson GSON = new GsonBuilder().create();
    private final String taskName;

    public TaskInfoProvider(String taskName) {
        this.taskName = taskName;
    }

    public TaskInfo get() {
        TaskInfo taskInfo;

        BinlogTaskConfigMapper mapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        Optional<BinlogTaskConfig> opTask = mapper
            .selectOne(
                s -> s
                    .where(BinlogTaskConfigDynamicSqlSupport.clusterId,
                        IsEqualTo.of(() -> DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                    .and(BinlogTaskConfigDynamicSqlSupport.taskName, IsEqualTo.of(() -> taskName)));
        if (!opTask.isPresent()) {
            throw new PolardbxException("task config is null");
        }
        BinlogTaskConfig task = opTask.get();
        TaskConfig config = GSON.fromJson(task.getConfig(), TaskConfig.class);

        taskInfo = new TaskInfo();
        taskInfo.setId(task.getId());
        taskInfo.setName(task.getTaskName());
        taskInfo.setType(TaskType.valueOf(task.getRole()));
        taskInfo.setServerPort(task.getPort());
        taskInfo.setBinlogTaskConfig(task);

        MergeSourceType mergeSourceType = MergeSourceType.valueOf(config.getType());
        List<MergeSourceInfo> sourceInfos = new ArrayList<>();
        if (mergeSourceType == MergeSourceType.BINLOG) {
            AtomicInteger num = new AtomicInteger();

            config.getSources().forEach(p -> {
                MergeSourceInfo info = new MergeSourceInfo();
                info.setId(String.format("%s-db-%s", num.getAndIncrement(), p));
                info.setType(mergeSourceType);
                info.setTaskName(taskName);

                BinlogParameter parameter = new BinlogParameter();
                parameter.setStorageInstId(p);
                info.setBinlogParameter(parameter);

                sourceInfos.add(info);
            });
        } else if (mergeSourceType == MergeSourceType.RPC) {
            AtomicInteger num = new AtomicInteger();

            config.getSources().forEach(p -> {
                MergeSourceInfo info = new MergeSourceInfo();
                info.setId(String.format("%s-rpc-%s", num.getAndIncrement(), p));
                info.setType(mergeSourceType);
                info.setTaskName(taskName);

                RpcParameter parameter = new RpcParameter();
                parameter.setTaskName(taskName);
                parameter.setDynamic(true);
                info.setRpcParameter(parameter);

                sourceInfos.add(info);
            });
        } else {
            throw new PolardbxException("invalid merge source type : " + mergeSourceType);
        }

        taskInfo.setMergeSourceInfos(sourceInfos);
        taskInfo.setForceCompleteHbWindow(getStorageContent(config.getTso()).isRepaired());
        return taskInfo;
    }

    private StorageContent getStorageContent(String currentTso) {
        StorageHistoryInfoMapper storageHistoryMapper = SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
        List<StorageHistoryInfo> storageHistoryInfos =
            storageHistoryMapper.select(s -> s.where(tso, isEqualTo(currentTso)));
        if (storageHistoryInfos.isEmpty()) {
            throw new PolardbxException("can`t find storage info for tso " + currentTso);
        }
        log.info("storage content is " + storageHistoryInfos.get(0).getStorageContent());
        return GSON.fromJson(storageHistoryInfos.get(0).getStorageContent(), StorageContent.class);
    }
}
