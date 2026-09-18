/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * @author zm
 */
@Slf4j
public class LogBufferTest {
    @Test
    public void testGetFixString() {
        byte[] testBytes = "polardbx".getBytes(StandardCharsets.UTF_8);
        LogBuffer buffer = new LogBuffer(testBytes, 0, testBytes.length);
        String s = buffer.getFixString(0, testBytes.length, "utf-8");
        log.info(s);
        Assert.assertEquals("polardbx", s);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetFixStringUnsupportedCharset1() {
        byte[] testBytes = "polardbx".getBytes(StandardCharsets.UTF_8);
        LogBuffer buffer = new LogBuffer(testBytes, 0, testBytes.length);
        String s = buffer.getFixString(0, testBytes.length, "utf-1");
        log.info(s);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetFixStringUnsupportedCharset2() {
        byte[] testBytes = "polardbx".getBytes(StandardCharsets.UTF_8);
        LogBuffer buffer = new LogBuffer(testBytes, 0, testBytes.length);
        String s = buffer.getFixString(testBytes.length, "utf-1");
        log.info(s);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetFullStringUnsupportedCharset1() {
        byte[] testBytes = "polardbx".getBytes(StandardCharsets.UTF_8);
        LogBuffer buffer = new LogBuffer(testBytes, 0, testBytes.length);
        String s = buffer.getFullString(0, testBytes.length, "utf-1");
        log.info(s);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetFullStringUnsupportedCharset2() {
        byte[] testBytes = "polardbx".getBytes(StandardCharsets.UTF_8);
        LogBuffer buffer = new LogBuffer(testBytes, 0, testBytes.length);
        String s = buffer.getFullString(testBytes.length, "utf-1");
        log.info(s);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetStringUnsupportedCharset() {
        byte[] testBytes = "polardbx".getBytes(StandardCharsets.UTF_8);
        LogBuffer buffer = new LogBuffer(testBytes, 0, testBytes.length);
        String s = buffer.getString("utf-1");
        log.info(s);
    }
}
