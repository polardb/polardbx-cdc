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
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author shicai.xsc 2021/5/17 20:52
 * @since 5.0.0.0
 */
@Slf4j
public class MergeApplier extends MergeTransactionApplier {

    ExecutorService mergeAndTranExecutorService;
    MergeTransactionApplier mergeTransactionApplier;

    public MergeApplier(ApplierConfig applierConfig, HostInfo hostInfo) {
        super(applierConfig, hostInfo);
    }

    @Override
    public boolean init() {
        super.init();
        mergeTransactionApplier = new MergeTransactionApplier(applierConfig, hostInfo);
        mergeTransactionApplier.init();
        mergeTransactionApplier.dbMetaCache = dbMetaCache;
        mergeAndTranExecutorService = ThreadPoolUtil.createExecutorWithFixedNum(2, "MergeApplier");
        return true;
    }

    @Override
    protected boolean dmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        if (dbmsEvents == null || dbmsEvents.size() == 0) {
            return true;
        }

        // Map<fullTableName, Map<rowPk/rowUk, RowChange>>
        Map<String, Map<RowKey, DefaultRowChange>> insertRowChanges = new HashMap<>();
        Map<String, Map<RowKey, DefaultRowChange>> deleteRowChanges = new HashMap<>();
        Map<String, DefaultRowChange> lastRowChanges = new HashMap<>();
        Set<String> changedWhereColumnTables = new HashSet<>();
        mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges, lastRowChanges, changedWhereColumnTables);

        // 考虑如下情况：
        // 某表 a.a，既有 uk 又有 pk: create table a.a (f0 int, f1 int, f2 int, primary key(f0), unique key uk(f1));
        // 在执行过程中，任务发生了重启。

        // 已经记录的位点 A
        // A.1 insert (1, 1, 1)，当前: (1, 1, 1)
        // A.2 insert (2, 2, 2)，当前: (1, 1, 1), (2, 2, 2)
        // A.3 update (1, 1, 1) to (1, 3, 1), 当前: (1, 3, 1), (2, 2, 2)
        // A.4 update (2, 2, 2) to (2, 1, 2), 当前: (1, 3, 1), (2, 1, 2)
        // 实际执行完的地方 B

        // 任务重启后，从 A 开始接收数据，第一批收到数据可能为 A.1 而不是 A-B 之间所有数据。
        // 此时生成 sql: insert (1, 1, 1) on duplicate key update，
        // 则此 sql 无法写入目标表，会发生 uk(f1) 冲突。

        // 如果任务发生故障重启，需将 A-B之间的任务采用safe mode写入
        // 这里采用lazy处理，对于执行失败的采取safe mode写入

        return parallelExecSqlContexts(deleteRowChanges)
            && parallelExecSqlContexts(insertRowChanges);
    }

    private boolean parallelExecSqlContexts(Map<String, Map<RowKey, DefaultRowChange>> allRowChanges) throws Throwable {
        List<MergeDmlSqlContext> mergeDmlSqlContexts = new ArrayList<>();

        for (String tbName : allRowChanges.keySet()) {
            Collection<DefaultRowChange> tbRowChanges = allRowChanges.get(tbName).values();
            if (tbRowChanges.size() == 0) {
                continue;
            }

            int insertMode = safeMode ?
                RplConstants.INSERT_MODE_REPLACE: RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE;
            // merge
            List<MergeDmlSqlContext> sqlContexts = getMergeDmlSqlContexts(tbRowChanges, insertMode);
            mergeDmlSqlContexts.addAll(sqlContexts);
        }

        List<Future<Boolean>> futures = new ArrayList<>();

        // parallel execute, each table cost a thread
        for (MergeDmlSqlContext sqlContext : mergeDmlSqlContexts) {
            Callable<Boolean> task = () -> {
                boolean succeed = execSqlContexts(Collections.singletonList(sqlContext));
                sqlContext.setSucceed(succeed);
                return succeed;
            };
            futures.add(executorService.submit(task));
            // record merge size
            StatisticalProxy.getInstance().addMergeBatchSize(sqlContext.getOriginRowChanges().size());
        }
        return checkResultAndReRun(futures, mergeDmlSqlContexts);

    }

    protected boolean checkResultAndReRun(List<Future<Boolean>> futures,
                                          List<MergeDmlSqlContext> mergeDmlSqlContexts) throws Exception {
        boolean res = true;
        // get result
        for (Future<Boolean> future : futures) {
            res &= future.get();
        }
        // return res;
        if (res) {
            return true;
        }
        // for those failed sqlContext, excute the originRowChanges with the sql to be
        // REPLACE INTO
        futures.clear();
        res = true;
        for (final MergeDmlSqlContext sqlContext : mergeDmlSqlContexts) {
            if (sqlContext.isSucceed()) {
                continue;
            }
            log.error("merge execute failed for: {}, try serial execute", sqlContext.getDstTable());
            for (DefaultRowChange rowChange : sqlContext.getOriginRowChanges()) {
                final Callable<Boolean> task = () -> {
                    // when failed, use safe mode to forcibly write
                    List<SqlContext> newSqlContexts = getSqlContexts(rowChange, true);
                    return newSqlContexts != null && execSqlContexts(newSqlContexts);
                };
                futures.add(executorService.submit(task));
            }
        }
        for (Future<Boolean> future : futures) {
            res &= future.get();
        }
        if (!res) {
            log.error("single execute failed");
        }
        return res;
    }


}
