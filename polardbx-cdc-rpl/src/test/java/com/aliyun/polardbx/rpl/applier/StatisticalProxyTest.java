/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplStatMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.error.DdlApplyException;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.pipeline.SerialPipeline;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_APPLY_DRY_RUN_ENABLED;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author shicai.xsc 2021/5/13 13:38
 * @since 5.0.0.0
 */
public class StatisticalProxyTest extends RplWithGmsTablesBaseTest {

    @Test
    public void testFill() {
        int i = 1;
        int avgSeconds = DynamicApplicationConfig.getInt(ConfigKeys.RPL_STATE_METRICS_FLUSH_INTERVAL_SECOND);
        while (i++ <= avgSeconds) {
            StatMetrics.getInstance().addSkipCount(2);
            StatMetrics.getInstance().addMergeBatchSize(300);
            StatMetrics.getInstance().addApplyCount(1);
            StatMetrics.getInstance().addRt(10);
            StatMetrics.getInstance().addInMessageCount(600);
            StatMetrics.getInstance().addOutMessageCount(300);
        }
        RplStatMetrics rplStatMetrics = new RplStatMetrics();
        StatisticalProxy.getInstance().flushInterval =
            DynamicApplicationConfig.getInt(ConfigKeys.RPL_STATE_METRICS_FLUSH_INTERVAL_SECOND);
        StatisticalProxy.getInstance().fill(rplStatMetrics, StatMetrics.getInstance(), null,
            null);
        Assert.assertEquals(rplStatMetrics.getApplyCount().intValue(), 1);
        Assert.assertEquals(rplStatMetrics.getApplyCount().intValue(), 1);
        Assert.assertEquals(rplStatMetrics.getOutRps().intValue(), 300);
        Assert.assertEquals(rplStatMetrics.getInEps().intValue(), 600);
        Assert.assertEquals(rplStatMetrics.getRt().intValue(), 10);
    }

    @Test
    public void testDryRunApply() throws Exception {
        List<DBMSEvent> rowChanges = Lists.newArrayList(new DefaultRowChange());
        try {
            mockConfig(RPL_APPLY_DRY_RUN_ENABLED, String.valueOf(false));
            StatisticalProxy.getInstance().apply(rowChanges);
            Assert.fail("can`t run in dry run mode");

            StatisticalProxy.getInstance().tranApply(Lists.newArrayList());
            Assert.fail("can`t run in dry run mode");
        } catch (Exception ignored) {
        }

        try {
            mockConfig(RPL_APPLY_DRY_RUN_ENABLED, String.valueOf(true));
            StatisticalProxy.getInstance().apply(rowChanges);
            StatisticalProxy.getInstance().tranApply(Lists.newArrayList());
        } catch (NullPointerException ignored) {
            Assert.fail("must run in dry run mode!");
        }
    }

    @Test
    public void testInnerApply() throws Exception {
        DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
        when(rowChange.toString()).thenReturn("xxx");
        List<DBMSEvent> dmlEvents = Lists.newArrayList();
        dmlEvents.add(rowChange);
        dmlEvents.add(rowChange);

        List<DBMSEvent> ddlEvents = Lists.newArrayList();
        ddlEvents.add(new DefaultQueryLog());

        Throwable t1 = new RuntimeException();
        Throwable t2 = new RuntimeException();
        Throwable t3 = new RuntimeException();
        BaseApplier mysqlApplier = Mockito.mock(BaseApplier.class);
        StatisticalProxy.getInstance().setApplier(mysqlApplier);
        doThrow(t1).when(mysqlApplier).apply(argThat(list -> list == dmlEvents));
        doThrow(t2).when(mysqlApplier).apply(argThat(list -> list == ddlEvents));
        doThrow(t3).when(mysqlApplier).apply(argThat(list -> list != ddlEvents && list != dmlEvents));

        try {
            StatisticalProxy.getInstance().innerApply(dmlEvents);
            Assert.fail();
        } catch (Throwable t) {
            Assert.assertEquals(t3, t);
        }

        try {
            StatisticalProxy.getInstance().innerApply(ddlEvents);
            Assert.fail();
        } catch (Throwable t) {
            Assert.assertTrue(t instanceof DdlApplyException);
            Assert.assertEquals(t2, t.getCause());
        }
    }

