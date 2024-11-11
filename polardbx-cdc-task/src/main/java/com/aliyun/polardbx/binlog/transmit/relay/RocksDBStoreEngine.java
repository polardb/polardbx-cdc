/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.RocksDBException;
import org.rocksdb.util.ByteUtil;

import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.ORIGIN_TSO;

/**
 * created by ziyang.lb
 **/
public class RocksDBStoreEngine extends StoreEngineBase {
    private final RepoUnit repoUnit;

    public RocksDBStoreEngine(RepoUnit metaRepoUnit, String persistPath, int streamSeq) {
        super(metaRepoUnit, streamSeq, persistPath);
        this.repoUnit = new RepoUnit(persistPath, false, false, false);
    }

    @Override
    public void appendInternal(WriteItem writeItem, byte[] data) {
        try {
            this.repoUnit.put(writeItem.getKey(), data);
        } catch (RocksDBException e) {
            throw new PolardbxException("rocks db put error!", e);
        }
    }

    @Override
    public void open() {
        try {
            this.repoUnit.open();
        } catch (Throwable e) {
            throw new PolardbxException("rocks db open error!", e);
        }
    }

    @Override
    public void close() {
        try {
            this.repoUnit.close();
        } catch (Throwable e) {
            throw new PolardbxException("rocks db close error!", e);
        }
    }

    @SneakyThrows
    @Override
    protected void cleanInternal(String tso) {
        metaRepoUnit.put(ByteUtil.bytes(metaStreamMaxCleanTsoKey), ByteUtil.bytes(tso));
        byte[] beginKey = RelayKeyUtil.buildMinRelayKey(ORIGIN_TSO);
        byte[] endKey = RelayKeyUtil.buildMinRelayKey(tso);
        deleteRange(beginKey, endKey);
    }

    @SneakyThrows
    @Override
    public String seekMaxTso() {
        //需要找到到一个事务写入完整的TSO，这样进行Recover的时候才更安全
        byte[] maxBytes = this.repoUnit.getMaxKey();
        if (maxBytes != null && maxBytes.length > 0) {
            String tso = RelayKeyUtil.extractTsoFromKey(maxBytes);
            byte[] checkpointKey = ByteUtil.bytes(metaStreamTsoCheckPointPrefix + new String(maxBytes));
            if (metaRepoUnit.exists(checkpointKey)) {
                return tso;
            } else {
                byte[] minBytes = RelayKeyUtil.buildMinRelayKey(tso);
                Pair<byte[], byte[]> pair = this.repoUnit.seekPre(minBytes, null);
                if (pair != null) {
                    checkpointKey = ByteUtil.bytes(metaStreamTsoCheckPointPrefix + new String(pair.getKey()));
                    metaRepoUnit.put(checkpointKey, ByteUtil.bytes("1"));
                }
                repoUnit.deleteRange(minBytes, maxBytes);
                repoUnit.delete(maxBytes);
                return pair == null ? "" : RelayKeyUtil.extractTsoFromKey(pair.getKey());
            }
        }
        return "";
    }

    @Override
    public RelayDataReader newRelayDataReader(byte[] beginKey) {
        return new RocksDBRelayDataReader(this, repoUnit, metrics, beginKey);
    }

    private void deleteRange(byte[] beginKey, byte[] endKey) {
        try {
            this.repoUnit.deleteRange(beginKey, endKey);
        } catch (RocksDBException e) {
            throw new PolardbxException("rocks db put error!", e);
        }
    }
}
