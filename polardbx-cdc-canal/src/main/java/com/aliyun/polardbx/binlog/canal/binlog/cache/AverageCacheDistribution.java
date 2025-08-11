/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

public class AverageCacheDistribution implements CacheDistributionStrategy {

    @Override
    public CacheDistributionType getType() {
        return CacheDistributionType.AVERAGE;
    }

    @Override
    public boolean tryAcquireBuffer(String dn, int alreadyAllocatedCounter, int dnNum, int alreadyAllocatedDnNum,
                                    int totalCounter, int totalAllocatedCounter) {
        int dnCounterLimit = totalCounter / dnNum;
        return alreadyAllocatedCounter < dnCounterLimit;
    }
}
