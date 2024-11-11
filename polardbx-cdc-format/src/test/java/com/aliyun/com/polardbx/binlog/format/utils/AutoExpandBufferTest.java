/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.com.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class AutoExpandBufferTest {
    @Test
    public void testBigEndian() {
        test(ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void testLittleEndian() {
        test(ByteOrder.LITTLE_ENDIAN);
    }

    private void test(ByteOrder order) {
        AutoExpandBuffer autoBuffer = new AutoExpandBuffer(32, 32);
        ByteBuffer bb = ByteBuffer.allocate(1023);
        // test LITTLE_ENDIAN
        autoBuffer.order(order);
        bb.order(order);
        byte[] array = "测试order排序逻辑，这段文字稍微长一点".getBytes(StandardCharsets.UTF_8);
        bb.put(array);
        autoBuffer.put(array);
        // test double
        double f = 3.1415926f;
        autoBuffer.putDouble(f);
        bb.putDouble(f);

        int i = 32768;
        autoBuffer.putInt(i);
        bb.putInt(i);

        bb.put(array);
        autoBuffer.put(array);

        bb.put(array);
        autoBuffer.put(array);

        Assert.assertEquals(bb.position(), autoBuffer.position());
        int size = bb.position();
        byte[] hb = new byte[size];
        byte[] ab = new byte[size];
        bb.flip();
        bb.get(hb);
        autoBuffer.writeTo(ab);
        Assert.assertArrayEquals(hb, ab);
    }
}
