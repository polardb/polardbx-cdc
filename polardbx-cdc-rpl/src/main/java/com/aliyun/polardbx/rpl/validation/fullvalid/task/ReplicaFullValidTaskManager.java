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
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.RplFullValidDiffDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplFullValidDiffMapper;
import com.aliyun.polardbx.binlog.dao.RplFullValidSubTaskDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplFullValidSubTaskMapper;
import com.aliyun.polardbx.binlog.dao.RplFullValidTaskDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplFullValidTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.RplFullValidSubTask;
import com.aliyun.polardbx.binlog.domain.po.RplFullValidTask;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.validation.fullvalid.ReplicaFullValidProgressInfo;
import com.aliyun.polardbx.rpl.validation.fullvalid.ReplicaFullValidRunner;
import com.aliyun.polardbx.rpl.validation.fullvalid.ReplicaFullValidUtil;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 全量校验任务生命周期管理
 * rpl_full_valid_task: 每行对应一个校验逻辑表的任务
 * rpl_full_valid_sub_task: 每行对应一个子任务，包括：sample，check, repair
 * <p>
 * 全量校验任务有多个stage，组成一个流水线：
 * INIT -> CHECK -> REPAIR
 * 每个stage中有一个或者多个子任务
 * INIT: sample & create check sub task
 * CHECK: exec check subtask
 * REPAIR: exec repair subtask
 * <p>
 * 父任务可能处于三种状态：RUNNING / PAUSED / FINISHED
 * 子任务可能处于四种状态：READY / RUNNING / PAUSED / FINISHED
 * 只有处于READY状态的子任务才会被调度执行
 *
 * @author yudong
 * @since 2023/10/18 15:08
 **/
public class ReplicaFullValidTaskManager {

    private static final Logger logger = LoggerFactory.getLogger("fullValidLogger");

    private static final RplFullValidTaskMapper taskMapper =
        SpringContextHolder.getObject(RplFullValidTaskMapper.class);
    private static final RplFullValidSubTaskMapper subTaskMapper =
        SpringContextHolder.getObject(RplFullValidSubTaskMapper.class);
    private static final RplFullValidDiffMapper diffMapper =
        SpringContextHolder.getObject(RplFullValidDiffMapper.class);
    private static final JdbcTemplate polarxTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
    private static final TransactionTemplate metaTransactionTemplate =
        SpringContextHolder.getObject("metaTransactionTemplate");

    public static ResultCode<?> createTask(String channel, String dbName, String tbName, String mode) {
        try {
            RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
            if (null == stateMachine) {
                throw new IllegalArgumentException("channel not exists! channel:" + channel);
            }

            List<String> tables = getTables(dbName, tbName);

            // 用户可能提交了一个校验库的请求，但是库内没有表
            if (tables.isEmpty()) {
                logger.warn("DB is empty, db:{}", dbName);
                return ResultCode.builder().code(RplConstants.SUCCESS_CODE)
                    .msg("There is no table in the database! DB:" + dbName)
                    .data(RplConstants.SUCCESS).build();
            }

            logger.info("Try to create full valid task for tables: {}", tables);
            for (String tb : tables) {
                // TODO @jiyue: support db mapping
                createTask(stateMachine.getId(), dbName, tb, dbName, tb, ReplicaFullValidType.FULL_DATA, mode);
            }
        } catch (Exception e) {
            logger.error("Failed to create task for table {}.{}", dbName, tbName, e);
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("Failed to create task, error info:" + e.getMessage()).data(RplConstants.FAILURE).build();
        }

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("create replica task success")
            .data(RplConstants.SUCCESS).build();
    }

    /**
     * 创建全量校验任务
     * 如果当前系统中有这张表对应的全量校验任务，且处于RUNNING状态，则直接返回；否则，清空上次任务的数据，重新创建
     * 如果当前系统中没有这张表对应的全量校验任务，则直接进行创建
     */
    private static void createTask(long fsmId, String srcDb, String srcTb, String dstDb, String dstTb,
                                   ReplicaFullValidType type, String mode) {
        Optional<RplFullValidTask> optional = selectTaskByTableName(dstDb, dstTb);
        if (optional.isPresent()) {
            RplFullValidTask task = optional.get();
            if (isTaskFinished(task)) {
                logger.info("Task has already finished, will recreate a new task. table:{}.{}", dstDb, dstTb);
                metaTransactionTemplate.execute(t -> {
                    purgeTaskImpl(task.getId());
                    createTaskImpl(fsmId, srcDb, srcTb, dstDb, dstTb, type, mode);
                    return null;
                });
            } else {
                logger.info("Task has not finished, will not create a new one. table:{}.{}", dstDb, dstTb);
            }
        } else {
            logger.info("There exists no task, will create. table:{}.{}", dstDb, dstTb);
            metaTransactionTemplate.execute(t -> {
                createTaskImpl(fsmId, srcDb, srcTb, dstDb, dstTb, type, mode);
                return null;
            });
        }
    }

