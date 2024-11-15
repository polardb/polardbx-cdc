/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.unit;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.Data;
import org.joda.time.DateTime;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author shicai.xsc 2021/5/13 13:08
 * @since 5.0.0.0
 */
@Data
public class StatisticCounter {

    private Queue<Counter> counters = new ConcurrentLinkedQueue<>();
    private AtomicLong totalCount = new AtomicLong(0);

    public StatisticCounter() {
    }

    public void add(long count) {
        Counter counter = new Counter(System.currentTimeMillis(), count);
        counters.add(counter);
        totalCount.addAndGet(count);
    }

    public long getCount() {
        Counter counter = counters.peek();
        if (counter == null) {
            return 0;
        }
        return counter.count;
    }

    public long getSpeed() {
        return getTotalCount() / DynamicApplicationConfig.getInt(ConfigKeys.RPL_STATE_METRICS_FLUSH_INTERVAL_SECOND);
    }

    public synchronized long getTotalCount() {
        int size = counters.size();
        for (int i = 0; i < size; i++) {
            Counter peek = counters.peek();
            if (peek == null) {
                return 0;
            }
            if (!isOutdated(peek)) {
                break;
            }

            totalCount.addAndGet(-peek.count);
            counters.remove();
        }
        return totalCount.get();
    }

    private boolean isOutdated(Counter counter) {
        return DateTime.now()
            .minusSeconds(DynamicApplicationConfig.getInt(ConfigKeys.RPL_STATE_METRICS_FLUSH_INTERVAL_SECOND)).
            isAfter(counter.timestamp);
    }

    private static class Counter {
        private final long timestamp;
        private final long count;

        public Counter(long timestamp, long count) {
            this.timestamp = timestamp;
            this.count = count;
        }
    }
}
