/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.protocol;

import com.google.protobuf.AbstractMessageLite;
import com.google.protobuf.ByteString;
import org.junit.Test;

import java.util.ArrayList;
import java.util.UUID;

public class TxnItemTest {

    @Test
    public void testSerializePerformance() {
        ArrayList<TxnItem> list = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            list.add(TxnItem.newBuilder()
                .setTraceId(UUID.randomUUID().toString())
                .setPayload(ByteString.copyFrom(new byte[100]))
                .build());
        }

        // 序列化
        long start = System.currentTimeMillis();
        list.forEach(AbstractMessageLite::toByteArray);
        long end = System.currentTimeMillis();
        System.out.println(end - start);

        // 序列化size
        start = System.currentTimeMillis();
        list.forEach(TxnItem::getSerializedSize);
        end = System.currentTimeMillis();
        System.out.println(end - start);
    }

    @Test
    public void testSizeCalculate() {
        TxnItem item = TxnItem.newBuilder()
            .setTraceId(UUID.randomUUID().toString())
            .setPayload(ByteString.copyFrom(new byte[100]))
            .build();
        System.out.println(item.getTraceIdBytes().size());
        System.out.println(item.getPayload().size());
        System.out.println(item.getSerializedSize());
    }
}
