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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * @author shicai.xsc 2021/2/18 21:45
 * @since 5.0.0.0
 */
@Data
@Slf4j
public class BaseApplier {

    protected ApplierConfig applierConfig;

    protected boolean skipAllException;

    protected boolean safeMode;

    public BaseApplier(ApplierConfig applierConfig) {
        this.applierConfig = applierConfig;
    }

    public boolean init() {
        return true;
    }

    public boolean apply(List<DBMSEvent> dbmsEvents) {
        return true;
    }

    public boolean tranApply(List<Transaction> transactions) {
        return true;
    }

    public void logCommitInfo(List<DBMSEvent> dbmsEvents) {
        List<String> logs = new ArrayList<>();
        if (applierConfig.getLogCommitLevel() == RplConstants.LOG_ALL_COMMIT) {
            for (DBMSEvent event : dbmsEvents) {
                logs.addAll(LogUtil.generateCommitLog(event, null));
            }
        } else if (dbmsEvents.size() > 0 && applierConfig.getLogCommitLevel() == RplConstants.LOG_END_COMMIT) {
            logs.addAll(LogUtil.generateCommitLog(dbmsEvents.get(dbmsEvents.size() - 1), null));
        }

        LogUtil.writeBatchLogs(logs, LogUtil.getCommitLogger());
    }

}