    /**
     * 为逻辑表创建全量校验任务
     */
    private static void createTaskImpl(long fsmId, String srcDb, String srcTb, String dstDb, String dstTb,
                                       ReplicaFullValidType type, String mode) {
        RplFullValidTask task = new RplFullValidTask();
        task.setStateMachineId(fsmId);
        task.setSrcLogicalDb(srcDb);
        task.setSrcLogicalTable(srcTb);
        task.setDstLogicalDb(dstDb);
        task.setDstLogicalTable(dstTb);
        task.setTaskStage(ReplicaFullValidType.getFirstStage(type).toString());
        task.setTaskState(ReplicaFullValidTaskState.RUNNING.toString());
        taskMapper.insertSelective(task);

        Optional<RplFullValidTask> optionalTask = selectTaskByTableName(dstDb, dstTb);
        if (!optionalTask.isPresent()) {
            logger.error("create full valid task for table {}.{} failed!", dstDb, dstTb);
            return;
        }

        task = optionalTask.get();

        RplFullValidSubTask subTask;
        if (type == ReplicaFullValidType.FULL_DATA) {
            ReplicaFullValidInitTask.TaskConfig config =
                new ReplicaFullValidInitTask.TaskConfig(srcDb, srcTb, dstDb, dstTb, mode);
            subTask = ReplicaFullValidInitTask.generateTaskMeta(fsmId, task.getId(), config);
            subTaskMapper.insertSelective(subTask);
        } else if (type == ReplicaFullValidType.SCHEMA) {
            for (ReplicaFullValidCheckSchemaType schemaType : ReplicaFullValidCheckSchemaType.values()) {
                ReplicaFullValidCheckSchemaTask.TaskConfig config =
                    new ReplicaFullValidCheckSchemaTask.TaskConfig(schemaType.name());
                subTask = ReplicaFullValidCheckSchemaTask.generateTaskMeta(fsmId, task.getId(), config);
                subTaskMapper.insertSelective(subTask);
            }
        }
    }

    public static ResultCode<?> createSchemaCheckTask(String channel) {
        logger.info("Try to create schema valid task for channel: {}", channel);
        RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
        if (null == stateMachine) {
            throw new IllegalArgumentException("channel not exists! channel:" + channel);
        }
        createTask(stateMachine.getId(), null, null, null, null, ReplicaFullValidType.SCHEMA, null);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("create replica schema check task success")
            .data(RplConstants.SUCCESS).build();
    }

    /**
     * 暂停全量校验任务
     * 将所有处于READY的子任务状态设置为PAUSED，暂停其调度
     *
     * @param dbName db name
     * @param tbName tb name, if null, then stop all tables in db
     */
    public static ResultCode<?> pauseTask(String dbName, String tbName) {
        try {
            List<String> tables = getTables(dbName, tbName);
            for (String tb : tables) {
                Optional<RplFullValidTask> optional = selectTaskByTableName(dbName, tb);
                if (optional.isPresent() && isTaskRunning(optional.get())) {
                    metaTransactionTemplate.execute(t -> {
                        pauseTaskImpl(optional.get().getId());
                        return null;
                    });
                }
            }
        } catch (Exception e) {
            logger.error("stop full valid task for table {}.{} failed!", dbName, tbName, e);
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("Pause replica task failed! db name:" + dbName + ", tb name" + tbName).data(RplConstants.FAILURE)
                .build();
        }

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("stop replica task success")
            .data(RplConstants.SUCCESS).build();
    }

    public static ResultCode<?> pauseSchemaCheckTask() {
        try {
            Optional<RplFullValidTask> optional = selectTaskByTableName(null, null);
            if (optional.isPresent() && isTaskRunning(optional.get())) {
                metaTransactionTemplate.execute(t -> {
                    pauseTaskImpl(optional.get().getId());
                    return null;
                });
            }
        } catch (Exception e) {
            logger.error("stop full valid task for schema check failed!", e);
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("Pause replica schema check task failed!").data(RplConstants.FAILURE)
                .build();
        }

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("stop replica task success")
            .data(RplConstants.SUCCESS).build();
    }

