/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.pipeline.CommandPipeline;
import com.aliyun.polardbx.binlog.daemon.vo.CommandResult;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_PORT;
import static com.aliyun.polardbx.binlog.ConfigKeys.INST_IP;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DELAY_ALARM_THRESHOLD_SECOND;

@Slf4j
public class TaskDistributor {

    private static final ResourceManager RESOURCE_MANAGER =
        new ResourceManager(DynamicApplicationConfig.getString(CLUSTER_ID));
    private static final CommandPipeline COMMAND_PIPELINE = new CommandPipeline();
    private static final String GREP_RPL_TASK_COUNT_COMMAND = "ps -ef | grep 'RplTaskEngine' | grep -v grep | wc -l";
    private static final String GREP_RPL_TASK_COMMAND = "ps -ef | grep 'RplTaskEngine' | grep -v grep";

    // todo 后续考虑持久化和增加版本号
    private static Set<String> containers = new HashSet<>();

    /**
     * For Leader
     */
    public synchronized static void distributeTasks() {
        try {
            List<Container> workers = RESOURCE_MANAGER.availableContainers();
            if (workers.isEmpty()) {
                log.error("distributeTasks, no running workers");
                return;
            }
            Set<String> nowContainers = workers.stream().map(Container::getHostString)
                .collect(Collectors.toSet());
            // 重启不进行rebalance
            // 历史container列表和当前container列表不一致时，触发rebalance
            boolean needRebalance = !containers.isEmpty() && !nowContainers.equals(containers);

            Map<String, Integer> workerLoads = new HashMap<>();
            for (Container worker : workers) {
                workerLoads.put(worker.getHostString(), 0);
            }
            int maxRunningTaskNum = computeMaxRunningTaskNum(workers);
            String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            List<RplTask> runningTasks = DbTaskMetaManager.listClusterTask(TaskStatus.RUNNING, clusterId);
            List<RplTask> readyTasks = DbTaskMetaManager.listClusterTask(TaskStatus.READY, clusterId);
            List<RplTask> restartTasks = DbTaskMetaManager.listClusterTask(TaskStatus.RESTART, clusterId);
            List<RplTask> toDistributeTasks = new ArrayList<>();

            // restart状态的任务，如果没有worker或者worker不在container列表里，直接视为ready task
            for (RplTask task : restartTasks) {
                if (StringUtils.isBlank(task.getWorker()) || !workerLoads.containsKey(task.getWorker())) {
                    readyTasks.add(task);
                }
            }

            for (RplTask task : runningTasks) {
                // 如果需要rebalance，那么直接全部放入待调度列表
                if (StringUtils.isBlank(task.getWorker()) || !isTaskRunning(task)
                    || !workerLoads.containsKey(task.getWorker()) || needRebalance) {
                    toDistributeTasks.add(task);
                    log.info("distributeTasks, need distribute running task: {}", task.getId());
                } else {
                    log.info("distributeTasks, task: {} no need to distribute, worker: {}, gmtModified: {}",
                        task.getId(),
                        task.getWorker(),
                        task.getGmtHeartbeat());
                    workerLoads.put(task.getWorker(), workerLoads.get(task.getWorker()) + 1);
                }
            }

            // the only way to set task to running state
            int readyToRunningTaskNum = Math.min(maxRunningTaskNum - runningTasks.size(), readyTasks.size());
            if (readyToRunningTaskNum > 0) {
                for (int i = 0; i < readyToRunningTaskNum; i++) {
                    DbTaskMetaManager.updateTaskStatus(readyTasks.get(i).getId(), TaskStatus.RUNNING);
                    // 优化历史调度亲和性，如果ready的task存在历史worker，则直接调度至该worker
                    // todo: 亲和性导致存在调度不均衡的可能，暂不处理
                    // 如果需要rebalance，那么直接全部放入待调度列表
                    if (StringUtils.isBlank(readyTasks.get(i).getWorker()) || needRebalance) {
                        log.info("distributeTasks, need distribute ready task: {}",
                            readyTasks.get(i).getId());
                        toDistributeTasks.add(readyTasks.get(i));
                    } else {
                        workerLoads.put(readyTasks.get(i).getWorker(),
                            workerLoads.get(readyTasks.get(i).getWorker()) + 1);
                    }
                }
            }

            log.info("distributeTasks, workerLoads before: {}", JSON.toJSONString(workerLoads));
            List<Map.Entry<String, Integer>> sortedWorkLoad = new ArrayList<>(workerLoads.entrySet());
            sortedWorkLoad.sort(Comparator.comparingInt(Map.Entry::getValue));

            for (RplTask task : toDistributeTasks) {
                Map.Entry<String, Integer> entry = sortedWorkLoad.get(0);
                DbTaskMetaManager.updateTaskWorker(task.getId(), entry.getKey());
                log.info("distributeTasks, task: {}, to worker: {}", task.getId(), entry.getKey());
                entry.setValue(entry.getValue() + 1);
                sortedWorkLoad.sort(Comparator.comparingInt(Map.Entry::getValue));
            }
            workerLoads = sortedWorkLoad.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            // TODO by jiyue resource check and alarm
            containers = nowContainers;
            log.info("distributeTasks end, workerLoads after: {}", JSON.toJSONString(workerLoads));
        } catch (Exception e) {
            log.error("distributeTasks error:", e);
            throw e;
        }
    }

