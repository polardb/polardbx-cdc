/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.metrics;

import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author yanfenglin
 */
public class ExtractorMetrics {

    private static final ExtractorMetrics INSTANCE = new ExtractorMetrics();

    private final AtomicLong tsoCount = new AtomicLong(0);
    private final AtomicLong heartbeatCount = new AtomicLong(0);
    private final AtomicLong noTsoCount = new AtomicLong(0);
    private final AtomicLong netIn = new AtomicLong(0);
    private final AtomicLong tranTotalCount = new AtomicLong(0);
    private final AtomicLong eventTotalCount = new AtomicLong(0);
    private final AtomicLong delay = new AtomicLong(0);
    private final Map<Long, ThreadRecorder> recorderMap = new ConcurrentHashMap<>();

    private ExtractorMetrics() {
    }

    public static ExtractorMetrics get() {
        return INSTANCE;
    }

    public void restart() {
        tsoCount.set(0);
        noTsoCount.set(0);
        heartbeatCount.set(0);
        tranTotalCount.set(0);
        eventTotalCount.set(0);
        netIn.set(0);
        recorderMap.clear();
    }

    public void removeRecord(ThreadRecorder threadRecorder) {
        if (threadRecorder == null || threadRecorder.getTname() == null) {
            return;
        }
        this.recorderMap.remove(threadRecorder.getTid());
    }

    public ExtractorMetrics snapshot() {

        long minWhen = Long.MAX_VALUE;
        for (ThreadRecorder recorder : recorderMap.values()) {
            minWhen = Math.min(minWhen, recorder.getWhen());
            netIn.addAndGet(recorder.getNetIn());
            recorder.resetNetIn();
        }
        delay.set(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - minWhen);

        return this;
    }

    public void metricEvent(Transaction transaction) {
        if (transaction.isHeartbeat()) {
            heartbeatCount.addAndGet(1);
        } else if (transaction.isTsoTransaction()) {
            tsoCount.addAndGet(1);
        } else {
            noTsoCount.addAndGet(1);
        }
        tranTotalCount.addAndGet(1);
        eventTotalCount.addAndGet(transaction.getEventCount());
    }

    public void recordRt(ThreadRecorder threadRecorder) {
        this.recorderMap.put(threadRecorder.getTid(), threadRecorder);
    }

    public AtomicLong getTsoCount() {
        return tsoCount;
    }

    public AtomicLong getHeartbeatCount() {
        return heartbeatCount;
    }

    public AtomicLong getNoTsoCount() {
        return noTsoCount;
    }

    public AtomicLong getTranTotalCount() {
        return tranTotalCount;
    }

    public AtomicLong getEventTotalCount() {
        return eventTotalCount;
    }

    public AtomicLong getNetIn() {
        return netIn;
    }

    public AtomicLong getDelay() {
        return delay;
    }

    public Map<Long, ThreadRecorder> getRecorderMap() {
        return recorderMap;
    }
}
