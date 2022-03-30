/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
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
                                Set<String> changedIdentifyColumnTables) throws Throwable {
        Map<String, List<Integer>> allTbIdentifyColumns = new HashMap<>();

        for (DBMSEvent event : dbmsEvents) {
            DefaultRowChange rowChange = (DefaultRowChange) event;
            String fullTbName = rowChange.getSchema() + "." + rowChange.getTable();

            // filter
            if (filterCommitedEvent(fullTbName, rowChange)) {
                continue;
            }

            // get identify columns
            List<Integer> identifyColumns = getIdentifyColumns(allTbIdentifyColumns, fullTbName, rowChange);

            if (!insertRowChanges.containsKey(fullTbName)) {
                insertRowChanges.put(fullTbName, new HashMap<>());
            }
            if (!deleteRowChanges.containsKey(fullTbName)) {
                deleteRowChanges.put(fullTbName, new HashMap<>());
            }

            mergeTableRowChanges(rowChange,
                identifyColumns,
                insertRowChanges.get(fullTbName),
                deleteRowChanges.get(fullTbName));

            lastRowChanges.put(fullTbName, rowChange);

            // find out events which changed where columns of a table
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
        }
    }

    private void mergeTableRowChanges(DefaultRowChange rowChange, List<Integer> identifyColumns,
                                      Map<RowKey, DefaultRowChange> insertRowChanges,
                                      Map<RowKey, DefaultRowChange> deleteRowChanges) {
        RowKey key = new RowKey(rowChange, identifyColumns);
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

            if (insertRowChanges.containsKey(key)) {
                insertRowChanges.remove(key);
            }
            deleteRowChanges.put(key, beforeRowChange);

            RowKey afterKey = new RowKey(afterRowChange, identifyColumns);
            insertRowChanges.put(afterKey, afterRowChange);
            break;
        case DELETE:
            if (insertRowChanges.containsKey(key)) {
                insertRowChanges.remove(key);
            }
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
        for (String tbName : insertRowChanges.keySet()) {
            allTbNames.add(tbName);
        }
        for (String table : deleteRowChanges.keySet()) {
            allTbNames.add(table);
        }

        boolean res = true;
        List<Future> futures = new ArrayList<>();

        Map<String, List<MergeDmlSqlContext>> allTbMergeDmlSqlContexts = new HashMap<>();
        for (String tbName : allTbNames) {
            List<MergeDmlSqlContext> mergeDmlSqlContexts = new ArrayList<>();

            // execute delete first
            if (deleteRowChanges.containsKey(tbName)) {
                List<MergeDmlSqlContext> sqlContexts = getMergeDmlSqlContexts(deleteRowChanges.get(tbName).values(),
                    RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);
                mergeDmlSqlContexts.addAll(sqlContexts);
            }

            if (insertRowChanges.containsKey(tbName)) {
                List<MergeDmlSqlContext> sqlContexts = getMergeDmlSqlContexts(insertRowChanges.get(tbName).values(),
                    RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);
                mergeDmlSqlContexts.addAll(sqlContexts);
            }

            if (mergeDmlSqlContexts.size() == 0) {
                continue;
            }
            allTbMergeDmlSqlContexts.put(tbName, mergeDmlSqlContexts);

            // submit task
            Callable task = () -> {
                List<SqlContext> sqlContexts = new ArrayList<>();
                for (MergeDmlSqlContext sqlContext : mergeDmlSqlContexts) {
                    sqlContexts.add(sqlContext);
                }

                boolean succeed = tranExecSqlContexts(sqlContexts);
                if (succeed) {
                    recordTablePosition(tbName, lastRowChanes.get(tbName));
                }
                mergeDmlSqlContexts.get(0).setSucceed(succeed);
                return succeed;
            };
            futures.add(executorService.submit(() -> task.call()));

            // record merge size
            for (int i = 0; i < mergeDmlSqlContexts.size(); i++) {
                StatisticalProxy.getInstance()
                    .addMergeBatchSize(mergeDmlSqlContexts.get(i).getOriginRowChanges().size());
            }
        }

        // get result
        for (Future future : futures) {
            res &= (Boolean) future.get();
        }

        if (res) {
            return res;
        }

        // for those failed sqlContext, excute the originRowChanges with the sql to be
        // INSERT ON DUPLICATE UPDATE
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
                    List<SqlContext> sqlContexts = getSqlContexts(rowChange, mergeDmlSqlContext.getDstTable());
                    tbSqlContexts.addAll(sqlContexts);
                }
            }

            final Callable task = () -> {
                // execute
                boolean succeed;
                if (skipAllException) {
                    succeed = execSqlContexts(tbSqlContexts);
                } else {
                    succeed = tranExecSqlContexts(tbSqlContexts);
                }
                if (succeed) {
                    recordTablePosition(tbName, lastRowChanes.get(tbName));
                }
                return succeed;

            };
            futures.add(executorService.submit(() -> task.call()));
        }

        for (Future future : futures) {
            res &= (Boolean) future.get();
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
        int count = 0;

        while (iterator.hasNext()) {
            DefaultRowChange mergedRowChange = new DefaultRowChange();
            DefaultRowChange firstRowChange = iterator.next();
            mergedRowChange.setAction(firstRowChange.getAction());
            mergedRowChange.setSchema(firstRowChange.getSchema());
            mergedRowChange.setTable(firstRowChange.getTable());
            mergedRowChange.setColumnSet(firstRowChange.getColumnSet());
            List<DBMSRowData> dataSet = new ArrayList<>();
            dataSet.addAll(firstRowChange.getDataSet());
            mergedRowChange.setDataSet(dataSet);

            List<DefaultRowChange> originRowChanges = new ArrayList<>();
            originRowChanges.add(firstRowChange);

            while (iterator.hasNext() && count <= applierConfig.getMergeBatchSize()) {
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

            count = 0;
        }
        return mergeDmlSqlContexts;
    }
}
