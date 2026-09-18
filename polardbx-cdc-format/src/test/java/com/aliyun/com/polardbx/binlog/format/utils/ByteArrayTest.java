/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.com.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-08-21 13:40
 **/
@Slf4j
public class ByteArrayTest {

    @Test
    public void testRead() {
        String s = "000000001b01000000240000009c000000000062696e6c6f672e303030303032f62e5aba";
        byte[] data = ByteBufUtil.decodeHexDump(s);
        ByteArray ba = new ByteArray(data);

        long l1 = ba.readLong(4);
        log.info("timestamp {}", l1);
        Assert.assertEquals(0, l1);

        int l2 = ba.read();
        log.info("event type {}", l2);
        Assert.assertEquals(27, l2);

        long l3 = ba.readLong(4);
        log.info("server-id {}", l3);
        Assert.assertEquals(1, l3);

        long l4 = ba.readLong(4);
        log.info("event-size {}", l4);
        Assert.assertEquals(36, l4);

        long l5 = ba.readLong(4);
        log.info("log pos {}", l5);
        Assert.assertEquals(156, l5);

        int l6 = ba.readInteger(2);
        log.info("flags {}", l6);
        Assert.assertEquals(0, l6);
    }

    @Test
    public void testWriteLongWriteStore() {
        byte[] data = new byte[16];
        ByteArray byteArray = new ByteArray(data);
        byteArray.writeLongNetStore(0);
        byteArray.setPos(0);
        long value = byteArray.readLenenc();
        Assert.assertEquals(0, value);

        data = new byte[16];
        byteArray = new ByteArray(data);
        byteArray.writeLongNetStore(1);
        byteArray.setPos(0);
        value = byteArray.readLenenc();
        Assert.assertEquals(1, value);

        data = new byte[16];
        byteArray = new ByteArray(data);
        byteArray.writeLongNetStore(65537L);
        byteArray.setPos(0);
        value = byteArray.readLenenc();
        Assert.assertEquals(65537, value);

        data = new byte[16];
        byteArray = new ByteArray(data);
        byteArray.writeLongNetStore(16777217L);
        byteArray.setPos(0);
        value = byteArray.readLenenc();
        Assert.assertEquals(16777217L, value);
    }

    @Test
    public void testWrite() {
        byte[] a = new byte[16];
        byte[] b = new byte[16];
        ByteArray src = new ByteArray(a);
        ByteArray target = new ByteArray(b);
        for (int i = 0; i < 16; i++) {
            src.writeLong(i, 1);
        }
        target.write(a, 8);
        for (int i = 0; i < 8; i++) {
            Assert.assertEquals(a[i], b[i]);
        }
        for (int i = 8; i < 16; i++) {
            Assert.assertEquals(0, b[i]);
        }
    }
}
