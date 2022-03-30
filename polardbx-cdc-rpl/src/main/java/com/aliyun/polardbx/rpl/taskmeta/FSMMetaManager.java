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

package com.aliyun.polardbx.rpl.taskmeta;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.daemon.pipeline.CommandPipeline;
import com.aliyun.polardbx.binlog.daemon.vo.CommandResult;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.rpl.applier.StatisticUnit;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.HostManager;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.common.ResultCode;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.AbstractFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMState;
import com.aliyun.polardbx.rpl.common.fsmutil.ReplicaFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.ServiceDetail;
import com.aliyun.polardbx.rpl.common.fsmutil.TaskDetail;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.INST_IP;

/**
 * @author shicai.xsc 2021/1/8 11:15
 * @since 5.0.0.0
 */
public class FSMMetaManager {

    private final static Logger metaLogger = LogUtil.getMetaLogger();
    private final static CommandPipeline commander = new CommandPipeline();
    private final static ResourceManager resourceManager =
        new ResourceManager(DynamicApplicationConfig.getString(CLUSTER_ID));
    private final static String GREP_RPL_TASK_COUNT_COMMAND = "ps -ef | grep 'RplTaskEngine' | grep -v grep | wc -l";
    private final static String GREP_RPL_TASK_COMMAND = "ps -ef | grep 'RplTaskEngine' | grep -v grep";

    public static ResultCode startStateMachine(long FSMId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(FSMId);
        if (stateMachine == null) {
            metaLogger.error("from id: {} , fsm not found", FSMId);
            return new ResultCode(RplConstants.FAILURE_CODE, "fsm not found", RplConstants.FAILURE);
        } else {
            metaLogger.info("startStateMachine, stateMachineId: {}, state: {}",
                FSMId, stateMachine.getState());
            DbTaskMetaManager.updateStateMachineStatus(FSMId, StateMachineStatus.RUNNING);
            return startServiceByState(FSMId, FSMState.from(stateMachine.getState()));
        }
    }

