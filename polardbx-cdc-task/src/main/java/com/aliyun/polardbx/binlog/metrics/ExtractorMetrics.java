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
package com.aliyun.polardbx.binlog.metrics;

import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author yanfenglin
 */
public class ExtractorMetrics {

    private static final ExtractorMetrics INSTANCE = new ExtractorMetrics();

    private final AtomicLong tsoTranCount = new AtomicLong(0);
    private final AtomicLong noTsoTranCount = new AtomicLong(0);
    private final AtomicLong totalTranCount = new AtomicLong(0);
    private final AtomicLong heartbeatCount = new AtomicLong(0);
    private final AtomicLong eventTotalCount = new AtomicLong(0);
    private final AtomicLong netIn = new AtomicLong(0);
    private final AtomicLong maxDelay = new AtomicLong(0);
    private final AtomicLong maxSorterQueuedSize = new AtomicLong(0);

    private ExtractorMetrics() {
    }

    public static ExtractorMetrics get() {
        return INSTANCE;
    }

    public ExtractorMetrics snapshot() {
        ExtractorMetrics snapshot = new ExtractorMetrics();
        snapshot.tsoTranCount.set(this.tsoTranCount.get());
        snapshot.noTsoTranCount.set(this.noTsoTranCount.get());
        snapshot.totalTranCount.set(this.totalTranCount.get());
        snapshot.heartbeatCount.set(this.heartbeatCount.get());
        snapshot.eventTotalCount.set(this.eventTotalCount.get());

        long maxSorterQueuedSize = 0;
        long minWhen = System.currentTimeMillis();
        for (ThreadRecorder recorder : ThreadRecorder.getRecorderMap().values()) {
            maxSorterQueuedSize = Math.max(maxSorterQueuedSize, recorder.getQueuedTransSizeInSorter());
            minWhen = Math.min(minWhen, recorder.getWhen() <= 0 ?
                System.currentTimeMillis() : recorder.getWhen() * 1000);
            snapshot.netIn.addAndGet(recorder.getNetIn());
        }
        snapshot.maxSorterQueuedSize.set(maxSorterQueuedSize);
        snapshot.maxDelay.set(System.currentTimeMillis() - minWhen);
        return snapshot;
    }

    public void metricsEvent(Transaction transaction) {
        if (transaction.isHeartbeat()) {
            heartbeatCount.addAndGet(1);
        } else if (transaction.isTsoTransaction()) {
            tsoTranCount.addAndGet(1);
        } else {
            noTsoTranCount.addAndGet(1);
        }
        totalTranCount.addAndGet(1);
        eventTotalCount.addAndGet(transaction.getEventCount());
    }

    //----------------------------------------------------- getters -------------------------------------------------
    public long getTsoTranCount() {
        return tsoTranCount.get();
    }

    public long getHeartbeatCount() {
        return heartbeatCount.get();
    }

    public long getNoTsoTranCount() {
        return noTsoTranCount.get();
    }

    public long getTotalTranCount() {
        return totalTranCount.get();
    }

    public long getEventTotalCount() {
        return eventTotalCount.longValue();
    }

    public long getNetIn() {
        return netIn.get();
    }

    public long getMaxDelay() {
        return maxDelay.get();
    }

    public long getMaxSorterQueuedSize() {
        return maxSorterQueuedSize.get();
    }
}
