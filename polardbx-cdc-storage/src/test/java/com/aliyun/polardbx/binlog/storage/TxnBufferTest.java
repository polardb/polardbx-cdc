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
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 *
 **/
public class TxnBufferTest {

    public static void main(String args[]) {
        testMergePerformance();
    }

    @Test
    public void testMerge() {
        int size = 10000;
        int count = 15;
        TxnBuffer txnBuffer = buildOneBuffer(size);
        txnBuffer.markComplete();
        List<TxnBuffer> buffers = buildBufferList(size, count);
        buffers.forEach(TxnBuffer::markComplete);

        // 验证个数是否一致
        buffers.stream().forEach(b -> txnBuffer.merge(b));
        Assert.assertEquals(size * (count + 1), txnBuffer.itemSize());

        // 验证是否有序
        final TxnItemRef lastRef = new TxnItemRef(txnBuffer, "", "", 19, new byte[1],
            null, null);
        txnBuffer.iterator().forEachRemaining(i -> {
            int result = i.compareTo(lastRef);
            Assert.assertTrue(result > 0);
        });
    }

    @Test
    public void testSeek() {
        int size = 1001;
        TxnBuffer txnBuffer = buildOneBuffer(size);
        int index = (int) (Math.random() * (size - 1));
        TxnItemRef seed = txnBuffer.getItemRef(index);

        boolean result = txnBuffer.seek(seed);
        Assert.assertTrue(result);
        Assert.assertEquals(index, txnBuffer.itemSize());
        Assert.assertFalse(txnBuffer.seek(
            new TxnItemRef(txnBuffer, UUID.randomUUID().toString(), "", 19, null,
                null, null)));
    }

    private static void testMergePerformance() {
        int size = 100;
        int count = 1024;
        TxnBuffer txnBuffer = buildOneBuffer(size);
        List<TxnBuffer> buffers = buildBufferList(size, count);

        long startTime = System.currentTimeMillis();
        buffers.stream().forEach(b -> txnBuffer.merge(b));
        long endTime = System.currentTimeMillis();
        System.out.println("cost time: " + (endTime - startTime));
    }

    private static List<TxnBuffer> buildBufferList(int size, int count) {
        ArrayList<TxnBuffer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildOneBuffer(size));
        }
        return result;
    }

    private static TxnBuffer buildOneBuffer(int size) {
        TxnBuffer txnBuffer =
            new TxnBuffer(new TxnKey(UUID.randomUUID().toString(), UUID.randomUUID().toString()), null);
        txnBuffer.markStart();
        long seed = System.currentTimeMillis();
        String suffix = UUID.randomUUID().toString();

        for (int i = 0; i < size; i++) {
            TxnBufferItem txnItem = TxnBufferItem.builder()
                .traceId((seed++) + "-" + suffix)
                .payload(new byte[0])
                .eventType(i % 2 == 0 ? LogEvent.TABLE_MAP_EVENT : LogEvent.WRITE_ROWS_EVENT)
                .build();
            txnBuffer.push(txnItem);
        }
        return txnBuffer;
    }
}
