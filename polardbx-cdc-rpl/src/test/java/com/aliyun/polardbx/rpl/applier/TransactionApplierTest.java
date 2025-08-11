/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.sql.Timestamp;
import java.util.List;

import static org.mockito.Mockito.mockStatic;

public class TransactionApplierTest extends BaseTest {

    @Test
    public void updateMetricsTestWithNoLab(){
        try(MockedStatic<StatMetrics> statMetricsMock = mockStatic(StatMetrics.class)){
            StatMetrics statMetrics = new StatMetrics();
            statMetricsMock.when(StatMetrics::getInstance).thenReturn(statMetrics);
            mockConfig(ConfigKeys.IS_LAB_ENV, "false");
            ApplierConfig applierConfig = new ApplierConfig();
            TransactionApplier applier = new TransactionApplier(applierConfig, null, null);
            List<Transaction> transactionList = Lists.newArrayList();
            Transaction transaction = new Transaction(null, null);
            transaction.appendRowChange(new DefaultRowChange());
            transaction.appendQueryLog(new DefaultQueryLog("polardbx", "CALL trigger_sync_point_trx(0)", new Timestamp(System.currentTimeMillis()), 1, 1));
            transactionList.add(transaction);
            applier.updateMetrics(transactionList);
            Assert.assertEquals(2L, statMetrics.getPeriodCommitCount().get());
        }
    }

    @Test
    public void updateMetricsTestWithLab(){
        try(MockedStatic<StatMetrics> statMetricsMock = mockStatic(StatMetrics.class)){
            StatMetrics statMetrics = new StatMetrics();
            statMetricsMock.when(StatMetrics::getInstance).thenReturn(statMetrics);
            mockConfig(ConfigKeys.IS_LAB_ENV, "true");
            ApplierConfig applierConfig = new ApplierConfig();
            TransactionApplier applier = new TransactionApplier(applierConfig, null, null);
            List<Transaction> transactionList = Lists.newArrayList();
            Transaction transaction = new Transaction(null, null);
            transaction.appendRowChange(new DefaultRowChange());
            transaction.appendQueryLog(new DefaultQueryLog("polardbx", "CALL trigger_sync_point_trx(0)", new Timestamp(System.currentTimeMillis()), 1, 1));
            transactionList.add(transaction);
            applier.updateMetrics(transactionList);
            Assert.assertEquals(1L, StatMetrics.getInstance().getPeriodCommitCount().get());
        }
    }

}