    public static ResultCode stopStateMachine(long stateMachineId) {
        metaLogger.info("stopStateMachine, stateMachineId: {}", stateMachineId);
        DbTaskMetaManager.updateStateMachineStatus(stateMachineId, StateMachineStatus.STOPPED);
        List<RplService> services = DbTaskMetaManager.listService(stateMachineId);
        if (services.isEmpty()) {
            return new ResultCode(RplConstants.FAILURE_CODE, "no service in this fsm", RplConstants.FAILURE);
        }
        for (RplService service : services) {
            stopService(service);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode startServiceByState(long FSMId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(FSMId);
        if (stateMachine != null && stateMachine.getStatus() == StateMachineStatus.RUNNING.getValue()) {
            return startServiceByState(FSMId, FSMState.from(stateMachine.getState()));
        } else {
            return new ResultCode(RplConstants.FAILURE_CODE, "fsm not found or fsm not set to running",
                RplConstants.FAILURE);
        }
    }

    public static ResultCode startServiceByState(long FSMId, FSMState state) {
        List<RplService> services = DbTaskMetaManager.listService(FSMId);
        for (RplService service : services) {
            if (FSMState.contain(service.getStateList(), state)) {
                startService(service);
            } else {
                stopService(service);
            }
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static RplStateMachine initStateMachine(StateMachineType type, ReplicateMeta meta,
                                                   AbstractFSM fsm) throws Exception {
        if (type.getValue() != StateMachineType.REPLICA.getValue()) {
            metaLogger.error("initStateMachine failed because of unmatched type and meta");
            return null;
        }
        try {
            String config = JSON.toJSONString(meta);
            metaLogger.info("initStateMachine, config: {}", config);

            // create stateMachine
            RplStateMachine stateMachine = DbTaskMetaManager.createStateMachine(config, StateMachineType.REPLICA,
                fsm, meta.channel, DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID), null);
            metaLogger.info("initStateMachine, created stateMachine, {}", stateMachine.getId());

            // rpl service
            RplService rplService = DbTaskMetaManager
                .addService(stateMachine.getId(), ServiceType.REPLICA,
                    Arrays.asList(FSMState.REPLICA));
            createReplicaTasks(rplService, meta);
            metaLogger.info("initStateMachine, created rplService: {}", rplService.getId());
            // should not start automatically for replica task
            return stateMachine;
        } catch (Throwable e) {
            metaLogger.error("initStateMachine failed", e);
            throw e;
        }
    }


    public static long computeTaskDelay(RplTask task) {
        String position = task.getPosition();
        BinlogPosition binlogPosition = BinlogPosition.parseFromString(position);
        if (binlogPosition == null || CommonUtil.isMeaninglessBinlogFileName(binlogPosition)) {
            return -1;
        }
        // how to compute delay
        long positionTimeStamp = binlogPosition.getTimestamp();
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - positionTimeStamp;
    }

    public static boolean isServiceAndTaskFinish(long FSMId, ServiceType type) {
        RplService service = DbTaskMetaManager.getService(FSMId, type);
        if (service == null) {
            return false;
        }
        if (service.getStatus() == ServiceStatus.FINISHED.getValue()) {
            return true;
        }
        // need to check service finish or not
        RplService updatedService = checkAndUpdateServiceFinish(service);
        if (updatedService.getStatus() != ServiceStatus.FINISHED.getValue()) {
            metaLogger.info("service still not finished, stateMachine: {}, service: {}",
                FSMId,
                updatedService.getId());
            return false;
        }
        return true;
    }

    public static ResultCode startService(RplService service) {
        metaLogger.info("startService, {}", service.getId());
        if (service.getStatus() != ServiceStatus.FINISHED.getValue()) {
            return updateService(service, ServiceStatus.RUNNING, TaskStatus.RUNNING);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode stopService(RplService service) {
        metaLogger.info("stopService, {}", service.getId());
        if (service.getStatus() != ServiceStatus.FINISHED.getValue()) {
            return updateService(service, ServiceStatus.STOPPED, TaskStatus.STOPPED);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    private static void createReplicaTasks(RplService rplService, ReplicateMeta meta) {
        metaLogger.info("createTasks start, service: {}", rplService.getId());
        try {
            RplTask rplTask = DbTaskMetaManager.addTask(rplService.getStateMachineId(),
                rplService.getId(),
                null,
                null,
                null,
                ServiceType.from(rplService.getServiceType()),
                0,
                DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));
            updateReplicaTasksConfig(rplService, meta, true);
            metaLogger.info("createTasks end, service: {}, task: {}", rplService.getId(), rplTask.getId());
        } catch (Throwable e) {
            metaLogger.error("createTasks failed, service: {}", rplService.getId(), e);
            throw e;
        }
    }

    public static void updateReplicaConfig(RplStateMachine stateMachine, ReplicateMeta replicateMeta,
                                           boolean useMetaPosition) {
        metaLogger.info("update rpl config start, statemachine: {}", stateMachine.getId());
        String config = JSON.toJSONString(replicateMeta);
        RplStateMachine newStateMachine = new RplStateMachine();
        newStateMachine.setId(stateMachine.getId());
        newStateMachine.setConfig(config);
        DbTaskMetaManager.updateStateMachine(newStateMachine);
        List<RplService> services = DbTaskMetaManager.listService(stateMachine.getId());
        for (RplService service: services) {
            FSMMetaManager.updateReplicaTasksConfig(service, replicateMeta, useMetaPosition);
        }
    }

    private static void updateReplicaTasksConfig(RplService rplService, ReplicateMeta meta, boolean useMetaPosition) {
        String extractorConfigStr = "";
        String pipelineConfigStr = "";
        String applierConfigStr = "";
        metaLogger.info("update rpl tasks start, service: {}", rplService.getId());

        ExtractorConfig extractorConfig = new ExtractorConfig();
        extractorConfig.setExtractorType(ExtractorType.RPL_INC.getValue());
        extractorConfig.setFilterType(FilterType.RPL_FILTER.getValue());
        extractorConfig.setSourceToTargetConfig(JSON.toJSONString(meta));
        extractorConfig.setHostInfo(getRplExtractorHostInfo(meta));
        extractorConfig.setEnableHeartbeat(true);
        extractorConfigStr = JSON.toJSONString(extractorConfig);

        PipelineConfig pipelineConfig = new PipelineConfig();
        pipelineConfigStr = JSON.toJSONString(pipelineConfig);

        ApplierConfig applierConfig = new ApplierConfig();
        applierConfig.setHostInfo(getRplApplierHostInfo());
        applierConfig.setLogCommitLevel(RplConstants.LOG_ALL_COMMIT);
        applierConfigStr = JSON.toJSONString(applierConfig);

        List<RplTask> tasks = DbTaskMetaManager.listTask(rplService.getId());
        for (RplTask task: tasks) {
            DbTaskMetaManager.updateTaskConfig(task.getId(), extractorConfigStr, pipelineConfigStr, applierConfigStr);
            if (useMetaPosition) {
                DbTaskMetaManager.updateBinlogPosition(task.getId(), meta.position);
            }
        }
    }

    public static void clearReplicaHistory(RplStateMachine stateMachine, boolean clearPosOnly) {
        metaLogger.info("clear rpl history start, statemachine: {}", stateMachine.getId());
        if (!clearPosOnly) {
            RplStateMachine newStateMachine = new RplStateMachine();
            newStateMachine.setId(stateMachine.getId());
            newStateMachine.setContext("");
            DbTaskMetaManager.updateStateMachine(newStateMachine);
        }
        DbTaskMetaManager.deleteTablePositionByFsm(stateMachine.getId());
        DbTaskMetaManager.deleteDdlByFSM(stateMachine.getId());
        List<RplService> services = DbTaskMetaManager.listService(stateMachine.getId());
        for (RplService service: services) {
            FSMMetaManager.clearReplicaTasksHistory(service, clearPosOnly);
        }
    }

    private static void clearReplicaTasksHistory(RplService rplService, boolean clearPosOnly) {
        metaLogger.info("clear history of rpl tasks start, service: {}", rplService.getId());
        List<RplTask> tasks = DbTaskMetaManager.listTask(rplService.getId());
        for (RplTask task: tasks) {
            metaLogger.info("clear history of rpl task start, task: {}", task.getId());
            if (!clearPosOnly) {
                DbTaskMetaManager.clearHistory(task.getId());
            }
            DbTaskMetaManager.updateBinlogPosition(task.getId(), CommonUtil.getRplInitialPosition());
        }
    }

    private static ResultCode updateService(RplService service, ServiceStatus serviceStatus, TaskStatus taskStatus) {
        if (service.getStatus() != ServiceStatus.FINISHED.getValue()) {
            DbTaskMetaManager.updateService(service.getId(), serviceStatus);
        }
        List<RplTask> tasks = DbTaskMetaManager.listTask(service.getId());
        if (tasks.isEmpty()) {
            return new ResultCode(RplConstants.FAILURE_CODE, "no task in this taskgroup", RplConstants.FAILURE);
        }
        for (RplTask task : tasks) {
            if (task.getStatus() != TaskStatus.FINISHED.getValue()) {
                DbTaskMetaManager.updateTaskStatus(task.getId(), taskStatus);
            }
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static void setTaskFinish(long taskId) {
        DbTaskMetaManager.updateTaskStatus(taskId, TaskStatus.FINISHED);
    }

    private static RplService checkAndUpdateServiceFinish(RplService service) {
        metaLogger.info("checkService start, service: {}", service.getId());

        try {
            boolean allFinished = true;
            List<RplTask> tasks = DbTaskMetaManager.listTask(service.getId());
            for (RplTask task : tasks) {
                TaskStatus status = TaskStatus.from(task.getStatus());
                if (status != TaskStatus.FINISHED) {
                    metaLogger.info("checkService, service: {} NOT allFinished, running task: {}",
                        service.getId(),
                        task.getId());
                    allFinished = false;
                    break;
                }
            }

            if (allFinished) {
                metaLogger.info("checkService, {} allFinished", service.getId());
                RplService newService = new RplService();
                newService.setId(service.getId());
                newService.setStatus(ServiceStatus.FINISHED.getValue());
                return DbTaskMetaManager.updateService(newService);
            }
        } catch (Throwable e) {
            metaLogger.error("checkService failed", e);
        }

        metaLogger.info("checkService end, service: {}", service.getId());
        return service;
    }

    private static boolean isTaskAlive(RplTask task) {
        return task.getGmtModified()
            .after(new Date(System.currentTimeMillis() - RplConstants.TASK_KEEP_ALIVE_INTERVAL_SECONDS * 1000));
    }

    private static HostInfo getRplApplierHostInfo() {
        HostInfo info = HostManager.getDstPolarxHost();
        info.setUsePolarxPoolCN(true);
        return info;
    }

    private static HostInfo getRplExtractorHostInfo(ReplicateMeta replicateMeta) {
        return new HostInfo(replicateMeta.getMasterHost(),
            replicateMeta.getMasterPort(),
            replicateMeta.getMasterUser(),
            replicateMeta.getMasterPassword(),
            "",
            replicateMeta.getMasterType(), RplConstants.SERVER_ID_NULL);
    }

    //////////////////////////// For Daemon //////////////////////////////

    /**
     * For Leader
     */
    public static int distributeTasks() {
        try {
            List<Container> workers = resourceManager.availableContainers();
            if (workers.size() == 0) {
                metaLogger.error("distributeTasks, no running workers");
                return 0;
            }

            Map<String, Integer> workerLoads = new HashMap<>();
            for (Container worker : workers) {
                workerLoads.put(worker.getNodeHttpAddress(), 0);
            }
            String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            List<RplTask> tasks = DbTaskMetaManager.listClusterTask(TaskStatus.RUNNING, clusterId);
            List<RplTask> toDistributeTasks = new ArrayList<>();

            for (RplTask task : tasks) {
                if (StringUtils.isBlank(task.getWorker()) || !isTaskRunning(task)
                    || !workerLoads.containsKey(task.getWorker())) {
                    toDistributeTasks.add(task);
                    metaLogger.info("distributeTasks, need distribute task: {}", task.getId());
                } else {
                    metaLogger.info("distributeTasks, task: {} no need to distribute, worker: {}, gmtModified: {}",
                        task.getId(),
                        task.getWorker(),
                        task.getGmtModified());
                    workerLoads.put(task.getWorker(), workerLoads.get(task.getWorker()) + 1);
                }
            }

            metaLogger.info("distributeTasks, workerLoads before: {}", JSON.toJSONString(workerLoads));
            List<Map.Entry<String, Integer>> sortedWorkLoad = new ArrayList<>(workerLoads.entrySet());
            Collections.sort(sortedWorkLoad, Comparator.comparingInt(Map.Entry::getValue));

            for (RplTask task : toDistributeTasks) {
                Map.Entry<String, Integer> entry = sortedWorkLoad.get(0);
                DbTaskMetaManager.updateTaskWorker(task.getId(), entry.getKey());
                metaLogger.info("distributeTasks, task: {}, to worker: {}", task.getId(), entry.getKey());
                entry.setValue(entry.getValue() + 1);
                Collections.sort(sortedWorkLoad, Comparator.comparingInt(Map.Entry::getValue));
            }
            for (Map.Entry<String, Integer> entry : sortedWorkLoad) {
                workerLoads.put(entry.getKey(), entry.getValue());
            }

            metaLogger.info("distributeTasks end, workerLoads after: {}", JSON.toJSONString(workerLoads));
            return toDistributeTasks.size();
        } catch (Throwable e) {
            metaLogger.error("distributeTasks failed", e);
            return 0;
        }
    }

    /**
     * For Worker
     */
    public static void checkAndRunLocalTasks(String clusterId) throws Exception {
        metaLogger.info("checkAndRunLocalTasks start");
        List<RplTask> localNeedRestartTasks = DbTaskMetaManager.listTask(DynamicApplicationConfig.getString(INST_IP),
            TaskStatus.RESTART, clusterId);
        for (RplTask task : localNeedRestartTasks) {
            commander.stopRplTask(task.getId());
            DbTaskMetaManager.updateTaskStatus(task.getId(), TaskStatus.RUNNING);
        }
        List<RplTask> localNeedRunTasks =
            DbTaskMetaManager.listTask(DynamicApplicationConfig.getString(INST_IP),
                TaskStatus.RUNNING,clusterId);
        Set<Long> needRunTaskIds = localNeedRunTasks.stream().map(RplTask::getId).collect(Collectors.toSet());
        Set<Long> runningTaskIds = Sets.newHashSet(listLocalRunningTaskId());
        stopNoLocalTasks(runningTaskIds, needRunTaskIds);
        startLocalTasks(runningTaskIds, localNeedRunTasks);
        metaLogger.info("checkAndRunLocalTasks end");
    }

    /**
     * Check if task RUNNING on current worker
     */
    public static boolean isTaskLocalRunning(long taskId) throws Exception {
        List<Long> runningTaskIds = listLocalRunningTaskId();
        return runningTaskIds.contains(taskId);
    }

    /**
     * Check if task is RUNNING on any worker
     */
    private static boolean isTaskRunning(RplTask task) {
        return task.getGmtModified()
            .after(new Date(System.currentTimeMillis() - RplConstants.TASK_KEEP_ALIVE_INTERVAL_SECONDS * 1000));
    }

    public static List<Long> listLocalRunningTaskId() throws Exception {
        List<Long> runningTaskIds = new ArrayList<>();

        CommandResult countResult = commander.execCommand(new String[] {"bash", "-c", GREP_RPL_TASK_COUNT_COMMAND},
            3000);
        if (countResult.getCode() != 0) {
            metaLogger.warn("check local running RplTaskEngine fail, result code: {}, msg: {}",
                countResult.getCode(),
                countResult.getMsg());
            return runningTaskIds;
        }

        String countStr = StringUtils.split(countResult.getMsg(), System.getProperty("line.separator"))[0];
        if (Integer.valueOf(StringUtils.trim(countStr)) <= 0) {
            metaLogger.warn("no local running RplTaskEngine");
            return runningTaskIds;
        }

        CommandResult result = commander.execCommand(new String[] {"bash", "-c", GREP_RPL_TASK_COMMAND}, 3000);
        if (result.getCode() == 0) {
            String[] runningTaskInfos = StringUtils.split(result.getMsg(), System.getProperty("line.separator"));
            for (String taskInfo : runningTaskInfos) {
                String[] tokens = StringUtils.splitByWholeSeparator(taskInfo, "RplTaskEngine");
                Map<String, String> args = CommonUtil.handleArgs(StringUtils.trim(tokens[tokens.length - 1]));
                String taskId = args.get(RplConstants.TASK_ID);
                runningTaskIds.add(Long.valueOf(taskId));
            }
        } else {
            metaLogger.warn("check local running RplTaskEngine fail, result code: {}, msg: {}",
                result.getCode(),
                result.getMsg());
        }

        return runningTaskIds;
    }

    public static void stopNoLocalTasks(Set<Long> runningTaskIds, Set<Long> needRunTaskIds) throws Exception {
        if (runningTaskIds == null || runningTaskIds.size() == 0) {
            metaLogger.info("startLocalTasks, no tasks to stop");
            return;
        }

        for (Long runningTaskId : runningTaskIds) {
            if (!needRunTaskIds.contains(runningTaskId)) {
                commander.stopRplTask(runningTaskId);
                metaLogger.warn("stop local running RplTaskEngine {} not in {}", runningTaskId, needRunTaskIds);
            }
        }
    }

    public static void startLocalTasks(Set<Long> runningTaskIds, List<RplTask> needRunTasks) throws Exception {
        if (needRunTasks == null || needRunTasks.size() == 0) {
            metaLogger.info("startLocalTasks, no tasks to start");
            return;
        }

        for (RplTask needRunTask : needRunTasks) {
            if (!runningTaskIds.contains(needRunTask.getId())) {
                String taskName = CommonUtil.buildRplTaskName(needRunTask);
                commander.startRplTask(needRunTask.getId(), taskName);
                metaLogger.warn("start local running RplTaskEngine {} {}", needRunTask.getId(), taskName);
            }
        }
    }
}
