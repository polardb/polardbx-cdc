/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Test;

import java.util.Random;

import static com.aliyun.polardbx.binlog.CommonConstants.GROUP_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.BINLOG_FILE_NAME_MAX_SEQUENCE;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.BINLOG_FILE_PREFIX;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.extractRootPathFromFullPath;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.getBinlogFileNameBySequence;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.getBinlogFilePrefix;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.getFirstBinlogFileName;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.getFullPath;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.getNextBinlogFileName;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.getRootPath;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.isBinlogFile;
import static com.aliyun.polardbx.binlog.util.BinlogFileUtil.isValidFullPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author yudong
 * @since 2023/1/12 14:29
 **/
public class BinlogFileUtilTest extends BaseTest {

    @Test
    public void getBinlogFilePrefixTest() {
        // 单流测试
        String groupName = GROUP_NAME_GLOBAL;
        String streamName = STREAM_NAME_GLOBAL;
        assertEquals(BINLOG_FILE_PREFIX, getBinlogFilePrefix(groupName, streamName));
        // 多流测试
        groupName = "group1";
        streamName = "stream1";
        String expectedPrefix = streamName + "_" + BINLOG_FILE_PREFIX;
        assertEquals(expectedPrefix, getBinlogFilePrefix(groupName, streamName));
    }

    @Test
    public void isBinlogFileWithPrefixParamTest() {
        // 单流测试
        String prefix = "binlog";
        assertTrue(isBinlogFile("binlog.000001", prefix));
        assertFalse(isBinlogFile("binlog.000000", prefix));
        assertFalse(isBinlogFile("binlog_000001", prefix));
        assertFalse(isBinlogFile("binlog.001", prefix));
        assertFalse(isBinlogFile("bin.000001", prefix));
        assertFalse(isBinlogFile("binlog.000001.tmp", prefix));
        assertFalse(isBinlogFile(null, prefix));
        assertFalse(isBinlogFile("binlog.000001", null));
        // 多流测试
        String groupName = "group1";
        String streamName = "stream1";
        prefix = getBinlogFilePrefix(groupName, streamName);
        assertTrue(isBinlogFile(prefix + ".000001", prefix));
        assertFalse(isBinlogFile(prefix + "_000001", prefix));
        assertFalse(isBinlogFile(prefix + ".001", prefix));
        assertFalse(isBinlogFile(prefix + ".000001.tmp", prefix));
    }

    @Test
    public void isBinlogFileWithoutPrefixParamTest() {
        assertTrue(isBinlogFile("binlog.000001"));
        assertTrue(isBinlogFile("binlog.999999"));
        assertTrue(isBinlogFile("stream1_binlog.000001"));
        assertTrue(isBinlogFile("stream1_binlog.999999"));

        assertFalse(isBinlogFile(null));
        assertFalse(isBinlogFile("binlog.000000"));
        assertFalse(isBinlogFile("binlog.1000000"));
        assertFalse(isBinlogFile("binlog.001"));
        assertFalse(isBinlogFile("bin.log.000001"));
        assertFalse(isBinlogFile("binlog.000001.tmp"));
    }

    @Test
    public void getFirstBinlogFileNameTest() {
        // 单流测试
        String groupName = GROUP_NAME_GLOBAL;
        String streamName = STREAM_NAME_GLOBAL;
        assertEquals("binlog.000001", getFirstBinlogFileName(groupName, streamName));
        // 多流测试
        groupName = "group1";
        streamName = "stream1";
        assertEquals("stream1_binlog.000001", getFirstBinlogFileName(groupName, streamName));
    }

    @Test
    public void getNextBinlogFileNameTest() {
        // 单流测试
        String groupName = GROUP_NAME_GLOBAL;
        String streamName = STREAM_NAME_GLOBAL;
        String fileName = getFirstBinlogFileName(groupName, streamName);
        int randomSeq = new Random().nextInt(BINLOG_FILE_NAME_MAX_SEQUENCE);
        for (int i = 1; i < randomSeq; i++) {
            fileName = getNextBinlogFileName(fileName);
        }
        String expectedFileName = getBinlogFileNameBySequence(groupName, streamName, randomSeq);
        assertEquals(expectedFileName, fileName);
        // 多流测试
        groupName = "group1";
        streamName = "stream1";
        fileName = getFirstBinlogFileName(groupName, streamName);
        for (int i = 1; i < randomSeq; i++) {
            fileName = getNextBinlogFileName(fileName);
        }
        expectedFileName = getBinlogFileNameBySequence(groupName, streamName, randomSeq);
        assertEquals(expectedFileName, fileName);
    }

    @Test
    public void should_back_to_1_when_reach_max() {
        // 单流测试
        String groupName = GROUP_NAME_GLOBAL;
        String streamName = STREAM_NAME_GLOBAL;
        String fileName = getFirstBinlogFileName(groupName, streamName);
        for (int i = 1; i < BINLOG_FILE_NAME_MAX_SEQUENCE; i++) {
            fileName = getNextBinlogFileName(fileName);
        }
        assertEquals(getFirstBinlogFileName(groupName, streamName), getNextBinlogFileName(fileName));
    }

    @Test(expected = PolardbxException.class)
    public void should_throw_exception_when_seq_less_0() {
        getBinlogFileNameBySequence("", -1);
    }

    @Test(expected = PolardbxException.class)
    public void should_throw_exception_when_seq_bigger_than_max() {
        getBinlogFileNameBySequence("", BINLOG_FILE_NAME_MAX_SEQUENCE + 1);
    }

    @Test
    public void extractRootPathFromFullPathTest() {
        // 单流测试
        String groupName = GROUP_NAME_GLOBAL;
        String streamName = STREAM_NAME_GLOBAL;
        String rootPath = getRootPath(TaskType.Dumper, -1);
        String fullPath = BinlogFileUtil.getFullPath(rootPath, groupName, streamName);
        assertEquals(rootPath, extractRootPathFromFullPath(fullPath, groupName, streamName));
        // 多流测试
        groupName = "group1";
        streamName = "stream1";
        rootPath = getRootPath(TaskType.DumperX, 1);
        fullPath = BinlogFileUtil.getFullPath(rootPath, groupName, streamName);
        assertEquals(rootPath, extractRootPathFromFullPath(fullPath, groupName, streamName));

        groupName = "1";
        streamName = "1";
        rootPath = getRootPath(TaskType.DumperX, 1);
        fullPath = BinlogFileUtil.getFullPath(rootPath, groupName, streamName);
        assertEquals(rootPath, extractRootPathFromFullPath(fullPath, groupName, streamName));
    }

    @Test
    public void isValidFullPathTest() {
        // 单流测试
        String groupName = GROUP_NAME_GLOBAL;
        String streamName = STREAM_NAME_GLOBAL;
        String fullPath = getFullPath(groupName, streamName, -1);
        assertTrue(isValidFullPath(fullPath, groupName, streamName));
        groupName = "group1";
        streamName = "stream1";
        fullPath = getFullPath(groupName, streamName, 1);
        assertTrue(isValidFullPath(fullPath, groupName, streamName));
    }

    @Test
    public void extractStreamNameTest() {
        String fileName = "binlog.000001";
        assertEquals(STREAM_NAME_GLOBAL, BinlogFileUtil.extractStreamName(fileName));

        String groupName = "group1";
        String streamName = "group1_stream1";
        fileName = getFirstBinlogFileName(groupName, streamName);
        assertEquals(streamName, BinlogFileUtil.extractStreamName(fileName));
    }

}
