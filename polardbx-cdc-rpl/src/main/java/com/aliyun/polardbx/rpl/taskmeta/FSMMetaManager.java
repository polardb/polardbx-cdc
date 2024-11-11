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
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.RplDbFullPosition;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.binlog.filesys.CdcFileSystem;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.MasterStatus;
import com.aliyun.polardbx.rpc.cdc.Request;
import com.aliyun.polardbx.rpl.applier.StatisticUnit;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.HostManager;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportTaskDetailInfo;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMState;
import com.aliyun.polardbx.rpl.common.fsmutil.RecoveryFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.ReplicaFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.ServiceDetail;
import com.aliyun.polardbx.rpl.common.fsmutil.TaskDetail;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author shicai.xsc 2021/1/8 11:15
 * @since 5.0.0.0
 */
public class FSMMetaManager {
    private static final Logger defaultLogger = LoggerFactory.getLogger(FSMMetaManager.class);
    private static final JdbcTemplate cnJdbcTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
    private static final String LOCK_DB = "ALTER DATABASE `%s` SET read_only=true";
    private static final String UNLOCK_DB = "ALTER DATABASE `%s` SET read_only=false";

    /* /v1/import/service/start */
    public static ResultCode<?> startStateMachine(long FSMId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(FSMId);
        if (stateMachine == null) {
            defaultLogger.error("from id: {} , fsm not found", FSMId);
            return new ResultCode<>(RplConstants.FAILURE_CODE, "fsm not found", RplConstants.FAILURE);
        }
        if (FSMState.valueOf(stateMachine.getState()) == FSMState.FINISHED) {
            defaultLogger.info("checkStateMachines fsmId: {} finished", FSMId);
            return new ResultCode<>(RplConstants.FAILURE_CODE, "stateMachine state is finished", RplConstants.FAILURE);
        } else {
            defaultLogger.info("startStateMachine, fsmId: {}, state: {}", FSMId, stateMachine.getState());
            DbTaskMetaManager.updateStateMachineStatus(FSMId, StateMachineStatus.RUNNING);
            return startServiceByState(FSMId, FSMState.valueOf(stateMachine.getState()));
        }
    }

