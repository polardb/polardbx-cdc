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

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author shicai.xsc 2021/5/19 19:56
 * @since 5.0.0.0
 */
@Slf4j
public class MergeTransactionApplier extends MysqlApplier {
    public MergeTransactionApplier(ApplierConfig applierConfig, HostInfo hostInfo) {
        super(applierConfig, hostInfo);
    }

    @Override
    protected boolean dmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        if (dbmsEvents == null || dbmsEvents.size() == 0) {
            return true;
        }
        // Map<fullTableName, Map<rowPk/rowUk, RowChange>>
        Map<String, Map<RowKey, DefaultRowChange>> insertRowChanges = new HashMap<>();
        Map<String, Map<RowKey, DefaultRowChange>> deleteRowChanges = new HashMap<>();
        Map<String, DefaultRowChange> lastRowChanes = new HashMap<>();
        mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges, lastRowChanes, null);

        // execute delete
        return parallelExecSqlContexts(insertRowChanges, deleteRowChanges, lastRowChanes);
    }

    protected void mergeByTable(List<DBMSEvent> dbmsEvents,
                                Map<String, Map<RowKey, DefaultRowChange>> insertRowChanges,
                                Map<String, Map<RowKey, DefaultRowChange>> deleteRowChanges,
                                Map<String, DefaultRowChange> lastRowChanges,
                                Set<String> changedWhereColumnTables) throws Throwable {
        Map<String, List<Integer>> allTbWhereColumns = new HashMap<>();

        for (DBMSEvent event : dbmsEvents) {
            DefaultRowChange rowChange = (DefaultRowChange) event;
            String fullTbName = rowChange.getSchema() + "." + rowChange.getTable();
            if (rowChange.getRowSize() > 1) {
                log.error("unexpected: row change has multiply row values: {}", rowChange);
                Runtime.getRuntime().halt(1);
            }

            // filter
            if (filterCommitedEvent(fullTbName, rowChange)) {
                continue;
            }

            // get identify columns
            List<Integer> whereColumns = getWhereColumnsIndex(allTbWhereColumns, fullTbName, rowChange);

            insertRowChanges.putIfAbsent(fullTbName, new HashMap<>());
            deleteRowChanges.putIfAbsent(fullTbName, new HashMap<>());

            mergeTableRowChanges(rowChange,
                whereColumns,
                insertRowChanges.get(fullTbName),
                deleteRowChanges.get(fullTbName));

            lastRowChanges.put(fullTbName, rowChange);

            // find out events which changed where columns of a table
            if (changedWhereColumnTables != null
                && !changedWhereColumnTables.contains(fullTbName)
                && rowChange.getAction() == DBMSAction.UPDATE) {
                for (Integer column : whereColumns) {
                    if (rowChange.hasChangeColumn(column)) {
                        changedWhereColumnTables.add(fullTbName);
                        break;
                    }
                }
            }
        }
    }

    private void mergeTableRowChanges(DefaultRowChange rowChange, List<Integer> whereColumns,
                                      Map<RowKey, DefaultRowChange> insertRowChanges,
                                      Map<RowKey, DefaultRowChange> deleteRowChanges) {
        RowKey key = new RowKey(rowChange, whereColumns);
        switch (rowChange.getAction()) {
        case INSERT:
            insertRowChanges.put(key, rowChange);
            break;
        case UPDATE:
            DefaultRowChange beforeRowChange = new DefaultRowChange();
            beforeRowChange.setDataSet(rowChange.getDataSet());
            beforeRowChange.setAction(DBMSAction.DELETE);
            beforeRowChange.setSchema(rowChange.getSchema());
            beforeRowChange.setTable(rowChange.getTable());
            beforeRowChange.setColumnSet(rowChange.getColumnSet());

            DefaultRowChange afterRowChange = new DefaultRowChange();
            afterRowChange.setDataSet(rowChange.getChangeDataSet());
            afterRowChange.setAction(DBMSAction.INSERT);
            afterRowChange.setSchema(rowChange.getSchema());
            afterRowChange.setTable(rowChange.getTable());
            afterRowChange.setColumnSet(rowChange.getColumnSet());

            insertRowChanges.remove(key);
            deleteRowChanges.put(key, beforeRowChange);

            RowKey afterKey = new RowKey(afterRowChange, whereColumns);
            insertRowChanges.put(afterKey, afterRowChange);
            break;
        case DELETE:
            insertRowChanges.remove(key);
            deleteRowChanges.put(key, rowChange);
            break;
        default:
            break;
        }
    }

    protected boolean parallelExecSqlContexts(Map<String, Map<RowKey, DefaultRowChange>> insertRowChanges,
                                              Map<String, Map<RowKey, DefaultRowChange>> deleteRowChanges,
                                              Map<String, DefaultRowChange> lastRowChanes)
        throws Throwable {
        Set<String> allTbNames = new HashSet<>();
        allTbNames.addAll(insertRowChanges.keySet());
        allTbNames.addAll(deleteRowChanges.keySet());

        boolean res = true;
        List<Future<Boolean>> futures = new ArrayList<>();

        Map<String, List<MergeDmlSqlContext>> allTbMergeDmlSqlContexts = new HashMap<>();
        for (String tbName : allTbNames) {
            List<MergeDmlSqlContext> mergeDmlSqlContexts = new ArrayList<>();

            // execute delete first
            int insertMode = safeMode ?
                RplConstants.INSERT_MODE_REPLACE: RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE;
            if (deleteRowChanges.containsKey(tbName)) {
                List<MergeDmlSqlContext> sqlContexts = getMergeDmlSqlContexts(deleteRowChanges.get(tbName).values(),
                    insertMode);
                mergeDmlSqlContexts.addAll(sqlContexts);
            }

            if (insertRowChanges.containsKey(tbName)) {
                List<MergeDmlSqlContext> sqlContexts = getMergeDmlSqlContexts(insertRowChanges.get(tbName).values(),
                    insertMode);
                mergeDmlSqlContexts.addAll(sqlContexts);
            }

            if (mergeDmlSqlContexts.size() == 0) {
                continue;
            }
            allTbMergeDmlSqlContexts.put(tbName, mergeDmlSqlContexts);

            // submit task
            Callable<Boolean> task = () -> {
                List<SqlContext> sqlContexts = new ArrayList<>(mergeDmlSqlContexts);

                boolean succeed = tranExecSqlContexts(sqlContexts);
                if (succeed) {
                    recordTablePosition(tbName, lastRowChanes.get(tbName));
                }
                mergeDmlSqlContexts.get(0).setSucceed(succeed);
                return succeed;
            };
            futures.add(executorService.submit(task));

            // record merge size
            for (MergeDmlSqlContext mergeDmlSqlContext : mergeDmlSqlContexts) {
                StatisticalProxy.getInstance()
                    .addMergeBatchSize(mergeDmlSqlContext.getOriginRowChanges().size());
            }
        }

        // get result
        for (Future<Boolean> future : futures) {
            res &= future.get();
        }

        if (res) {
            return true;
        }

        // for those failed sqlContext, execute the originRowChanges with the sql to be
        // REPLACE INTO
        futures.clear();
        res = true;

        for (String tbName : allTbMergeDmlSqlContexts.keySet()) {
            List<MergeDmlSqlContext> tbMergeDmlSqlContexts = allTbMergeDmlSqlContexts.get(tbName);
            if (tbMergeDmlSqlContexts.get(0).isSucceed()) {
                continue;
            }

            List<SqlContext> tbSqlContexts = new ArrayList<>();
            for (MergeDmlSqlContext mergeDmlSqlContext : tbMergeDmlSqlContexts) {
                log.error("merge execute failed for: {}, try serial execute",
                    mergeDmlSqlContext.getDstTable());
                for (DefaultRowChange rowChange : mergeDmlSqlContext.getOriginRowChanges()) {
                    List<SqlContext> sqlContexts = getSqlContexts(rowChange, true);
                    tbSqlContexts.addAll(sqlContexts);
                }
            }

            final Callable<Boolean> task = () -> {
                // execute
                if (tranExecSqlContexts(tbSqlContexts)) {
                    recordTablePosition(tbName, lastRowChanes.get(tbName));
                    return true;
                }
                return false;

            };
            futures.add(executorService.submit(task));
        }

        for (Future<Boolean> future : futures) {
            res &= future.get();
        }

        if (!res) {
            log.error("single execute failed");
        }

        return res;
    }

    protected List<MergeDmlSqlContext> getMergeDmlSqlContexts(Collection<DefaultRowChange> rowChanges, int insertMode)
        throws Throwable {
        List<MergeDmlSqlContext> mergeDmlSqlContexts = new ArrayList<>();

        Iterator<DefaultRowChange> iterator = rowChanges.iterator();
        int count = 1;

        while (iterator.hasNext()) {
            DefaultRowChange mergedRowChange = new DefaultRowChange();
            DefaultRowChange firstRowChange = iterator.next();
            mergedRowChange.setAction(firstRowChange.getAction());
            mergedRowChange.setSchema(firstRowChange.getSchema());
            mergedRowChange.setTable(firstRowChange.getTable());
            mergedRowChange.setColumnSet(firstRowChange.getColumnSet());
            List<DBMSRowData> dataSet = new ArrayList<>(firstRowChange.getDataSet());
            mergedRowChange.setDataSet(dataSet);

            List<DefaultRowChange> originRowChanges = new ArrayList<>();
            originRowChanges.add(firstRowChange);

            while (iterator.hasNext() && count < applierConfig.getMergeBatchSize()) {
                DefaultRowChange rowChange = iterator.next();
                mergedRowChange.addRowData(rowChange.getRowData(1));
                originRowChanges.add(rowChange);
                count++;
            }

            // get MergeDmlSqlContext
            MergeDmlSqlContext sqlContext = null;
            TableInfo dstTbInfo = dbMetaCache.getTableInfo(mergedRowChange.getSchema(), mergedRowChange.getTable());
            switch (mergedRowChange.getAction()) {
            case DELETE:
                sqlContext = ApplyHelper.getMergeDeleteSqlExecContext(mergedRowChange, dstTbInfo);
                break;
            case INSERT:
                sqlContext = ApplyHelper.getMergeInsertSqlExecContext(mergedRowChange, dstTbInfo, insertMode);
                break;
            default:
                break;
            }

            if (sqlContext != null) {
                sqlContext.setOriginRowChanges(originRowChanges);
                mergeDmlSqlContexts.add(sqlContext);
            }

            count = 1;
        }
        return mergeDmlSqlContexts;
    }
}
