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

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author jiyue 2021/10/14 11:32
 * @since 5.0.0.0
 */

@Slf4j
public class FullCopyApplier extends MysqlApplier {
    public FullCopyApplier(ApplierConfig applierConfig, HostInfo hostInfo) {
        super(applierConfig, hostInfo);
    }

    @Override
    public boolean apply(List<DBMSEvent> dbmsEvents) {
        if (dbmsEvents == null || dbmsEvents.size() == 0) {
            return true;
        }
        try {
            boolean res = dmlApply(dbmsEvents);
            if (res) {
                logCommitInfo(dbmsEvents);
            }
            return res;
        } catch (Throwable e) {
            log.error("apply failed", e);
            return false;
        }
    }

    @Override
    protected boolean dmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        if (dbmsEvents == null || dbmsEvents.size() == 0) {
            return true;
        }
        return parallelExecSqlContexts((List<DefaultRowChange>) (List<?>) dbmsEvents);
    }

    private boolean parallelExecSqlContexts(List<DefaultRowChange> allRowChanges) throws Throwable {
        List<SqlContextV2> sqlContexts = getMergeInsertIgnoreSqlContexts(allRowChanges);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (SqlContextV2 sqlContext : sqlContexts) {
            Callable<Boolean> task = () -> {
                boolean succeed = execSqlContextsV2(Arrays.asList(sqlContext));
                sqlContext.setSucceed(succeed);
                return succeed;
            };
            futures.add(executorService.submit(task));
            // record merge size
            StatisticalProxy.getInstance().addMergeBatchSize(sqlContext.getParamsList().size());
        }
        boolean res = true;
        for (Future<Boolean> future : futures) {
            res &= future.get();
        }
        return res;
    }


    protected List<SqlContextV2> getMergeInsertIgnoreSqlContexts(List<DefaultRowChange> rowChanges)
        throws Throwable {
        List<SqlContextV2> sqlContexts = new ArrayList<>();
        int nowMergeCount = 0;
        DefaultRowChange mergedRowChange = new DefaultRowChange();
        DefaultRowChange nowRowChange;
        for (int i = 0; i < rowChanges.size(); i++) {
            nowRowChange = rowChanges.get(i);
            for (int j = 1; j <= nowRowChange.getRowSize(); j++) {
                if (nowMergeCount == 0) {
                    mergedRowChange = new DefaultRowChange();
                    mergedRowChange.setAction(nowRowChange.getAction());
                    mergedRowChange.setSchema(nowRowChange.getSchema());
                    mergedRowChange.setTable(nowRowChange.getTable());
                    mergedRowChange.setColumnSet(nowRowChange.getColumnSet());
                    mergedRowChange.setDataSet(new ArrayList<>(applierConfig.getMergeBatchSize()));
                }
                mergedRowChange.addRowData(nowRowChange.getRowData(j));
                nowMergeCount++;
                // 达到batch数要求 || 是最后一批数据
                if (nowMergeCount == applierConfig.getMergeBatchSize() ||
                    (i == rowChanges.size() -1 && j == nowRowChange.getRowSize())) {
                    TableInfo dstTbInfo = dbMetaCache.getTableInfo(mergedRowChange.getSchema(), mergedRowChange.getTable());
                    SqlContextV2 sqlContext = ApplyHelper.getMergeInsertSqlExecContextV2(mergedRowChange, dstTbInfo,
                        RplConstants.INSERT_MODE_INSERT_IGNORE);
                    sqlContexts.add(sqlContext);
                    nowMergeCount = 0;
                }
            }
        }
        return sqlContexts;
    }
}