    /* /v1/import/service/stop */
    public static ResultCode<?> stopStateMachine(long fsmId) {
        defaultLogger.info("stopStateMachine, fsmId: {}", fsmId);
        DbTaskMetaManager.updateStateMachineStatus(fsmId, StateMachineStatus.STOPPED);
        List<RplService> services = DbTaskMetaManager.listService(fsmId);
        if (services.isEmpty()) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "no service in this fsm", RplConstants.FAILURE);
        }
        for (RplService service : services) {
            stopService(service);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/service/restart/{serverId} */
    public static ResultCode<?> restartStateMachine(long fsmId) {
        defaultLogger.info("restartStateMachine, fsmId: {}", fsmId);
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        List<RplService> services = DbTaskMetaManager.listService(fsmId);
        if (stateMachine == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "this fsmid has no fsm", RplConstants.FAILURE);
        }
        if (services.isEmpty()) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "no service in this fsm", RplConstants.FAILURE);
        }
        DbTaskMetaManager.updateStateMachineStatus(fsmId, StateMachineStatus.RUNNING);
        for (RplService service : services) {
            if (FSMState.contain(service.getStateList(), FSMState.valueOf(stateMachine.getState()))) {
                updateServiceStatus(service, ServiceStatus.RUNNING, TaskStatus.RESTART);
            }
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/service/delete */
    public static ResultCode<?> deleteStateMachine(long FSMId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(FSMId);
        if (stateMachine == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "fsm not found", RplConstants.FAILURE);
        }
        // you need first stop then delete
        if (StateMachineStatus.valueOf(stateMachine.getStatus()) == StateMachineStatus.RUNNING) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "fsm state not support delete action",
                RplConstants.FAILURE);
        }
        DbTaskMetaManager.deleteStateMachine(FSMId);
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/service/detail */
    public static ResultCode<DataImportTaskDetailInfo> getStateMachineDetail(long FSMId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(FSMId);
        if (stateMachine == null) {
            defaultLogger.error("from id: {} , fsm not found", FSMId);
            return new ResultCode<>(RplConstants.FAILURE_CODE, "fsm not found", new DataImportTaskDetailInfo());
        }
        ResultCode<DataImportTaskDetailInfo> returnResult = new ResultCode<>(RplConstants.SUCCESS_CODE, "success");
        DataImportTaskDetailInfo info = new DataImportTaskDetailInfo();
        info.setFsmId(stateMachine.getId());
        info.setFsmState(stateMachine.getState());
        info.setFsmStatus(stateMachine.getStatus());
        info.setServiceDetailList(new ArrayList<>());
        List<RplService> services = DbTaskMetaManager.listService(FSMId);
        for (RplService service : services) {
            ServiceDetail serviceDetail = new ServiceDetail();
            serviceDetail.setId(service.getId());
            serviceDetail.setStatus(service.getStatus());
            serviceDetail.setType(service.getServiceType());
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
    public static ResultCode<?> startTask(long taskId) {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        if (task == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "taskgroup not found", RplConstants.FAILURE);
        }
        if (TaskStatus.valueOf(task.getStatus()) != TaskStatus.FINISHED) {
            DbTaskMetaManager.updateTaskStatus(task.getId(), TaskStatus.READY);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/stop */
    public static ResultCode<?> stopTask(long taskId) {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        if (task == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "task not found", RplConstants.FAILURE);
        }
        if (TaskStatus.valueOf(task.getStatus()) != TaskStatus.FINISHED) {
            DbTaskMetaManager.updateTaskStatus(task.getId(), TaskStatus.STOPPED);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /*/v1/import/task/reset/{taskId} 清除全量信息 */
    public static ResultCode<?> resetTask(long taskId) {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        if (task == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "taskgroup not found", RplConstants.FAILURE);
        }
        RplService service = DbTaskMetaManager.getService(task.getServiceId());
        if (service == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "taskgroup not found", RplConstants.FAILURE);
        }
        DbTaskMetaManager.updateTaskStatus(taskId, TaskStatus.STOPPED);
        DbTaskMetaManager.deleteDbFullPositionByTask(taskId);
        DbTaskMetaManager.deleteValidationTaskByTask(taskId);
        DbTaskMetaManager.deleteValidationDiffByTask(taskId);
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> markBackFlowPosition(long fsmId) {
        try {
            RplService backFlowService = DbTaskMetaManager.getService(fsmId, ServiceType.CDC_INC);
            List<RplTask> backFlowTasks = DbTaskMetaManager.listTaskByService(backFlowService.getId());
            DumperInfoMapper mapper = SpringContextHolder.getObject(DumperInfoMapper.class);
            RetryTemplate template =
                RetryTemplate.builder().maxAttempts(120).fixedBackoff(1000).retryOn(RetryableException.class).build();
            DumperInfo info = template.execute((RetryCallback<DumperInfo, Throwable>) retryContext -> {
                Optional<DumperInfo> dumperInfo =
                    mapper.selectOne(c -> c.where(DumperInfoDynamicSqlSupport.role, IsEqualTo.of(() -> "M")));
                if (!dumperInfo.isPresent()) {
                    throw new RetryableException("dumper leader is not ready");
                }
                return dumperInfo.get();
            }, retryContext -> null);

            ManagedChannel channel = ManagedChannelBuilder.forAddress(info.getIp(), info.getPort()).usePlaintext()
                .maxInboundMessageSize(0xFFFFFF + 0xFF).build();

            BinlogPosition position = findStartPosition(channel);
            channel.shutdownNow();
            if (position != null) {
                for (RplTask task : backFlowTasks) {
                    DbTaskMetaManager.updateTask(task.getId(), null, null, position.toString(), null, null);
                }
                defaultLogger.info("success in marking back flow position");
                return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", true);
            } else {
                defaultLogger.error("fail to mark back flow position because newest position is null");
            }
        } catch (Throwable e) {
            defaultLogger.error("exception when mark back flow position: ", e);
        }
        return new ResultCode<>(RplConstants.FAILURE_CODE, "failure", false);
    }

    public static BinlogPosition findStartPosition(ManagedChannel channel) throws InterruptedException {
        BinlogPosition position = new BinlogPosition(null, 0, -1, -1);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CdcServiceGrpc.CdcServiceStub cdcServiceStub = CdcServiceGrpc.newStub(channel);
        cdcServiceStub.showMasterStatus(Request.newBuilder().build(), new StreamObserver<MasterStatus>() {
            @Override
            public void onNext(MasterStatus value) {
                // position = new BinlogPosition(value.getFile(), value.getPosition(), -1, -1);
                position.setFileName(value.getFile());
                position.setPosition(value.getPosition());
                countDownLatch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                defaultLogger.error("find start position error !", t);
                countDownLatch.countDown();
                throw new PolardbxException(t);
            }

            @Override
            public void onCompleted() {

            }
        });
        countDownLatch.await();
        return (position.getFileName() != null) ? position : null;
    }

    public static ResultCode<?> lockDbs(long fsmId) {
        MonitorManager.getInstance().triggerAlarm(MonitorType.RPL_LOCK_UNLOCK_DB_WARNING, fsmId);
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        DataImportMeta meta = JSON.parseObject(stateMachine.getConfig(), DataImportMeta.class);
        Collection<String> dbs = meta.getLogicalDbMappings().values();
        if (lockDbs(dbs)) {
            defaultLogger.info("success lock dbs : {}", dbs);
            return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", true);
        }
        return new ResultCode<>(RplConstants.FAILURE_CODE, "failure", false);
    }

    public static ResultCode<?> unlockDbs(long fsmId) {
        MonitorManager.getInstance().triggerAlarm(MonitorType.RPL_LOCK_UNLOCK_DB_WARNING, fsmId);
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        DataImportMeta meta = JSON.parseObject(stateMachine.getConfig(), DataImportMeta.class);
        Collection<String> dbs = meta.getLogicalDbMappings().values();
        if (unlockDbs(dbs)) {
            defaultLogger.info("success lock dbs : {}", dbs);
            return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", true);
        }
        return new ResultCode<>(RplConstants.FAILURE_CODE, "failure", false);
    }

    private static boolean lockDbs(Collection<String> dbNames) {
        try {
            for (String dbName : dbNames) {
                cnJdbcTemplate.execute(String.format(LOCK_DB, dbName));
            }
            return true;
        } catch (Throwable e) {
            unlockDbs(dbNames);
            defaultLogger.error("fail to lock dbs: {}, exception: {}", dbNames, e);
        }
        return false;
    }

    private static boolean unlockDbs(Collection<String> dbNames) {
        try {
            for (String dbName : dbNames) {
                cnJdbcTemplate.execute(String.format(UNLOCK_DB, dbName));
            }
            return true;
        } catch (Throwable e) {
            defaultLogger.error("fail to unlock dbs: {}, exception: {}", dbNames, e);
        }
        return false;
    }

    /*/v1/import/task/getconfig/{taskId} */
    public static ResultCode<TaskDetail> getTaskDetail(long taskId) {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        if (task == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "task not found", new TaskDetail());
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", getTaskSpecificDetail(task));
    }

    /* /v1/import/task/exception/skip/start/{taskId} */
    public static ResultCode<?> startSkipException(long taskId) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
        pipelineConfig.setSkipException(true);
        String newConfigStr = JSON.toJSONString(pipelineConfig);
        DbTaskMetaManager.updateTaskConfig(taskId, null, newConfigStr, null, null);
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/exception/skip/stop/{taskId} */
    public static ResultCode<?> stopSkipException(long taskId) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
        pipelineConfig.setSkipException(false);
        String newConfigStr = JSON.toJSONString(pipelineConfig);
        DbTaskMetaManager.updateTaskConfig(taskId, null, newConfigStr, null, null);
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/safemode/start/{taskId} */
    public static ResultCode<?> startSafeMode(long taskId) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
        pipelineConfig.setSafeMode(true);
        String newConfigStr = JSON.toJSONString(pipelineConfig);
        DbTaskMetaManager.updateTaskConfig(taskId, null, newConfigStr, null, null);
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/safemode/stop/{taskId} */
    public static ResultCode<?> stopSafeMode(long taskId) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
        pipelineConfig.setSafeMode(false);
        String newConfigStr = JSON.toJSONString(pipelineConfig);
        DbTaskMetaManager.updateTaskConfig(taskId, null, newConfigStr, null, null);
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/exception/skip/start/{taskId} */
    public static ResultCode<?> startIncValidation(long fsmId) {
        RplService service = DbTaskMetaManager.getService(fsmId, ServiceType.INC_COPY);
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
        for (RplTask task : tasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
            pipelineConfig.setUseIncValidation(true);
            String newConfigStr = JSON.toJSONString(pipelineConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), null, newConfigStr, null, null);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    /* /v1/import/task/exception/skip/stop/{taskId} */
    public static ResultCode<?> stopIncValidation(long fsmId) {
        RplService service = DbTaskMetaManager.getService(fsmId, ServiceType.INC_COPY);
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
        for (RplTask task : tasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
            pipelineConfig.setUseIncValidation(false);
            String newConfigStr = JSON.toJSONString(pipelineConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), null, newConfigStr, null, null);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> setFlowControl(long taskId, int fixedRps) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
        pipelineConfig.setFixedTpsLimit(fixedRps);
        String newConfigStr = JSON.toJSONString(pipelineConfig);
        DbTaskMetaManager.updateTaskConfig(taskId, null, newConfigStr, null, null);
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> setConvertToByte(long fsmId, boolean convertToByte) {
        RplService fullValidationService = DbTaskMetaManager.getService(fsmId, ServiceType.FULL_VALIDATION);
        List<RplTask> fullValidationTasks = DbTaskMetaManager.listTaskByService(fullValidationService.getId());
        for (RplTask task : fullValidationTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            ValidationExtractorConfig extractorConfig =
                JSON.parseObject(config.getExtractorConfig(), ValidationExtractorConfig.class);
            extractorConfig.setConvertToByte(convertToByte);
            String newConfigStr = JSON.toJSONString(extractorConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), newConfigStr, null, null, null);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> setParallelCount(long fsmId, int parallelCount) {
        RplService fullCopyService = DbTaskMetaManager.getService(fsmId, ServiceType.FULL_COPY);
        List<RplTask> fullCopyTasks = DbTaskMetaManager.listTaskByService(fullCopyService.getId());
        for (RplTask task : fullCopyTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            FullExtractorConfig extractorConfig =
                JSON.parseObject(config.getExtractorConfig(), FullExtractorConfig.class);
            extractorConfig.setParallelCount(parallelCount);
            String newConfigStr = JSON.toJSONString(extractorConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), newConfigStr, null, null, null);
        }
        RplService fullValidationService = DbTaskMetaManager.getService(fsmId, ServiceType.FULL_VALIDATION);
        List<RplTask> fullValidationTasks = DbTaskMetaManager.listTaskByService(fullValidationService.getId());
        for (RplTask task : fullValidationTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            ValidationExtractorConfig extractorConfig =
                JSON.parseObject(config.getExtractorConfig(), ValidationExtractorConfig.class);
            extractorConfig.setParallelCount(parallelCount);
            String newConfigStr = JSON.toJSONString(extractorConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), newConfigStr, null, null, null);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> setEventBufferSize(long fsmId, int bufferSize) {
        RplService incService = DbTaskMetaManager.getService(fsmId, ServiceType.INC_COPY);
        RplService backFlowService = DbTaskMetaManager.getService(fsmId, ServiceType.CDC_INC);
        RplService replicaService = DbTaskMetaManager.getService(fsmId, ServiceType.REPLICA_INC);
        List<RplTask> allTasks = new ArrayList<>();
        if (incService != null) {
            allTasks.addAll(DbTaskMetaManager.listTaskByService(incService.getId()));
        }
        if (backFlowService != null) {
            allTasks.addAll(DbTaskMetaManager.listTaskByService(backFlowService.getId()));
        }
        if (replicaService != null) {
            allTasks.addAll(DbTaskMetaManager.listTaskByService(replicaService.getId()));
        }
        String newConfigStr1 = null;
        if (allTasks.size() == 0) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        for (RplTask task : allTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            switch (ServiceType.valueOf(task.getType())) {
            case INC_COPY:
                RdsExtractorConfig extractorConfig1 =
                    JSON.parseObject(config.getExtractorConfig(), RdsExtractorConfig.class);
                extractorConfig1.setEventBufferSize(bufferSize);
                newConfigStr1 = JSON.toJSONString(extractorConfig1);
                break;
            case CDC_INC:
                CdcExtractorConfig extractorConfig2 =
                    JSON.parseObject(config.getExtractorConfig(), CdcExtractorConfig.class);
                extractorConfig2.setEventBufferSize(bufferSize);
                newConfigStr1 = JSON.toJSONString(extractorConfig2);
                break;
            case REPLICA_INC:
                ExtractorConfig extractorConfig3 = JSON.parseObject(config.getExtractorConfig(), ExtractorConfig.class);
                extractorConfig3.setEventBufferSize(bufferSize);
                newConfigStr1 = JSON.toJSONString(extractorConfig3);
            default:
                break;
            }
            PipelineConfig pipelineConfig = JSON.parseObject(config.getPipelineConfig(), PipelineConfig.class);
            pipelineConfig.setBufferSize(bufferSize);
            String newConfigStr2 = JSON.toJSONString(pipelineConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), newConfigStr1, newConfigStr2, null, null);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> modifyExtractorHostInfo(long taskId, String ip, Integer port) {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        String newConfigStr1 = null;
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
        if (config == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        switch (ServiceType.valueOf(task.getType())) {
        case INC_COPY:
            RdsExtractorConfig extractorConfig1 =
                JSON.parseObject(config.getExtractorConfig(), RdsExtractorConfig.class);
            defaultLogger.info("old host info: {}", JSON.toJSONString(extractorConfig1.getHostInfo()));
            extractorConfig1.getHostInfo().setHost(ip);
            extractorConfig1.getHostInfo().setPort(port);
            defaultLogger.info("new host info: {}", JSON.toJSONString(extractorConfig1.getHostInfo()));
            newConfigStr1 = JSON.toJSONString(extractorConfig1);
            break;
        case FULL_COPY:
            FullExtractorConfig extractorConfig2 =
                JSON.parseObject(config.getExtractorConfig(), FullExtractorConfig.class);
            defaultLogger.info("old host info: {}", JSON.toJSONString(extractorConfig2.getHostInfo()));
            extractorConfig2.getHostInfo().setHost(ip);
            extractorConfig2.getHostInfo().setPort(port);
            defaultLogger.info("old host info: {}", JSON.toJSONString(extractorConfig2.getHostInfo()));
            newConfigStr1 = JSON.toJSONString(extractorConfig2);
            break;
        case FULL_VALIDATION:
            ValidationExtractorConfig extractorConfig3 =
                JSON.parseObject(config.getExtractorConfig(), ValidationExtractorConfig.class);
            defaultLogger.info("old host info: {}", JSON.toJSONString(extractorConfig3.getHostInfo()));
            extractorConfig3.getHostInfo().setHost(ip);
            extractorConfig3.getHostInfo().setPort(port);
            defaultLogger.info("old host info: {}", JSON.toJSONString(extractorConfig3.getHostInfo()));
            newConfigStr1 = JSON.toJSONString(extractorConfig3);
            break;
        case RECONCILIATION:
            ReconExtractorConfig extractorConfig4 =
                JSON.parseObject(config.getExtractorConfig(), ReconExtractorConfig.class);
            defaultLogger.info("old host info: {}", JSON.toJSONString(extractorConfig4.getHostInfo()));
            extractorConfig4.getHostInfo().setHost(ip);
            extractorConfig4.getHostInfo().setPort(port);
            defaultLogger.info("old host info: {}", JSON.toJSONString(extractorConfig4.getHostInfo()));
            newConfigStr1 = JSON.toJSONString(extractorConfig4);
            break;
        default:
            break;
        }
        DbTaskMetaManager.updateTaskConfig(task.getId(), newConfigStr1, null, null, null);
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> setFullReadBatchSize(long fsmId, int readBatchSize) {
        List<RplService> allServices = new ArrayList<>();
        List<RplTask> allTasks = new ArrayList<>();
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.FULL_COPY));
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.REPLICA_FULL));
        for (RplService service : allServices) {
            if (service != null) {
                allTasks.addAll(DbTaskMetaManager.listTaskByService(service.getId()));
            }
        }
        if (allTasks.isEmpty()) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        for (RplTask task : allTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            FullExtractorConfig extractorConfig =
                JSON.parseObject(config.getExtractorConfig(), FullExtractorConfig.class);
            extractorConfig.setFetchBatchSize(readBatchSize);
            String newConfigStr1 = JSON.toJSONString(extractorConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), newConfigStr1, null, null, null);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> setWriteBatchSize(long fsmId, int writeBatchSize) {
        List<RplService> allServices = new ArrayList<>();
        List<RplTask> allTasks = new ArrayList<>();
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.FULL_COPY));
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.INC_COPY));
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.REPLICA_FULL));
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.REPLICA_INC));
        for (RplService service : allServices) {
            if (service != null) {
                allTasks.addAll(DbTaskMetaManager.listTaskByService(service.getId()));
            }
        }
        if (allTasks.isEmpty()) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        for (RplTask task : allTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            ApplierConfig applierConfig = JSON.parseObject(config.getApplierConfig(), ApplierConfig.class);
            applierConfig.setMergeBatchSize(writeBatchSize);
            String newConfigStr1 = JSON.toJSONString(applierConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), null, null, newConfigStr1, null);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> setWriteParallelCount(long fsmId, int writeParallelCount) {
        List<RplService> allServices = new ArrayList<>();
        List<RplTask> allTasks = new ArrayList<>();
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.FULL_COPY));
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.INC_COPY));
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.REPLICA_FULL));
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.REPLICA_INC));
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.CDC_INC));
        for (RplService service : allServices) {
            if (service != null) {
                allTasks.addAll(DbTaskMetaManager.listTaskByService(service.getId()));
            }
        }
        if (allTasks.isEmpty()) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        for (RplTask task : allTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            ApplierConfig applierConfig = JSON.parseObject(config.getApplierConfig(), ApplierConfig.class);
            applierConfig.setMaxPoolSize(writeParallelCount);
            String newConfigStr1 = JSON.toJSONString(applierConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), null, null, newConfigStr1, null);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> setFullCopyFinishTime(long fsmId, long fullCopyFinishTimeStamp) {
        List<RplService> allServices = new ArrayList<>();
        List<RplTask> allTasks = new ArrayList<>();
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.INC_COPY));
        allServices.add(DbTaskMetaManager.getService(fsmId, ServiceType.REPLICA_INC));
        for (RplService service : allServices) {
            if (service != null) {
                allTasks.addAll(DbTaskMetaManager.listTaskByService(service.getId()));
            }
        }
        if (allTasks.isEmpty()) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        for (RplTask task : allTasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            if (config == null) {
                return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
            }
            ApplierConfig applierConfig = JSON.parseObject(config.getApplierConfig(), ApplierConfig.class);
            applierConfig.setFullCopyFinishTimeStamp(fullCopyFinishTimeStamp);
            String newConfigStr1 = JSON.toJSONString(applierConfig);
            DbTaskMetaManager.updateTaskConfig(task.getId(), null, null, newConfigStr1, null);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> setMemory(long taskId, int memoryInMb) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(taskId);
        if (config == null) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "config not found", RplConstants.FAILURE);
        }
        DbTaskMetaManager.updateTaskConfig(taskId, null, null, null, memoryInMb);
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static TaskDetail getTaskSpecificDetail(RplTask task) {
        TaskDetail taskDetail = new TaskDetail();
        taskDetail.setTaskId(task.getId());
        taskDetail.setStatus(task.getStatus());
        taskDetail.setType(task.getType());
        taskDetail.setStatistics(task.getStatistic());
        taskDetail.setLastError(task.getLastError());
        String stat = task.getStatistic();
        StatisticUnit unit = JSON.parseObject(stat, StatisticUnit.class);
        if (unit != null && unit.getMessageRps() != null) {
            taskDetail.setRps(unit.getMessageRps());
        }
        switch (ServiceType.valueOf(task.getType())) {
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

    public static ResultCode<?> startServiceByState(long FSMId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(FSMId);
        if (stateMachine != null
            && StateMachineStatus.valueOf(stateMachine.getStatus()) == StateMachineStatus.RUNNING) {
            return startServiceByState(FSMId, FSMState.valueOf(stateMachine.getState()));
        } else {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "fsm not found or fsm not set to running",
                RplConstants.FAILURE);
        }
    }

    public static ResultCode<?> startServiceByState(long FSMId, FSMState state) {
        List<RplService> services = DbTaskMetaManager.listService(FSMId);
        for (RplService service : services) {
            if (FSMState.contain(service.getStateList(), state)) {
                startService(service);
            } else {
                stopService(service);
            }
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static RplStateMachine initStateMachine(StateMachineType type, DataImportMeta meta, DataImportFSM fsm)
        throws Throwable {
        if (type != StateMachineType.DATA_IMPORT) {
            defaultLogger.error("initStateMachine failed because of unmatched type and meta");
            return null;
        }

        try {
            String config = JSON.toJSONString(meta);
            defaultLogger.info("initStateMachine, config: {}", config);

            // create stateMachine
            RplStateMachine stateMachine =
                DbTaskMetaManager.createStateMachine(config, type, fsm, null, meta.getCdcClusterId(), null);
            defaultLogger.info("initStateMachine, created stateMachine, {}", stateMachine.getId());
            defaultLogger.info("initStateMachine, stateMachine type: {}", type.name());

            // full
            RplService fullService = DbTaskMetaManager.addService(stateMachine.getId(), ServiceType.FULL_COPY,
                Arrays.asList(FSMState.FULL_COPY));
            createImportTasks(fullService, meta);
            defaultLogger.info("initStateMachine, created fullService: {}", fullService.getId());

            // inc copy and check
            RplService incService = DbTaskMetaManager.addService(stateMachine.getId(), ServiceType.INC_COPY,
                Arrays.asList(FSMState.INC_COPY, FSMState.CATCH_UP_VALIDATION, FSMState.RECON_FINISHED_WAIT_CATCH_UP,
                    FSMState.RECON_FINISHED_CATCH_UP, FSMState.BI_DIRECTION));
            createImportTasks(incService, meta);
            defaultLogger.info("initStateMachine, created incService: {}", incService.getId());

            // full validation
            RplService checkService = DbTaskMetaManager.addService(stateMachine.getId(), ServiceType.FULL_VALIDATION,
                Arrays.asList(FSMState.CATCH_UP_VALIDATION));
            createImportTasks(checkService, meta);
            defaultLogger.info("initStateMachine, created checkService: {}", checkService.getId());

            // reconciliation
            RplService reconService = DbTaskMetaManager.addService(stateMachine.getId(), ServiceType.RECONCILIATION,
                Arrays.asList(FSMState.RECONCILIATION));
            createImportTasks(reconService, meta);
            defaultLogger.info("initStateMachine, created reconService: {}", reconService.getId());

            // full validation crosscheck
//            RplService valCrossCheckService = DbTaskMetaManager
//                .addService(stateMachine.getId(), ServiceType.FULL_VALIDATION_CROSSCHECK,
//                    new ArrayList<>());
//            createImportTasks(valCrossCheckService, meta);
//            defaultLogger.info("initStateMachine, created checkService: {}", valCrossCheckService.getId());

            // reconciliation crosscheck
//            RplService reconCrossCheckService = DbTaskMetaManager
//                .addService(stateMachine.getId(), ServiceType.RECONCILIATION_CROSSCHECK,
//                    new ArrayList<>());
//            createImportTasks(reconCrossCheckService, meta);
//            defaultLogger.info("initStateMachine, created reconService: {}", reconCrossCheckService.getId());

            // 回流
            RplService backflowService = DbTaskMetaManager.addService(stateMachine.getId(), ServiceType.CDC_INC,
                Arrays.asList(FSMState.BACK_FLOW, FSMState.BACK_FLOW_CATCH_UP, FSMState.BI_DIRECTION));
            createImportTasks(backflowService, meta);

            startStateMachine(stateMachine.getId());
            return stateMachine;
        } catch (Throwable e) {
            defaultLogger.error("initStateMachine failed", e);
            throw e;
        }
    }

    public static RplStateMachine initStateMachine(StateMachineType type, ReplicaMeta meta, ReplicaFSM fsm)
        throws Throwable {
        if (type != StateMachineType.REPLICA) {
            defaultLogger.error("initStateMachine failed because of unmatched type and meta");
            return null;
        }
        CommonUtil.hostSafeCheck(meta.getMasterHost());
        defaultLogger.warn(
            "connect to " + String.format("jdbc:mysql://%s:%s", meta.getMasterHost(), meta.getMasterPort()));
        try (Connection connection = DriverManager.getConnection(String.format(
                "jdbc:mysql://%s:%s?allowLoadLocalInfile=false&autoDeserialize=false"
                    + "&allowLocalInfile=false&allowUrlInLocalInfile=false", meta.getMasterHost(),
                meta.getMasterPort()),
            meta.getMasterUser(), meta.getMasterPassword())) {

            List<MutableTriple<String, Integer, String>> masterInfos;
            if (meta.isEnableDynamicMasterHost()) {
                if (StringUtils.isNotEmpty(meta.getMasterInstId())) {
                    masterInfos = CommonUtil.getComputeNodesWithFixedInstId(connection, meta.getMasterInstId());
                } else {
                    masterInfos = CommonUtil.getComputeNodes(connection);
                    meta.setMasterInstId(masterInfos.get(0).getRight());
                }
                meta.setMasterHostList(masterInfos);
            } else {
                masterInfos = Collections.singletonList(
                    new MutableTriple<>(meta.getMasterHost(), meta.getMasterPort(), meta.getMasterInstId()));
            }

            // create stateMachine
            // store common meta
            String config = JSON.toJSONString(meta);
            defaultLogger.info("initStateMachine, config: {}", config);
            RplStateMachine stateMachine =
                DbTaskMetaManager.createStateMachine(config, StateMachineType.REPLICA, fsm, meta.channel,
                    meta.getClusterId(), null);
            defaultLogger.info("initStateMachine, created stateMachine, {}", stateMachine.getId());

            // rpl full extraction service
            if (meta.isImageMode()) {
                RplService rplFullService = DbTaskMetaManager.addService(stateMachine.getId(), ServiceType.REPLICA_FULL,
                    Collections.singletonList(FSMState.REPLICA_FULL));
                createReplicaTasks(rplFullService, meta);
                defaultLogger.info("initStateMachine, created rplFullService: {}", rplFullService.getId());
            }

            // rpl incremental extraction service
            RplService rplIncService = DbTaskMetaManager.addService(stateMachine.getId(), ServiceType.REPLICA_INC,
                Arrays.asList(FSMState.REPLICA_INC, FSMState.REPLICA_INC_CATCH_UP, FSMState.REPLICA_FULL_VALID));
            if (StringUtils.isNotBlank(meta.getStreamGroup())) {
                createStreamReplicaTasks(rplIncService, meta, connection, masterInfos);
            } else {
                createReplicaTasks(rplIncService, meta);
            }
            defaultLogger.info("initStateMachine, created rplIncService: {}", rplIncService.getId());

            // rpl full data validation service
            RplService fullValidService =
                DbTaskMetaManager.addService(stateMachine.getId(), ServiceType.REPLICA_FULL_VALIDATION,
                    Collections.singletonList(FSMState.REPLICA_FULL_VALID));
            defaultLogger.info("initStateMachine, create replica full validation service: {}",
                fullValidService.getId());
            createReplicaFullValidTask(fullValidService, meta);

            // should not start automatically for replica task
            return stateMachine;
        } catch (Throwable e) {
            defaultLogger.error("initStateMachine failed", e);
            throw e;
        }
    }

    public static RplStateMachine initStateMachine(StateMachineType type, RecoveryMeta meta, RecoveryFSM fsm) {
        if (type != StateMachineType.RECOVERY) {
            defaultLogger.error("initStateMachine failed because of unmatched type and meta");
            return null;
        }
        try {
            String config = JSON.toJSONString(meta);
            defaultLogger.info("initStateMachine, config: {}", config);
            String randomUUID = UUID.randomUUID().toString();

            // create stateMachine
            String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            RplStateMachine stateMachine =
                DbTaskMetaManager.createStateMachine(config, StateMachineType.RECOVERY, fsm, null, clusterId, null);

            // flashback task
            RplService searchService = DbTaskMetaManager.addService(stateMachine.getId(), ServiceType.REC_SEARCH,
                Arrays.asList(FSMState.REC_SEARCH));
            createFlashbackTasks(searchService, meta, randomUUID);
            defaultLogger.info("initStateMachine, created rplService: {}", searchService.getId());

            // combine result task
            RplService combineService = DbTaskMetaManager.addService(stateMachine.getId(), ServiceType.REC_COMBINE,
                Arrays.asList(FSMState.REC_COMBINE));
            createCombineTask(combineService, meta, randomUUID);
            defaultLogger.info("initStateMachine, created rplService: {}", combineService.getId());

            startStateMachine(stateMachine.getId());
            return stateMachine;
        } catch (Throwable e) {
            defaultLogger.error("initStateMachine failed", e);
            throw e;
        }
    }

    /**
     * 计算服务延迟 取最大延迟时间 only support inc/cdc_inc
     */
    public static boolean checkServiceCatchUp(long FSMId, ServiceType type) {
        RplService incService = DbTaskMetaManager.getService(FSMId, type);
        if (incService == null || ServiceStatus.valueOf(incService.getStatus()) != ServiceStatus.RUNNING) {
            return false;
        }
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(incService.getId());
        for (RplTask task : tasks) {
            long delay = computeTaskDelay(task);
            defaultLogger.info("now inc copy delay: {}", delay);
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
        if (ServiceStatus.valueOf(service.getStatus()) == ServiceStatus.FINISHED) {
            return true;
        }
        // need to check service finish or not
        RplService updatedService = checkAndUpdateServiceFinish(service);
        if (ServiceStatus.valueOf(updatedService.getStatus()) != ServiceStatus.FINISHED) {
            defaultLogger.info("service still not finished, stateMachine: {}, service: {}", FSMId,
                updatedService.getId());
            return false;
        }
        return true;
    }

    public static boolean isAllTaskFinished(long FSMId, ServiceType type) {
        RplService service = DbTaskMetaManager.getService(FSMId, type);
        if (service == null) {
            return true;
        }
        if (ServiceStatus.valueOf(service.getStatus()) == ServiceStatus.FINISHED) {
            return true;
        }

        // check whether all tasks belong to this service finished
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
        for (RplTask task : tasks) {
            if (TaskStatus.valueOf(task.getStatus()) != TaskStatus.FINISHED) {
                return false;
            }
        }
        return true;
    }

    public static boolean isReplicaImageMode(long FSMId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(FSMId);
        if (stateMachine != null && stateMachine.getConfig() != null) {
            ReplicaMeta replicaMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
            return replicaMeta.isImageMode();
        }
        return false;
    }

    public static ResultCode<?> startService(RplService service) {
        defaultLogger.info("startService, {}", service.getId());
        if (ServiceStatus.valueOf(service.getStatus()) != ServiceStatus.FINISHED
            && ServiceStatus.valueOf(service.getStatus()) != ServiceStatus.RUNNING) {
            // 防止stop then start没有真正重启，目前的简易做法：stop then start must restart once
            // todo 后续需添加真实运行状态字段
            return updateServiceStatus(service, ServiceStatus.RUNNING, TaskStatus.RESTART);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static ResultCode<?> stopService(RplService service) {
        defaultLogger.info("stopService, {}", service.getId());
        if (ServiceStatus.valueOf(service.getStatus()) != ServiceStatus.FINISHED) {
            return updateServiceStatus(service, ServiceStatus.STOPPED, TaskStatus.STOPPED);
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    private static void createFlashbackTasks(RplService rplService, RecoveryMeta meta, String randomUUID) {
        String extractorConfigStr = "";
        String pipelineConfigStr = "";
        String applierConfigStr = "";
        defaultLogger.info("create SQL flash back tasks start, service: {}", rplService.getId());

        try {
            List<List<String>> lst = new ArrayList<>();
            List<String> binlogFiles =
                CdcFileSystem.listFilesInTimeRange(meta.getStartTimestamp(), meta.getEndTimestamp());
            for (int i = 0; i < binlogFiles.size(); i += meta.getBinlogFilesCountPerTask()) {
                List<String> tempList = new ArrayList<>();
                for (int j = i; j < binlogFiles.size() && j < i + meta.getBinlogFilesCountPerTask(); j++) {
                    tempList.add(binlogFiles.get(j));
                }
                lst.add(tempList);
            }
            defaultLogger.info("binlog files to consume: " + lst);

            for (int i = 0; i < lst.size(); i++) {
                RecoveryExtractorConfig extractorConfig = new RecoveryExtractorConfig();
                extractorConfig.setExtractorType(ExtractorType.RECOVERY);
                extractorConfig.setFilterType(FilterType.FLASHBACK_FILTER);
                extractorConfig.setHostInfo(getRecoveryExtractorHostInfo(meta));
                extractorConfig.setBinlogList(lst.get(i));
                extractorConfigStr = JSON.toJSONString(extractorConfig);

                PipelineConfig pipelineConfig = new PipelineConfig();
                pipelineConfigStr = JSON.toJSONString(pipelineConfig);

                RecoveryApplierConfig recoveryApplierConfig = new RecoveryApplierConfig();
                recoveryApplierConfig.setApplierType(ApplierType.RECOVERY);
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

                RplTask rplTask =
                    DbTaskMetaManager.addTask(rplService.getStateMachineId(), rplService.getId(), extractorConfigStr,
                        pipelineConfigStr, applierConfigStr, ServiceType.valueOf(rplService.getServiceType()), i,
                        DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));

                defaultLogger.info("create SQL flash back task, service: {}, task: {}", rplService.getId(),
                    rplTask.getId());
            }
            defaultLogger.info("SQL flashback tasks are all created");
        } catch (Throwable e) {
            defaultLogger.error("createTasks failed, service: {}", rplService.getId(), e);
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
        RplTask rplTask =
            DbTaskMetaManager.addTask(rplService.getStateMachineId(), rplService.getId(), "", "", combineConfigStr,
                ServiceType.valueOf(rplService.getServiceType()), mirror,
                DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));
        defaultLogger.info("create combine task end, service: {}, task: {}", rplService.getId(), rplTask.getId());
    }

    private static void createReplicaTasks(RplService rplService, ReplicaMeta meta) throws Exception {
        defaultLogger.info("createTasks start, service: {}", rplService.getId());
        CommonUtil.hostSafeCheck(meta.getMasterHost());
        try {
            if (ServiceType.valueOf(rplService.getServiceType()) == ServiceType.REPLICA_INC && StringUtils.isBlank(
                meta.getPosition())) {
                try (Connection connection = DriverManager.getConnection(String.format(
                    "jdbc:mysql://%s:%s?allowLoadLocalInfile=false&autoDeserialize=false"
                        + "&allowLocalInfile=false&allowUrlInLocalInfile=false", meta.getMasterHost(),
                    meta.getMasterPort()), meta.getMasterUser(), meta.getMasterPassword())) {
                    defaultLogger.warn("connect to " + String.format("jdbc:mysql://%s:%s", meta.getMasterHost(),
                        meta.getMasterPort()));
                    meta.setPosition(CommonUtil.getBinaryLatestPosition(connection));
                }
            }
            RplTask rplTask =
                DbTaskMetaManager.addTask(rplService.getStateMachineId(), rplService.getId(), null, null, null,
                    ServiceType.valueOf(rplService.getServiceType()), 0, meta.getClusterId());
            updateReplicaTaskConfig(rplTask, meta, true);
            defaultLogger.info("createTasks end, service: {}, task: {}", rplService.getId(), rplTask.getId());
        } catch (Throwable e) {
            defaultLogger.error("createTasks failed, service: {}", rplService.getId(), e);
            throw e;
        }
    }

    private static void createStreamReplicaTasks(RplService rplService, ReplicaMeta meta, Connection connection,
                                                 List<MutableTriple<String, Integer, String>> masterInfos) {
        defaultLogger.info("createTasks start, service: {}", rplService.getId());
        List<String> streamPositions = CommonUtil.getStreamLatestPositions(connection, meta.getStreamGroup());
        for (int i = 0; i < streamPositions.size(); i++) {
            // use dynamic host info
            meta.setMasterHost(masterInfos.get(i % masterInfos.size()).getLeft());
            meta.setMasterPort(masterInfos.get(i % masterInfos.size()).getMiddle());
            meta.setPosition(streamPositions.get(i));
            RplTask rplTask =
                DbTaskMetaManager.addTaskWithMemory(rplService.getStateMachineId(), rplService.getId(), null, null,
                    null, ServiceType.valueOf(rplService.getServiceType()), 0, meta.getClusterId(),
                    DynamicApplicationConfig.getInt(ConfigKeys.RPL_DEFAULT_MEMORY) / 2);
            updateReplicaTaskConfig(rplTask, meta, true);
            defaultLogger.info("createTasks end, service: {}, task: {}", rplService.getId(), rplTask.getId());
        }
    }

    public static void createReplicaFullValidTask(RplService rplService, ReplicaMeta rplMeta) {
        defaultLogger.info("create replica full validation task start, service: {}", rplService.getId());
        try {
            RplTask rplTask =
                DbTaskMetaManager.addTask(rplService.getStateMachineId(), rplService.getId(), null, null, null,
                    ServiceType.REPLICA_FULL_VALIDATION, 0, rplMeta.getClusterId());
            defaultLogger.info("createTasks end, service: {}, task: {}", rplService.getId(), rplTask.getId());
            updateReplicaTaskConfig(rplTask, rplMeta, false);
        } catch (Throwable e) {
            defaultLogger.error("createTasks failed, service: {}", rplService.getId(), e);
        }
    }

    public static void resetReplicaFsm(RplStateMachine stateMachine) {
        RplStateMachine newStateMachine = new RplStateMachine();
        newStateMachine.setId(stateMachine.getId());
        newStateMachine.setState(FSMState.REPLICA_INIT.name());
        List<RplService> services = DbTaskMetaManager.listService(stateMachine.getId());
        ReplicaMeta replicaMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
        for (RplService service : services) {
            DbTaskMetaManager.updateServiceStatus(service.getId(), ServiceStatus.STOPPED);
            List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
            for (RplTask task : tasks) {
                DbTaskMetaManager.updateTaskStatus(task.getId(), TaskStatus.STOPPED);
                updateReplicaTaskConfig(task, replicaMeta, true);
            }
        }
        DbTaskMetaManager.updateStateMachine(newStateMachine);
    }

    public static void updateReplicaConfig(RplStateMachine stateMachine, ReplicaMeta replicaMeta) {
        defaultLogger.info("update rpl config start, statemachine: {}", stateMachine.getId());
        String config = JSON.toJSONString(replicaMeta);
        RplStateMachine newStateMachine = new RplStateMachine();
        newStateMachine.setId(stateMachine.getId());
        newStateMachine.setConfig(config);
        DbTaskMetaManager.updateStateMachine(newStateMachine);
    }

    // 由于meta和实际运行的position以及hostInfo不一定一致，用参数来控制是否采用meta里的数据
    public static void updateReplicaTaskConfig(RplTask rplTask, ReplicaMeta meta, boolean useMetaPosition) {
        String extractorConfigStr = "";
        String pipelineConfigStr = "";
        String applierConfigStr = "";
        Integer memory = null;
        defaultLogger.info("update rpl tasks start, task: {}", rplTask.getId());
        RplTaskConfig rplTaskConfig = DbTaskMetaManager.getTaskConfig(rplTask.getId());
        switch (ServiceType.valueOf(rplTask.getType())) {
        case REPLICA_FULL:
            FullExtractorConfig fullExtractorConfig = rplTaskConfig.getExtractorConfig() != null ?
                JSON.parseObject(rplTaskConfig.getExtractorConfig(), FullExtractorConfig.class) :
                new FullExtractorConfig();
            fullExtractorConfig.setExtractorType(ExtractorType.RPL_FULL);
            fullExtractorConfig.setFilterType(FilterType.RPL_FILTER);
            fullExtractorConfig.setPrivateMeta(JSON.toJSONString(meta));
            fullExtractorConfig.setHostInfo(getRplExtractorHostInfo(meta));

            extractorConfigStr = JSON.toJSONString(fullExtractorConfig);

            PipelineConfig fullPipelineConfig = rplTaskConfig.getPipelineConfig() != null ?
                JSON.parseObject(rplTaskConfig.getPipelineConfig(), PipelineConfig.class) : new PipelineConfig();
            pipelineConfigStr = JSON.toJSONString(fullPipelineConfig);

            ApplierConfig fullApplierConfig = rplTaskConfig.getApplierConfig() != null ?
                JSON.parseObject(rplTaskConfig.getApplierConfig(), ApplierConfig.class) : new ApplierConfig();
            Long writeServerId1 =
                StringUtils.isNotBlank(meta.getServerId()) ? Long.parseLong(meta.getServerId()) : null;
            fullApplierConfig.setHostInfo(getRplApplierHostInfo(writeServerId1));
            fullApplierConfig.setLogCommitLevel(RplConstants.LOG_NO_COMMIT);
            fullApplierConfig.setApplierType(ApplierType.FULL_COPY);
            applierConfigStr = JSON.toJSONString(fullApplierConfig);

            if (rplTaskConfig.getMemory() == null) {
                memory = RplConstants.DEFAULT_MEMORY_SIZE_FOR_FULL_COPY;
            }
            break;
        case REPLICA_INC:
            ExtractorConfig extractorConfig = rplTaskConfig.getExtractorConfig() != null ?
                JSON.parseObject(rplTaskConfig.getExtractorConfig(), ExtractorConfig.class) : new ExtractorConfig();
            extractorConfig.setExtractorType(ExtractorType.RPL_INC);
            extractorConfig.setFilterType(FilterType.RPL_FILTER);
            extractorConfig.setPrivateMeta(JSON.toJSONString(meta));
            extractorConfig.setHostInfo(getRplExtractorHostInfo(meta));
            extractorConfig.setEnableSrcLogicalMetaSnapshot(meta.isEnableSrcLogicalMetaSnapshot());
            extractorConfigStr = JSON.toJSONString(extractorConfig);

            PipelineConfig pipelineConfig = rplTaskConfig.getPipelineConfig() != null ?
                JSON.parseObject(rplTaskConfig.getPipelineConfig(), PipelineConfig.class) : new PipelineConfig();
            pipelineConfigStr = JSON.toJSONString(pipelineConfig);

            ApplierConfig applierConfig = rplTaskConfig.getApplierConfig() != null ?
                JSON.parseObject(rplTaskConfig.getApplierConfig(), ApplierConfig.class) : new ApplierConfig();
            Long writeServerId = StringUtils.isNotBlank(meta.getServerId()) ? Long.parseLong(meta.getServerId()) : null;
            applierConfig.setHostInfo(getRplApplierHostInfo(writeServerId));
            if (rplTaskConfig.getApplierConfig() == null) {
                applierConfig.setLogCommitLevel(RplConstants.LOG_ALL_COMMIT);
            }
            applierConfig.setEnableDdl(meta.isEnableDdl());
            applierConfig.setApplierType(meta.getApplierType());
            applierConfig.setCompareAll(meta.isCompareAll());
            applierConfig.setInsertOnUpdateMiss(meta.isInsertOnUpdateMiss());
            applierConfig.setConflictStrategy(meta.getConflictStrategy());
            applierConfigStr = JSON.toJSONString(applierConfig);

            if (rplTaskConfig.getMemory() == null) {
                memory = DynamicApplicationConfig.getInt(ConfigKeys.RPL_DEFAULT_MEMORY);
            }
            if (useMetaPosition) {
                DbTaskMetaManager.updateBinlogPosition(rplTask.getId(), meta.position);
            }
            break;
        case REPLICA_FULL_VALIDATION:
            ExtractorConfig rplFullValidExtractorConfig = rplTaskConfig.getExtractorConfig() != null ?
                JSON.parseObject(rplTaskConfig.getExtractorConfig(), ExtractorConfig.class) : new ExtractorConfig();
            rplFullValidExtractorConfig.setPrivateMeta(JSON.toJSONString(meta));
            rplFullValidExtractorConfig.setHostInfo(getRplExtractorHostInfo(meta));
            extractorConfigStr = JSON.toJSONString(rplFullValidExtractorConfig);

            ApplierConfig rplFullValidApplierConfig = rplTaskConfig.getApplierConfig() != null ?
                JSON.parseObject(rplTaskConfig.getApplierConfig(), ApplierConfig.class) : new ApplierConfig();
            Long serverId = StringUtils.isNotBlank(meta.getServerId()) ? Long.parseLong(meta.getServerId()) : null;
            rplFullValidApplierConfig.setHostInfo(getRplApplierHostInfo(serverId));
            applierConfigStr = JSON.toJSONString(rplFullValidApplierConfig);
            if (rplTaskConfig.getMemory() == null) {
                memory = DynamicApplicationConfig.getInt(ConfigKeys.RPL_DEFAULT_MEMORY);
            }
            if (useMetaPosition) {
                DbTaskMetaManager.updateBinlogPosition(rplTask.getId(), meta.position);
            }
            break;
        }

        DbTaskMetaManager.updateTaskConfig(rplTask.getId(), extractorConfigStr, pipelineConfigStr, applierConfigStr,
            memory);
    }

    public static void clearReplicaHistory(RplStateMachine stateMachine) {
        defaultLogger.info("clear rpl history start, statemachine: {}", stateMachine.getId());
        DbTaskMetaManager.deleteDbFullPositionByFSM(stateMachine.getId());
        DbTaskMetaManager.deleteDdlByFSM(stateMachine.getId());
        DbTaskMetaManager.deleteRplStatMetricsByFSM(stateMachine.getId());
        DbTaskMetaManager.deleteValidationTaskByFSM(stateMachine.getId());
        DbTaskMetaManager.deleteValidationDiffByFSM(stateMachine.getId());
        List<RplService> services = DbTaskMetaManager.listService(stateMachine.getId());
        for (RplService service : services) {
            FSMMetaManager.clearReplicaTasksHistory(service);
        }
        FSMMetaManager.resetReplicaFsm(stateMachine);
    }

    private static void clearReplicaTasksHistory(RplService rplService) {
        defaultLogger.info("clear history of rpl tasks start, service: {}", rplService.getId());
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(rplService.getId());
        for (RplTask task : tasks) {
            defaultLogger.info("clear history of rpl task start, task: {}", task.getId());
            DbTaskMetaManager.clearHistory(task.getId());
        }
    }

    private static void createImportTasks(RplService rplService, DataImportMeta meta) throws Throwable {
        String extractorConfigStr = "";
        String pipelineConfigStr = "";
        String applierConfigStr = "";
        defaultLogger.info("createTasks start, service: {}", rplService.getId());

        try {
            // extractor config
            for (int i = 0; i < meta.getMetaList().size(); ++i) {
                DataImportMeta.PhysicalMeta physicalMeta = meta.getMetaList().get(i);
                switch (ServiceType.valueOf(rplService.getServiceType())) {
                case FULL_COPY:
                    FullExtractorConfig fullExtractorConfig = new FullExtractorConfig();
                    fullExtractorConfig.setExtractorType(ExtractorType.DATA_IMPORT_FULL);
                    fullExtractorConfig.setFilterType(FilterType.IMPORT_FILTER);
                    fullExtractorConfig.setParallelCount(meta.getProducerParallelCount());
                    fullExtractorConfig.setFetchBatchSize(meta.getFetchBatchSize());
                    fullExtractorConfig.setPrivateMeta(JSON.toJSONString(physicalMeta));
                    fullExtractorConfig.setHostInfo(getImportExtractorHostInfo(physicalMeta));
                    extractorConfigStr = JSON.toJSONString(fullExtractorConfig);
                    break;
                case INC_COPY:
                    RdsExtractorConfig extractorConfig = new RdsExtractorConfig();
                    extractorConfig.setExtractorType(ExtractorType.DATA_IMPORT_INC);
                    extractorConfig.setFilterType(FilterType.IMPORT_FILTER);
                    extractorConfig.setPrivateMeta(JSON.toJSONString(physicalMeta));
                    extractorConfig.setHostInfo(getImportExtractorHostInfo(physicalMeta));
                    extractorConfig.setUid(physicalMeta.getRdsUid());
                    extractorConfig.setBid(physicalMeta.getRdsBid());
                    extractorConfig.setRdsInstanceId(physicalMeta.getRdsInstanceId());
                    extractorConfigStr = JSON.toJSONString(extractorConfig);
                    break;
                case FULL_VALIDATION:
                    ValidationExtractorConfig validationExtractorConfig = new ValidationExtractorConfig();
                    validationExtractorConfig.setExtractorType(ExtractorType.FULL_VALIDATION);
                    validationExtractorConfig.setFilterType(FilterType.NO_FILTER);
                    validationExtractorConfig.setParallelCount(meta.getProducerParallelCount());
                    validationExtractorConfig.setHostInfo(getValidationExtractorHostInfo(meta.getValidationMeta()));
                    validationExtractorConfig.setPrivateMeta(JSON.toJSONString(meta.getValidationMeta()));
                    extractorConfigStr = JSON.toJSONString(validationExtractorConfig);
                    break;
                case RECONCILIATION:
                    ReconExtractorConfig config = new ReconExtractorConfig();
                    config.setExtractorType(ExtractorType.RECONCILIATION);
                    config.setFilterType(FilterType.NO_FILTER);
                    config.setParallelCount(meta.getProducerParallelCount());
                    config.setHostInfo(getValidationExtractorHostInfo(meta.getValidationMeta()));
                    config.setPrivateMeta(JSON.toJSONString(meta.getValidationMeta()));
                    extractorConfigStr = JSON.toJSONString(config);
                    break;
                case CDC_INC:
                    DumperInfoMapper mapper = SpringContextHolder.getObject(DumperInfoMapper.class);
                    RetryTemplate template =
                        RetryTemplate.builder().maxAttempts(120).fixedBackoff(1000).retryOn(RetryableException.class)
                            .build();

                    DumperInfo info = template.execute((RetryCallback<DumperInfo, Throwable>) retryContext -> {
                        Optional<DumperInfo> dumperInfo =
                            mapper.selectOne(c -> c.where(DumperInfoDynamicSqlSupport.role, IsEqualTo.of(() -> "M")));
                        if (!dumperInfo.isPresent()) {
                            throw new RetryableException("dumper leader is not ready");
                        }
                        return dumperInfo.get();
                    }, retryContext -> null);

                    CdcExtractorConfig cdcExtractorConfig = new CdcExtractorConfig();
                    cdcExtractorConfig.setCdcServerIp(info.getIp());
                    cdcExtractorConfig.setCdcServerPort(info.getPort());
                    cdcExtractorConfig.setExtractorType(ExtractorType.CDC_INC);
                    cdcExtractorConfig.setFilterType(FilterType.IMPORT_FILTER);
                    cdcExtractorConfig.setPrivateMeta(JSON.toJSONString(meta.getBackFlowMeta()));
                    cdcExtractorConfig.setHostInfo(getImportApplierHostInfo(physicalMeta));
                    extractorConfigStr = JSON.toJSONString(cdcExtractorConfig);
                    break;
                default:
                    break;
                }

                // pipeline config
                PipelineConfig pipelineConfig = new PipelineConfig();
                pipelineConfig.setSupportXa(ServiceType.valueOf(rplService.getServiceType()) != ServiceType.CDC_INC);
                pipelineConfig.setFixedTpsLimit(physicalMeta.getFixedTpsLimit());
                pipelineConfig.setSkipException(physicalMeta.isSkipException());
                pipelineConfig.setBufferSize(meta.getRingBufferSize());
                pipelineConfig.setConsumerParallelCount(meta.getConsumerParallelCount());
                pipelineConfigStr = JSON.toJSONString(pipelineConfig);

                // applier config
                ApplierConfig applierConfig = new ApplierConfig();
                applierConfig.setApplierType(meta.getApplierType());
                // 评估升级默认关闭ddl支持
                applierConfig.setEnableDdl(false);

                // add crosscheck config
                ServiceType type = ServiceType.valueOf(rplService.getServiceType());
                if (type == ServiceType.CDC_INC || type == ServiceType.FULL_VALIDATION_CROSSCHECK
                    || type == ServiceType.RECONCILIATION_CROSSCHECK) {
                    applierConfig.setHostInfo(getBackflowApplierHostInfo(meta));
                    applierConfig.setLogCommitLevel(RplConstants.LOG_ALL_COMMIT);
                } else {
                    applierConfig.setHostInfo(getImportApplierHostInfo(physicalMeta));
                }
                if (type == ServiceType.INC_COPY) {
                    applierConfig.setLogCommitLevel(RplConstants.LOG_ALL_COMMIT);
                    applierConfig.setMergeBatchSize(meta.getIncMergeBatchSize());
                }
                if (type == ServiceType.FULL_COPY) {
                    applierConfig.setApplierType(ApplierType.FULL_COPY);
                    applierConfig.setMergeBatchSize(meta.getFullMergeBatchSize());
                }
                applierConfigStr = JSON.toJSONString(applierConfig);

                // memory
                int memory = DynamicApplicationConfig.getInt(ConfigKeys.RPL_DEFAULT_MEMORY);
                if (type == ServiceType.FULL_COPY) {
                    memory = RplConstants.DEFAULT_MEMORY_SIZE_FOR_FULL_COPY;
                }

                RplTask rplTask =
                    DbTaskMetaManager.addTaskWithMemory(rplService.getStateMachineId(), rplService.getId(),
                        extractorConfigStr, pipelineConfigStr, applierConfigStr,
                        ServiceType.valueOf(rplService.getServiceType()), i, meta.getCdcClusterId(), memory);
                defaultLogger.info("createTasks end, service: {}, task: {}", rplService.getId(), rplTask.getId());
                // 增量任务需要特殊处理，写入增量起始位点
                if (type == ServiceType.INC_COPY) {
                    String position = CommonUtil.createInitialBinlogPosition();
                    DbTaskMetaManager.updateBinlogPosition(rplTask.getId(), position);
                }

                if (type == ServiceType.CDC_INC
                    || type == ServiceType.FULL_VALIDATION
                    || type == ServiceType.RECONCILIATION
                    || type == ServiceType.FULL_VALIDATION_CROSSCHECK
                    || type == ServiceType.RECONCILIATION_CROSSCHECK) {
                    // one process/task per service
                    break;
                }
            }
        } catch (Throwable e) {
            defaultLogger.error("createTasks failed, service: {}", rplService.getId(), e);
            throw e;
        }
    }

    private static ResultCode<?> updateServiceStatus(RplService service, ServiceStatus serviceStatus,
                                                     TaskStatus taskStatus) {
        if (ServiceStatus.valueOf(service.getStatus()) != ServiceStatus.FINISHED) {
            DbTaskMetaManager.updateServiceStatus(service.getId(), serviceStatus);
        }
        List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
        if (tasks.isEmpty()) {
            return new ResultCode<>(RplConstants.FAILURE_CODE, "no task in this taskgroup", RplConstants.FAILURE);
        }
        for (RplTask task : tasks) {
            if (TaskStatus.valueOf(task.getStatus()) != TaskStatus.FINISHED) {
                DbTaskMetaManager.updateTaskStatus(task.getId(), taskStatus);
            }
        }
        return new ResultCode<>(RplConstants.SUCCESS_CODE, "success", RplConstants.SUCCESS);
    }

    public static void setTaskFinish(long taskId) {
        DbTaskMetaManager.updateTaskStatus(taskId, TaskStatus.FINISHED);
    }

    private static RplService checkAndUpdateServiceFinish(RplService service) {
        defaultLogger.info("checkService start, service: {}", service.getId());

        try {
            boolean allFinished = true;
            List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
            for (RplTask task : tasks) {
                TaskStatus status = TaskStatus.valueOf(task.getStatus());
                if (status != TaskStatus.FINISHED) {
                    defaultLogger.info("checkService, service: {} NOT allFinished, running task: {}", service.getId(),
                        task.getId());
                    allFinished = false;
                    break;
                }
            }

            if (allFinished) {
                defaultLogger.info("checkService, {} allFinished", service.getId());
                RplService newService = new RplService();
                newService.setId(service.getId());
                newService.setStatus(ServiceStatus.FINISHED.name());
                return DbTaskMetaManager.updateService(newService);
            }
        } catch (Throwable e) {
            defaultLogger.error("checkService failed", e);
        }

        defaultLogger.info("checkService end, service: {}", service.getId());
        return service;
    }

    private static HostInfo getImportApplierHostInfo(DataImportMeta.PhysicalMeta physicalMeta) {
        HostInfo info = new HostInfo(physicalMeta.getDstHost(), physicalMeta.getDstPort(), physicalMeta.getDstUser(),
            physicalMeta.getDstPassword(), "", physicalMeta.getDstType(), physicalMeta.getDstServerId());
        info.setUsePolarxPoolCN(true);
        return info;
    }

    private static HostInfo getRplApplierHostInfo(Long writeServerId) {
        HostInfo info = HostManager.getDstPolarxHost();
        if (writeServerId != null) {
            info.setServerId(writeServerId);
        }
        info.setUsePolarxPoolCN(true);
        return info;
    }

    private static HostInfo getRplExtractorHostInfo(ReplicaMeta replicaMeta) {
        HostInfo info =
            new HostInfo(replicaMeta.getMasterHost(), replicaMeta.getMasterPort(), replicaMeta.getMasterUser(),
                replicaMeta.getMasterPassword(), "", replicaMeta.getMasterType(), RplConstants.SERVER_ID_NULL);
        info.setUsePolarxPoolCN(false);
        return info;
    }

    private static HostInfo getRecoveryExtractorHostInfo(RecoveryMeta recoveryMeta) {
        return new HostInfo(recoveryMeta.getHost(), recoveryMeta.getPort(), recoveryMeta.getUser(),
            recoveryMeta.getPwd(), recoveryMeta.getSchema(), HostType.POLARX2, RplConstants.SERVER_ID_NULL);
    }

    private static HostInfo getImportExtractorHostInfo(DataImportMeta.PhysicalMeta physicalMeta) {
        return new HostInfo(physicalMeta.getSrcHost(), physicalMeta.getSrcPort(), physicalMeta.getSrcUser(),
            physicalMeta.getSrcPassword(), "", physicalMeta.getSrcType(), RplConstants.SERVER_ID_NULL);
    }

    private static HostInfo getValidationExtractorHostInfo(DataImportMeta.ValidationMeta validationMeta) {
        DataImportMeta.ConnInfo connInfo = validationMeta.getSrcLogicalConnInfo();
        return new HostInfo(connInfo.getHost(), connInfo.getPort(), connInfo.getUser(), connInfo.getPassword(), "",
            connInfo.getType(), RplConstants.SERVER_ID_NULL);
    }

    private static HostInfo getBackflowApplierHostInfo(DataImportMeta meta) {
        return new HostInfo(meta.getBackFlowMeta().getDstHost(), meta.getBackFlowMeta().getDstPort(),
            meta.getBackFlowMeta().getDstUser(), meta.getBackFlowMeta().getDstPassword(), "",
            meta.getBackFlowMeta().getDstType(), meta.getBackFlowMeta().getDstServerId());
    }

    //////////////////////////// For Daemon //////////////////////////////

    private static int computeValProgress(RplTask task) {
        if (TaskStatus.valueOf(task.getStatus()) == TaskStatus.FINISHED) {
            return RplConstants.FINISH_PERCENTAGE;
        }

        long fsmId = task.getStateMachineId();
        int totalCount = DbTaskMetaManager.countTotalValTask(fsmId, ValidationTypeEnum.FORWARD);
        if (totalCount == 0) {
            return 0;
        }
        int doneTask = DbTaskMetaManager.countDoneValTask(fsmId, ValidationTypeEnum.FORWARD);

        return (doneTask * 100) / totalCount;
    }

    private static int computeFullCopyPercentage(RplTask task) {
        if (TaskStatus.valueOf(task.getStatus()) == TaskStatus.FINISHED) {
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
}
