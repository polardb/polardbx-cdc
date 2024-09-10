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

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.ConflictStrategy;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_MERGE_APPLY_GROUP_BY_TABLE_ENABLED;

/**
 * @author shicai.xsc 2021/5/17 20:52
 * @since 5.0.0.0
 */
@Slf4j
public class MergeApplier extends MysqlApplier {

    protected long deleteBeforeInsertTimeStamp = -1;

    public MergeApplier(ApplierConfig applierConfig, HostInfo hostInfo, HostInfo srcHostInfo) {
        super(applierConfig, hostInfo, srcHostInfo);
    }

    @Override
    public void init() throws Exception {
        super.init();
        this.deleteBeforeInsertTimeStamp = applierConfig.getFullCopyFinishTimeStamp();
    }

    @Override
    protected void dmlApply(List<DBMSEvent> dbmsEvents) throws Exception {
        if (dbmsEvents == null || dbmsEvents.isEmpty()) {
            return;
        }

        boolean deleteBeforeInsert = false;
        if (deleteBeforeInsertTimeStamp != -1) {
            deleteBeforeInsert = dbmsEvents.get(0).getSourceTimeStamp() <= deleteBeforeInsertTimeStamp;
            if (!deleteBeforeInsert) {
                deleteBeforeInsertTimeStamp = -1;
            }
        }

        // Map<fullTableName, Map<rowPk/rowUk, RowChange>>
        Map<String, Map<RowKey, DefaultRowChange>> insertRowChanges = new HashMap<>();
        Map<String, Map<RowKey, DefaultRowChange>> deleteRowChanges = new HashMap<>();
        Map<String, DefaultRowChange> lastRowChanges = new HashMap<>();
        mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges, lastRowChanges, deleteBeforeInsert);

