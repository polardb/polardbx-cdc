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
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;

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
public class SplitTransactionApplier extends MysqlApplier {
    public SplitTransactionApplier(ApplierConfig applierConfig, HostInfo hostInfo) {
        super(applierConfig, hostInfo);
    }

    @Override
    protected boolean dmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        if (dbmsEvents == null || dbmsEvents.size() == 0) {
            return true;
        }

        Map<String, List<DefaultRowChange>> splitRowChanges = splitByTable(dbmsEvents);
        return parallelExecSqlContexts(splitRowChanges, true);
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

    protected boolean parallelExecSqlContexts(Map<String, List<DefaultRowChange>> allRowChanges, boolean tbTranExec)
        throws Throwable {
        boolean res = true;
        List<Future<Boolean>> futures = new ArrayList<>();

        for (String fullTbName : allRowChanges.keySet()) {
            List<DefaultRowChange> tbRowChanges = allRowChanges.get(fullTbName);
            List<SqlContext> tbSqlContexts = new ArrayList<>();

            for (DefaultRowChange rowChange : tbRowChanges) {
                tbSqlContexts.addAll(getSqlContexts(rowChange, safeMode));
            }

            // execute
            final Callable<Boolean> task;
            if (tbTranExec) {
                task = () -> tranExecSqlContexts(tbSqlContexts);
            } else {
                task = () -> execSqlContexts(tbSqlContexts);
            }
            futures.add(executorService.submit(task));
        }

        for (Future<Boolean> future : futures) {
            res &= future.get();
        }

        return res;
    }
}
