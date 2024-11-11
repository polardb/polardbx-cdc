/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TABLE_MAP_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT;

public class TxnBufferTest extends BaseTest {

    @Test
    public void testMerge() {
        int size = 10;
        int count = 2;
        TxnBuffer txnBuffer = buildOneBuffer(size);
        List<TxnBuffer> buffers = buildBufferList(size, count);

        // 验证个数是否一致
        buffers.forEach(txnBuffer::merge);
        Assert.assertEquals(size * (count + 1), txnBuffer.itemSize());

        // 验证是否有序
        final TxnItemRef lastRef = new TxnItemRef(txnBuffer, "", "", 19, new byte[10],
            null, null, 0, null);
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
        Assert.assertFalse(txnBuffer.seek(
            new TxnItemRef(txnBuffer, UUID.randomUUID().toString(), "", 19, new byte[10],
                null, null, 0, null)));
    }

    @Test
    public void testMergePerformance() {
        int size = 100;
        int count = 1024;
        TxnBuffer txnBuffer = buildOneBuffer(size);
        List<TxnBuffer> buffers = buildBufferList(size, count);

        long startTime = System.currentTimeMillis();
        buffers.forEach(txnBuffer::merge);
        long endTime = System.currentTimeMillis();
        System.out.println("cost time: " + (endTime - startTime));
    }

    private List<TxnBuffer> buildBufferList(int size, int count) {
        ArrayList<TxnBuffer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildOneBuffer(size));
        }
        return result;
    }

    private TxnBuffer buildOneBuffer(int size) {
        TxnBuffer txnBuffer = new TxnBuffer(new TxnKey(System.nanoTime(), UUID.randomUUID().toString()), null);
        txnBuffer.markStart();

        for (int i = 0; i < size; i++) {
            int eventType;
            if (i == 0) {
                eventType = TABLE_MAP_EVENT;
            } else {
                eventType = WRITE_ROWS_EVENT;
            }
            TxnBufferItem txnItem = TxnBufferItem.builder()
                .traceId("00001")
                .eventType(eventType)
                .payload(new byte[0])
                .eventType(i % 2 == 0 ? LogEvent.TABLE_MAP_EVENT : LogEvent.WRITE_ROWS_EVENT)
                .build();
            txnBuffer.push(txnItem);
        }
        txnBuffer.markComplete();
        return txnBuffer;
    }
}
