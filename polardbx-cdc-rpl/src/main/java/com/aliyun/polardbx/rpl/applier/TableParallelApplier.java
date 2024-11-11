/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

/**
 * @since 5.0.0.0
 */

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class TableParallelApplier extends MysqlApplier {

    public TableParallelApplier(ApplierConfig applierConfig, HostInfo hostInfo, HostInfo srcHostInfo) {
        super(applierConfig, hostInfo, srcHostInfo);
    }

    @Override
    protected void dmlApply(List<DBMSEvent> dbmsEvents) {
        Map<String, List<DefaultRowChange>> parallelRowChanges = new HashMap<>();
        for (DBMSEvent event : dbmsEvents) {
            DefaultRowChange rowChange = (DefaultRowChange) event;
            String fullTableName = rowChange.getSchema() + "." + rowChange.getTable();
            parallelRowChanges.putIfAbsent(fullTableName, new ArrayList<>());
            parallelRowChanges.get(fullTableName).add(rowChange);
        }
        List<Future<Void>> futures = new ArrayList<>();
        for (List<DefaultRowChange> rowChanges : parallelRowChanges.values()) {
            Callable<Void> task = () -> {
                DataSource dataSource = dbMetaCache.getDataSource(rowChanges.get(0).getSchema());
                try (Connection conn = dataSource.getConnection()) {
                    DmlApplyHelper.executeDML(conn, rowChanges, conflictStrategy);
                }
                return null;
            };
            futures.add(executorService.submit(task));
        }
        PolardbxException exception = CommonUtil.waitAllTaskFinishedAndReturn(futures);
        if (exception != null) {
            throw exception;
        }
    }
}
