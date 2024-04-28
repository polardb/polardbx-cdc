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
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
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
    public void tranApply(List<Transaction> transactions) throws Exception {
        if (transactions.size() == 1 && transactions.get(0).getEventCount() > 0
            && DdlApplyHelper.isDdl(transactions.get(0).peekFirst())) {
            ddlApply(transactions.get(0).peekFirst());
            logCommitInfo(Collections.singletonList(transactions.get(0).peekFirst()));
            return;
        }

        DataSource dataSource = dbMetaCache.getBuiltInDefaultDataSource();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int i = 0;
                while (i < transactions.size()) {
                    List<DefaultRowChange> curRowChanges = new ArrayList<>();
                    // merge small transactions into one to speed up write
                    while (i < transactions.size()) {
                        Transaction curTransaction = transactions.get(i);
                        if (curTransaction.getEventCount() == 0) {
                            i++;
                            continue;
                        }
                        // 对于持久化的超大事务，不保证事务性，这里后面要想办法优化
                        if (curTransaction.isPersisted()) {
                            log.info("current transaction is persisted, will apply with stream mode!");
                            if (!curRowChanges.isEmpty()) {
                                DmlApplyHelper.executeDML(conn, curRowChanges, conflictStrategy);
                                logCommitInfo((List<DBMSEvent>) (List<?>) curRowChanges);
                                conn.commit();
                                logTransactionCommit();
                                curRowChanges = new ArrayList<>();
                            }
                            Transaction.RangeIterator iterator = curTransaction.rangeIterator();
                            while (iterator.hasNext()) {
                                Transaction.Range range = iterator.next();
                                DmlApplyHelper.executeDML(conn, (List<DefaultRowChange>) (List<?>) range.getEvents(),
                                    conflictStrategy);
                                // note that this transaction has not been finished : may not commit successfully
                                logCommitInfo((List<DBMSEvent>) (List<?>) curRowChanges);
                            }
                            i++;
                        } else {
                            if (curRowChanges.isEmpty() || curRowChanges.size() + curTransaction.getEventCount() <
                                applierConfig.getTransactionEventBatchSize()) {
                                curRowChanges.addAll(getRowChanges(curTransaction));
                                i++;
                            } else {
                                break;
                            }
                        }
                    }
                    if (curRowChanges.isEmpty()) {
                        continue;
                    }
                    DmlApplyHelper.executeDML(conn, curRowChanges, conflictStrategy);
                    logCommitInfo((List<DBMSEvent>) (List<?>) curRowChanges);
                    conn.commit();
                    logTransactionCommit();
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
}
