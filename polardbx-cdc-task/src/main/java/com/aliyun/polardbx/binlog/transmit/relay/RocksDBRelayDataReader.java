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
