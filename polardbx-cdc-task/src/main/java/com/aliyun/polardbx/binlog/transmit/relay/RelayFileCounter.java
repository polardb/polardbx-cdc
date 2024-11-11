/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * created by ziyang.lb
 **/
public class RelayFileCounter {
    private final ConcurrentHashMap<Integer, Integer> countMap = new ConcurrentHashMap<>();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private volatile boolean dirty = true;
    private volatile int summary;

    public int getTotalRelayFileCount() {
        if (dirty) {
            readWriteLock.readLock().lock();
            try {
                HashMap<Integer, Integer> map = new HashMap<>(countMap);
                summary = map.values().stream().reduce(0, Integer::sum);
                dirty = false;
            } finally {
                readWriteLock.readLock().unlock();
            }
        }
        return summary;
    }

    public int getCountByStream(int streamSeq) {
        Integer count = countMap.get(streamSeq);
        return count == null ? 0 : count;
    }

    public void setCount(int streamSeq, int fileCount) {
        readWriteLock.writeLock().lock();
        try {
            countMap.put(streamSeq, fileCount);
            dirty = true;
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

}
