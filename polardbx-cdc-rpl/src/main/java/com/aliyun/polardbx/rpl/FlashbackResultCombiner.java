/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.enums.BinlogBackupType;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.util.LoopRetry;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryCombineConfig;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryCombineTaskResultMeta;
import com.aliyun.polardbx.rpl.taskmeta.RecoverySearchTaskResultMeta;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryStateMachineContext;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.taskmeta.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.enums.BinlogBackupType.LINDORM;
import static com.aliyun.polardbx.binlog.enums.BinlogBackupType.OSS;
import static com.aliyun.polardbx.binlog.ConfigKeys.FLASHBACK_DOWNLOAD_LINK_PRESERVE_SECOND;
import static com.aliyun.polardbx.binlog.ConfigKeys.FLASHBACK_UPLOAD_MULTI_MODE_THRESHOLD;
import static com.aliyun.polardbx.rpl.common.RplConstants.FLASH_BACK_COMBINE_RESULT_FILE;

/**
 * @author yudong
 */
@Slf4j
public class FlashbackResultCombiner {

    private final long taskId;

    private final RplTaskConfig taskConfig;

    private final RplTask rplTask;

    private final RecoveryStateMachineContext stateMachineContext;

    private final RecoveryCombineConfig combineConfig;

    private final String resultDir;

    private final String resultFile;

    private final long expireTimeInSec;

    private final RecoverySearchTaskResultMeta allTaskResultMeta;

    private RecoveryCombineTaskResultMeta previousCombineResultMeta;

    public FlashbackResultCombiner(long taskId) {
        this.taskId = taskId;
        this.rplTask = DbTaskMetaManager.getTask(taskId);
        this.taskConfig = DbTaskMetaManager.getTaskConfig(rplTask.getId());
        this.stateMachineContext = new RecoveryStateMachineContext();
        this.combineConfig = JSON.parseObject(taskConfig.getApplierConfig(), RecoveryCombineConfig.class);
        this.resultDir = MessageFormat.format(RplConstants.FLASH_BACK_RESULT_DIR, combineConfig.getRandomUUID());
        this.resultFile = MessageFormat.format(FLASH_BACK_COMBINE_RESULT_FILE, combineConfig.getRandomUUID());
        this.expireTimeInSec = DynamicApplicationConfig.getLong(FLASHBACK_DOWNLOAD_LINK_PRESERVE_SECOND);
        this.allTaskResultMeta = buildSearchTaskResultMeta();
    }

    public void run() {
        RplTask task = DbTaskMetaManager.getTask(taskId);
        if (TaskStatus.valueOf(task.getStatus()) == TaskStatus.FINISHED) {
            log.info("task {} has already finished, skip execute.", taskId);
            throw new PolardbxException("task has already finished, skip execute");
        }

        if (StringUtils.isNotBlank(task.getExtra()) && task.getExtra().contains("injectTroubleCount")) {
            previousCombineResultMeta = JSONObject.parseObject(task.getExtra(), RecoveryCombineTaskResultMeta.class);
        }

        configBackupStorage();
        LoopRetry loopRetry = new LoopRetry(new LoopRetry.SleepIntervalStrategy(500)) {
            @Override
            public boolean retry() {
                try {
                    mergeResult();
                } catch (Exception e) {
                    log.error("mergeResult error, will retry", e);
                    return false;
                }
                return true;
            }
        };
        if (!loopRetry.loop(new AtomicInteger(10))) {
            StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.RPL_FLASHBACK_ERROR,
                TaskContext.getInstance().getTaskId(), "mergeResult failed!");
            throw new RuntimeException("mergeResult failed!");
        }

