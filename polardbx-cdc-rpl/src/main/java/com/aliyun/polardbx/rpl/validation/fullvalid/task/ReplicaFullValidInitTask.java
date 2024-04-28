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
package com.aliyun.polardbx.rpl.validation.fullvalid.task;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.Constants;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.RplFullValidSubTaskDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplFullValidSubTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.RplFullValidSubTask;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.validation.fullvalid.ReplicaFullValidSampler;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 对逻辑表进行采样
 * 需要注意，构造的采样SQL中主键之间是有序的（与CN replica hashcheck的物理执行计划保持一致）
 *
 * @author yudong
 * @since 2023/10/24 14:46
 **/
public class ReplicaFullValidInitTask extends ReplicaFullValidSubTask {

    private static final Logger log = LoggerFactory.getLogger("fullValidLogger");

    private static final RplFullValidSubTaskMapper subTaskMapper =
        SpringContextHolder.getObject(RplFullValidSubTaskMapper.class);
    private static final TransactionTemplate metaTransactionTemplate =
        SpringContextHolder.getObject("metaTransactionTemplate");
    private final ReplicaFullValidSubTaskContext context;
    private final TaskConfig config;

    public ReplicaFullValidInitTask(ReplicaFullValidSubTaskContext context) {
        this.context = context;
        Optional<RplFullValidSubTask> subTask = subTaskMapper.selectOne(
            s -> s.where(RplFullValidSubTaskDynamicSqlSupport.id, SqlBuilder.isEqualTo(context.getSubTaskId())));
        if (!subTask.isPresent()) {
            throw new RuntimeException("Failed to find sub task!");
        }
        config = JSON.parseObject(subTask.get().getTaskConfig(), TaskConfig.class);
    }

    @Override
    public void run() {
        try {
            MDC.put(Constants.MDC_RPL_FULL_VALID_TASK_ID_KEY, String.valueOf(context.getSubTaskId()));

            boolean switchSucc =
                ReplicaFullValidTaskManager.switchSubTaskState(context.getSubTaskId(), ReplicaFullValidTaskState.READY,
                    ReplicaFullValidTaskState.RUNNING);
            if (!switchSucc) {
                log.warn("Failed to switch sub task state from ready to running!");
                return;
            }

            try {
                DataSource dstDataSource = context.getDstDbMetaCache().getDataSource(config.getDstDb());
                TableInfo dstTableInfo = context.getDstDbMetaCache().getTableInfo(config.getDstDb(), config.getDstTb());

                // do sample
                List<List<Object>> sampleResult =
                    ReplicaFullValidSampler.sample(dstDataSource, config.getDstDb(), config.getDstTb(),
                        dstTableInfo.getPks());

                // build sub tasks
                List<RplFullValidSubTask> subTasks = new ArrayList<>();
                if (sampleResult.isEmpty()) {
                    subTasks.add(createCheckTaskMetaHelper(new ArrayList<>(), new ArrayList<>()));
                } else {
                    subTasks.add(createCheckTaskMetaHelper(new ArrayList<>(), sampleResult.get(0)));
                    for (int i = 0; i < sampleResult.size() - 1; i++) {
                        subTasks.add(createCheckTaskMetaHelper(sampleResult.get(i), sampleResult.get(i + 1)));
                    }
                    subTasks.add(
                        createCheckTaskMetaHelper(sampleResult.get(sampleResult.size() - 1), new ArrayList<>()));
                }

                ReplicaFullValidTaskManager.switchSubTaskState(context.getSubTaskId(),
                    ReplicaFullValidTaskState.RUNNING,
                    ReplicaFullValidTaskState.FINISHED);
                metaTransactionTemplate.execute(t -> {
                    subTasks.forEach(subTaskMapper::insertSelective);
                    return null;
                });
            } catch (Exception e) {
                log.error("Failed to run init task!", e);
                subTaskMapper.update(
                    r -> r.set(RplFullValidSubTaskDynamicSqlSupport.summary).equalTo(JSON.toJSONString(e.toString()))
                        .set(RplFullValidSubTaskDynamicSqlSupport.taskState)
                        .equalTo(ReplicaFullValidTaskState.ERROR.toString())
                        .where(RplFullValidSubTaskDynamicSqlSupport.id, SqlBuilder.isEqualTo(context.getSubTaskId())));
            }
        } finally {
            MDC.remove(Constants.MDC_RPL_FULL_VALID_TASK_ID_KEY);
        }
    }

    private RplFullValidSubTask createCheckTaskMetaHelper(List<Object> lowerBound, List<Object> upperBound) {
        ReplicaFullValidCheckTask.TaskConfig taskConfig =
            new ReplicaFullValidCheckTask.TaskConfig(config.getSrcDb(), config.getSrcTb(), config.getDstDb(),
                config.getDstTb(), lowerBound, upperBound);
        return ReplicaFullValidCheckTask.generateTaskMeta(context.getFsmId(), context.getFullValidTaskId(), taskConfig);
    }

    public static RplFullValidSubTask generateTaskMeta(long fsmId, long taskId, TaskConfig config) {
        RplFullValidSubTask res = new RplFullValidSubTask();
        res.setStateMachineId(fsmId);
        res.setTaskId(taskId);
        res.setTaskType(ReplicaFullValidInitTask.class.getName());
        res.setTaskStage(ReplicaFullValidTaskStage.INIT.toString());
        res.setTaskState(ReplicaFullValidTaskState.READY.toString());
        res.setTaskConfig(JSON.toJSONString(config));
        return res;
    }

    @Data
    @AllArgsConstructor
    public static class TaskConfig {
        String srcDb;
        String srcTb;
        String dstDb;
        String dstTb;
    }

}
