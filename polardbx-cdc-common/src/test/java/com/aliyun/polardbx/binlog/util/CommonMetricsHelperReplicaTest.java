/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplStatMetrics;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CommonMetricsHelperReplicaTest {

    @Test
    public void addReplicaMetrics_OmitsUnknownDelay() {
        RplStatMetrics statMetrics = completeMetrics();
        List<CommonMetrics> metrics = new ArrayList<>();

        CommonMetricsHelper.addReplicaMetrics(metrics, statMetrics, "replica_");

        Assert.assertEquals(20, metrics.size());
        Assert.assertFalse(metrics.stream().anyMatch(metric -> metric.getKey().equals("replica_trueDelayMills")));
        Assert.assertTrue(metrics.stream()
            .anyMatch(metric -> metric.getKey().equals("replica_outRps") && metric.getValue() == 1D));
    }

    @Test
    public void addReplicaMetrics_ReportsKnownDelayWithPrefixAndValue() {
        RplStatMetrics statMetrics = completeMetrics();
        statMetrics.setTrueDelayMills(123L);
        List<CommonMetrics> metrics = new ArrayList<>();

        CommonMetricsHelper.addReplicaMetrics(metrics, statMetrics, "replica_");

        Assert.assertEquals(21, metrics.size());
        Assert.assertTrue(metrics.stream()
            .anyMatch(metric -> metric.getKey().equals("replica_trueDelayMills") && metric.getValue() == 123D));
    }

    private static RplStatMetrics completeMetrics() {
        RplStatMetrics statMetrics = new RplStatMetrics();
        statMetrics.setOutRps(1L);
        statMetrics.setApplyCount(1L);
        statMetrics.setInEps(1L);
        statMetrics.setOutBps(1L);
        statMetrics.setInBps(1L);
        statMetrics.setOutInsertRps(1L);
        statMetrics.setOutUpdateRps(1L);
        statMetrics.setOutDeleteRps(1L);
        statMetrics.setReceiveDelay(1L);
        statMetrics.setProcessDelay(1L);
        statMetrics.setMergeBatchSize(1L);
        statMetrics.setRt(1L);
        statMetrics.setSkipCounter(1L);
        statMetrics.setSkipExceptionCounter(1L);
        statMetrics.setPersistMsgCounter(1L);
        statMetrics.setMsgCacheSize(1L);
        statMetrics.setCpuUseRatio(1);
        statMetrics.setMemUseRatio(1);
        statMetrics.setFullGcCount(1L);
        statMetrics.setTotalCommitCount(1L);
        return statMetrics;
    }
}
