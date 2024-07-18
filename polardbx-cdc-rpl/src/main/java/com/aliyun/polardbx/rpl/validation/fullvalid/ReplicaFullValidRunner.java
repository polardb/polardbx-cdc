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
package com.aliyun.polardbx.rpl.validation.fullvalid;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.RplFullValidSubTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.RplFullValidSubTask;
import com.aliyun.polardbx.binlog.domain.po.RplFullValidTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidSubTask;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidSubTaskContext;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidTaskManager;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidTaskStage;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidTaskState;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author yudong
 * @since 2023/9/12 10:26
 **/
@ToString
public class ReplicaFullValidRunner {

    private static final Logger log = LoggerFactory.getLogger("fullValidLogger");

    private static ReplicaFullValidRunner instance;
    @Setter
    private long fsmId;
    @Setter
    private long rplTaskId;
    private DbMetaCache srcMetaCache;
    private DbMetaCache dstMetaCache;
    private ExecutorService subTaskExecutor;

    // key: sub task id, value: future of running task
    private final Map<Long, Future<?>> futureMap = new ConcurrentHashMap<>();

    private static final RplFullValidSubTaskMapper subTaskMapper =
        SpringContextHolder.getObject(RplFullValidSubTaskMapper.class);

    private ReplicaFullValidRunner() {
    }

    public static synchronized ReplicaFullValidRunner getInstance() {
        if (instance == null) {
            instance = new ReplicaFullValidRunner();
        }
        return instance;
    }

    public void run() {
        log.info("replica full valid runner start to run...");

        RplTaskConfig taskConfig = DbTaskMetaManager.getTaskConfig(rplTaskId);
        ExtractorConfig extractorConfig = JSON.parseObject(taskConfig.getExtractorConfig(), ExtractorConfig.class);
        HostInfo srcHostInfo = extractorConfig.getHostInfo();
        ApplierConfig applierConfig = JSON.parseObject(taskConfig.getApplierConfig(), ApplierConfig.class);
        HostInfo dstHostInfo = applierConfig.getHostInfo();

        // 所有的全量校验任务共用一个连接池
        int poolSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_FULL_VALID_CN_CONN_POOL_COUNT);
        srcMetaCache = new DbMetaCache(srcHostInfo, poolSize, poolSize, true);
        dstMetaCache = new DbMetaCache(dstHostInfo, poolSize, poolSize, true);

        // 所有的全量校验任务共用一个线程池
        subTaskExecutor = createThreadPool();

        // 幂等处理：将所有处于RUNNING状态的子任务状态改为READY重跑
        List<RplFullValidSubTask> runningSubTasks =
            ReplicaFullValidTaskManager.selectSubTasksByState(fsmId, ReplicaFullValidTaskState.RUNNING);
        for (RplFullValidSubTask task : runningSubTasks) {
            task.setTaskState(ReplicaFullValidTaskState.READY.toString());
            subTaskMapper.updateByPrimaryKey(task);
            log.info("update sub task state from running to ready, sub task id:{}", task.getId());
        }

        while (true) {
            try {
                pushTaskToNextStage();
                List<ReplicaFullValidSubTask> tasks = buildRunnableTasks();
                if (CollectionUtils.isEmpty(tasks)) {
                    Thread.sleep(5000);
                    continue;
                }

                for (ReplicaFullValidSubTask subTask : tasks) {
                    log.info("prepare to submit subtask to executor, sub task id:{}", subTask.getTaskId());
                    try {
                        futureMap.put(subTask.getTaskId(), subTaskExecutor.submit(subTask));
                        log.info("success to submit subtask to executor, sub task id:{}", subTask.getTaskId());
                    } catch (RejectedExecutionException e) {
                        log.warn("failed to submit task to executor because queue is full. sub task id:{}",
                            subTask.getTaskId());
                        break;
                    }
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("replica full valid runner meet an exception!", e);
            }
        }
    }

