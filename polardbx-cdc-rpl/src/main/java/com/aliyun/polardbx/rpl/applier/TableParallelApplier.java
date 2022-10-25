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


/**
 * @author jiyue 2022/05/21 13:44
 * @since 5.0.0.0
 */

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class TableParallelApplier extends MysqlApplier {

    public TableParallelApplier(ApplierConfig applierConfig, HostInfo hostInfo) {
        super(applierConfig, hostInfo);
    }

    @Override
    protected boolean dmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        Map<String, List<SqlContext>> parallelSqlContexts = new HashMap<>();
        for (DBMSEvent event : dbmsEvents) {
            DBMSRowChange rowChangeEvent = (DBMSRowChange) event;
            List<SqlContext> sqlContexts = getSqlContexts(rowChangeEvent, safeMode);
            if (!CollectionUtils.isEmpty(sqlContexts)) {
                parallelSqlContexts.putIfAbsent(sqlContexts.get(0).getFullTable(), new ArrayList<>());
                parallelSqlContexts.get(sqlContexts.get(0).getFullTable()).addAll(sqlContexts);
            }
        }
        List<Future<Boolean>> futures = new ArrayList<>();
        for (List<SqlContext> contexts: parallelSqlContexts.values()) {
            Callable<Boolean> task = () -> execSqlContexts(contexts);
            futures.add(executorService.submit(task));
        }
        boolean res = true;
        for (Future<Boolean> future : futures) {
            res &= future.get();
        }
        return res;
    }
}
