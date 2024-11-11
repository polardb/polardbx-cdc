/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.rpl.common.CommonUtil;
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

    protected final ApplierConfig applierConfig;

    public BaseApplier(ApplierConfig applierConfig) {
        this.applierConfig = applierConfig;
    }

    public void init() throws Exception {
    }

    public void apply(List<DBMSEvent> dbmsEvents) throws Exception {
    }

    public void tranApply(List<Transaction> transactions) throws Exception {
    }

    public void applyDdlSql(String sql, String schema) throws Exception {
    }

    public void logCommitInfo(List<DBMSEvent> dbmsEvents) {
        List<String> logs = new ArrayList<>();
        if (applierConfig.getLogCommitLevel() == RplConstants.LOG_ALL_COMMIT) {
            for (DBMSEvent event : dbmsEvents) {
                logs.addAll(LogUtil.generateCommitLog(event, null));
            }
        } else if (!dbmsEvents.isEmpty() && applierConfig.getLogCommitLevel() == RplConstants.LOG_END_COMMIT) {
            logs.addAll(LogUtil.generateCommitLog(dbmsEvents.get(dbmsEvents.size() - 1), null));
        }
        LogUtil.writeBatchLogs(logs, LogUtil.getCommitLogger());
    }

    public void logTransactionCommit() {
        LogUtil.getCommitLogger().info("{} : COMMIT", CommonUtil.getCurrentTime());
    }

    public void logTransactionRollback() {
        LogUtil.getCommitLogger().info("{} : ROLLBACK", CommonUtil.getCurrentTime());
    }

    public void start() {
    }

    public void stop() {
    }
}
