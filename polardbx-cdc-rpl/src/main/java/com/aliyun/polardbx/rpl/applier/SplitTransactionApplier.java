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

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author shicai.xsc 2021/5/18 14:19
 * @since 5.0.0.0
 */
@Slf4j
public class SplitTransactionApplier extends MysqlApplier {
    public SplitTransactionApplier(ApplierConfig applierConfig, HostInfo hostInfo, HostInfo srcHostInfo) {
        super(applierConfig, hostInfo, srcHostInfo);
    }

    @Override
    protected void dmlApply(List<DBMSEvent> dbmsEvents) throws Exception {
        if (dbmsEvents == null || dbmsEvents.isEmpty()) {
            return;
        }

        Map<String, List<DefaultRowChange>> splitRowChanges = splitByTable(dbmsEvents);
        parallelExecSqlContexts(splitRowChanges, true);
    }

    private Map<String, List<DefaultRowChange>> splitByTable(List<DBMSEvent> dbmsEvents) {
        Map<String, List<DefaultRowChange>> splitRowChanges = new HashMap<>();

        for (DBMSEvent event : dbmsEvents) {
            DefaultRowChange rowChange = (DefaultRowChange) event;
            String fullTbName = rowChange.getSchema() + "." + rowChange.getTable();
            if (splitRowChanges.containsKey(fullTbName)) {
                splitRowChanges.get(fullTbName).add(rowChange);
            } else {
                List<DefaultRowChange> tbRowChanges = new ArrayList<>();
                tbRowChanges.add(rowChange);
                splitRowChanges.put(fullTbName, tbRowChanges);
            }
        }

        return splitRowChanges;
    }

    protected void parallelExecSqlContexts(Map<String, List<DefaultRowChange>> allRowChanges, boolean tbTranExec)
        throws Exception {
        List<Future<Void>> futures = new ArrayList<>();

        for (String fullTbName : allRowChanges.keySet()) {
            List<DefaultRowChange> tbRowChanges = allRowChanges.get(fullTbName);
            TableInfo tableInfo = dbMetaCache.getTableInfo(
                tbRowChanges.get(0).getSchema(), tbRowChanges.get(0).getTable());

            // execute
            final Callable<Void> task;
            if (tbTranExec && TableInfo.ENGINE_TYPE_INNODB.equalsIgnoreCase(tableInfo.getEngine())) {
                task = () -> {
                    DataSource dataSource = dbMetaCache.getDataSource(tbRowChanges.get(0).getSchema());
                    try (Connection conn = dataSource.getConnection()) {
                        conn.setAutoCommit(false);
                        try {
                            DmlApplyHelper.executeDML(conn, tbRowChanges, conflictStrategy);
                            conn.commit();
                        } catch (Exception e) {
                            conn.rollback();
                            throw e;
                        }
                    }
                    return null;
                };
            } else {
                if (tbTranExec && !TableInfo.ENGINE_TYPE_INNODB.equalsIgnoreCase(tableInfo.getEngine())) {
                    log.warn("table engine is not InnoDB, skip transaction execute,  {}:{}", fullTbName,
                        tableInfo.getEngine());
                }

                task = () -> {
                    DataSource dataSource = dbMetaCache.getDataSource(tbRowChanges.get(0).getSchema());
                    try (Connection conn = dataSource.getConnection()) {
                        DmlApplyHelper.executeDML(conn, tbRowChanges, conflictStrategy);
                    }
                    return null;
                };
            }
            futures.add(executorService.submit(task));
        }
        PolardbxException exception = CommonUtil.waitAllTaskFinishedAndReturn(futures);
        if (exception != null) {
            throw exception;
        }
    }
}