    public static int computeMaxRunningTaskNum(List<Container> workers) {
        int totalCpu = 0;
        int totalMem = 0;
        for (Container worker : workers) {
            totalCpu = totalCpu + worker.getCapability().getCpu();
            totalMem = totalMem + worker.getCapability().getMemory_mb();
        }
        // sql flashback任务cpu占用率高，全量迁移cpu和内存占用率高
        // 综合下来，这里暂时以内存作为单任务所需资源
        return totalMem / DynamicApplicationConfig.getInt(ConfigKeys.RPL_SINGLE_TASK_MEMORY_WHEN_DISTRIBUTE);
    }

    /**
     * Check if task is RUNNING on any worker
     */
    private static boolean isTaskRunning(RplTask task) {
        if (!ServiceType.supportRunningCheck(ServiceType.valueOf(task.getType())) || !DynamicApplicationConfig
            .getBoolean(ConfigKeys.RPL_SUPPORT_RUNNING_CHECK)) {
            return true;
        }
        long when = System.currentTimeMillis() -
            DynamicApplicationConfig.getInt(ConfigKeys.RPL_TASK_KEEP_ALIVE_INTERVAL_SECONDS) * 1000;
        boolean after = task.getGmtHeartbeat().after(new Date(when));
        if (!after) {
            MonitorManager.getInstance().triggerAlarm(MonitorType.RPL_HEARTBEAT_TIMEOUT_ERROR, task.getId(),
                "check is task running error");
            log.error("Check isTaskRunning. task id: {}, task heartbeat time: {}, current - 300 seconds: {}",
                task.getId(), task.getGmtHeartbeat(), new Date(when));
        }
        return after;
    }

    /**
     * For Worker
     */
    public static void checkAndRunLocalTasks(String clusterId) throws Exception {
        log.info("checkAndRunLocalTasks start");
        String nodeHostString = DynamicApplicationConfig.getString(INST_IP) + ":" +
            DynamicApplicationConfig.getString(DAEMON_PORT);
        List<RplTask> localNeedRunTasks =
            DbTaskMetaManager.listTaskByService(nodeHostString, TaskStatus.RUNNING, clusterId);
        List<RplTask> localNeedRestartTasks =
            DbTaskMetaManager.listTaskByService(nodeHostString, TaskStatus.RESTART, clusterId);
        for (RplTask task : localNeedRunTasks) {
            if (!isTaskRunning(task)) {
                DbTaskMetaManager.updateTask(task.getId(), null, null, null, null,
                    new Date(System.currentTimeMillis()));
                COMMAND_PIPELINE.stopRplTask(task.getId());
            }
        }
        for (RplTask task : localNeedRestartTasks) {
            COMMAND_PIPELINE.stopRplTask(task.getId());
        }
        Set<Long> needRunTaskIds = localNeedRunTasks.stream().map(RplTask::getId).collect(Collectors.toSet());
        Set<Long> runningTaskIds = Sets.newHashSet(listLocalRunningTaskId());
        stopNoLocalTasks(runningTaskIds, needRunTaskIds);
        startLocalTasks(runningTaskIds, localNeedRunTasks);
        for (RplTask task : localNeedRestartTasks) {
            // restart means MUST stop once
            // 1. leader set tasks to restart
            // 2. worker stop them and then set to ready
            // 3. leader schedule ready tasks
            DbTaskMetaManager.updateTaskStatus(task.getId(), TaskStatus.READY);
        }

        List<RplTask> rplIncTasks = DbTaskMetaManager.listRunningTaskByType(nodeHostString,
            ServiceType.REPLICA_INC, clusterId);
        for (RplTask rplIncTask : rplIncTasks) {
            long delaySec = FSMMetaManager.computeTaskDelay(rplIncTask);
            if (delaySec > DynamicApplicationConfig.getInt(RPL_DELAY_ALARM_THRESHOLD_SECOND)) {
                MonitorManager.getInstance().triggerAlarmSync(MonitorType.IMPORT_INC_ERROR,
                    rplIncTask.getId(), String.format("主备复制延迟超时报警：延迟%s秒", delaySec));
            }
        }
        log.info("checkAndRunLocalTasks end");
    }