    private static void pauseTaskImpl(long taskId) {
        List<RplFullValidSubTask> subTasks = selectSubTasksByTaskId(taskId);
        // 只能暂停还没有运行的子任务. 如果这个任务已经提交到线程池中还没来得及运行，那么这里更改完状态之后
        // 在任务运行最初会检测它的state是否还是ready，如果不是就不会运行.
        subTasks.forEach(
            t -> switchSubTaskState(t.getId(), ReplicaFullValidTaskState.READY, ReplicaFullValidTaskState.PAUSED));
        switchTaskState(taskId, ReplicaFullValidTaskState.RUNNING, ReplicaFullValidTaskState.PAUSED);
    }

    /**
     * 继续全量校验任务的执行
     * 将所有处于PAUSED的子任务状态设置为PAUSED，恢复其调度
     *
     * @param dbName db name
     * @param tbName tb name, if null, then continue all tables in db
     */
    public static ResultCode<?> continueTask(String dbName, String tbName) {
        try {
            List<String> tables = getTables(dbName, tbName);
            for (String tb : tables) {
                Optional<RplFullValidTask> optional = selectTaskByTableName(dbName, tb);
                if (optional.isPresent() && isTaskStopped(optional.get())) {
                    metaTransactionTemplate.execute(t -> {
                        continueTaskImpl(optional.get().getId());
                        return null;
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Continue full valid task failed! db:{}, tb:{}", dbName, tbName);
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("Continue replica task failed! db name:" + dbName + ", tb name:" + tbName)
                .data(RplConstants.FAILURE).build();
        }

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("continue replica task success")
            .data(RplConstants.SUCCESS).build();
    }

    public static ResultCode<?> continueSchemaCheckTask() {
        try {
            Optional<RplFullValidTask> optional = selectTaskByTableName(null, null);
            if (optional.isPresent() && isTaskStopped(optional.get())) {
                metaTransactionTemplate.execute(t -> {
                    continueTaskImpl(optional.get().getId());
                    return null;
                });
            }
        } catch (Exception e) {
            logger.error("Continue full valid schema check task failed!");
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("Continue replica schema check task failed!")
                .data(RplConstants.FAILURE).build();
        }

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("continue replica task success")
            .data(RplConstants.SUCCESS).build();
    }

    private static void continueTaskImpl(long taskId) {
        logger.info("task {} is stopped, will resume it.", taskId);
        List<RplFullValidSubTask> subTasks = selectSubTasksByTaskId(taskId);
        subTasks.forEach(
            t -> switchSubTaskState(t.getId(), ReplicaFullValidTaskState.PAUSED, ReplicaFullValidTaskState.READY));
        switchTaskState(taskId, ReplicaFullValidTaskState.PAUSED, ReplicaFullValidTaskState.RUNNING);
    }

    /**
     * 取消全量校验任务
     * 等待所有正在运行中的子任务运行结束（否则，正在运行中的子任务可能会产生垃圾数据，得不到清理）
     * 然后清理元数据以及垃圾数据(rpl_full_valid_diff)
     *
     * @param dbName db name
     * @param tbName tb name, if null, then cancel all tables in db
     */
    public static ResultCode<?> cancelTask(String dbName, String tbName) {
        try {
            List<String> tables = getTables(dbName, tbName);
            for (String tb : tables) {
                Optional<RplFullValidTask> optional = selectTaskByTableName(dbName, tb);
                if (optional.isPresent()) {
                    long taskId = optional.get().getId();
                    metaTransactionTemplate.execute(t -> {
                        cancelTaskImpl(taskId);
                        return null;
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Cancel full valid task failed! db:{}, tb:{}", dbName, tbName);
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("Cancel replica full valid task failed! db name:" + dbName + ", tb name:" + tbName)
                .data(RplConstants.FAILURE).build();
        }

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("cancel replica task success")
            .data(RplConstants.SUCCESS).build();
    }

    public static ResultCode<?> cancelSchemaCheckTask() {
        try {
            Optional<RplFullValidTask> optional = selectTaskByTableName(null, null);
            if (optional.isPresent()) {
                long taskId = optional.get().getId();
                metaTransactionTemplate.execute(t -> {
                    cancelTaskImpl(taskId);
                    return null;
                });
            }
        } catch (Exception e) {
            logger.error("Cancel full valid schema check task failed!");
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("Cancel replica full valid schema check task failed!")
                .data(RplConstants.FAILURE).build();
        }

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("cancel replica task success")
            .data(RplConstants.SUCCESS).build();
    }

    public static void cancelTaskImpl(long taskId) {
        pauseTaskImpl(taskId);
        interruptTasksImpl(taskId);
        purgeTaskImpl(taskId);
    }

    /**
     * 中断正在运行的子任务
     * 注意与pause区分，pause是暂停调度ready的子任务
     */
    private static void interruptTasksImpl(long taskId) {
        List<RplFullValidSubTask> subTasks = selectSubTasksByTaskId(taskId);
        List<Long> runningTaskIds = new ArrayList<>();
        for (RplFullValidSubTask subTask : subTasks) {
            if (subTask.getTaskState().equals(ReplicaFullValidTaskState.RUNNING.toString())) {
                runningTaskIds.add(subTask.getId());
            }
        }
        ReplicaFullValidRunner.getInstance().stopTasks(runningTaskIds);
    }

    /**
     * 重置全量校验任务
     *
     * @param dbName db name
     * @param tbName tb name, if null, then reset all tables in db
     */
    public static ResultCode<?> resetTask(String dbName, String tbName) {
        try {
            List<String> tables = getTables(dbName, tbName);

            for (String tb : tables) {
                Optional<RplFullValidTask> fullValidTask = selectTaskByTableName(dbName, tb);
                if (fullValidTask.isPresent()) {
                    RplFullValidTask task = fullValidTask.get();
                    metaTransactionTemplate.execute(t -> {
                        cancelTaskImpl(task.getId());
                        createTaskImpl(task.getStateMachineId(), task.getSrcLogicalDb(), task.getSrcLogicalTable(),
                            task.getDstLogicalDb(), task.getDstLogicalTable(), ReplicaFullValidType.FULL_DATA, null);
                        return null;
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Reset full valid task failed! db:{}, tb:{}", dbName, tbName);
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("Reset replica full valid task failed! db name:" + dbName + ", tb name:" + tbName)
                .data(RplConstants.FAILURE).build();
        }

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("reset replica task success")
            .data(RplConstants.SUCCESS).build();
    }

    public static ResultCode<?> resetSchemaCheckTask() {
        try {
            Optional<RplFullValidTask> fullValidTask = selectTaskByTableName(null, null);
            if (fullValidTask.isPresent()) {
                RplFullValidTask task = fullValidTask.get();
                metaTransactionTemplate.execute(t -> {
                    cancelTaskImpl(task.getId());
                    createTaskImpl(task.getStateMachineId(), task.getSrcLogicalDb(), task.getSrcLogicalTable(),
                        task.getDstLogicalDb(), task.getDstLogicalTable(), ReplicaFullValidType.SCHEMA, null);
                    return null;
                });
            }
        } catch (Exception e) {
            logger.error("Reset full valid schema check task failed!");
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("Reset replica full valid schema check task failed!")
                .data(RplConstants.FAILURE).build();
        }

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("reset replica task success")
            .data(RplConstants.SUCCESS).build();
    }

    private static void purgeTaskImpl(long taskId) {
        diffMapper.delete(r -> r.where(RplFullValidDiffDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId)));
        subTaskMapper.delete(
            r -> r.where(RplFullValidSubTaskDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId)));
        taskMapper.delete(r -> r.where(RplFullValidTaskDynamicSqlSupport.id, SqlBuilder.isEqualTo(taskId)));
    }

    public static ResultCode<?> showProgress(String dbName, String tbName) {
        try {
            List<ReplicaFullValidProgressInfo> res = new ArrayList<>();
            List<String> tables = getTables(dbName, tbName);
            for (String tb : tables) {
                Optional<RplFullValidTask> fullValidTask = selectTaskByTableName(dbName, tb);
                if (fullValidTask.isPresent()) {
                    RplFullValidTask task = fullValidTask.get();
                    ReplicaFullValidProgressInfo info = new ReplicaFullValidProgressInfo();
                    info.setDbName(task.getDstLogicalDb());
                    info.setTbName(task.getDstLogicalTable());
                    info.setStage(task.getTaskStage());
                    info.setStatus(task.getTaskState());
                    if (task.getTaskStage().equals(ReplicaFullValidTaskStage.CHECK.toString())) {
                        List<RplFullValidSubTask> subTasks =
                            ReplicaFullValidTaskManager.selectSubTasksByTaskIdAndTaskStage(task.getId(),
                                ReplicaFullValidTaskStage.CHECK);
                        int finishedTasks = 0;
                        int miss = 0;
                        int diff = 0;
                        int orphan = 0;
                        boolean success = true;
                        for (RplFullValidSubTask subTask : subTasks) {
                            if (subTask.getTaskState().equals(ReplicaFullValidTaskState.FINISHED.toString())
                                && !StringUtils.isBlank(subTask.getSummary())) {
                                finishedTasks++;
                                ReplicaFullValidCheckTask.TaskSummary taskSummary =
                                    JSON.parseObject(subTask.getSummary(), ReplicaFullValidCheckTask.TaskSummary.class);
                                miss += taskSummary.miss;
                                diff += taskSummary.diff;
                                orphan += taskSummary.orphan;
                                success &= taskSummary.success;
                            }
                        }

                        // FIXME：no diff可能是运行中出错，最好用一个单独的列表示运行状态
                        StringBuilder summarySb = new StringBuilder();
                        if (finishedTasks == subTasks.size()) {
                            if (success) {
                                summarySb.append("SUCCESS");
                            } else {
                                summarySb.append("FAILED");
                                if (diff > 0) {
                                    summarySb.append(", DIFF:").append(diff);
                                }
                                if (miss > 0) {
                                    summarySb.append(", MISS:").append(miss);
                                }
                                if (orphan > 0) {
                                    summarySb.append(", ORPHAN:").append(orphan);
                                }
                            }
                        } else {
                            int progress = (int) (finishedTasks / (double) subTasks.size() * 100);
                            summarySb.append("PROGRESS:").append(progress).append("%");
                            if (success) {
                                summarySb.append(", NO DIFF");
                            } else {
                                if (diff > 0) {
                                    summarySb.append(", DIFF:").append(diff);
                                }
                                if (miss > 0) {
                                    summarySb.append(", MISS:").append(miss);
                                }
                                if (orphan > 0) {
                                    summarySb.append(", ORPHAN:").append(orphan);
                                }
                            }
                        }
                        info.setSummary(summarySb.toString());
                    }
                    res.add(info);
                }
            }
            return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("show progress success")
                .data(JSON.toJSONString(res)).build();
        } catch (Exception e) {
            logger.error("Show full valid task progress! db:{}, tb:{}", dbName, tbName, e);
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("Show replica full valid task progress! db name:" + dbName + ", tb name:" + tbName)
                .data(RplConstants.FAILURE).build();
        }
    }

    /**
     * 获得校验的表名
     *
     * @param dbName db name, not null
     * @param tbName tb name, if null, then return all table names in db
     */
    private static List<String> getTables(String dbName, String tbName) {
        List<String> result = new ArrayList<>();
        if (tbName == null) {
            List<String> tables = ReplicaFullValidUtil.showTables(polarxTemplate, dbName);
            result.addAll(tables);
        } else {
            if (ReplicaFullValidUtil.isTableExist(polarxTemplate, dbName, tbName)) {
                result.add(tbName);
            } else {
                throw new IllegalArgumentException(
                    "Table not exist! dbname:" + dbName + ", tbName:" + tbName);
            }
        }
        return result;
    }

    public static Optional<RplFullValidTask> selectTaskByTableName(String dbName, String tbName) {
        return taskMapper.selectOne(
            s -> s.where(RplFullValidTaskDynamicSqlSupport.dstLogicalDb,
                    dbName == null ? SqlBuilder.isNull() : SqlBuilder.isEqualTo(dbName))
                .and(RplFullValidTaskDynamicSqlSupport.dstLogicalTable,
                    tbName == null ? SqlBuilder.isNull() : SqlBuilder.isEqualTo(tbName)));
    }

    public static List<RplFullValidTask> selectTasksByFsmId(long fsmId) {
        return taskMapper.select(
            s -> s.where(RplFullValidTaskDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(fsmId)));
    }

    public static List<RplFullValidTask> selectTasksByState(long fsmId, ReplicaFullValidTaskState state) {
        return taskMapper.select(
            s -> s.where(RplFullValidTaskDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(fsmId))
                .and(RplFullValidTaskDynamicSqlSupport.taskState, SqlBuilder.isEqualTo(state.toString())));
    }

    public static List<RplFullValidSubTask> selectSubTasksByState(long fsmId, ReplicaFullValidTaskState state) {
        return subTaskMapper.select(
            s -> s.where(RplFullValidSubTaskDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(fsmId))
                .and(RplFullValidSubTaskDynamicSqlSupport.taskState, SqlBuilder.isEqualTo(state.toString())));
    }

    public static void resetErrorTasks(long fsmId) {
        List<RplFullValidTask> errorTasks = selectTasksByState(fsmId, ReplicaFullValidTaskState.ERROR);
        for (RplFullValidTask task : errorTasks) {
            logger.info("reset error task:{}", task.getId());
            resetTask(task.getDstLogicalDb(), task.getDstLogicalTable());
        }
    }

    public static List<RplFullValidSubTask> selectSubTasksByTaskId(long taskId) {
        return subTaskMapper.select(
            r -> r.where(RplFullValidSubTaskDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId)));
    }

    public static List<RplFullValidSubTask> selectSubTasksByTaskIdAndTaskStage(long taskId,
                                                                               ReplicaFullValidTaskStage stage) {
        return subTaskMapper.select(
            r -> r.where(RplFullValidSubTaskDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId))
                .and(RplFullValidSubTaskDynamicSqlSupport.taskStage, SqlBuilder.isEqualTo(stage.toString())));
    }

    public static void switchTaskStage(long taskId, ReplicaFullValidTaskStage from,
                                       ReplicaFullValidTaskStage to) {
        taskMapper.update(r -> r.set(RplFullValidTaskDynamicSqlSupport.taskStage).equalTo(to.toString())
            .where(RplFullValidTaskDynamicSqlSupport.id, SqlBuilder.isEqualTo(taskId))
            .and(RplFullValidTaskDynamicSqlSupport.taskStage, SqlBuilder.isEqualTo(from.toString())));
    }

    public static void switchTaskState(long taskId, ReplicaFullValidTaskState from,
                                       ReplicaFullValidTaskState to) {
        taskMapper.update(r -> r.set(RplFullValidTaskDynamicSqlSupport.taskState).equalTo(to.toString())
            .where(RplFullValidTaskDynamicSqlSupport.id, SqlBuilder.isEqualTo(taskId))
            .and(RplFullValidTaskDynamicSqlSupport.taskState, SqlBuilder.isEqualTo(from.toString())));
    }

    public static boolean switchSubTaskState(long taskId, ReplicaFullValidTaskState from,
                                             ReplicaFullValidTaskState to) {
        int affectedRows =
            subTaskMapper.update(r -> r.set(RplFullValidSubTaskDynamicSqlSupport.taskState).equalTo(to.toString())
                .where(RplFullValidSubTaskDynamicSqlSupport.id, SqlBuilder.isEqualTo(taskId))
                .and(RplFullValidSubTaskDynamicSqlSupport.taskState, SqlBuilder.isEqualTo(from.toString())));
        return affectedRows == 1;
    }

    private static boolean isTaskRunning(RplFullValidTask task) {
        return StringUtils.equals(task.getTaskState(), ReplicaFullValidTaskState.RUNNING.toString());
    }

    private static boolean isTaskStopped(RplFullValidTask task) {
        return StringUtils.equals(task.getTaskState(), ReplicaFullValidTaskState.PAUSED.toString());
    }

    private static boolean isTaskFinished(RplFullValidTask task) {
        String stage = task.getTaskStage();
        String state = task.getTaskState();
        if (state.equals(ReplicaFullValidTaskState.ERROR.toString())) {
            return true;
        }
        if (stage.equals(ReplicaFullValidTaskStage.CHECK.toString())) {
            return state.equals(ReplicaFullValidTaskState.FINISHED.toString());
        }
        if (stage.equals(ReplicaFullValidTaskStage.SCHEMA_CHECK.toString())) {
            return state.equals(ReplicaFullValidTaskState.FINISHED.toString());
        }
        return false;
    }

    public static boolean isAllTaskFinished(long fsmId) {
        List<RplFullValidTask> tasks = selectTasksByFsmId(fsmId);
        for (RplFullValidTask task : tasks) {
            if (!isTaskFinished(task)) {
                return false;
            }
        }
        return true;
    }

}
