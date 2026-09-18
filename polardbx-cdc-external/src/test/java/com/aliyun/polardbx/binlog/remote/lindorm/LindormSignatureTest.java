/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.lindorm;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;

/**
 * LindormClient signature method unit tests
 *
 * @author zm
 */
@Slf4j
public class LindormSignatureTest {
    @Test
    public void testSignature() throws Exception {
        // 创建一个 LindormClient 实例用于测试
        LindormClient client = Mockito.mock(LindormClient.class, Mockito.CALLS_REAL_METHODS);
        client.setAccessSecret("test-access-secret");
        client.setAccessKey("test-access-key");

        // 测试 signature 方法的基本功能
        long timestamp = 1761891739484L;
        log.info("timestamp: {}", timestamp);
        String action = "test-action";
        String signature = client.signature(timestamp, action);
        log.info("sign: {}", signature);
        Assert.assertEquals("69feab608a5eee5dda8eeb92cda96dd5df594f12", signature);
    }
}