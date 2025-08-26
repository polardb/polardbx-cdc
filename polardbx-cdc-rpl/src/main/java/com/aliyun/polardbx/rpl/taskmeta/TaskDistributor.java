/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_PORT;
import static com.aliyun.polardbx.binlog.ConfigKeys.INST_IP;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DELAY_ALARM_THRESHOLD_SECOND;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_RESOURCE_USE_RATIO;

@Slf4j
public class TaskDistributor {

    public static ResourceManager RESOURCE_MANAGER =
        new ResourceManager(DynamicApplicationConfig.getString(CLUSTER_ID));
    private static final CommandPipeline COMMAND_PIPELINE = new CommandPipeline();
    private static final String GREP_RPL_TASK_COUNT_COMMAND = "ps -ef | grep 'RplTaskEngine' | grep -v grep | wc -l";
    private static final String GREP_RPL_TASK_COMMAND = "ps -ef | grep 'RplTaskEngine' | grep -v grep";

    // todo 后续考虑持久化和增加版本号
    public static Map<String, Integer> containerMemory = new HashMap<>();
    private static String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);

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

            // 判断是否要 rebalance:历史container列表和当前container列表的内存不一致时，触发rebalance
            // 默认占用95%的pod内存
            double ratio = DynamicApplicationConfig.getDouble(RPL_RESOURCE_USE_RATIO);
            Map<String, Integer> nowContainers = workers.stream().collect(Collectors.toMap(Container::getHostString,
                c -> (int) (c.getCapability().getMemory_mb() * ratio)));
            boolean needRebalance = !nowContainers.equals(containerMemory);
            Map<String, Integer> workerResiMemory = new HashMap<>(nowContainers);

            // 优先调度 running task
            // running task 调度失败，不继续进行后续调度
            if (distributeRunningTasks(workerResiMemory, needRebalance)) {
                distributeReadyTasks(workerResiMemory);
            }

            // 记录当前container列表
            containerMemory = nowContainers;
        } catch (Exception e) {
            log.error("distributeTasks error:", e);
            throw e;
        }
    }

    public static boolean distributeRunningTasks(Map<String, Integer> workerResiMemory, boolean needRebalance) {
        List<RplTask> runningTasks = DbTaskMetaManager.listClusterTask(TaskStatus.RUNNING, clusterId);
        for (RplTask task : runningTasks) {
            if (!distributeOneTask(task, workerResiMemory, !isTaskRunning(task) || needRebalance)) {
                return false;
            }
        }
        return true;
    }

    public static void distributeReadyTasks(Map<String, Integer> workerResiMemory) {
        List<RplTask> readyTasks = DbTaskMetaManager.listClusterTask(TaskStatus.READY, clusterId);
        List<RplTask> restartTasks = DbTaskMetaManager.listClusterTask(TaskStatus.RESTART, clusterId);

        // restart状态的任务，如果有在container列表里的worker，那么等待local worker kill后设置为ready再调度
        // 如果没有worker或者worker不在container列表里，直接视为ready task
        for (RplTask task : restartTasks) {
            if (StringUtils.isBlank(task.getWorker()) || !workerResiMemory.containsKey(task.getWorker())) {
                readyTasks.add(task);
            }
        }

        // the only way to set task to running state
        for (RplTask task : readyTasks) {
            if (!distributeOneTask(task, workerResiMemory, true)) {
                return;
            }
            DbTaskMetaManager.updateTaskStatus(task.getId(), TaskStatus.RUNNING);
        }
    }

    public static boolean distributeOneTask(RplTask task, Map<String, Integer> workerResiMemory,
                                            boolean needRebalance) {
        int memory = DbTaskMetaManager.getTaskMemory(task.getId());

        // 正常运行的任务，优先考虑亲和性，防止频繁调度
        if (!needRebalance) {
            String originalWorker = task.getWorker();
            if (StringUtils.isNotBlank(originalWorker) && workerResiMemory.containsKey(originalWorker)) {
                int resiMemory = workerResiMemory.get(originalWorker);
                if (memory <= resiMemory) {
                    if (log.isDebugEnabled()) {
                        log.debug("distributeTasks, task: {} no need to distribute, worker: {}", task.getId(),
                            originalWorker);
                    }
                    workerResiMemory.put(originalWorker, resiMemory - memory);
                    return true;
                }
                log.info("distributeTasks, task: {} need to re distribute due to insufficient memory", task.getId());
            }
        }

        log.info("distributeTasks, task: {} need to distribute", task.getId());

        // 运行但非正常的任务 & 资源不够的正常运行任务 & ready状态的任务 & rebalance场景
        List<Map.Entry<String, Integer>> sortedWorkResiMemory = new ArrayList<>(workerResiMemory.entrySet());
        sortedWorkResiMemory.sort(Comparator.comparingInt(Map.Entry::getValue));
        Collections.reverse(sortedWorkResiMemory);
        Map.Entry<String, Integer> maxResiMemoryWorker = sortedWorkResiMemory.get(0);

        String workerName = maxResiMemoryWorker.getKey();
        int maxResiMemory = maxResiMemoryWorker.getValue();
        if (memory > maxResiMemory) {
            DbTaskMetaManager.updateTaskWorker(task.getId(), "");
            // 针对特定类型的任务，资源不足需要报警
            log.warn("distributeTasks, task: {} need to re distribute due to insufficient memory,"
                + " need {}, max resi {}", task.getId(), memory, maxResiMemory);
            if (ServiceType.alarmWhenNoResource(ServiceType.valueOf(task.getType()))) {
                MonitorManager.getInstance().triggerAlarm(MonitorType.RPL_RESOURCE_NOT_ENOUGH_ERROR, task.getId());
            }
            return false;
        }
        log.info("distributeTasks, task: {}, to worker: {}", task.getId(), workerName);
        workerResiMemory.put(workerName, maxResiMemory - memory);
        DbTaskMetaManager.updateTaskWorker(task.getId(), workerName);
        return true;
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
