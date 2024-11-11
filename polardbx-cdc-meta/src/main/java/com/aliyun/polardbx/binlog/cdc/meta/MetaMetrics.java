/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import lombok.Data;

/**
 * created by ziyang.lb
 **/
@Data
public class MetaMetrics {
    private static final MetaMetrics META_METRICS;

    private long logicDbCount;
    private long logicTableCount;
    private long phyDbCount;
    private long phyTableCount;
    private long rollbackFinishCount;
    private long rollbackAvgTime;
    private long rollbackMaxTime;
    private long rollbackMinTime;

    private long logicApplySnapshotAvgTime;
    private long logicApplySnapshotMaxTime;
    private long logicApplySnapshotMinTime;

    private long logicApplyHistoryAvgTime;
    private long logicApplyHistoryMaxTime;
    private long logicApplyHistoryMinTime;

    private long logicQueryDdlHistoryAvgTime;
    private long logicQueryDdlHistoryMaxTime;
    private long logicQueryDdlHistoryMinTime;
    private long avgLogicQueryDdlHistoryCount;
    private long avgLogicQuerySnapshotCostTime;

    private long phyApplySnapshotAvgTime;
    private long phyApplySnapshotMaxTime;
    private long phyApplySnapshotMinTime;

    private long phyApplyHistoryAvgTime;
    private long phyApplyHistoryMaxTime;
    private long phyApplyHistoryMinTime;

    private long phyQueryDdlHistoryAvgTime;
    private long phyQueryDdlHistoryMaxTime;
    private long phyQueryDdlHistoryMinTime;
    private long avgPhyQueryDdlHistoryCount;

    static {
        META_METRICS = new MetaMetrics();
    }

    private MetaMetrics() {
    }

    public static MetaMetrics get() {
        return META_METRICS;
    }

    public MetaMetrics snapshot() {
        MetaMetrics metrics = new MetaMetrics();
        metrics.logicDbCount = this.logicDbCount;
        metrics.logicTableCount = this.logicTableCount;
        metrics.phyDbCount = this.phyDbCount;
        metrics.phyTableCount = this.phyTableCount;

        metrics.rollbackFinishCount = this.rollbackFinishCount;
        metrics.rollbackAvgTime = this.rollbackAvgTime;
        metrics.rollbackMaxTime = this.rollbackMaxTime;
        metrics.rollbackMinTime = this.rollbackMinTime;

        metrics.logicApplySnapshotAvgTime = this.logicApplySnapshotAvgTime;
        metrics.logicApplySnapshotMaxTime = this.logicApplySnapshotMaxTime;
        metrics.logicApplySnapshotMinTime = this.logicApplySnapshotMinTime;

        metrics.logicApplyHistoryAvgTime = this.logicApplyHistoryAvgTime;
        metrics.logicApplyHistoryMaxTime = this.logicApplyHistoryMaxTime;
        metrics.logicApplyHistoryMinTime = this.logicApplyHistoryMinTime;

        metrics.logicQueryDdlHistoryAvgTime = this.logicQueryDdlHistoryAvgTime;
        metrics.logicQueryDdlHistoryMaxTime = this.logicQueryDdlHistoryMaxTime;
        metrics.logicQueryDdlHistoryMinTime = this.logicQueryDdlHistoryMinTime;
        metrics.avgLogicQueryDdlHistoryCount = this.avgLogicQueryDdlHistoryCount;
        metrics.avgLogicQuerySnapshotCostTime = this.avgLogicQuerySnapshotCostTime;

        metrics.phyApplySnapshotAvgTime = this.phyApplySnapshotAvgTime;
        metrics.phyApplySnapshotMaxTime = this.phyApplySnapshotMaxTime;
        metrics.phyApplySnapshotMinTime = this.phyApplySnapshotMinTime;

        metrics.phyApplyHistoryAvgTime = this.phyApplyHistoryAvgTime;
        metrics.phyApplyHistoryMaxTime = this.phyApplyHistoryMaxTime;
        metrics.phyApplyHistoryMinTime = this.phyApplyHistoryMinTime;

        metrics.phyQueryDdlHistoryAvgTime = this.phyQueryDdlHistoryAvgTime;
        metrics.phyQueryDdlHistoryMaxTime = this.phyQueryDdlHistoryMaxTime;
        metrics.phyQueryDdlHistoryMinTime = this.phyQueryDdlHistoryMinTime;
        metrics.avgPhyQueryDdlHistoryCount = this.avgPhyQueryDdlHistoryCount;

        return metrics;
    }
}
