/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.com.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandByteArray;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

public class AutoExpandByteArrayTest extends BaseTest {

    @Test(expected = RuntimeException.class)
    public void testExpand() {
        // 本用例预期在最后一次write抛出RuntimeException，配置mock交由BaseTest的
        // @Before/@After管理（异常路径下@After仍会close，不会泄漏static mock）
        mockConfig(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION_MAX_UNCOMPRESSED_SIZE, "4096");
        byte[] data = new byte[1024];
        AutoExpandByteArray byteArray = new AutoExpandByteArray(data);
        byteArray.skip(1023);
        byteArray.write((byte) 1);
        Assert.assertEquals(1024, byteArray.getPos());
        Assert.assertEquals(1024, byteArray.getLimit());
        Assert.assertEquals(1024, byteArray.getData().length);
        byteArray.write(new byte[] {1, 2, 3});
        Assert.assertEquals(1027, byteArray.getPos());
        Assert.assertEquals(2048, byteArray.getLimit());
        Assert.assertEquals(2048, byteArray.getData().length);
        byteArray.setPos(2047);
        byteArray.write(new byte[] {1, 2, 3});
        Assert.assertEquals(2050, byteArray.getPos());
        Assert.assertEquals(4096, byteArray.getLimit());
        Assert.assertEquals(4096, byteArray.getData().length);
        byteArray.setPos(4095);
        byteArray.write(new byte[] {1, 2});
    }
}
