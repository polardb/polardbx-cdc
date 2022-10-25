/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.daemon.pipeline.CommandPipeline;
import com.aliyun.polardbx.binlog.daemon.vo.CommandResult;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.RplDbFullPosition;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.binlog.io.OSSFileSystem;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.rpl.applier.StatisticUnit;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.HostManager;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.common.ResultCode;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportTaskDetailInfo;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMState;
import com.aliyun.polardbx.rpl.common.fsmutil.RecoveryFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.ReplicaFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.ServiceDetail;
import com.aliyun.polardbx.rpl.common.fsmutil.TaskDetail;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;
import org.slf4j.Logger;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    /* /v1/import/service/create */
    public static ResultCode createFSM(StateMachineType type, Object meta) {
        long FSMId = RplConstants.ERROR_FSMID;
        switch (StateMachineType.from(type.getValue())) {
        case DATA_IMPORT:
            FSMId = DataImportFSM.getInstance().create((DataImportMeta) meta);
            break;
        case REPLICA:
            FSMId = ReplicaFSM.getInstance().create((ReplicateMeta) meta);
            break;
        default:
            break;
        }
        if (FSMId == RplConstants.ERROR_FSMID) {
            return new ResultCode(RplConstants.FAILURE_CODE, "create failed", RplConstants.FAILURE);
        } else {
            ResultCode returnResult = new ResultCode(RplConstants.SUCCESS_CODE, "create success");
            returnResult.setData(FSMId);
            return returnResult;
        }
    }

    /* /v1/import/service/start */
    public static ResultCode startStateMachine(long FSMId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(FSMId);
        if (stateMachine == null) {
            metaLogger.error("from id: {} , fsm not found", FSMId);
            return new ResultCode(RplConstants.FAILURE_CODE, "fsm not found", RplConstants.FAILURE);
        }
        if (stateMachine.getState() == FSMState.FINISHED.getValue()) {
            metaLogger.info("checkStateMachines fsmId: {} finished", FSMId);
            return new ResultCode(RplConstants.FAILURE_CODE, "stateMachine state is finished", RplConstants.FAILURE);
        } else {
            metaLogger.info("startStateMachine, fsmId: {}, state: {}",
                FSMId, stateMachine.getState());
            DbTaskMetaManager.updateStateMachineStatus(FSMId, StateMachineStatus.RUNNING);
            return startServiceByState(FSMId, FSMState.from(stateMachine.getState()));
        }
    }

    /* /v1/import/service/stop */
    public static ResultCode stopStateMachine(long fsmId) {
        metaLogger.info("stopStateMachine, fsmId: {}", fsmId);
        DbTaskMetaManager.updateStateMachineStatus(fsmId, StateMachineStatus.STOPPED);
        List<RplService> services = DbTaskMetaManager.listService(fsmId);
        if (services.isEmpty()) {
            return new ResultCode(RplConstants.FAILURE_CODE, "no service in this fsm", RplConstants.FAILURE);
        }
        for (RplService service : services) {
            stopService(service);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/service/restart/{serverId} */
    public static ResultCode restartStateMachine(long fsmId) {
        metaLogger.info("restartStateMachine, fsmId: {}", fsmId);
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        List<RplService> services = DbTaskMetaManager.listService(fsmId);
        if (stateMachine == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "this fsmid has no fsm", RplConstants.FAILURE);
        }
        if (services.isEmpty()) {
            return new ResultCode(RplConstants.FAILURE_CODE, "no service in this fsm", RplConstants.FAILURE);
        }
        DbTaskMetaManager.updateStateMachineStatus(fsmId, StateMachineStatus.RUNNING);
        for (RplService service : services) {
            if (FSMState.contain(service.getStateList(), FSMState.from(stateMachine.getState()))) {
                updateServiceStatus(service, ServiceStatus.RUNNING, TaskStatus.RESTART);
            }
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/service/delete */
    public static ResultCode deleteStateMachine(long FSMId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(FSMId);
        if (stateMachine == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "fsm not found", RplConstants.FAILURE);
        }
        // you need first stop then delete
        if (stateMachine.getStatus() == StateMachineStatus.RUNNING.getValue()) {
            return new ResultCode(RplConstants.FAILURE_CODE, "fsm state not support delete action",
                RplConstants.FAILURE);
        }
        DbTaskMetaManager.deleteStateMachine(FSMId);
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/service/detail */
    public static ResultCode<DataImportTaskDetailInfo> getStateMachineDetail(long FSMId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(FSMId);
        if (stateMachine == null) {
            metaLogger.error("from id: {} , fsm not found", FSMId);
            return new ResultCode(RplConstants.FAILURE_CODE, "fsm not found", RplConstants.FAILURE);
        }
        ResultCode<DataImportTaskDetailInfo> returnResult = new ResultCode(RplConstants.SUCCESS_CODE, "success");
        DataImportTaskDetailInfo info = new DataImportTaskDetailInfo();
        info.setFsmId(stateMachine.getId());
        info.setFsmState(FSMState.from(stateMachine.getState()).name());
        info.setFsmStatus(StateMachineStatus.from(stateMachine.getStatus()).name());
        info.setServiceDetailList(new ArrayList<>());
        List<RplService> services = DbTaskMetaManager.listService(FSMId);
        for (RplService service : services) {
            ServiceDetail serviceDetail = new ServiceDetail();
            serviceDetail.setId(service.getId());
            serviceDetail.setStatus(ServiceStatus.nameFrom(service.getStatus()));
            serviceDetail.setType(ServiceType.from(service.getServiceType()).name());
            serviceDetail.setTaskDetailList(new ArrayList<>());
            List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
            for (RplTask task : tasks) {
                serviceDetail.getTaskDetailList().add(getTaskSpecificDetail(task));
            }
            info.getServiceDetailList().add(serviceDetail);
        }
        returnResult.setData(info);
        return returnResult;
    }

    /* /v1/import/task/start */
    public static ResultCode startTask(long taskId) {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        if (task == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "taskgroup not found", RplConstants.FAILURE);
        }
        if (task.getStatus() != TaskStatus.FINISHED.getValue()) {
            DbTaskMetaManager.updateTaskStatus(task.getId(), TaskStatus.READY);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/stop */
    public static ResultCode stopTask(long taskId) {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        if (task == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "taskgroup not found", RplConstants.FAILURE);
        }
        if (task.getStatus() != TaskStatus.FINISHED.getValue()) {
            DbTaskMetaManager.updateTaskStatus(task.getId(), TaskStatus.STOPPED);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /*/v1/import/task/reset/{taskId} 清除位点 */
    public static ResultCode resetTask(long taskId) {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        if (task == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "taskgroup not found", RplConstants.FAILURE);
        }
        RplService service = DbTaskMetaManager.getService(task.getServiceId());
        if (service == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "taskgroup not found", RplConstants.FAILURE);
        }
        DbTaskMetaManager.updateTaskStatus(taskId, TaskStatus.STOPPED);
        DbTaskMetaManager.deleteTablePositionByTask(taskId);
        DbTaskMetaManager.deleteDbFullPositionByTask(taskId);
        DbTaskMetaManager.deleteDdlByTask(taskId);
        // TODO :
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /*/v1/import/task/getconfig/{taskId} */
    public static ResultCode getTaskDetail(long taskId) {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        if (task == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "task not found", RplConstants.FAILURE);
        }
        ResultCode returnResult = new ResultCode(RplConstants.SUCCESS_CODE, "success");
        returnResult.setData(getTaskSpecificDetail(task));
        return returnResult;
    }

    /* /v1/import/task/exception/skip/start/{taskId} */
    public static ResultCode startSkipException(long taskId) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
        pipelineConfig.setSkipException(true);
        String newConfigStr = JSON.toJSONString(pipelineConfig);
        DbTaskMetaManager.updateTaskConfig(taskId, null, newConfigStr, null);
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/exception/skip/stop/{taskId} */
    public static ResultCode stopSkipException(long taskId) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
        pipelineConfig.setSkipException(false);
        String newConfigStr = JSON.toJSONString(pipelineConfig);
        DbTaskMetaManager.updateTaskConfig(taskId, null, newConfigStr, null);
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/safemode/start/{taskId} */
    public static ResultCode startSafeMode(long taskId) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
        pipelineConfig.setSafeMode(true);
        String newConfigStr = JSON.toJSONString(pipelineConfig);
        DbTaskMetaManager.updateTaskConfig(taskId, null, newConfigStr, null);
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/safemode/stop/{taskId} */
    public static ResultCode stopSafeMode(long taskId) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
        pipelineConfig.setSafeMode(false);
        String newConfigStr = JSON.toJSONString(pipelineConfig);
        DbTaskMetaManager.updateTaskConfig(taskId, null, newConfigStr, null);
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }


    /* /v1/import/task/exception/skip/start/{taskId} */
    public static ResultCode startIncValidation(long fsmId) {
        RplService service = DbTaskMetaManager.getService(fsmId, ServiceType.INC_COPY);
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
        for (RplTask task : tasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
            pipelineConfig.setUseIncValidation(true);
            String newConfigStr = JSON.toJSONString(pipelineConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), null, newConfigStr, null);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/exception/skip/stop/{taskId} */
    public static ResultCode stopIncValidation(long fsmId) {
        RplService service = DbTaskMetaManager.getService(fsmId, ServiceType.INC_COPY);
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
        for (RplTask task : tasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
            pipelineConfig.setUseIncValidation(false);
            String newConfigStr = JSON.toJSONString(pipelineConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), null, newConfigStr, null);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode setFlowControl(long taskId, int fixedRps) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
        pipelineConfig.setFixedTpsLimit(fixedRps);
        String newConfigStr = JSON.toJSONString(pipelineConfig);
        DbTaskMetaManager.updateTaskConfig(taskId, null, newConfigStr, null);
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode setConvertToByte(long fsmId, boolean convertToByte) {
        RplService fullValidationService = DbTaskMetaManager.getService(fsmId, ServiceType.FULL_VALIDATION);
        List<RplTask> fullValidationTasks = DbTaskMetaManager.listTaskByService(fullValidationService.getId());
        for (RplTask task: fullValidationTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            ValidationExtractorConfig extractorConfig = JSON.parseObject(config.getExtractorConfig(),
                ValidationExtractorConfig.class);
            extractorConfig.setConvertToByte(convertToByte);
            String newConfigStr = JSON.toJSONString(extractorConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), newConfigStr, null, null);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode setParallelCount(long fsmId, int parallelCount) {
        RplService fullCopyService = DbTaskMetaManager.getService(fsmId, ServiceType.FULL_COPY);
        List<RplTask> fullCopyTasks = DbTaskMetaManager.listTaskByService(fullCopyService.getId());
        for (RplTask task: fullCopyTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            FullExtractorConfig extractorConfig = JSON.parseObject(config.getExtractorConfig(),
                FullExtractorConfig.class);
            extractorConfig.setParallelCount(parallelCount);
            String newConfigStr = JSON.toJSONString(extractorConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), newConfigStr, null, null);
        }
        RplService fullValidationService = DbTaskMetaManager.getService(fsmId, ServiceType.FULL_VALIDATION);
        List<RplTask> fullValidationTasks = DbTaskMetaManager.listTaskByService(fullValidationService.getId());
        for (RplTask task: fullValidationTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            ValidationExtractorConfig extractorConfig = JSON.parseObject(config.getExtractorConfig(),
                ValidationExtractorConfig.class);
            extractorConfig.setParallelCount(parallelCount);
            String newConfigStr = JSON.toJSONString(extractorConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), newConfigStr, null, null);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }


    public static ResultCode setEventBufferSize(long fsmId, int bufferSize) {
        RplService incService = DbTaskMetaManager.getService(fsmId, ServiceType.INC_COPY);
        List<RplTask> incTasks = DbTaskMetaManager.listTaskByService(incService.getId());
        for (RplTask task: incTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            RdsExtractorConfig extractorConfig = JSON.parseObject(config.getExtractorConfig(),
                RdsExtractorConfig.class);
            PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
            extractorConfig.setEventBufferSize(bufferSize);
            pipelineConfig.setBufferSize(bufferSize);
            String newConfigStr1 = JSON.toJSONString(extractorConfig);
            String newConfigStr2 = JSON.toJSONString(pipelineConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), newConfigStr1, newConfigStr2, null);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode setMemory(long taskId, int memoryInMb) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        DbTaskMetaManager.updateTaskMemory(taskId, memoryInMb);
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static TaskDetail getTaskSpecificDetail(RplTask task) {
        TaskDetail taskDetail = new TaskDetail();
        taskDetail.setTaskId(task.getId());
        taskDetail.setStatus(TaskStatus.from(task.getStatus()).name());
        taskDetail.setType(ServiceType.from(task.getType()).name());
        taskDetail.setStatistics(task.getStatistic());
        taskDetail.setLastError(task.getLastError());
        String stat = task.getStatistic();
        StatisticUnit unit = JSON.parseObject(stat, StatisticUnit.class);
        if (unit != null && unit.getMessageTps() != null && !unit.getMessageTps().isEmpty()) {
            taskDetail.setRps(unit.getMessageTps().get(StatisticalProxy.INTERVAL_1M));
        }
        switch (ServiceType.from(task.getType())) {
        case FULL_COPY:
            taskDetail.setProgress(computeFullCopyPercentage(task));
            break;
        case INC_COPY:
            taskDetail.setDelay(computeTaskDelay(task));
            break;
        case CDC_INC:
            taskDetail.setDelay(computeTaskDelay(task));
            break;
        case FULL_VALIDATION:
            taskDetail.setProgress(computeValProgress(task));
        default:
            break;
        }
        return taskDetail;
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

    public static RplStateMachine initStateMachine(StateMachineType type, DataImportMeta meta,
                                                   DataImportFSM fsm) throws Throwable {
        if (type.getValue() != StateMachineType.DATA_IMPORT.getValue()) {
            metaLogger.error("initStateMachine failed because of unmatched type and meta");
            return null;
        }

        try {
            String config = JSON.toJSONString(meta);
            metaLogger.info("initStateMachine, config: {}", config);

            // create stateMachine
            RplStateMachine stateMachine = DbTaskMetaManager
                .createStateMachine(config, type, fsm, null, meta.getCdcClusterId(), null);
            metaLogger.info("initStateMachine, created stateMachine, {}", stateMachine.getId());
            metaLogger.info("initStateMachine, stateMachine type: {}", type.name());

            // full
            RplService fullService = DbTaskMetaManager
                .addService(stateMachine.getId(), ServiceType.FULL_COPY,
                    Arrays.asList(FSMState.FULL_COPY));
            createImportTasks(fullService, meta);
            metaLogger.info("initStateMachine, created fullService: {}", fullService.getId());

            // inc copy and check
            RplService incService = DbTaskMetaManager
                .addService(stateMachine.getId(), ServiceType.INC_COPY,
                    Arrays.asList(FSMState.INC_COPY,
                        FSMState.CATCH_UP_VALIDATION,
                        FSMState.RECONCILIATION,
                        FSMState.RECON_FINISHED_WAIT_CATCH_UP,
                        FSMState.RECON_FINISHED_CATCH_UP,
                        FSMState.BACK_FLOW));
            createImportTasks(incService, meta);
            metaLogger.info("initStateMachine, created incService: {}", incService.getId());

            // full validation
            RplService checkService = DbTaskMetaManager
                .addService(stateMachine.getId(), ServiceType.FULL_VALIDATION,
                    Arrays.asList(FSMState.CATCH_UP_VALIDATION));
            createImportTasks(checkService, meta);
            metaLogger.info("initStateMachine, created checkService: {}", checkService.getId());

            // reconciliation
            RplService reconService = DbTaskMetaManager
                .addService(stateMachine.getId(), ServiceType.RECONCILIATION,
                    Arrays.asList(FSMState.RECONCILIATION));
            createImportTasks(reconService, meta);
            metaLogger.info("initStateMachine, created reconService: {}", reconService.getId());

            // full validation crosscheck
            RplService valCrossCheckService = DbTaskMetaManager
                .addService(stateMachine.getId(), ServiceType.FULL_VALIDATION_CROSSCHECK,
                    new ArrayList<>());
            createImportTasks(valCrossCheckService, meta);
            metaLogger.info("initStateMachine, created checkService: {}", valCrossCheckService.getId());

            // reconciliation crosscheck
            RplService reconCrossCheckService = DbTaskMetaManager
                .addService(stateMachine.getId(), ServiceType.RECONCILIATION_CROSSCHECK,
                    new ArrayList<>());
            createImportTasks(reconCrossCheckService, meta);
            metaLogger.info("initStateMachine, created reconService: {}", reconCrossCheckService.getId());

            // 回流
            RplService backflowService = DbTaskMetaManager
                .addService(stateMachine.getId(), ServiceType.CDC_INC,
                    Arrays.asList(FSMState.BACK_FLOW));
            createImportTasks(backflowService, meta);

            startStateMachine(stateMachine.getId());
            return stateMachine;
        } catch (Throwable e) {
            metaLogger.error("initStateMachine failed", e);
            throw e;
        }
    }

    public static RplStateMachine initStateMachine(StateMachineType type, ReplicateMeta meta, ReplicaFSM fsm) {
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

    public static RplStateMachine initStateMachine(StateMachineType type, RecoveryMeta meta, RecoveryFSM fsm) {
        if (type.getValue() != StateMachineType.RECOVERY.getValue()) {
            metaLogger.error("initStateMachine failed because of unmatched type and meta");
            return null;
        }
        try {
            String config = JSON.toJSONString(meta);
            metaLogger.info("initStateMachine, config: {}", config);
            String randomUUID = UUID.randomUUID().toString();

            // create stateMachine
            String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            RplStateMachine stateMachine = DbTaskMetaManager.createStateMachine(config,
                StateMachineType.RECOVERY, fsm, null, clusterId, null);

            // flashback task
            RplService searchService = DbTaskMetaManager
                .addService(stateMachine.getId(), ServiceType.REC_SEARCH, Arrays.asList(FSMState.REC_SEARCH));
            createFlashbackTasks(searchService, meta, randomUUID);
            metaLogger.info("initStateMachine, created rplService: {}", searchService.getId());

            // combine result task
            RplService combineService = DbTaskMetaManager
                .addService(stateMachine.getId(), ServiceType.REC_COMBINE, Arrays.asList(FSMState.REC_COMBINE));
            createCombineTask(combineService, meta, randomUUID);
            metaLogger.info("initStateMachine, created rplService: {}", combineService.getId());

            startStateMachine(stateMachine.getId());
            return stateMachine;
        } catch (Throwable e) {
            metaLogger.error("initStateMachine failed", e);
            throw e;
        }
    }

    /**
     * 计算服务延迟 取最大延迟时间
     */
    public static boolean checkIncServiceCatchUp(long FSMId) {
        // boolean allCatchUp = true;
        List<RplService> services = DbTaskMetaManager.listService(FSMId);
        RplService incService = DbTaskMetaManager.getService(FSMId, ServiceType.INC_COPY);
        if (incService == null || incService.getStatus() != ServiceStatus.RUNNING.getValue()) {
            return false;
        }
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(incService.getId());
        for (RplTask task : tasks) {
            long delay = computeTaskDelay(task);
            metaLogger.info("now inc copy delay: {}", delay);
            if (delay < 0 || delay > RplConstants.INC_CATCH_UP_SECOND_THRESHOLD) {
                return false;
            }
        }
        return true;
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
            return updateServiceStatus(service, ServiceStatus.RUNNING, TaskStatus.READY);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode stopService(RplService service) {
        metaLogger.info("stopService, {}", service.getId());
        if (service.getStatus() != ServiceStatus.FINISHED.getValue()) {
            return updateServiceStatus(service, ServiceStatus.STOPPED, TaskStatus.STOPPED);
        }
        return new ResultCode(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    private static void createFlashbackTasks(RplService rplService, RecoveryMeta meta, String randomUUID) {
        String extractorConfigStr = "";
        String pipelineConfigStr = "";
        String applierConfigStr = "";
        metaLogger.info("create SQL flash back tasks start, service: {}", rplService.getId());

        try {
            List<List<String>> lst = new ArrayList<>();
            List<String> binlogFiles =
                OSSFileSystem.getAllLogFileNamesBetweenTimeRange(meta.getStartTimestamp(), meta.getEndTimestamp());
            for (int i = 0; i < binlogFiles.size(); i += meta.getBinlogFilesCountPerTask()) {
                List<String> tempList = new ArrayList<>();
                for (int j = i; j < binlogFiles.size() && j < i + meta.getBinlogFilesCountPerTask(); j++) {
                    tempList.add(binlogFiles.get(j));
                }
                lst.add(tempList);
            }
            metaLogger.info("binlog files to consume: " + lst);

            for (int i = 0; i < lst.size(); i++) {
                RecoveryExtractorConfig extractorConfig = new RecoveryExtractorConfig();
                extractorConfig.setExtractorType(ExtractorType.RECOVERY.getValue());
                extractorConfig.setFilterType(FilterType.NO_FILTER.getValue());
                extractorConfig.setHostInfo(getRecoveryExtractorHostInfo(meta));
                extractorConfig.setBinlogList(lst.get(i));
                extractorConfigStr = JSON.toJSONString(extractorConfig);

                PipelineConfig pipelineConfig = new PipelineConfig();
                pipelineConfigStr = JSON.toJSONString(pipelineConfig);

                RecoveryApplierConfig recoveryApplierConfig = new RecoveryApplierConfig();
                recoveryApplierConfig.setApplierType(ApplierType.RECOVERY.getValue());
                recoveryApplierConfig.setSchema(meta.getSchema());
                recoveryApplierConfig.setMirror(meta.isMirror());
                recoveryApplierConfig.setBeginTime(meta.getStartTimestamp());
                recoveryApplierConfig.setEndTime(meta.getEndTimestamp());
                recoveryApplierConfig.setSqlType(meta.getSqlType());
                recoveryApplierConfig.setTraceId(meta.getTraceId());
                recoveryApplierConfig.setTable(meta.getTable());
                recoveryApplierConfig.setBinlogList(lst.get(i));
                recoveryApplierConfig.setRandomUUID(randomUUID);
                recoveryApplierConfig.setInjectTrouble(meta.isInjectTrouble());
                recoveryApplierConfig.setSequence(i);
                applierConfigStr = JSON.toJSONString(recoveryApplierConfig);

                RplTask rplTask = DbTaskMetaManager.addTask(rplService.getStateMachineId(),
                    rplService.getId(),
                    extractorConfigStr,
                    pipelineConfigStr,
                    applierConfigStr,
                    ServiceType.from(rplService.getServiceType()), i,
                    DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));

                metaLogger
                    .info("create SQL flash back task, service: {}, task: {}", rplService.getId(), rplTask.getId());
            }
            metaLogger.info("SQL flashback tasks are all created");
        } catch (Throwable e) {
            metaLogger.error("createTasks failed, service: {}", rplService.getId(), e);
            throw e;
        }
    }

    private static void createCombineTask(RplService rplService, RecoveryMeta recoveryMeta, String randomUUID) {
        boolean isMirror = recoveryMeta.isMirror();
        int mirror = 0;
        if (isMirror) {
            mirror = 1;
        }

        // 将isMirror记录在CombineTask的extra字段
        // CombineTask据此决定文件合并的方式
        RecoveryCombineConfig combineConfig = new RecoveryCombineConfig();
        combineConfig.setRandomUUID(randomUUID);
        combineConfig.setInjectTrouble(recoveryMeta.isInjectTrouble());
        combineConfig.setMirror(recoveryMeta.isMirror());
        String combineConfigStr = JSON.toJSONString(combineConfig);
        RplTask rplTask = DbTaskMetaManager.addTask(rplService.getStateMachineId(),
            rplService.getId(),
            "", "", combineConfigStr,
            ServiceType.from(rplService.getServiceType()),
            mirror,
            DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));
        metaLogger.info("create combine task end, service: {}, task: {}", rplService.getId(), rplTask.getId());
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

    public static void addTaskConfig(Long fsmId) {
        List<RplTask> tasks = DbTaskMetaManager.listTaskByStateMachine(fsmId);
        for (RplTask task: tasks) {
            RplTaskConfig taskConfig = DbTaskMetaManager.getTaskConfig(task.getId());
            if (taskConfig != null) {
                continue;
            }
            DbTaskMetaManager.addTaskConfig(task.getId(), task.getExtractorConfig(),
                task.getPipelineConfig(), task.getApplierConfig(), RplConstants.DEFAULT_MEMORY_SIZE);
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

        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(rplService.getId());
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
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(rplService.getId());
        for (RplTask task: tasks) {
            metaLogger.info("clear history of rpl task start, task: {}", task.getId());
            if (!clearPosOnly) {
                DbTaskMetaManager.clearHistory(task.getId());
            }
            DbTaskMetaManager.updateBinlogPosition(task.getId(), CommonUtil.getRplInitialPosition());
        }
    }

    private static void createImportTasks(RplService rplService, DataImportMeta meta) throws Throwable {
        String extractorConfigStr = "";
        String pipelineConfigStr = "";
        String applierConfigStr = "";
        metaLogger.info("createTasks start, service: {}", rplService.getId());

        try {
            // extractor config
            for (int i = 0; i < meta.getMetaList().size(); ++i) {
                DataImportMeta.PhysicalMeta physicalMeta = meta.getMetaList().get(i);
                switch (ServiceType.from(rplService.getServiceType())) {
                case FULL_COPY:
                    FullExtractorConfig fullExtractorConfig = new FullExtractorConfig();
                    fullExtractorConfig.setExtractorType(ExtractorType.MYSQL_FULL.getValue());
                    fullExtractorConfig.setFilterType(FilterType.NORMAL_FILTER.getValue());
                    fullExtractorConfig.setParallelCount(meta.getProducerParallelCount());
                    fullExtractorConfig.setFetchBatchSize(meta.getFetchBatchSize());
                    fullExtractorConfig.setSourceToTargetConfig(
                        JSON.toJSONString(physicalMeta));
                    fullExtractorConfig.setHostInfo(getImportExtractorHostInfo(physicalMeta));
                    extractorConfigStr = JSON.toJSONString(fullExtractorConfig);
                    break;
                case INC_COPY:
                    RdsExtractorConfig extractorConfig = new RdsExtractorConfig();
                    extractorConfig.setExtractorType(ExtractorType.MYSQL_INC.getValue());
                    extractorConfig.setFilterType(FilterType.NORMAL_FILTER.getValue());
                    extractorConfig.setSourceToTargetConfig(JSON.toJSONString(physicalMeta));
                    extractorConfig.setHostInfo(getImportExtractorHostInfo(physicalMeta));
                    extractorConfig.setEnableHeartbeat(false);
                    extractorConfig.setUid(physicalMeta.getRdsUid());
                    extractorConfig.setBid(physicalMeta.getRdsBid());
                    extractorConfig.setRdsInstanceId(physicalMeta.getRdsInstanceId());
                    extractorConfigStr = JSON.toJSONString(extractorConfig);
                    break;
                case FULL_VALIDATION:
                    ValidationExtractorConfig validationExtractorConfig = new ValidationExtractorConfig();
                    validationExtractorConfig.setExtractorType(ExtractorType.FULL_VALIDATION.getValue());
                    validationExtractorConfig.setFilterType(FilterType.NORMAL_FILTER.getValue());
                    validationExtractorConfig.setParallelCount(meta.getProducerParallelCount());
                    validationExtractorConfig.setHostInfo(getImportExtractorHostInfo(physicalMeta));
                    validationExtractorConfig.setSourceToTargetConfig(JSON.toJSONString(physicalMeta));
                    extractorConfigStr = JSON.toJSONString(validationExtractorConfig);
                    break;
                case RECONCILIATION:
                    ReconExtractorConfig config = new ReconExtractorConfig();
                    config.setExtractorType(ExtractorType.RECONCILIATION.getValue());
                    config.setFilterType(FilterType.NORMAL_FILTER.getValue());
                    config.setParallelCount(meta.getProducerParallelCount());
                    config.setHostInfo(getImportExtractorHostInfo(physicalMeta));
                    config.setSourceToTargetConfig(JSON.toJSONString(physicalMeta));
                    extractorConfigStr = JSON.toJSONString(config);
                    break;
                case FULL_VALIDATION_CROSSCHECK:
                    ValidationExtractorConfig validationCrossCheckExtractorConfig = new ValidationExtractorConfig();
                    validationCrossCheckExtractorConfig.setExtractorType(ExtractorType.FULL_VALIDATION_CROSSCHECK.getValue());
                    validationCrossCheckExtractorConfig.setFilterType(FilterType.NORMAL_FILTER.getValue());
                    validationCrossCheckExtractorConfig.setParallelCount(meta.getProducerParallelCount());
                    validationCrossCheckExtractorConfig.setHostInfo(getImportApplierHostInfo(physicalMeta));
                    validationCrossCheckExtractorConfig.setSourceToTargetConfig(JSON.toJSONString(physicalMeta));
                    extractorConfigStr = JSON.toJSONString(validationCrossCheckExtractorConfig);
                    break;
                case RECONCILIATION_CROSSCHECK:
                    ReconExtractorConfig reconCrossCheckConfig = new ReconExtractorConfig();
                    reconCrossCheckConfig.setExtractorType(ExtractorType.RECONCILIATION_CROSSCHECK.getValue());
                    reconCrossCheckConfig.setFilterType(FilterType.NORMAL_FILTER.getValue());
                    reconCrossCheckConfig.setParallelCount(meta.getProducerParallelCount());
                    reconCrossCheckConfig.setHostInfo(getImportApplierHostInfo(physicalMeta));
                    reconCrossCheckConfig.setSourceToTargetConfig(JSON.toJSONString(physicalMeta));
                    extractorConfigStr = JSON.toJSONString(reconCrossCheckConfig);
                    break;
                case CDC_INC:
                    DumperInfoMapper mapper = SpringContextHolder.getObject(DumperInfoMapper.class);
                    RetryTemplate template = RetryTemplate.builder()
                        .maxAttempts(120)
                        .fixedBackoff(1000)
                        .retryOn(RetryableException.class)
                        .build();

                    DumperInfo info = template.execute((RetryCallback<DumperInfo, Throwable>) retryContext -> {
                        Optional<DumperInfo> dumperInfo = mapper.selectOne(c -> c
                            .where(DumperInfoDynamicSqlSupport.role, IsEqualTo.of(() -> "M")));
                        if (!dumperInfo.isPresent()) {
                            throw new RetryableException("dumper leader is not ready");
                        }
                        return dumperInfo.get();
                    }, retryContext -> null);

                    CdcExtractorConfig cdcExtractorConfig = new CdcExtractorConfig();
                    cdcExtractorConfig.setCdcServerIp(info.getIp());
                    cdcExtractorConfig.setCdcServerPort(info.getPort());
                    cdcExtractorConfig.setExtractorType(ExtractorType.CDC_INC.getValue());
                    cdcExtractorConfig.setFilterType(FilterType.NORMAL_FILTER.getValue());
                    DataImportMeta.PhysicalMeta backflowMeta =
                        (DataImportMeta.PhysicalMeta) BeanUtils.cloneBean(physicalMeta);
                    backflowMeta.setIgnoreServerIds(meta.getBackflowIgnoreServerId() + "");
                    backflowMeta.setDstServerId(meta.getBackflowServerId());
                    Map<String, Set<String>> allowTableList = Maps.newHashMap();
                    allowTableList.put(meta.getBackflowDbName(), Sets.newHashSet(meta.getBackflowTableList()));
                    backflowMeta.setAllowTableList(allowTableList);
                    backflowMeta.setRewriteTableMapping(Maps.newHashMap());
                    backflowMeta.setDstDb(meta.getBackflowDstDbName());
                    Set set = Sets.newHashSet();
                    set.add(meta.getBackflowDbName());
                    backflowMeta.setSrcDbList(set);
                    cdcExtractorConfig.setSourceToTargetConfig(JSON.toJSONString(backflowMeta));
                    // define source to target config here
                    cdcExtractorConfig.setHostInfo(getImportApplierHostInfo(physicalMeta));
                    extractorConfigStr = JSON.toJSONString(cdcExtractorConfig);
                    break;
                default:
                    break;
                }

                // pipeline config
                PipelineConfig pipelineConfig = new PipelineConfig();
                pipelineConfig.setSupportXa(rplService.getServiceType() != ServiceType.CDC_INC.getValue());
                pipelineConfig.setFixedTpsLimit(physicalMeta.getFixedTpsLimit());
                pipelineConfig.setSkipException(physicalMeta.isSkipException());
                pipelineConfig.setBufferSize(meta.getRingBufferSize());
                pipelineConfig.setConsumerParallelCount(meta.getConsumerParallelCount());
                pipelineConfigStr = JSON.toJSONString(pipelineConfig);

                // applier config
                ApplierConfig applierConfig = new ApplierConfig();
                applierConfig.setApplierType(meta.getApplierType().getValue());
                applierConfig.setMergeBatchSize(meta.getMergeBatchSize());
                applierConfig.setSendBatchSize(meta.getSendBatchSize());
                // 评估升级默认关闭ddl支持
                applierConfig.setEnableDdl(false);

                // add crosscheck config
                if (rplService.getServiceType() == ServiceType.CDC_INC.getValue() ||
                    rplService.getServiceType() == ServiceType.FULL_VALIDATION_CROSSCHECK.getValue() ||
                    rplService.getServiceType() == ServiceType.RECONCILIATION_CROSSCHECK.getValue()) {
                    applierConfig.setHostInfo(getBackflowApplierHostInfo(meta));
                    applierConfig.setLogCommitLevel(RplConstants.LOG_ALL_COMMIT);
                } else {
                    applierConfig.setHostInfo(getImportApplierHostInfo(physicalMeta));
                }
                if (rplService.getServiceType() == ServiceType.INC_COPY.getValue()) {
                    applierConfig.setLogCommitLevel(RplConstants.LOG_ALL_COMMIT);
                }
                if (rplService.getServiceType() == ServiceType.FULL_COPY.getValue()) {
                    applierConfig.setApplierType(ApplierType.FULL_COPY.getValue());
                }
                applierConfigStr = JSON.toJSONString(applierConfig);

                // memory
                int memory = RplConstants.DEFAULT_MEMORY_SIZE;
                if (rplService.getServiceType() == ServiceType.FULL_COPY.getValue()) {
                    memory = RplConstants.DEFAULT_MEMORY_SIZE_FOR_FULL_COPY;
                }

                RplTask rplTask = DbTaskMetaManager.addTaskWithMemory(rplService.getStateMachineId(),
                    rplService.getId(),
                    extractorConfigStr,
                    pipelineConfigStr,
                    applierConfigStr,
                    ServiceType.from(rplService.getServiceType()),
                    i,
                    meta.getCdcClusterId(),
                    memory);
                metaLogger.info("createTasks end, service: {}, task: {}", rplService.getId(), rplTask.getId());
                // 增量任务需要特殊处理，写入增量起始位点
                if (rplService.getServiceType() == ServiceType.INC_COPY.getValue()) {
                    String position = CommonUtil.createInitialBinlogPosition();
                    DbTaskMetaManager.updateBinlogPosition(rplTask.getId(), position);
                }

                if (rplService.getServiceType() == ServiceType.CDC_INC.getValue()
                    || rplService.getServiceType() == ServiceType.FULL_VALIDATION_CROSSCHECK.getValue()
                    || rplService.getServiceType() == ServiceType.RECONCILIATION_CROSSCHECK.getValue()) {
                    // one process/task per service
                    break;
                }
            }
        } catch (Throwable e) {
            metaLogger.error("createTasks failed, service: {}", rplService.getId(), e);
            throw e;
        }
    }

    private static ResultCode updateServiceStatus(RplService service, ServiceStatus serviceStatus, TaskStatus taskStatus) {
        if (service.getStatus() != ServiceStatus.FINISHED.getValue()) {
            DbTaskMetaManager.updateServiceStatus(service.getId(), serviceStatus);
        }
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
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
            List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
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

    private static HostInfo getImportApplierHostInfo(DataImportMeta.PhysicalMeta physicalMeta) {
        // DataImportMeta.PhysicalMeta tempMeta = importMeta.getMetaList().get(physicalId);
        HostInfo info = new HostInfo(physicalMeta.getDstHost(),
            physicalMeta.getDstPort(),
            physicalMeta.getDstUser(),
            physicalMeta.getDstPassword(),
            physicalMeta.getDstDb(),
            physicalMeta.getDstType(), physicalMeta.getDstServerId());
        info.setUsePolarxPoolCN(true);
        return info;
    }

    private static HostInfo getRplApplierHostInfo() {
        HostInfo info = HostManager.getDstPolarxHost();
        info.setUsePolarxPoolCN(true);
        return info;
    }

    private static HostInfo getRplExtractorHostInfo(ReplicateMeta replicateMeta) {
        HostInfo info = new HostInfo(replicateMeta.getMasterHost(),
            replicateMeta.getMasterPort(),
            replicateMeta.getMasterUser(),
            replicateMeta.getMasterPassword(),
            "",
            replicateMeta.getMasterType(), RplConstants.SERVER_ID_NULL);
        info.setUsePolarxPoolCN(true);
        return info;
    }

    private static HostInfo getRecoveryExtractorHostInfo(RecoveryMeta recoveryMeta) {
        return new HostInfo(recoveryMeta.getHost(),
            recoveryMeta.getPort(),
            recoveryMeta.getUser(),
            recoveryMeta.getPwd(),
            recoveryMeta.getSchema(),
            HostType.POLARX2, RplConstants.SERVER_ID_NULL);
    }

    private static HostInfo getImportExtractorHostInfo(DataImportMeta.PhysicalMeta physicalMeta) {
        return new HostInfo(physicalMeta.getSrcHost(),
            physicalMeta.getSrcPort(),
            physicalMeta.getSrcUser(),
            physicalMeta.getSrcPassword(),
            "",
            physicalMeta.getSrcType(), RplConstants.SERVER_ID_NULL);
    }

    private static HostInfo getBackflowApplierHostInfo(DataImportMeta meta) {
        return new HostInfo(meta.getBackflowHost(),
            meta.getBackflowPort(),
            meta.getBackflowUser(),
            meta.getBackflowPwd(),
            meta.getBackflowDstDbName(),
            meta.getBackflowType(),
            meta.getBackflowServerId());
    }

    private static int computeValProgress(RplTask task) {
        if (task.getStatus() == TaskStatus.FINISHED.getValue()) {
            return RplConstants.FINISH_PERCENTAGE;
        }

        long fsmId = task.getStateMachineId();
        int totalCount = DbTaskMetaManager.countTotalValTask(fsmId, task.getType());
        if (totalCount == 0) {
            metaLogger.error("Validation task total count returns 0. fsmId: {}", fsmId);
            return 0;
        }
        int doneTask = DbTaskMetaManager.countDoneValTask(fsmId, task.getType());

        return (doneTask * 100) / totalCount;
    }

    private static int computeFullCopyPercentage(RplTask task) {
        if (task.getStatus() == TaskStatus.FINISHED.getValue()) {
            return RplConstants.FINISH_PERCENTAGE;
        }
        List<RplDbFullPosition> positionList = DbTaskMetaManager.listDbFullPosition(task.getId());
        long dealtCount = 0;
        long totalCount = 0;
        for (RplDbFullPosition position : positionList) {
            if (position.getFinished() == RplConstants.FINISH) {
                dealtCount += position.getFinishedCount();
                totalCount += position.getFinishedCount();
            } else {
                dealtCount += position.getFinishedCount();
                totalCount += position.getTotalCount();
            }
        }
        if (dealtCount >= totalCount) {
            return 99;
        }
        return (int) (dealtCount * 100 / totalCount);
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
            int maxRunningTaskNum = computeMaxRunningTaskNum(workers);
            String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            List<RplTask> runningTasks = DbTaskMetaManager.listClusterTask(TaskStatus.RUNNING, clusterId);
            List<RplTask> readyTasks = DbTaskMetaManager.listClusterTask(TaskStatus.READY, clusterId);
            List<RplTask> toDistributeTasks = new ArrayList<>();

            for (RplTask task : runningTasks) {
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

            // the only way to set task to running state
            int readyToRunningTaskNum = Math.min(maxRunningTaskNum - runningTasks.size(), readyTasks.size());
            if (readyToRunningTaskNum > 0) {
                for (int i = 0; i < readyToRunningTaskNum; i++) {
                    DbTaskMetaManager.updateTaskStatus(readyTasks.get(i).getId(), TaskStatus.RUNNING);
                    toDistributeTasks.add(readyTasks.get(i));
                }
            }

            metaLogger.info("distributeTasks, workerLoads before: {}", JSON.toJSONString(workerLoads));
            List<Map.Entry<String, Integer>> sortedWorkLoad = new ArrayList<>(workerLoads.entrySet());
            sortedWorkLoad.sort(Comparator.comparingInt(Map.Entry::getValue));

            for (RplTask task : toDistributeTasks) {
                for (Map.Entry<String, Integer> entry : sortedWorkLoad) {
                    if (!StringUtils.equals(entry.getKey(), task.getWorker())) {
                        DbTaskMetaManager.updateTaskWorker(task.getId(), entry.getKey());
                        metaLogger.info("distributeTasks, task: {}, to worker: {}", task.getId(), entry.getKey());
                        entry.setValue(entry.getValue() + 1);
                        sortedWorkLoad.sort(Comparator.comparingInt(Map.Entry::getValue));
                        break;
                    }
                }
            }
            for (Map.Entry<String, Integer> entry : sortedWorkLoad) {
                workerLoads.put(entry.getKey(), entry.getValue());
            }

            // TODO by jiyue resource check and alarm

            metaLogger.info("distributeTasks end, workerLoads after: {}", JSON.toJSONString(workerLoads));
            return toDistributeTasks.size();
        } catch (Throwable e) {
            metaLogger.error("distributeTasks failed", e);
            return 0;
        }
    }

    public static int computeMaxRunningTaskNum(List<Container> workers) {
        int totalCpu = 0;
        int totalMem = 0;
        for (Container worker: workers) {
            totalCpu = totalCpu + worker.getCapability().getCpu();
            totalMem = totalMem + worker.getCapability().getMemory_mb();
        }
        // sql flashback任务cpu占用率高，全量迁移cpu和内存占用率高
        // 综合下来，这里暂时以1c2G作为单任务所需资源
        return Math.min(totalCpu, totalMem / 2000);
    }

    /**
     * For Worker
     */
    public static void checkAndRunLocalTasks(String clusterId) throws Exception {
        metaLogger.info("checkAndRunLocalTasks start");
        List<RplTask> localNeedRunTasks =
            DbTaskMetaManager.listTaskByService(DynamicApplicationConfig.getString(INST_IP),
                TaskStatus.RUNNING,clusterId);
        Set<Long> needRunTaskIds = localNeedRunTasks.stream().map(RplTask::getId).collect(Collectors.toSet());
        Set<Long> runningTaskIds = Sets.newHashSet(listLocalRunningTaskId());
        stopNoLocalTasks(runningTaskIds, needRunTaskIds);
        startLocalTasks(runningTaskIds, localNeedRunTasks);
        List<RplTask> localNeedRestartTasks = DbTaskMetaManager.listTaskByService(DynamicApplicationConfig.getString(INST_IP),
            TaskStatus.RESTART, clusterId);
        for (RplTask task : localNeedRestartTasks) {
            // restart means MUST stop once
            // 1. leader set tasks to restart
            // 2. worker stop them and then set to ready
            // 3. leader schedule ready tasks
            DbTaskMetaManager.updateTaskStatus(task.getId(), TaskStatus.READY);
        }
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
        long when = System.currentTimeMillis() - RplConstants.TASK_KEEP_ALIVE_INTERVAL_SECONDS * 1000;
        boolean after = task.getGmtModified()
            .after(new Date(when));
        if (!after) {
            MonitorManager.getInstance().triggerAlarm(MonitorType.RPL_HEARTBEAT_TIMEOUT_ERROR, task.getId(),
                "check is task running error");
            metaLogger.error("Check isTaskRunning. task id: {}, task modified time: {}, current - 300 seconds: {}", task.getId(), task.getGmtModified(), new Date(when));
        }
        return after;
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
        if (Integer.parseInt(StringUtils.trim(countStr)) <= 0) {
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
                RplTaskConfig config = DbTaskMetaManager.getTaskConfig(needRunTask.getId());
                if (config == null) {
                    MonitorManager.getInstance().triggerAlarm(MonitorType.RPL_HEARTBEAT_TIMEOUT_ERROR,
                        needRunTask.getId(), "Task has no config");
                    metaLogger.error("Task has no config. task id: {}", needRunTask.getId());
                }
                commander.startRplTask(needRunTask.getId(), taskName, config.getMemory());
                metaLogger.warn("start local running RplTaskEngine {} {}", needRunTask.getId(), taskName);
            }
        }
    }
}