    /**
     * Check if task RUNNING on current worker
     */
    public static boolean isTaskLocalRunning(long taskId) throws Exception {
        List<Long> runningTaskIds = listLocalRunningTaskId();
        return runningTaskIds.contains(taskId);
    }

    public static List<Long> listLocalRunningTaskId() throws Exception {
        List<Long> runningTaskIds = new ArrayList<>();

        CommandResult countResult =
            COMMAND_PIPELINE.execCommand(new String[] {"bash", "-c", GREP_RPL_TASK_COUNT_COMMAND},
                3000);
        if (countResult.getCode() != 0) {
            log.warn("check local running RplTaskEngine fail, result code: {}, msg: {}",
                countResult.getCode(),
                countResult.getMsg());
            return runningTaskIds;
        }

        String countStr = StringUtils.split(countResult.getMsg(), System.getProperty("line.separator"))[0];
        if (Integer.parseInt(StringUtils.trim(countStr)) <= 0) {
            log.warn("no local running RplTaskEngine");
            return runningTaskIds;
        }

        CommandResult result = COMMAND_PIPELINE.execCommand(new String[] {"bash", "-c", GREP_RPL_TASK_COMMAND}, 3000);
        if (result.getCode() == 0) {
            String[] runningTaskInfos = StringUtils.split(result.getMsg(), System.getProperty("line.separator"));
            for (String taskInfo : runningTaskInfos) {
                String[] tokens = StringUtils.splitByWholeSeparator(taskInfo, "RplTaskEngine");
                Map<String, String> args = CommonUtil.handleArgs(StringUtils.trim(tokens[tokens.length - 1]));
                String taskId = args.get(RplConstants.TASK_ID);
                runningTaskIds.add(Long.valueOf(taskId));
            }
        } else {
            log.warn("check local running RplTaskEngine fail, result code: {}, msg: {}",
                result.getCode(),
                result.getMsg());
        }

        return runningTaskIds;
    }

    public static void stopNoLocalTasks(Set<Long> runningTaskIds, Set<Long> needRunTaskIds) throws Exception {
        if (runningTaskIds == null || runningTaskIds.size() == 0) {
            log.info("startLocalTasks, no tasks to stop");
            return;
        }

        for (Long runningTaskId : runningTaskIds) {
            if (!needRunTaskIds.contains(runningTaskId)) {
                COMMAND_PIPELINE.stopRplTask(runningTaskId);
                log.warn("stop local running RplTaskEngine {} not in {}", runningTaskId, needRunTaskIds);
            }
        }
    }

    public static void startLocalTasks(Set<Long> runningTaskIds, List<RplTask> needRunTasks) throws Exception {
        if (needRunTasks == null || needRunTasks.size() == 0) {
            log.info("startLocalTasks, no tasks to start");
            return;
        }

        for (RplTask needRunTask : needRunTasks) {
            if (!runningTaskIds.contains(needRunTask.getId())) {
                String taskName = CommonUtil.buildRplTaskName(needRunTask);
                RplTaskConfig config = DbTaskMetaManager.getTaskConfig(needRunTask.getId());
                if (config == null) {
                    MonitorManager.getInstance().triggerAlarm(MonitorType.RPL_HEARTBEAT_TIMEOUT_ERROR,
                        needRunTask.getId(), "Task has no config");
                    log.error("Task has no config. task id: {}", needRunTask.getId());
                }
                COMMAND_PIPELINE.startRplTask(needRunTask.getId(), taskName, config.getMemory());
                log.warn("start local running RplTaskEngine {} {}", needRunTask.getId(), taskName);
            }
        }
    }
}
