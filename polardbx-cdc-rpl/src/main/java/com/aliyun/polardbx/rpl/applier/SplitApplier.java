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

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author shicai.xsc 2021/5/24 11:42
 * @since 5.0.0.0
 */
@Slf4j
public class SplitApplier extends SplitTransactionApplier {

    public SplitApplier(ApplierConfig applierConfig, HostInfo hostInfo) {
        super(applierConfig, hostInfo);
    }

    @Override
    protected boolean dmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        if (dbmsEvents == null || dbmsEvents.size() == 0) {
            return true;
        }

        Map<String, Map<RowKey, List<DefaultRowChange>>> allSplitRowChanges = new HashMap<>();
        Map<String, List<DefaultRowChange>> allSerialRowChanges = new HashMap<>();
        Set<String> changedIdentifyColumnTables = new HashSet<>();

        split(dbmsEvents, allSplitRowChanges, allSerialRowChanges, changedIdentifyColumnTables);

        int allSerialRowChangeCount = 0;
        for (String fullTbName : allSerialRowChanges.keySet()) {
            allSerialRowChangeCount += allSerialRowChanges.get(fullTbName).size();
        }

        // 对于 allSplitRowChanges，分成多个队列并行执行
        int avgQueueSize = (dbmsEvents.size() - allSerialRowChangeCount) / applierConfig.getMaxPoolSize();
        Map<String, List<DefaultRowChange>> allQueues = new HashMap<>();
        List<DefaultRowChange> curQueue = null;
        int queueIndex = 0;

        for (String fullTbName : allSplitRowChanges.keySet()) {
            Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges = allSplitRowChanges.get(fullTbName);
            // 保证同一个表 a.a 的同一个 identify key 的 rowChanges 在同一个 queue 内，且按照顺序执行
            // 同一个表 a.a 的不同 identify key 的 rowChanges 不必在同一个 queue 内
            for (List<DefaultRowChange> rowChanges : tbSplitRowChanges.values()) {
                if (curQueue == null || curQueue.size() >= avgQueueSize) {
                    curQueue = new ArrayList<>();
                    String fakeTbName = String.valueOf(queueIndex);
                    allQueues.put(fakeTbName, curQueue);
                    queueIndex++;
                }

                curQueue.addAll(rowChanges);
            }
        }

        // 多个队列并行执行，每个队列内部不需要事务
        boolean res = parallelExecSqlContexts(allQueues, false);
        if (!res) {
            return res;
        }

        // allSerialRowChanges 并行执行，每个队列内需要事务
        for (String fullTbName : allSerialRowChanges.keySet()) {
            log.info("{} changes will be executed by SplitTransactionApplier, rowChanges: {}", fullTbName,
                allSerialRowChanges.get(fullTbName).size());
        }
        return parallelExecSqlContexts(allSerialRowChanges, true);
    }

    protected void split(List<DBMSEvent> dbmsEvents,
                         Map<String, Map<RowKey, List<DefaultRowChange>>> allSplitRowChanges,
                         Map<String, List<DefaultRowChange>> allSerialRowChanges,
                         Set<String> changedIdentifyColumnTables) throws Throwable {
        Map<String, List<Integer>> allTbIdentifyColumns = new HashMap<>();

        for (DBMSEvent event : dbmsEvents) {
            DefaultRowChange rowChange = (DefaultRowChange) event;
            String fullTbName = rowChange.getSchema() + "." + rowChange.getTable();

            // get identify columns
            List<Integer> identifyColumns = getIdentifyColumnsIndex(allTbIdentifyColumns, fullTbName, rowChange);

            // find out events which changed identify columns of a table
            if (changedIdentifyColumnTables != null
                && !changedIdentifyColumnTables.contains(fullTbName)
                && rowChange.getAction() == DBMSAction.UPDATE) {
                for (Integer column : identifyColumns) {
                    if (rowChange.hasChangeColumn(column)) {
                        changedIdentifyColumnTables.add(fullTbName);
                        break;
                    }
                }
            }

            // 发现某表 a.a 某条记录 a.a.1 修改了 identify columns，则 a.a.1 之后所有记录都改为串行
            if (changedIdentifyColumnTables.contains(fullTbName)) {
                List<DefaultRowChange> tbSerialRowChanges = allSerialRowChanges.get(fullTbName);
                if (tbSerialRowChanges == null) {
                    tbSerialRowChanges = new ArrayList<>();
                    allSerialRowChanges.put(fullTbName, tbSerialRowChanges);
                }
                tbSerialRowChanges.add(rowChange);
                continue;
            }

            RowKey key = new RowKey(rowChange, identifyColumns);
            Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges = allSplitRowChanges.get(fullTbName);
            if (tbSplitRowChanges == null) {
                tbSplitRowChanges = new HashMap<>();
                allSplitRowChanges.put(fullTbName, tbSplitRowChanges);
            }

            // 保证同一个 key 的变更按照顺序排列
            List<DefaultRowChange> tbKeyRowChanges = tbSplitRowChanges.get(key);
            if (tbKeyRowChanges == null) {
                tbKeyRowChanges = new ArrayList<>();
                tbSplitRowChanges.put(key, tbKeyRowChanges);
            }
            tbKeyRowChanges.add(rowChange);
        }
    }
}