        // 如果任务发生故障重启，需采取safe mode写入
        // 这里采用lazy处理，对于执行失败的采取safe mode写入
        boolean groupByTable = DynamicApplicationConfig.getBoolean(RPL_MERGE_APPLY_GROUP_BY_TABLE_ENABLED);
        if (groupByTable) {
            parallelExecuteDMLGroupByTable(deleteRowChanges);
            parallelExecuteDMLGroupByTable(insertRowChanges);
        } else {
            parallelExecuteDML(deleteRowChanges);
            parallelExecuteDML(insertRowChanges);
        }
    }

    List<Future<Void>> parallelExecuteDMLGroupByTable(Map<String, Map<RowKey, DefaultRowChange>> allRowChanges)
        throws Exception {
        List<List<MergeDmlSqlContext>> mergeDmlSqlContexts = new ArrayList<>();
        List<Future<Void>> futures = new ArrayList<>();

        for (String tbName : allRowChanges.keySet()) {
            Collection<DefaultRowChange> tbRowChanges = allRowChanges.get(tbName).values();
            if (tbRowChanges.isEmpty()) {
                continue;
            }
            // merge
            List<MergeDmlSqlContext> sqlContexts = getMergeDmlSqlContexts(tbRowChanges,
                RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);
            mergeDmlSqlContexts.add(sqlContexts);
        }

        for (List<MergeDmlSqlContext> sqlContexts : mergeDmlSqlContexts) {
            futures.add(executorService.submit(getCallableTask(sqlContexts, dbMetaCache)));
            StatMetrics.getInstance()
                .addMergeBatchSize(sqlContexts.stream().mapToInt(c -> c.getOriginRowChanges().size()).sum());
        }

        checkResultAndReRun(futures,
            mergeDmlSqlContexts.stream().flatMap(Collection::stream).collect(Collectors.toList()));
        return futures;
    }

    private void parallelExecuteDML(Map<String, Map<RowKey, DefaultRowChange>> allRowChanges)
        throws Exception {
        List<MergeDmlSqlContext> mergeDmlSqlContexts = new ArrayList<>();

        for (String tbName : allRowChanges.keySet()) {
            Collection<DefaultRowChange> tbRowChanges = allRowChanges.get(tbName).values();
            if (tbRowChanges.isEmpty()) {
                continue;
            }
            // merge
            List<MergeDmlSqlContext> sqlContexts = getMergeDmlSqlContexts(tbRowChanges,
                RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);
            mergeDmlSqlContexts.addAll(sqlContexts);
        }

        List<Future<Void>> futures = new ArrayList<>();

        // parallel execute, each table cost a thread
        for (MergeDmlSqlContext sqlContext : mergeDmlSqlContexts) {
            futures.add(executorService.submit(getCallableTask(sqlContext, dbMetaCache)));
            // record merge size
            StatMetrics.getInstance().addMergeBatchSize(sqlContext.getOriginRowChanges().size());
        }
        checkResultAndReRun(futures, mergeDmlSqlContexts);
    }

    Callable<Void> getCallableTask(final List<MergeDmlSqlContext> sqlContexts, final DbMetaCache dbMetaCache) {
        return () -> {
            // do not need to check affected rows
            for (MergeDmlSqlContext sqlContext : sqlContexts) {
                try (Connection conn = dbMetaCache.getConnection(sqlContext.getDstSchema())) {
                    DmlApplyHelper.execSqlContext(conn, sqlContext);
                }
                sqlContext.setSucceed(true);
            }
            return null;
        };
    }

    Callable<Void> getCallableTask(final MergeDmlSqlContext sqlContext, final DbMetaCache dbMetaCache) {
        return () -> {
            // do not need to check affected rows
            try (Connection conn = dbMetaCache.getConnection(sqlContext.getDstSchema())) {
                DmlApplyHelper.execSqlContext(conn, sqlContext);
            }
            sqlContext.setSucceed(true);
            return null;
        };
    }

    protected void checkResultAndReRun(List<Future<Void>> futures, List<MergeDmlSqlContext> mergeDmlSqlContexts) {
        PolardbxException exception = CommonUtil.waitAllTaskFinishedAndReturn(futures);
        if (exception == null) {
            return;
        }
        // for those failed sqlContext, execute the originRowChanges with the sql to be
        // REPLACE INTO
        futures.clear();
        for (final MergeDmlSqlContext sqlContext : mergeDmlSqlContexts) {
            if (sqlContext.isSucceed()) {
                continue;
            }
            log.error("merge execute failed for: {}, try serial execute", sqlContext.getDstTable());
            final Callable<Void> task = () -> {
                DataSource dataSource =
                    dbMetaCache.getDataSource(sqlContext.getOriginRowChanges().get(0).getSchema());
                try (Connection conn = dataSource.getConnection()) {
                    DmlApplyHelper.executeDML(conn, sqlContext.getOriginRowChanges(),
                        ConflictStrategy.DIRECT_OVERWRITE);
                }
                return null;
            };
            futures.add(executorService.submit(task));
        }
        exception = CommonUtil.waitAllTaskFinishedAndReturn(futures);
        if (exception != null) {
            throw exception;
        }
    }

    protected List<MergeDmlSqlContext> getMergeDmlSqlContexts(Collection<DefaultRowChange> rowChanges, int insertMode)
        throws Exception {
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
                sqlContext = DmlApplyHelper.getMergeDeleteSqlExecContext(mergedRowChange, dstTbInfo);
                break;
            case INSERT:
                sqlContext = DmlApplyHelper.getMergeInsertSqlExecContext(mergedRowChange, dstTbInfo, insertMode);
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

    protected void mergeByTable(List<DBMSEvent> dbmsEvents,
                                Map<String, Map<RowKey, DefaultRowChange>> insertRowChanges,
                                Map<String, Map<RowKey, DefaultRowChange>> deleteRowChanges,
                                Map<String, DefaultRowChange> lastRowChanges,
                                boolean deleteBeforeInsert) throws Exception {
        Map<String, List<Integer>> allTbWhereColumns = new HashMap<>();

        for (DBMSEvent event : dbmsEvents) {
            DefaultRowChange rowChange = (DefaultRowChange) event;
            String fullTbName = rowChange.getSchema() + "." + rowChange.getTable();
            if (rowChange.getRowSize() > 1) {
                log.error("unexpected: row change has multiply row values: {}", rowChange);
                throw new PolardbxException("unexpected: row change has multiply row values :" + rowChange);
            }

            // get identify columns
            List<Integer> whereColumns = DmlApplyHelper.getWhereColumnsIndex(allTbWhereColumns, fullTbName, rowChange);

            insertRowChanges.putIfAbsent(fullTbName, new LinkedHashMap<>());
            deleteRowChanges.putIfAbsent(fullTbName, new LinkedHashMap<>());

            mergeTableRowChanges(rowChange,
                whereColumns,
                insertRowChanges.get(fullTbName),
                deleteRowChanges.get(fullTbName),
                !DmlApplyHelper.isNoPkTable(rowChange) && deleteBeforeInsert);

            lastRowChanges.put(fullTbName, rowChange);
        }
    }

    private void mergeTableRowChanges(DefaultRowChange rowChange, List<Integer> whereColumns,
                                      Map<RowKey, DefaultRowChange> insertRowChanges,
                                      Map<RowKey, DefaultRowChange> deleteRowChanges, boolean deleteBeforeInsert) {
        RowKey key = new RowKey(rowChange, whereColumns);
        switch (rowChange.getAction()) {
        case INSERT:
            if (deleteBeforeInsert) {
                DefaultRowChange deleteRowChange = new DefaultRowChange();
                deleteRowChange.setDataSet(rowChange.getDataSet());
                deleteRowChange.setAction(DBMSAction.DELETE);
                deleteRowChange.setSchema(rowChange.getSchema());
                deleteRowChange.setTable(rowChange.getTable());
                deleteRowChange.setColumnSet(rowChange.getColumnSet());
                deleteRowChanges.put(key, deleteRowChange);
            }
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

            if (deleteBeforeInsert) {
                DefaultRowChange deleteRowChange = new DefaultRowChange();
                deleteRowChange.setDataSet(rowChange.getChangeDataSet());
                deleteRowChange.setAction(DBMSAction.DELETE);
                deleteRowChange.setSchema(rowChange.getSchema());
                deleteRowChange.setTable(rowChange.getTable());
                deleteRowChange.setColumnSet(rowChange.getColumnSet());
                deleteRowChanges.put(afterKey, deleteRowChange);
            }
            break;
        case DELETE:
            insertRowChanges.remove(key);
            deleteRowChanges.put(key, rowChange);
            break;
        default:
            break;
        }
    }
}
