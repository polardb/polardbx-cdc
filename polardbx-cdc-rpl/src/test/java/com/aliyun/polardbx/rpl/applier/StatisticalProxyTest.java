/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplStatMetrics;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_APPLY_DRY_RUN_ENABLED;
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
        try (MockedStatic<DynamicApplicationConfig> theMockAppConfig = Mockito.mockStatic(
            DynamicApplicationConfig.class)) {
            when(DynamicApplicationConfig.getBoolean(RPL_APPLY_DRY_RUN_ENABLED)).thenReturn(false);
            StatisticalProxy.getInstance().apply(rowChanges);
            Assert.fail("can`t run in dry run mode");

            StatisticalProxy.getInstance().tranApply(Lists.newArrayList());
            Assert.fail("can`t run in dry run mode");
        } catch (NullPointerException ignored) {
        }

        try (MockedStatic<DynamicApplicationConfig> theMockAppConfig = Mockito.mockStatic(
            DynamicApplicationConfig.class)) {
            when(DynamicApplicationConfig.getBoolean(RPL_APPLY_DRY_RUN_ENABLED)).thenReturn(true);
            StatisticalProxy.getInstance().apply(rowChanges);
            StatisticalProxy.getInstance().tranApply(Lists.newArrayList());
        } catch (NullPointerException ignored) {
            Assert.fail("must run in dry run mode!");
        }
    }

}
