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

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static org.mockito.Mockito.when;

public class MergeApplierTest extends RplWithGmsTablesBaseTest {

    @Test
    public void testParallelExecuteDMLGroupByTable() throws Exception {
        Map<String, Map<RowKey, DefaultRowChange>> rowChanges = new HashMap<>();
        rowChanges.put("t1", prepareRowChanges("d1", "t1"));
        rowChanges.put("t2", prepareRowChanges("d1", "t2"));
        rowChanges.put("t3", prepareRowChanges("d1", "t3"));

        MergeApplier mergeApplierActual = new MergeApplier(new ApplierConfig(), null, null);
        MergeApplier mergeApplier = Mockito.spy(mergeApplierActual);
        when(mergeApplier.getMergeDmlSqlContexts(
            Mockito.anyCollection(), Mockito.anyInt())).thenReturn(
            Lists.newArrayList());
        when(mergeApplier.getCallableTask(Mockito.anyList(), Mockito.any())).thenReturn(() -> null);

        mergeApplier.buildExecutorService();
        List<Future<Void>> futures = mergeApplier.parallelExecuteDMLGroupByTable(rowChanges);
        Assert.assertEquals(3, futures.size());
    }

    @Test
    public void testGetCallableTask() throws Exception {
        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        when(dbMetaCache.getConnection(Mockito.anyString())).thenReturn(null);

        try (MockedStatic<DmlApplyHelper> dmlApplyHelper = Mockito.mockStatic(DmlApplyHelper.class)) {
            MergeApplier mergeApplier = new MergeApplier(new ApplierConfig(), null, null);

            MergeDmlSqlContext sqlContext = new MergeDmlSqlContext("", "", "", Lists.newArrayList());
            Callable<Void> callable = mergeApplier.getCallableTask(sqlContext, dbMetaCache);
            callable.call();
            Assert.assertTrue(sqlContext.succeed);

            List<MergeDmlSqlContext> sqlContexts = Lists.newArrayList(
                new MergeDmlSqlContext("", "", "", Lists.newArrayList()),
                new MergeDmlSqlContext("", "", "", Lists.newArrayList()),
                new MergeDmlSqlContext("", "", "", Lists.newArrayList()));
            Callable<Void> callable2 = mergeApplier.getCallableTask(sqlContexts, dbMetaCache);
            callable2.call();
            sqlContexts.forEach(s -> Assert.assertTrue(s.succeed));
        }

    }

    private Map<RowKey, DefaultRowChange> prepareRowChanges(String schema, String table) {
        Map<RowKey, DefaultRowChange> rowChanges = new HashMap<>();

        for (int i = 0; i < 100; i++) {
            DefaultRowChange rowChange =
                new DefaultRowChange(DBMSAction.INSERT, schema, table, new DefaultColumnSet(Lists.newArrayList()));
            rowChange.setAction(DBMSAction.INSERT);
            rowChange.setSchema(schema);
            rowChange.setTable(table);

            Map<Integer, Serializable> keys = new HashMap<>();
            keys.put(1, i);
            rowChanges.put(new RowKey(rowChange, keys), rowChange);
        }

        return rowChanges;
    }
}
