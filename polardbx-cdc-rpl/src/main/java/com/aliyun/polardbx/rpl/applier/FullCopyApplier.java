/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static com.aliyun.polardbx.rpl.applier.SqlContextExecutor.execSqlContextsV2;

/**
 * @author jiyue 2021/10/14 11:32
 * @since 5.0.0.0
 */

@Slf4j
public class FullCopyApplier extends MysqlApplier {

    DataSource dataSource;

    @Override
    public void init() throws Exception {
        super.init();
        dataSource = dbMetaCache.getBuiltInDefaultDataSource();
    }

    public FullCopyApplier(ApplierConfig applierConfig, HostInfo hostInfo, HostInfo srcHostInfo) {
        super(applierConfig, hostInfo, srcHostInfo);
    }

    @Override
    public void apply(List<DBMSEvent> dbmsEvents) throws Exception {
        if (dbmsEvents == null || dbmsEvents.isEmpty()) {
            return;
        }
        dmlApply(dbmsEvents);
        logCommitInfo(dbmsEvents);
    }

    @Override
    protected void dmlApply(List<DBMSEvent> dbmsEvents) throws Exception {
        if (dbmsEvents == null || dbmsEvents.isEmpty()) {
            return;
        }
        try {
            parallelExecuteDML((List<DefaultRowChange>) (List<?>) dbmsEvents, false);
        } catch (Exception e) {
            parallelExecuteDML((List<DefaultRowChange>) (List<?>) dbmsEvents, true);
        }
    }

    private void parallelExecuteDML(List<DefaultRowChange> allRowChanges, boolean isIgnore) throws Exception {
        List<SqlContextV2> sqlContexts = getMergeInsertSqlContexts(allRowChanges, isIgnore);
        List<Future<Void>> futures = new ArrayList<>();
        for (SqlContextV2 sqlContext : sqlContexts) {
            Callable<Void> task = () -> {
                execSqlContextsV2(dataSource, Collections.singletonList(sqlContext));
                sqlContext.setSucceed(true);
                return null;
            };
            futures.add(executorService.submit(task));
            // record merge size
            StatMetrics.getInstance().addMergeBatchSize(sqlContext.getParamsList().size());
        }
        PolardbxException exception = CommonUtil.waitAllTaskFinishedAndReturn(futures);
        if (exception != null) {
            throw exception;
        }
    }

    protected List<SqlContextV2> getMergeInsertSqlContexts(List<DefaultRowChange> rowChanges, boolean isIgnore)
        throws Exception {
        int insertMode = isIgnore ? RplConstants.INSERT_MODE_INSERT_IGNORE :
            RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE;
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
                    (i == rowChanges.size() - 1 && j == nowRowChange.getRowSize())) {
                    TableInfo dstTbInfo =
                        dbMetaCache.getTableInfo(mergedRowChange.getSchema(), mergedRowChange.getTable());
                    SqlContextV2 sqlContext = DmlApplyHelper.getMergeInsertSqlExecContextV2(mergedRowChange, dstTbInfo,
                        insertMode);
                    sqlContexts.add(sqlContext);
                    nowMergeCount = 0;
                }
            }
        }
        return sqlContexts;
    }
}

