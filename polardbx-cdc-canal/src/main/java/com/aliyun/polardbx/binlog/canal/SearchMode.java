/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
