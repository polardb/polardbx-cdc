/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author shicai.xsc 2021/5/25 14:45
 * @since 5.0.0.0
 */
@Slf4j
public class TransactionApplier extends MysqlApplier {
    public TransactionApplier(ApplierConfig applierConfig, HostInfo hostInfo, HostInfo srcHostInfo) {
        super(applierConfig, hostInfo, srcHostInfo);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void tranApply(List<Transaction> transactions) throws Exception {
        if (transactions.size() == 1 && transactions.get(0).getEventCount() > 0) {
            if (DdlApplyHelper.isDdl(transactions.get(0).peekFirst())) {
                ddlApply(transactions.get(0).peekFirst());
                logCommitInfo(Collections.singletonList(transactions.get(0).peekFirst()));
                return;
            } else if (containsNonInnoDBTable(transactions.get(0))) {
                super.apply((List<DBMSEvent>) (List<?>) (getRowChanges(transactions.get(0))));
                logCommitInfo((List<DBMSEvent>) (List<?>) getRowChanges(transactions.get(0)));
                return;
            }
        }

        DataSource dataSource = dbMetaCache.getBuiltInDefaultDataSource();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int i = 0;
                while (i < transactions.size()) {
                    List<DefaultRowChange> curRowChanges = new ArrayList<>();
                    List<Transaction> curTransactions = new ArrayList<>();

                    // merge small transactions into one to speed up write
                    while (i < transactions.size()) {
                        Transaction curTransaction = transactions.get(i);
                        if (curTransaction.getEventCount() == 0) {
                            i++;
                            continue;
                        }
                        if (curTransaction.isPersisted()) {
                            log.info("current transaction is persisted, will apply with stream mode!");
                            if (!curRowChanges.isEmpty()) {
                                executeInTransAndCommit(conn, curTransactions, curRowChanges);
                            }

                            // 对于持久化的事务，每个transaction commit一次
                            List<DefaultRowChange> persistedRowChanges = new ArrayList<>();
                            Transaction.RangeIterator iterator = curTransaction.rangeIterator();
                            while (iterator.hasNext()) {
                                Transaction.Range range = iterator.next();
                                List<DefaultRowChange> rangeRowChanges =
                                    (List<DefaultRowChange>) (List<?>) range.getEvents();
                                // note that this transaction has not been finished : may not commit successfully
                                executeInTrans(conn, rangeRowChanges);
                                persistedRowChanges.addAll(rangeRowChanges);  // 收集所有 rowChanges
                            }

                            doCommitTrans(conn, Collections.singletonList(curTransaction));
                            // ⚠️ 性能优化：持久化事务 commit 后统一写日志
                            logCommitInfoBatch((List<DBMSEvent>) (List<?>) persistedRowChanges);

                            curRowChanges = new ArrayList<>();
                            curTransactions = new ArrayList<>();
                            i++;
                        } else {
                            if (curRowChanges.isEmpty() || curRowChanges.size() + curTransaction.getEventCount() <
                                applierConfig.getTransactionEventBatchSize()) {
                                curRowChanges.addAll(getRowChanges(curTransaction));
                                curTransactions.add(curTransaction);
                                i++;
                            } else {
                                break;
                            }
                        }
                    }
                    if (curRowChanges.isEmpty()) {
                        continue;
                    }
                    executeInTransAndCommit(conn, curTransactions, curRowChanges);
                }
            } catch (Exception e) {
                conn.rollback();
                logTransactionRollback();
                throw e;
            }
        }
    }

    private List<DefaultRowChange> getRowChanges(Transaction transaction) {
        List<DefaultRowChange> rowChanges = new ArrayList<>();
        Transaction.RangeIterator iterator = transaction.rangeIterator();
        while (iterator.hasNext()) {
            Transaction.Range range = iterator.next();
            for (DBMSEvent event : range.getEvents()) {
                DefaultRowChange rowChange = (DefaultRowChange) event;
                rowChanges.add(rowChange);
            }
        }
        return rowChanges;
    }

    @SuppressWarnings("unchecked")
    private void executeInTrans(Connection conn, List<DefaultRowChange> curRowChanges) throws Exception {
        DmlApplyHelper.executeDML(conn, curRowChanges, conflictStrategy);
        // ⚠️ 性能优化：延迟到 commit 后统一写日志，避免两次 I/O
        // logCommitInfo((List<DBMSEvent>) (List<?>) curRowChanges);
    }

    private void executeInTransAndCommit(Connection conn, List<Transaction> curTransactions,
                                         List<DefaultRowChange> curRowChanges) throws Exception {
        executeInTrans(conn, curRowChanges);
        doCommitTrans(conn, curTransactions);
        // ⚠️ 性能优化：commit 后统一写日志（包含 DML + COMMIT）
        logCommitInfoBatch((List<DBMSEvent>) (List<?>) curRowChanges);
    }

    private void doCommitTrans(Connection conn, List<Transaction> curTransactions) throws SQLException {
        updateMetrics(curTransactions);
        conn.commit();
        // ⚠️ 性能优化：移除独立的 COMMIT 日志，合并到 logCommitInfoBatch 中
        // logTransactionCommit();
    }

    /**
     * 批量写入日志（DML + COMMIT），减少 I/O 次数
     */
    @SuppressWarnings("unchecked")
    private void logCommitInfoBatch(List<DBMSEvent> dbmsEvents) {
        int logLevel = applierConfig.getLogCommitLevel();

        // 早期退出：如果日志级别为 0（不记录）或事件为空
        if (logLevel <= 0 || dbmsEvents.isEmpty()) {
            return;
        }

        List<String> logs = new ArrayList<>();

        // 收集 DML 日志
        if (logLevel == RplConstants.LOG_ALL_COMMIT) {
            for (DBMSEvent event : dbmsEvents) {
                logs.addAll(LogUtil.generateCommitLog(event, null));
            }
        } else if (logLevel == RplConstants.LOG_END_COMMIT) {
            logs.addAll(LogUtil.generateCommitLog(dbmsEvents.get(dbmsEvents.size() - 1), null));
        }

        // 追加 COMMIT 日志
        if (logLevel == RplConstants.LOG_ALL_COMMIT) {
            logs.add(CommonUtil.getCurrentTime() + " : COMMIT");
        }

        // 一次性写入
        if (!logs.isEmpty()) {
            LogUtil.writeBatchLogs(logs, LogUtil.getCommitLogger());
        }
    }

    public void updateMetrics(List<Transaction> curTransactions) {
        for (Transaction trans : curTransactions) {
            StatMetrics.getInstance().doStatOut(
                trans.getInsertCount(), trans.getUpdateCount(), trans.getDeleteCount(),
                trans.getByteSize(), trans.peekLast());
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV)) {
                Transaction.RangeIterator iterator = trans.rangeIterator();
                while (iterator.hasNext()) {
                    Transaction.Range range = iterator.next();
                    StatMetrics.getInstance().addCommitCount(range.getEvents());
                }
            } else {
                StatMetrics.getInstance().addCommitCount(trans.getEventCount());
            }
        }
    }

    boolean containsNonInnoDBTable(Transaction transaction) {
        return transaction.getTables().stream().anyMatch(fullTableName -> {
            try {
                TableInfo tableInfo = dbMetaCache.getTableInfo(fullTableName);
                boolean flag = !TableInfo.ENGINE_TYPE_INNODB.equalsIgnoreCase(tableInfo.getEngine());
                if (flag) {
                    log.warn("table engine is not InnoDB, will skip transaction execute,  {}:{}:{}",
                        tableInfo.getSchema(), tableInfo.getName(), tableInfo.getEngine());
                }
                return flag;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
