/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ProcessorContextTest extends BaseTest {
    @Test
    public void tryAutoQuickSearchTest() throws InterruptedException {
        setConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_AUTO_QUICK_MODE_THRESHOLD_SECOND, "2");
        setConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "false");
        long pushBackwardSec = DynamicApplicationConfig.getInt(
            ConfigKeys.TASK_RECOVER_SEARCH_TSO_AUTO_QUICK_MODE_SWITCH_PUSH_BACKWARD_SECOND);
        String tso = "713564074829204691216667059638448988160000000000000000";
        long searchTso = CommonUtils.getTsoTimestamp(tso);
        ProcessorContext context = new ProcessorContext(null, searchTso, 0L);
        context.tryAutoQuickSearch();
        Assert.assertFalse(context.isInQuickMode());
        Assert.assertEquals(searchTso, context.getSearchTSO().longValue());
        Thread.sleep(3000);
        context.tryAutoQuickSearch();
        Assert.assertTrue(context.isInQuickMode());
        Assert.assertEquals(searchTso - CommonUtils.convertToTsoUnit(pushBackwardSec, TimeUnit.SECONDS),
            context.getSearchTSO().longValue());
        // 先获取毫秒级时间戳，再转换成tso， 会丢失22位逻辑时间戳和保留字节
        long convertTso = CommonUtils.convertToTsoUnit(
            CommonUtils.getTsoPhysicalTime(tso, TimeUnit.MILLISECONDS) - TimeUnit.SECONDS.toMillis(pushBackwardSec),
            TimeUnit.MILLISECONDS);
        Assert.assertEquals(convertTso,
            context.getSearchTSO().longValue() & ~0x3FFFFF);
    }

    @Test
    public void setQuickSearchTest() {
        long pushBackwardSec = DynamicApplicationConfig.getInt(
            ConfigKeys.TASK_RECOVER_SEARCH_TSO_AUTO_QUICK_MODE_SWITCH_PUSH_BACKWARD_SECOND);
        String tso = "713564074829204691216667059638448988160000000000000000";
        long searchTso = CommonUtils.getTsoTimestamp(tso);
        ProcessorContext context = new ProcessorContext(null, searchTso, 0L);
        context.setInQuickMode(false);
        Assert.assertFalse(context.isInQuickMode());
        Assert.assertEquals(searchTso, context.getSearchTSO().longValue());
        context.setInQuickMode(true);
        Assert.assertTrue(context.isInQuickMode());
        Assert.assertEquals(searchTso - CommonUtils.convertToTsoUnit(pushBackwardSec, TimeUnit.SECONDS),
            context.getSearchTSO().longValue());
        // 先获取毫秒级时间戳，再转换成tso， 会丢失22位逻辑时间戳和保留字节
        long convertTso = CommonUtils.convertToTsoUnit(
            CommonUtils.getTsoPhysicalTime(tso, TimeUnit.MILLISECONDS) - TimeUnit.SECONDS.toMillis(pushBackwardSec),
            TimeUnit.MILLISECONDS);
        Assert.assertEquals(convertTso,
            context.getSearchTSO().longValue() & ~0x3FFFFF);
    }

    @Test
    public void setQuickSearchWithBaseTest() {
        String tso = "713564074829204691216667059638448988160000000000000000";
        long searchTso = CommonUtils.getTsoTimestamp(tso);
        ProcessorContext context = new ProcessorContext(null, searchTso, searchTso - 1);
        context.setInQuickMode(false);
        Assert.assertFalse(context.isInQuickMode());
        Assert.assertEquals(searchTso, context.getSearchTSO().longValue());
        context.setInQuickMode(true);
        Assert.assertTrue(context.isInQuickMode());
        Assert.assertEquals(searchTso,
            context.getSearchTSO().longValue());
    }

    @Test(expected = PolardbxException.class)
    public void testOnStartError(){
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        ProcessorContext context = new ProcessorContext(authenticationInfo, 100L, 0L);
        TranPosition tranPosition = new TranPosition();
        tranPosition.setXid("test-xid");
        tranPosition.setBegin(new BinlogPosition("000", 888, -1, -1));
        BinlogPosition endPos = new BinlogPosition("001", 999, -1, -1);
        context.setLastTSO(888L);
        context.onComplete(tranPosition.getXid(), endPos);
        context.setReceivedCreateCdcPhyDbEvent(true, endPos);
        context.setFind("");
        context.onStart(tranPosition);
    }
}
