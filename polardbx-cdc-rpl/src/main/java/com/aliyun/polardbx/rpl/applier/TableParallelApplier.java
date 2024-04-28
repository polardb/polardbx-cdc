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
