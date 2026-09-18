/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowData;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_SPLIT_APPLY_BATCH_MERGE_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_SPLIT_APPLY_IN_TRANSACTION_WITHOUT_GSI;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.rpl.applier.DmlApplyHelper.execSqlContext;
import static com.aliyun.polardbx.rpl.applier.DmlApplyHelper.executeDML;
import static com.aliyun.polardbx.rpl.applier.DmlApplyHelper.getMergeDeleteSqlExecContext;
import static com.aliyun.polardbx.rpl.applier.DmlApplyHelper.getMergeInsertSqlExecContext;

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
        parallelExecSqlContexts(splitRowChanges.values().stream().map(RowQueue::new).collect(Collectors.toList()),
            true);
    }

    Map<String, List<DefaultRowChange>> splitByTable(List<DBMSEvent> dbmsEvents) {
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

    protected void parallelExecSqlContexts(Collection<RowQueue> allRowChanges, boolean tbTranExec) {
        List<Future<Void>> futures = new ArrayList<>();

        for (RowQueue rowQueue : allRowChanges) {
            Callable<Void> task = buildTask(rowQueue, tbTranExec);
            futures.add(executorService.submit(task));
        }

        waitAndCheck(futures);
    }

    void waitAndCheck(List<Future<Void>> futures) {
        // wait and check
        PolardbxException exception = CommonUtil.waitAllTaskFinishedAndReturn(futures);
        if (exception != null) {
            throw exception;
        }
    }

    Callable<Void> buildTask(RowQueue rowQueue, boolean tbTranExec) {
        return () -> {
            rowQueue.markCompleted();
            if (tbTranExec && !containsNonInnoDBTableOrGsiTable(rowQueue)) {
                executeWithExplicitTrans(rowQueue);
            } else {
                executeWithoutExplicitTrans(rowQueue);
            }
            return null;
        };
    }

    boolean containsNonInnoDBTableOrGsiTable(RowQueue rowQueue) {
        return rowQueue.getAllRowChanges().stream().anyMatch(rowChange -> {
            String schema = rowChange.getSchema();
            String table = rowChange.getTable();

            try {
                TableInfo tableInfo = dbMetaCache.getTableInfo(schema, table);
                boolean flag = !TableInfo.ENGINE_TYPE_INNODB.equalsIgnoreCase(tableInfo.getEngine());
                if (flag) {
                    log.warn("table engine is not InnoDB, will skip transaction execute,  {}:{}:{}",
                        tableInfo.getSchema(), tableInfo.getName(), tableInfo.getEngine());
                }

                // 含有gsi的表，并发写入时，容易触发死锁；不开启显式事务，可以降低死锁发生的概率，增加参数进行控制
                flag |= (tableInfo.getGsiNum() > 0 && getBoolean(RPL_SPLIT_APPLY_IN_TRANSACTION_WITHOUT_GSI));
                return flag;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    void executeWithExplicitTrans(RowQueue rowQueue) throws Exception {
        DataSource dataSource = dbMetaCache.getDataSource("");
        boolean supportBatchMerge = getBoolean(RPL_SPLIT_APPLY_BATCH_MERGE_ENABLED);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                if (supportBatchMerge) {
                    executeDMLInBatchMergeMode(rowQueue, conn, true);
                } else {
                    executeDML(conn, rowQueue.getAllRowChanges(), conflictStrategy);
                }
            } catch (Throwable t) {
                conn.rollback();
                throw t;
            }

            conn.commit();
        }
    }

    void executeWithoutExplicitTrans(RowQueue rowQueue) throws Exception {
        DataSource dataSource = dbMetaCache.getDataSource("");
        boolean supportBatchMerge = getBoolean(RPL_SPLIT_APPLY_BATCH_MERGE_ENABLED);

        try (Connection conn = dataSource.getConnection()) {
            if (supportBatchMerge) {
                executeDMLInBatchMergeMode(rowQueue, conn, false);
            } else {
                executeDML(conn, rowQueue.getAllRowChanges(), conflictStrategy);
            }
        }
    }

    void executeDMLInBatchMergeMode(RowQueue rowQueue, Connection conn, boolean isInExplicitTrans) throws Exception {
        try {
            rowQueue.forEachApply(rowBatch -> {
                try {
                    if (rowBatch.canMerge()) {
                        executeDmlInBatchMergeMode(rowBatch, conn);
                    } else {
                        executeDML(conn, rowBatch.getRowChanges(), conflictStrategy);
                    }
                } catch (Exception e) {
                    throw new PolardbxException("execute dml in batch merge mode failed!", e);
                }
            });
        } catch (Throwable e) {
            if (isInExplicitTrans) {
                conn.rollback();
            }
            executeDML(conn, rowQueue.getAllRowChanges(), conflictStrategy);
        }
    }

    void executeDmlInBatchMergeMode(RowBatch rowBatch, Connection conn) throws Exception {
        DefaultRowChange firstRowChange = rowBatch.getRowChanges().get(0);
        String schemaName = firstRowChange.getSchema();
        String tableName = firstRowChange.getTable();
        DBMSAction action = firstRowChange.getAction();
        DBMSColumnSet columnSet = firstRowChange.getColumnSet();
        TableInfo tableInfo = dbMetaCache.getTableInfo(schemaName, tableName);

        Iterator<DefaultRowChange> iterator = rowBatch.getRowChanges().iterator();
        while (iterator.hasNext()) {
            DefaultRowChange mergedRowChange = new DefaultRowChange();
            mergedRowChange.setAction(action);
            mergedRowChange.setSchema(schemaName);
            mergedRowChange.setTable(tableName);
            mergedRowChange.setColumnSet(columnSet);

            List<DBMSRowData> dataSet = new ArrayList<>(iterator.next().getDataSet());
            mergedRowChange.setDataSet(dataSet);
            int count = 1;

            while (iterator.hasNext() && count < applierConfig.getMergeBatchSize()) {
                mergedRowChange.addRowData(iterator.next().getRowData(1));
                count++;
            }

            SqlContext sqlContext;
            if (action == DBMSAction.INSERT) {
                sqlContext = getMergeInsertSqlExecContext(mergedRowChange, tableInfo, 1);
            } else if (action == DBMSAction.DELETE) {
                sqlContext = getMergeDeleteSqlExecContext(mergedRowChange, tableInfo);
            } else {
                throw new UnsupportedOperationException("unsupported action" + action);
            }

            execSqlContext(conn, sqlContext);
        }
    }
}