    @Test
    public void testInnerTranApply() throws Exception {
        List<Transaction> dmlTransactions = Lists.newArrayList();
        dmlTransactions.add(Mockito.mock(Transaction.class));
        dmlTransactions.add(Mockito.mock(Transaction.class));
        Transaction.RangeIterator rangeIterator = Mockito.mock(Transaction.RangeIterator.class);
        when(rangeIterator.hasNext()).thenReturn(false);
        when(dmlTransactions.get(0).rangeIterator()).thenReturn(rangeIterator);
        when(dmlTransactions.get(1).rangeIterator()).thenReturn(rangeIterator);

        List<Transaction> ddlTransactions = Lists.newArrayList();
        ddlTransactions.add(Mockito.mock(Transaction.class));
        when(ddlTransactions.get(0).getEventCount()).thenReturn(1L);
        when(ddlTransactions.get(0).peekFirst()).thenReturn(new DefaultQueryLog());

        Throwable t1 = new RuntimeException();
        Throwable t2 = new RuntimeException();
        Throwable t3 = new RuntimeException();
        BaseApplier mysqlApplier = Mockito.mock(BaseApplier.class);
        StatisticalProxy.getInstance().setApplier(mysqlApplier);
        doThrow(t1).when(mysqlApplier).tranApply(argThat(list -> list == dmlTransactions));
        doThrow(t2).when(mysqlApplier).tranApply(argThat(list -> list == ddlTransactions));
        doThrow(t3).when(mysqlApplier).tranApply(argThat(list -> list != dmlTransactions && list != ddlTransactions));

        try {
            StatisticalProxy.getInstance().innerTranApply(dmlTransactions);
            Assert.fail();
        } catch (Throwable t) {
            Assert.assertEquals(t3, t);
        }

        try {
            StatisticalProxy.getInstance().innerTranApply(ddlTransactions);
            Assert.fail();
        } catch (Throwable t) {
            Assert.assertTrue(t instanceof DdlApplyException);
            Assert.assertEquals(t2, t.getCause());
        }
    }

    @Test
    public void testRetryer() {
        Retryer<Void> retryer = StatisticalProxy.getInstance().buildRetryer(10, 5);

        AtomicInteger count = new AtomicInteger(0);
        try {
            retryer.call(() -> {
                count.incrementAndGet();
                throw new RuntimeException();
            });
            Assert.fail();
        } catch (RetryException | ExecutionException e) {
            Assert.assertEquals(5, count.get());
        }

        count.set(0);
        try {
            retryer.call(() -> {
                count.incrementAndGet();
                throw new DdlApplyException();
            });
            Assert.fail();
        } catch (RetryException | ExecutionException e) {
            Assert.assertEquals(1, count.get());
        }
    }

    @Test
    public void testInit() {
        BaseExtractor extractor = new BaseExtractor();
        extractor.setExtractorConfig(new ExtractorConfig());
        SerialPipeline pipeline = new SerialPipeline(new PipelineConfig(), extractor, null);
        TaskContext.getInstance().setPipeline(pipeline);
        TaskContext.getInstance().setTask(new RplTask());
        TaskContext.getInstance().getTask().setPosition("binlog.007113:0395877467#1434251977.1721277503.rtso(721956111966325971217506263351406223450000002488321155)");
        StatisticalProxy.getInstance().init();
        Assert.assertEquals(StatisticalProxy.getInstance().getPosition(), "binlog.007113:0395877467#1434251977.1721277503.rtso(721956111966325971217506263351406223450000002488321155)");
    }

    @Test
    public void testInnerApplyWithCommitCount() throws Exception {
        try(MockedStatic<StatMetrics> statMetricsMock = mockStatic(StatMetrics.class)){
            StatMetrics statMetrics = new StatMetrics();
            statMetricsMock.when(StatMetrics::getInstance).thenReturn(statMetrics);
            BaseExtractor extractor = new BaseExtractor();
            extractor.setExtractorConfig(new ExtractorConfig());
            MysqlApplier applier = Mockito.mock(MysqlApplier.class);
            SerialPipeline pipeline = new SerialPipeline(new PipelineConfig(), extractor, applier);
            TaskContext.getInstance().setPipeline(pipeline);
            StatisticalProxy statisticalProxy = StatisticalProxy.getInstance();
            statisticalProxy.init();
            mockConfig(ConfigKeys.IS_LAB_ENV, "true");
            List<DBMSEvent> dbmsEventList = Lists.newArrayList();
            for (int i = 0; i < 5; i++) {
                dbmsEventList.add(new DefaultQueryLog("polardbx", "select 1", new Timestamp(System.currentTimeMillis()), 1, 1));
            }
            dbmsEventList.add(new DefaultRowChange(DBMSAction.INSERT, "polardbx", "t1", new DefaultColumnSet(Lists.newArrayList()), Lists.newArrayList(), Lists.newArrayList()));
            statisticalProxy.innerApply(dbmsEventList);
            Assert.assertEquals(1L, StatMetrics.getInstance().getPeriodCommitCount().get());
        }
    }
}
