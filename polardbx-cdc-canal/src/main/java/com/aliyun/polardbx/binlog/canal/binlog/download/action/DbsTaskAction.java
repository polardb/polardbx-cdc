/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download.action;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.api.DbsApi;
import com.aliyun.polardbx.binlog.api.dbs.DescribeTaskStatusResult;
import com.aliyun.polardbx.binlog.api.dbs.RdsDownloadForRestoreResult;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.Shell;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_BID;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_UID;

public class DbsTaskAction implements IDownloadAction{

    private static final Logger logger = LoggerFactory.getLogger(DbsTaskAction.class);
    @Override
    public void exec(String storageInstanceId, String localFilePath, BinlogFile binlogFile) throws Exception {
        String uid = DynamicApplicationConfig.getString(RDS_UID);
        String bid = DynamicApplicationConfig.getString(RDS_BID);
        File distFile = new File(localFilePath);
        String parent = distFile.getParent()+File.separator+"gareth";
        RdsDownloadForRestoreResult
            result = DbsApi.submitDownloadTask(storageInstanceId, uid, bid, binlogFile.getArchiveLogId(), parent);
        String taskId = result.getData().getTaskId();
        if (!StringUtils.equalsIgnoreCase("OK", result.getData().getStatus())){
            throw new PolardbxException("download file by gareth failed!");
        }
        long interval = TimeUnit.SECONDS.toNanos(4);
        while (true){
            LockSupport.parkNanos(interval);
            if (Thread.currentThread().isInterrupted()){
                throw new PolardbxException("download thread occur interrupted exception");
            }
            DescribeTaskStatusResult taskStatusResult = DbsApi.describeTaskStatus(storageInstanceId, uid, bid, taskId);
            logger.info("download file {} , taskId : {}, progress : {}, status: {}", binlogFile.getLogname(), taskId, taskStatusResult.getData().getProgress(), taskStatusResult.getData().getStatus());
            if (StringUtils.equalsIgnoreCase("Failed", taskStatusResult.getData().getStatus())){
                DbsApi.cancelTask(storageInstanceId, uid, bid, taskId);
                throw new PolardbxException("download file "+binlogFile.getLogname()+" failed by dbs gareth!");
            }
            if (taskStatusResult.getData().getProgress() == 100){
                File file = new File(parent+File.separator+binlogFile.getLogname());
                String path = DynamicApplicationConfig.getString(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR);
                String res = Shell.execCommand("sudo", "chown", "-R", "admin:admin", path);
                logger.info("chown for gareth file, res {}", res);
                FileUtils.moveFile(file, distFile);
                break;
            }
        }
    }
}
