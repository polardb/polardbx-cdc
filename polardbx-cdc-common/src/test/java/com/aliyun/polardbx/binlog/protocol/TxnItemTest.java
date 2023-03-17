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
package com.aliyun.polardbx.binlog.protocol;

import com.aliyun.polardbx.binlog.base.BaseTest;
import com.google.protobuf.AbstractMessageLite;
import com.google.protobuf.ByteString;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.UUID;

public class TxnItemTest extends BaseTest {

    @Test
    @Ignore
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
    @Ignore
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
