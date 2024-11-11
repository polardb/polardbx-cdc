/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.enums.BinlogBackupType;
import com.aliyun.polardbx.binlog.enums.ClusterType;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_TYPE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_RECOVER_FORCE_QUICK_SEARCH_WHEN_LOSS_BACKUP;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getClusterType;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * created by ziyang.lb
 **/
public class SearchMode {

    public static boolean isSearchInQuickMode() {
        boolean forceQuickModeWhenLossBackup = getBoolean(TASK_RECOVER_FORCE_QUICK_SEARCH_WHEN_LOSS_BACKUP);
        if (isLossBackup() && forceQuickModeWhenLossBackup) {
            return true;
        } else {
            return getBoolean(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE);
        }
    }

    private static boolean isLossBackup() {
        String clusterType = getClusterType();
        String backupType = getString(BINLOG_BACKUP_TYPE);
        BinlogBackupType backupTypeEnum = BinlogBackupType.typeOf(backupType);
        return ClusterType.BINLOG_X.name().equals(clusterType) && backupTypeEnum == BinlogBackupType.NULL;
    }
}