    private ExecutorService createThreadPool() {
        int coreSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_FULL_VALID_RUNNER_THREAD_POOL_CORE_SIZE);
        int maxSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_FULL_VALID_RUNNER_THREAD_POOL_MAX_SIZE);
        int keepAliveTime =
            DynamicApplicationConfig.getInt(ConfigKeys.RPL_FULL_VALID_RUNNER_THREAD_POOL_KEEP_ALIVE_TIME_SECONDS);
        int queueSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_FULL_VALID_RUNNER_THREAD_POOL_QUEUE_SIZE);

        return new ThreadPoolExecutor(coreSize, maxSize, keepAliveTime, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(queueSize), new NamedThreadFactory("validator"));
    }

    private void pushTaskToNextStage() {
        List<RplFullValidTask> runningTasks =
            ReplicaFullValidTaskManager.selectTasksByState(fsmId, ReplicaFullValidTaskState.RUNNING);
        for (RplFullValidTask task : runningTasks) {
            ReplicaFullValidTaskStage stage = ReplicaFullValidTaskStage.valueOf(task.getTaskStage());
            List<RplFullValidSubTask> subTasks =
                ReplicaFullValidTaskManager.selectSubTasksByTaskIdAndTaskStage(task.getId(), stage);

            boolean hasError = subTasks.stream()
                .anyMatch(subTask -> subTask.getTaskState().equals(ReplicaFullValidTaskState.ERROR.toString()));
            if (hasError) {
                ReplicaFullValidTaskManager.switchTaskState(task.getId(), ReplicaFullValidTaskState.RUNNING,
                    ReplicaFullValidTaskState.ERROR);
                continue;
            }

            boolean allFinished = subTasks.stream()
                .allMatch(subTask -> subTask.getTaskState().equals(ReplicaFullValidTaskState.FINISHED.toString()));
            if (allFinished) {
                ReplicaFullValidTaskStage from = ReplicaFullValidTaskStage.valueOf(task.getTaskStage());
                ReplicaFullValidTaskStage to = ReplicaFullValidTaskStage.nextStage(from);
                if (to == null) {
                    ReplicaFullValidTaskManager.switchTaskState(task.getId(), ReplicaFullValidTaskState.RUNNING,
                        ReplicaFullValidTaskState.FINISHED);
                } else {
                    ReplicaFullValidTaskManager.switchTaskStage(task.getId(), from, to);
                }
            }
        }
    }

    /**
     * 查询rpl_full_valid_sub_task表，将处于ready状态的sub task构造成一个可执行的task，交给线程池执行
     *
     * @return a list of runnable tasks
     */
    private List<ReplicaFullValidSubTask> buildRunnableTasks() {
        log.info("start to build runnable tasks...");

        // reset error task
        boolean autoReset = DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_FULL_VALID_AUTO_RESET_ERROR_TASKS);
        if (autoReset) {
            ReplicaFullValidTaskManager.resetErrorTasks(fsmId);
        }

        List<RplFullValidSubTask> readyTasks =
            ReplicaFullValidTaskManager.selectSubTasksByState(fsmId, ReplicaFullValidTaskState.READY);
        if (CollectionUtils.isEmpty(readyTasks)) {
            return null;
        }

        List<ReplicaFullValidSubTask> result = new ArrayList<>();
        for (RplFullValidSubTask subTask : readyTasks) {
            if (futureMap.containsKey(subTask.getId())) {
                log.info("sub task is already running, will not resubmit again. sub task id:{}", subTask.getId());
                continue;
            }
            // 采用反射增加灵活性，主要为了以后扩展schema校验方便
            Class<?> clazz;
            try {
                clazz = Class.forName(subTask.getTaskType());
                ReplicaFullValidSubTaskContext context =
                    new ReplicaFullValidSubTaskContext(srcMetaCache, dstMetaCache, subTask.getStateMachineId(),
                        subTask.getTaskId(), subTask.getId());
                ReplicaFullValidSubTask task =
                    (ReplicaFullValidSubTask) clazz.getConstructor(ReplicaFullValidSubTaskContext.class)
                        .newInstance(context);
                task.setTaskId(subTask.getId());
                result.add(task);
            } catch (Exception e) {
                log.error("class not found for name:" + subTask.getTaskType());
            }
        }

        log.info("finished to build runnable tasks, size:{}", result.size());
        return result;
    }

    public void stopTasks(List<Long> taskIds) {
        for (Long taskId : taskIds) {
            Future<?> future = futureMap.remove(taskId);
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}
