/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CacheDistribution {
    private int bufferReferenceCounter = 0;
    private final Map<String, Integer> dnReferenceCounterMap = new HashMap<>();
    private int dnCount;
    private int totalCounter;
    private long bufferSize;
    private CacheDistributionStrategy strategy;

    public CacheDistribution(int dnCount, long bufferSize, long maxSize, CacheDistributionStrategy strategy) {
        this.dnCount = dnCount;
        this.totalCounter = (int) (maxSize / bufferSize);
        this.bufferSize = bufferSize;
        this.strategy = strategy;
    }

    public String buildDetail() {
        StringBuilder sb = new StringBuilder();
        synchronized (this) {
            sb.append("strategy: ").append(strategy.getType()).append("\n");
            sb.append("useBufferCounter: ").append(bufferReferenceCounter).append("\n");
            for (Map.Entry<String, Integer> entry : dnReferenceCounterMap.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        return sb.toString();
    }

    public boolean tryAcquireBuffer(String dn) {
        synchronized (this) {
            int alreadyAllocatedCounter = dnReferenceCounterMap.computeIfAbsent(dn, k -> 0);
            int alreadyAllocatedDnNum = 0;
            for (Map.Entry<String, Integer> entry : dnReferenceCounterMap.entrySet()) {
                if (entry.getValue() > 0) {
                    alreadyAllocatedDnNum++;
                }
            }
            if (strategy.tryAcquireBuffer(dn, alreadyAllocatedCounter, dnCount, alreadyAllocatedDnNum, totalCounter,
                bufferReferenceCounter)) {
                bufferReferenceCounter++;
                dnReferenceCounterMap.put(dn, alreadyAllocatedCounter + 1);
                if (log.isDebugEnabled()) {
                    String fileName = CacheLoggerContext.getFileName();
                    String uuid = CacheLoggerContext.getUuid();
                    Integer seq = CacheLoggerContext.getSeq();
                    log.debug(
                        "try allocate {} buffer with file {} seq {}, dn reference counter is {} , buffer reference counter is {} , id {}",
                        dn, fileName, seq, alreadyAllocatedCounter + 1, bufferReferenceCounter, uuid);
                }
                return true;
            }
            return false;
        }
    }

    public void releaseBuffer(String dn) {
        synchronized (this) {
            Integer counter = dnReferenceCounterMap.get(dn);
            if (counter != null && counter > 0) {
                dnReferenceCounterMap.put(dn, counter - 1);
                bufferReferenceCounter--;
            } else {
                String fileName = CacheLoggerContext.getFileName();
                String uuid = CacheLoggerContext.getUuid();
                Integer seq = CacheLoggerContext.getSeq();
                String errorMsg = MessageFormat.format(
                    "duplicate release {0} buffer with file {1} seq {2}, dn reference counter is {3} , buffer reference counter is {4}, id {5}",
                    dn, fileName, seq, counter - 1, bufferReferenceCounter, uuid);
                log.error(errorMsg);
                throw new PolardbxException(errorMsg);
            }
            if (log.isDebugEnabled()) {
                String fileName = CacheLoggerContext.getFileName();
                String uuid = CacheLoggerContext.getUuid();
                Integer seq = CacheLoggerContext.getSeq();
                log.debug(
                    "release {} buffer with file {} seq {}, dn reference counter is {} , buffer reference counter is {}, id {}",
                    dn, fileName, seq, counter - 1, bufferReferenceCounter, uuid);
            }
        }
    }

    public long getUnitBuffer() {
        return bufferSize;
    }

    public int getRefCount() {
        return bufferReferenceCounter;
    }

    public void checkBufferLimitAndStrategy(long bufferSize, long maxSize, CacheDistributionType distributionType,
                                            int dnCount) {
        synchronized (this) {
            int newTotalCounter = (int) (maxSize / bufferSize);
            if (newTotalCounter != totalCounter) {
                log.warn("total counter set to {} from {}", newTotalCounter, totalCounter);
                this.totalCounter = newTotalCounter;
                this.bufferSize = bufferSize;
            }
            if (distributionType != this.strategy.getType()) {
                log.warn("new cache distribution strategy was set to {} from {}", distributionType,
                    this.strategy.getType());
                this.strategy = CacheDistributionStrategyFactory.create(distributionType);
            }

            if (dnCount != this.dnCount) {
                log.warn("new dn count was set to {} from {}", dnCount, this.dnCount);
                this.dnCount = dnCount;
            }
        }
    }
}
