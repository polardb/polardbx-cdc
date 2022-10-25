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
public class FullCopyApplier extends MergeApplier {
    public FullCopyApplier(ApplierConfig applierConfig, HostInfo hostInfo) {
        super(applierConfig, hostInfo);
    }

    @Override
    protected boolean dmlApply(List<DBMSEvent> dbmsEvents) throws Throwable {
        if (dbmsEvents == null || dbmsEvents.size() == 0) {
            return true;
        }
        return parallelExecSqlContexts((List<DefaultRowChange>) (List<?>) dbmsEvents);
    }

    private boolean parallelExecSqlContexts(List<DefaultRowChange> allRowChanges) throws Throwable {
        // merge

        List<MergeDmlSqlContext> sqlContexts =
            getMergeDmlSqlContexts(allRowChanges, RplConstants.INSERT_MODE_INSERT_IGNORE);
        List<MergeDmlSqlContext> mergeDmlSqlContexts = new ArrayList<>(sqlContexts);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (MergeDmlSqlContext sqlContext : mergeDmlSqlContexts) {
            Callable<Boolean> task = () -> {
                boolean succeed = execSqlContexts(Arrays.asList(sqlContext));
                sqlContext.setSucceed(succeed);
                return succeed;
            };
            futures.add(executorService.submit(task));
            // record merge size
            StatisticalProxy.getInstance().addMergeBatchSize(sqlContext.getOriginRowChanges().size());
        }
        return checkResultAndReRun(futures, mergeDmlSqlContexts);
    }
}

