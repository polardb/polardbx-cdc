/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

public class CacheDistributionStrategyFactory {
    public static CacheDistributionStrategy create(CacheDistributionType distributionType) {
        CacheDistributionStrategy strategy;
        if (distributionType == CacheDistributionType.PREEMPTIVE) {
            strategy = new PreemptiveCacheDistribution();
        } else {
            strategy = new AverageCacheDistribution();
        }
        return strategy;
    }
}
