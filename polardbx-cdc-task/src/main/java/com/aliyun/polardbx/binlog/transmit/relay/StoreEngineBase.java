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
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.metrics.RelayStreamMetrics;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.rocksdb.util.ByteUtil;

import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.ORIGIN_TSO;

/**
 * create by ziyang.lb
 **/
@Slf4j
public abstract class StoreEngineBase implements StoreEngine {
    protected final String META_KEY_STREAM_TSO_CHECKPOINT_PREFIX = "__meta_stream_tso_checkpoint_";
    protected final String META_KEY_STREAM_MAX_CLEAN_TSO_PREFIX = "__meta_stream_max_clean_tso_";
    protected final String META_KEY_ORIGIN_START_TSO = "__meta_origin_start_tso_";

    protected final RepoUnit metaRepoUnit;
    protected final int streamSeq;
    protected final String persistPath;
    protected final String metaStreamTsoCheckPointPrefix;
    protected final String metaStreamMaxCleanTsoKey;
    protected final LockingCleaner lockingCleaner;
    protected final RelayStreamMetrics metrics;
    protected byte[] maxReadKey;

    private long lastWriteEvents;
    private long lastWriteBytes;
    private long lastCalcTimeStamp = System.currentTimeMillis();

    public StoreEngineBase(RepoUnit metaRepoUnit, int streamSeq, String persistPath) {
        this.metaRepoUnit = metaRepoUnit;
        this.streamSeq = streamSeq;
        this.persistPath = persistPath;
        this.metaStreamTsoCheckPointPrefix = META_KEY_STREAM_TSO_CHECKPOINT_PREFIX + streamSeq + "_";
        this.metaStreamMaxCleanTsoKey = META_KEY_STREAM_MAX_CLEAN_TSO_PREFIX + streamSeq + "_";
        this.lockingCleaner = new LockingCleaner();
        this.metrics = new RelayStreamMetrics(streamSeq);
        RelayStreamMetrics.register(streamSeq, metrics);
    }

    @Override
    public void append(WriteItem writeItem) {
        byte[] data = writeItem.toMessage().toByteArray();
        appendInternal(writeItem, data);

        long currentTimestamp = System.currentTimeMillis();
        long tsoTimestamp = CommonUtils.getTsoPhysicalTime(writeItem.getTxnToken().getTso(), TimeUnit.MILLISECONDS);
        this.metrics.getWriteEventCount().incrementAndGet();
        this.metrics.getWriteByteSize().getAndAdd(data.length);
        this.metrics.getWriteDelay().set(currentTimestamp - tsoTimestamp);
        this.metrics.getMaxRelayTimestamp().set(tsoTimestamp);
        long interval = currentTimestamp - lastCalcTimeStamp;
        if (interval >= 5000) {
            long items = this.metrics.getWriteEventCount().get() - lastWriteEvents;
            long bytes = this.metrics.getWriteByteSize().get() - lastWriteBytes;
            long eps = Double.valueOf((((double) items) / interval) * 1000).longValue();
            long bps = Double.valueOf((((double) bytes) / interval) * 1000).longValue();
            this.metrics.getWriteEps().set(eps);
            this.metrics.getWriteBps().set(bps);
            this.lastWriteEvents = this.metrics.getWriteEventCount().get();
            this.lastWriteBytes = this.metrics.getWriteByteSize().get();
            this.lastCalcTimeStamp = currentTimestamp;
        }
    }

    protected abstract void appendInternal(WriteItem writeItem, byte[] data);

    @Override
    public void clean(String tso) {
        LockingCleaner.CleanParameter parameter = new LockingCleaner.CleanParameter(tso, getMaxReadTso());
        lockingCleaner.cleanWithCallback(parameter, () -> {
            cleanInternal(tso);
            log.info("relay data is cleaned with checkpoint tso " + tso + ", stream seq " + streamSeq);
            return null;
        });
        metrics.getMinRelayTimestamp().set(CommonUtils.getTsoPhysicalTime(tso, TimeUnit.MILLISECONDS));
    }

    protected abstract void cleanInternal(String tso);

    @SneakyThrows
    @Override
    public void setOriginStartTso(String tso) {
        tso = StringUtils.isBlank(tso) ? ORIGIN_TSO : tso;
        byte[] key = ByteUtil.bytes(META_KEY_ORIGIN_START_TSO);
        if (!metaRepoUnit.exists(key)) {
            metaRepoUnit.put(key, ByteUtil.bytes(tso));
        }
    }

    @SneakyThrows
    @Override
    public String getOriginStartTso() {
        byte[] key = ByteUtil.bytes(META_KEY_ORIGIN_START_TSO);
        if (!metaRepoUnit.exists(key)) {
            throw new PolardbxException("origin start tso is not existing!");
        }
        return new String(metaRepoUnit.get(key));
    }

    @Override
    public void setMaxReadKey(byte[] key) {
        this.maxReadKey = key;
    }

    @SneakyThrows
    @Override
    public String getMaxCleanTso() {
        byte[] key = ByteUtil.bytes(metaStreamMaxCleanTsoKey);
        if (metaRepoUnit.exists(key)) {
            return new String(metaRepoUnit.get(key));
        }
        return "";
    }

    @Override
    public LockingCleaner getLockingCleaner() {
        return lockingCleaner;
    }

    protected String getMaxReadTso() {
        return (maxReadKey == null || maxReadKey.length == 0) ? "" : RelayKeyUtil.extractTsoFromKey(maxReadKey);
    }
}
