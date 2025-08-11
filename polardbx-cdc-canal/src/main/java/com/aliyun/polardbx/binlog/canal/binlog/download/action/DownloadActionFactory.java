/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download.action;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;

public class DownloadActionFactory {
    public static IDownloadAction create() {
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS)){
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH)){
                return new GarethAction();
            }else {
                return new DbsTaskAction();
            }
        }else {
            return new HttpAction();
        }
    }
}
