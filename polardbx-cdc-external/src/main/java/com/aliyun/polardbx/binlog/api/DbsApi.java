/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.api.dbs.CancelTaskRequest;
import com.aliyun.polardbx.binlog.api.dbs.CancelTaskResult;
import com.aliyun.polardbx.binlog.api.dbs.DescribeStorageInfoRequest;
import com.aliyun.polardbx.binlog.api.dbs.DescribeStorageInfoResult;
import com.aliyun.polardbx.binlog.api.dbs.DescribeTaskStatusRequest;
import com.aliyun.polardbx.binlog.api.dbs.DescribeTaskStatusResult;
import com.aliyun.polardbx.binlog.api.dbs.DescribeUnifyArchiveLogFilesRequest;
import com.aliyun.polardbx.binlog.api.dbs.DescribeUnifyArchiveLogFilesResult;
import com.aliyun.polardbx.binlog.api.dbs.RdsDownloadForRestoreRequest;
import com.aliyun.polardbx.binlog.api.dbs.RdsDownloadForRestoreResult;

public class DbsApi {
    public static DescribeUnifyArchiveLogFilesResult describeUnifyArchiveLogFiles(
        String dbInstanceName,
        String uid,
        String user_id,
        long startTime,
        long endTime,
        Integer maxRecordsPerPage,
        Integer pageNumbers) throws Exception {
        String apiUrl = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_URL);
        String apiAk = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_ACCESS_ID);
        String apiSk = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_ACCESS_KEY);
        String regionCode = DynamicApplicationConfig.getString(ConfigKeys.DBS_REGION_CODE);
        DescribeUnifyArchiveLogFilesRequest request = new DescribeUnifyArchiveLogFilesRequest();
        request.setInstanceName(dbInstanceName);
        request.setUserId(uid);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setCallerBid(user_id);
        request.setPageSize(maxRecordsPerPage);
        request.setPageNumber(pageNumbers);
        request.setAccessKey(apiAk);
        request.setAccessSecretKey(apiSk);
        request.setEndPoint(apiUrl);
        request.setRegionCode(regionCode);
        return request.doRequest();
    }

    /**
     * 提交大binlog下载任务
     * 会提交给DBS进行下载，返回下载任务ID
     * @param dbInstanceName
     * @param uid
     * @param user_id
     * @param archiveLogId 日志文件ID/批量传参，按逗号分割：12345,2345,3456
     * @param archiveLogPath 宿主机上的目录路径
     * @return
     */
    public static RdsDownloadForRestoreResult submitDownloadTask(String dbInstanceName, String uid, String user_id, String archiveLogId, String archiveLogPath){
        String apiUrl = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_URL);
        String apiAk = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_ACCESS_ID);
        String apiSk = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_ACCESS_KEY);
        String regionCode = DynamicApplicationConfig.getString(ConfigKeys.DBS_REGION_CODE);
        Integer HostInsId = DynamicApplicationConfig.getInt(ConfigKeys.INST_ID);
        RdsDownloadForRestoreRequest request = new RdsDownloadForRestoreRequest();
        request.setInstanceName(dbInstanceName);
        request.setUserId(uid);
        request.setCallerBid(user_id);
        request.setAccessKey(apiAk);
        request.setAccessSecretKey(apiSk);
        request.setEndPoint(apiUrl);
        request.setRegionCode(regionCode);
        request.setArchiveLogId(archiveLogId);
        request.setArchiveLogLocalFolder(archiveLogPath);
        request.setHostInsId(HostInsId);
        return  request.doRequest();
    }

    /**
     * 查询binlog下载任务状态
     * @param dbInstanceName
     * @param taskId
     * @return
     */
    public static DescribeTaskStatusResult describeTaskStatus(String dbInstanceName, String uid, String user_id, String taskId) {
        String apiUrl = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_URL);
        String apiAk = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_ACCESS_ID);
        String apiSk = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_ACCESS_KEY);
        String regionCode = DynamicApplicationConfig.getString(ConfigKeys.DBS_REGION_CODE);
        DescribeTaskStatusRequest request = new DescribeTaskStatusRequest();
        request.setInstanceName(dbInstanceName);
        request.setAccessKey(apiAk);
        request.setAccessSecretKey(apiSk);
        request.setEndPoint(apiUrl);
        request.setRegionCode(regionCode);
        request.setTaskId(taskId);
        request.setUserId(uid);
        request.setCallerBid(user_id);
        return request.doRequest();
    }

    public static CancelTaskResult cancelTask(String dbInstanceName, String uid, String user_id, String taskId) {
        String apiUrl = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_URL);
        String apiAk = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_ACCESS_ID);
        String apiSk = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_ACCESS_KEY);
        String regionCode = DynamicApplicationConfig.getString(ConfigKeys.DBS_REGION_CODE);
        CancelTaskRequest request = new CancelTaskRequest();
        request.setInstanceName(dbInstanceName);
        request.setAccessKey(apiAk);
        request.setAccessSecretKey(apiSk);
        request.setEndPoint(apiUrl);
        request.setRegionCode(regionCode);
        request.setTaskId(taskId);
        request.setUserId(uid);
        request.setCallerBid(user_id);
        return request.doRequest();
    }


    public static DescribeStorageInfoResult describeStorageInfo(String storageEntityId, String uid, String user_id) {
        String apiUrl = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_URL);
        String apiAk = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_ACCESS_ID);
        String apiSk = DynamicApplicationConfig.getString(ConfigKeys.DBS_API_ACCESS_KEY);
        String regionCode = DynamicApplicationConfig.getString(ConfigKeys.DBS_REGION_CODE);
        DescribeStorageInfoRequest request = new DescribeStorageInfoRequest();
        request.setStorageEntityId(storageEntityId);
        request.setUid(uid);
        request.setCallerBid(user_id);
        request.setAccessKey(apiAk);
        request.setAccessSecretKey(apiSk);
        request.setEndPoint(apiUrl);
        request.setRegion(regionCode);
        return request.doRequest();
    }

}
