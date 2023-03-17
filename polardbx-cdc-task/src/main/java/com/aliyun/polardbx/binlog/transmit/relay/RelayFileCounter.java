/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
