/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.metrics.RelayStreamMetrics;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * created by ziyang.lb
 **/
public class RocksDBRelayDataReader extends RelayDataReaderBase {
    private final RepoUnit repoUnit;
    private final AtomicBoolean firstFlag;

    RocksDBRelayDataReader(RocksDBStoreEngine rocksDBStoreEngine, RepoUnit repoUnit, RelayStreamMetrics metrics,
                           byte[] searchFromKey) {
        super(rocksDBStoreEngine, metrics, searchFromKey);
        this.repoUnit = repoUnit;
        this.firstFlag = new AtomicBoolean(true);
    }

    @Override
    public LinkedList<Pair<byte[], byte[]>> getDataInternal(int maxItemSize, long maxByteSize) {
        LinkedList<Pair<byte[], byte[]>> dataList = this.repoUnit.getRange(searchFromKey, maxItemSize, maxByteSize);
        if (!dataList.isEmpty()) {
            if (!firstFlag.compareAndSet(true, false)) {
                Pair<byte[], byte[]> pair = dataList.remove(0);
                Assert.isTrue(Arrays.equals(pair.getKey(), searchFromKey));
            }

            if (!dataList.isEmpty()) {
                searchFromKey = dataList.getLast().getKey();
            }
        }
        return dataList;
    }

    @Override
    public void close() {

    }
}
