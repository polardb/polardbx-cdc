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

import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.metrics.RelayStreamMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * created by ziyang.lb
 */
@Slf4j
public abstract class RelayDataReaderBase implements RelayDataReader {
    protected final StoreEngine storeEngine;
    protected final RelayStreamMetrics metrics;
    protected byte[] searchFromKey;

    private long lastReadEvents;
    private long lastReadBytes;
    private long lastCalcTimeStamp = System.currentTimeMillis();

    public RelayDataReaderBase(StoreEngine storeEngine, RelayStreamMetrics metrics, byte[] searchFromKey) {
        this.storeEngine = storeEngine;
        this.metrics = metrics;
        this.searchFromKey = searchFromKey;
        this.checkValid();
    }

    @Override
    public List<Pair<byte[], byte[]>> getData(int maxItemSize, long maxByteSize) {
        LinkedList<Pair<byte[], byte[]>> list = getDataInternal(maxItemSize, maxByteSize);
        if (!list.isEmpty()) {
            storeEngine.setMaxReadKey(list.getLast().getKey());

            metrics.getReadEventCount().getAndAdd(list.size());
            metrics.getReadDelay().set(calcDelayTime(list.getLast().getKey()));
            list.forEach(p -> metrics.getReadByteSize().getAndAdd(p.getValue().length));

            long currentTimestamp = System.currentTimeMillis();
            long interval = currentTimestamp - lastCalcTimeStamp;
            if (interval >= 5000) {
                long items = this.metrics.getReadEventCount().get() - lastReadEvents;
                long bytes = this.metrics.getReadByteSize().get() - lastReadBytes;
                long eps = Double.valueOf((((double) items) / interval) * 1000).longValue();
                long bps = Double.valueOf((((double) bytes) / interval) * 1000).longValue();
                this.metrics.getReadEps().set(eps);
                this.metrics.getReadBps().set(bps);
                this.lastReadEvents = this.metrics.getReadEventCount().get();
                this.lastReadBytes = this.metrics.getReadByteSize().get();
                this.lastCalcTimeStamp = currentTimestamp;
            }
        }
        return list;
    }

    protected abstract LinkedList<Pair<byte[], byte[]>> getDataInternal(int maxItemSize, long maxByteSize);

    private void checkValid() {
        try {
            String requestTso = RelayKeyUtil.extractTsoFromKey(searchFromKey);
            String originStartTso = storeEngine.getOriginStartTso();
            if (requestTso.compareTo(originStartTso) < 0) {
                throw new InvalidTsoException(
                    String.format("request tso is less than origin start tso, %s:%s.", requestTso, originStartTso));
            }

            LockingCleaner.CheckParameter parameter =
                new LockingCleaner.CheckParameter(requestTso, storeEngine.getMaxCleanTso());
            storeEngine.getLockingCleaner().checkWithCallback(parameter, () -> null);
        } catch (InvalidTsoException e) {
            log.error("invalid tso error, task will restart!!", e);
            storeEngine.close();
            StoreEngineManager.setForceCleanFlag();
            Runtime.getRuntime().halt(1);
        }
    }

    private long calcDelayTime(byte[] data) {
        String tso = new String(data);
        return System.currentTimeMillis() - CommonUtils.getTsoPhysicalTime(tso, TimeUnit.MILLISECONDS);
    }
}