        recordTaskExecuteInfo();
        tryInjectTrouble();
        recordStateMachineExecuteInfo();
        FSMMetaManager.setTaskFinish(taskId);
    }

    private void configBackupStorage() {
        String backupType = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_BACKUP_TYPE);
        BinlogBackupType backupTypeEnum = BinlogBackupType.typeOf(backupType);
        if (backupTypeEnum != null) {
            if (backupTypeEnum == OSS) {
                stateMachineContext.setFileStorageType(OSS.name());
            } else if (backupTypeEnum == LINDORM) {
                stateMachineContext.setFileStorageType(LINDORM.name());
            }
        } else {
            throw new PolardbxException("backup type should not be null");
        }
    }

    private void mergeResult() {
        RemoteBinlogProxy.getInstance().deleteAll(resultFile);
        if (RemoteBinlogProxy.getInstance().isObjectsExistForPrefix(resultFile)) {
            throw new PolardbxException("found dirty result file task " + taskId + " with filename " + resultFile);
        }

        upload();
    }

    private void upload() {
        List<String> objectList = RemoteBinlogProxy.getInstance().listFiles(resultDir + "result");
        Collections.sort(objectList, (o1, o2) -> {
            if (combineConfig.isMirror()) {
                return o1.compareTo(o2);
            }
            return o2.compareTo(o1);
        });

        log.info("Intermediate file: " + objectList);
        long totalSize = 0;
        for (String objectName : objectList) {
            checkMd5(objectName);
            StatisticalProxy.getInstance().heartbeat();
            totalSize += getFileSize(objectName);
        }
        log.info("total size for all files is " + totalSize);

        long multiModeThreshold = DynamicApplicationConfig.getLong(FLASHBACK_UPLOAD_MULTI_MODE_THRESHOLD);
        if (totalSize < multiModeThreshold) {
            uploadInSingeMode(objectList);
        } else {
            uploadInMultiMode(objectList, totalSize);
        }
    }

    private void uploadInSingeMode(List<String> objectList) {
        Appender appender = RemoteBinlogProxy.getInstance().providerAppender(resultFile);

        log.info("Start to merge in single mode.");
        appender.begin();
        for (String objectName : objectList) {
            checkMd5(objectName);
            StatisticalProxy.getInstance().heartbeat();
            byte[] data = RemoteBinlogProxy.getInstance().getFileData(resultDir + objectName);
            appender.append(data, data.length);
            log.info("file is successfully merged : " + objectName);
        }
        appender.end();
        log.info("Merge finished in single mode.");
    }

    private void uploadInMultiMode(List<String> objectList, long totalSize) {
        log.info("Start to merge in Multi mode.");
        Appender multiAppender = RemoteBinlogProxy.getInstance().providerMultiAppender(resultFile, totalSize);

        multiAppender.begin();
        for (String objectName : objectList) {
            checkMd5(objectName);
            StatisticalProxy.getInstance().heartbeat();
            byte[] data = RemoteBinlogProxy.getInstance().getFileData(resultDir + objectName);
            multiAppender.append(data, data.length);
            log.info("file is successfully merged : " + objectName);
        }
        multiAppender.end();

        log.info("Merge finished in Multi mode.");
    }

    private void recordStateMachineExecuteInfo() {
        stateMachineContext.setSqlCounter(allTaskResultMeta.getSqlCounter());
        stateMachineContext.setFileDirectory(resultDir);
        if (allTaskResultMeta.getSqlCounter() > 0) {
            stateMachineContext.setDownloadUrl(RemoteBinlogProxy.getInstance()
                .prepareDownLink(resultFile, expireTimeInSec));
        }
        stateMachineContext.setExpireTime(new Date(System.currentTimeMillis() + expireTimeInSec * 1000));

        long stateMachineId = rplTask.getStateMachineId();
        String context = JSON.toJSONString(stateMachineContext);
        DbTaskMetaManager.updateStateMachineContext(stateMachineId, context);
    }

    private void tryInjectTrouble() {
        if (combineConfig.isInjectTrouble()) {
            if (previousCombineResultMeta == null || previousCombineResultMeta.getInjectTroubleCount() < 3) {
                log.info("trigger trouble for task " + taskId);
                throw new PolardbxException("trouble inject for task " + taskId);
            }
        }
    }

    private void checkMd5(String fileName) {
        if (!allTaskResultMeta.getFileMd5Map().containsKey(fileName)) {
            throw new PolardbxException("md5 not found for file " + fileName);
        }
        String md5Old = allTaskResultMeta.getFileMd5Map().get(fileName);
        String md5Latest = RemoteBinlogProxy.getInstance().getMd5(resultDir + fileName);
        if (!StringUtils.equals(md5Old, md5Latest)) {
            throw new PolardbxException("md5 is mismatch, old is " + md5Old + ", latest is " + md5Latest);
        }
    }

    private Long getFileSize(String fileName) {
        if (!allTaskResultMeta.getFileSizeMap().containsKey(fileName)) {
            throw new PolardbxException("file size not found for file " + fileName);
        }
        long fileSizeOld = allTaskResultMeta.getFileSizeMap().get(fileName);
        long fileSizeLatest = RemoteBinlogProxy.getInstance().getSize(resultDir + fileName);
        if (fileSizeOld != fileSizeLatest) {
            throw new PolardbxException(
                "fileSize is mismatch, old is " + fileSizeOld + ", latest is " + fileSizeLatest);
        }
        return fileSizeLatest;
    }

    private void recordTaskExecuteInfo() {
        RecoveryCombineTaskResultMeta resultMeta = new RecoveryCombineTaskResultMeta();
        resultMeta.setInjectTroubleCount(previousCombineResultMeta != null ?
            previousCombineResultMeta.getInjectTroubleCount() + 1 : 0);
        DbTaskMetaManager.updateExtra(taskId, JSONObject.toJSONString(resultMeta));
    }

    private RecoverySearchTaskResultMeta buildSearchTaskResultMeta() {
        long statemachineId = rplTask.getStateMachineId();
        RplService rplService = DbTaskMetaManager.getService(statemachineId, ServiceType.REC_SEARCH);
        long serviceId = rplService.getId();
        List<RplTask> taskList = DbTaskMetaManager.listTaskByService(serviceId);

        RecoverySearchTaskResultMeta resultMeta = new RecoverySearchTaskResultMeta();
        taskList.forEach(t -> {
            String extra = t.getExtra();
            RecoverySearchTaskResultMeta oneMeta = JSONObject.parseObject(extra, RecoverySearchTaskResultMeta.class);
            resultMeta.setSqlCounter(resultMeta.getSqlCounter() + oneMeta.getSqlCounter());
            resultMeta.getFileMd5Map().putAll(oneMeta.getFileMd5Map());
            resultMeta.getFileSizeMap().putAll(oneMeta.getFileSizeMap());
        });

        return resultMeta;
    }
}
