/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download.action;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.api.DbsApi;
import com.aliyun.polardbx.binlog.api.dbs.DescribeStorageInfoResult;
import com.aliyun.polardbx.binlog.api.dbs.gareth.GarethActionFactory;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.util.Shell;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;

@Slf4j
public class GarethAction implements IDownloadAction{
    @Override
    public void exec(String storageInstanceId, String localPath, BinlogFile binlogFile) throws Exception {
        String garethConfig = DynamicApplicationConfig.getString(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_CONFIG);
        String type = DynamicApplicationConfig.getString(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_POOL_TYPE);;
        if (StringUtils.isBlank(garethConfig)){
            DescribeStorageInfoResult
                result = DbsApi.describeStorageInfo(binlogFile.getStorageEntityId(), DynamicApplicationConfig.getString(ConfigKeys.RDS_UID), DynamicApplicationConfig.getString(ConfigKeys.RDS_BID));
            type = result.getData().getType();
            garethConfig = result.getDataJson();
        }
        GarethActionFactory.create(type, garethConfig).download(localPath, binlogFile.getDownloadLink(), new File(localPath).getParent());
        String rootPath = DynamicApplicationConfig.getString(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR);
        String res = Shell.execCommand("sudo", "chown", "-R", "admin:admin", rootPath);
        log.info("chown for gareth path : {}, res {}",rootPath,  res);
    }
}
