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
package com.aliyun.polardbx.binlog.collect;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.AlreadyExistException;
import com.aliyun.polardbx.binlog.storage.DeleteMode;
import com.aliyun.polardbx.binlog.storage.LogEventStorage;
import com.aliyun.polardbx.binlog.storage.PersistMode;
import com.aliyun.polardbx.binlog.storage.Repository;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnBufferItem;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LogEventCollectorTest extends BaseTest {

    @Test
    @Ignore
    public void testFlush() throws Exception {
        testInternal(TaskType.Relay);
        testInternal(TaskType.Final);
    }

    private void testInternal(TaskType taskType) throws Exception {
        int txnCount = 1000;
        int partitionCount = 4;
        LogEventStorage storage = new LogEventStorage(
            new Repository(false, "/tmp/RocksDB", PersistMode.AUTO, 0.8, 500, 500,
                DeleteMode.SINGLE, 1));
        storage.start();
        List<TxnToken> tokens = generateTokens(txnCount, partitionCount, storage);

        LogEventCollector collector = new LogEventCollector(storage, null, 65536, taskType, false);
        collector.start();
        tokens.forEach(collector::push);
        while (true) {
            long ringBufferQueuedSize = collector.getQueuedSize();
            if (ringBufferQueuedSize == 0) {
                long queuedSize = collector.getQueuedSize();
                if (taskType == TaskType.Relay) {
                    Assert.assertEquals(txnCount * partitionCount * partitionCount + txnCount *
                        partitionCount, queuedSize);
                } else {
                    Assert.assertEquals(txnCount * partitionCount * partitionCount + txnCount, queuedSize);
                }
                break;
            }

            Thread.sleep(1000);
        }
    }

    private List<TxnToken> generateTokens(int txnCount, int partitionCount, LogEventStorage storage) {
        String seed = CommonUtils.generateTSO(System.currentTimeMillis(), System.currentTimeMillis() + "", "");

        List<TxnToken> tokens = new ArrayList<>();
        for (int i = 0; i < txnCount; i++) {
            long txnId = System.nanoTime();
            for (int j = 0; j < partitionCount; j++) {
                String partitionId = String.valueOf(j);
                if (j == partitionCount - 1) {
                    TxnToken token = TxnToken.newBuilder()
                        .setTso(seed)
                        .setTxnId(txnId)
                        .setPartitionId(partitionId)
                        .setXaTxn(true)
                        .addAllAllParties(Lists.newArrayList("0", "1", "2", "3"))
                        .build();
                    tokens.add(token);
                } else {
                    TxnToken token = TxnToken.newBuilder()
                        .setTso(seed)
                        .setTxnId(txnId)
                        .setPartitionId(partitionId)
                        .setXaTxn(true)
                        .build();
                    tokens.add(token);
                }
            }
        }

        tokens.forEach(t -> {
            TxnKey key = new TxnKey(t.getTxnId(), t.getPartitionId());
            for (int i = 0; i < partitionCount; i++) {
                TxnBufferItem item = TxnBufferItem.builder()
                    .traceId(String.valueOf(i))
                    .payload(new byte[10])
                    .schema("d1")
                    .table("t1")
                    .build();
                try {
                    storage.create(key);
                } catch (AlreadyExistException e) {
                }
                TxnBuffer txnBuffer = storage.fetch(key);
                txnBuffer.push(item);
            }
        });

        return tokens;
    }
}
