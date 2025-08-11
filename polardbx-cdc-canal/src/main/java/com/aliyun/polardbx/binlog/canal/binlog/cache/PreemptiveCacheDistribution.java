/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

public class PreemptiveCacheDistribution implements CacheDistributionStrategy {

    @Override
    public CacheDistributionType getType() {
        return CacheDistributionType.PREEMPTIVE;
    }

    @Override
    public boolean tryAcquireBuffer(String dn, int alreadyAllocatedCounter, int dnNum, int alreadyAllocatedDnNum,
                                    int totalCounter, int totalAllocatedCounter) {
        int reserveCount = Math.max(dnNum - alreadyAllocatedDnNum, 0);
        int maxCounter;
        if (alreadyAllocatedCounter > 0) {
            maxCounter = totalCounter - reserveCount;
        } else {
            maxCounter = totalCounter - reserveCount + 1;
        }
        return totalAllocatedCounter < maxCounter;
    }
}
