/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Date;

import static com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector.isDumperMasterOrX;
import static org.mockito.Mockito.when;

public class BinlogDumpReaderTest extends BaseTest {
    private final DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
    private final NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
    @Test
    public void testValidRequestBinlogPosition() {
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);
        ExecutionConfig executionConfig = Mockito.mock(ExecutionConfig.class);

        BinlogDumpReader binlogDumpReader =
            new BinlogDumpReader(logFileManager, "binlog.000085", 455698979, 0, 0, null);
        when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000085", 455698970L));
        when(logFileManager.getExecutionConfig()).thenReturn(executionConfig);
        when(logFileManager.getTaskType()).thenReturn(TaskType.Dumper);
        when(logFileManager.getTaskName()).thenReturn("Dumper_1");
        when(executionConfig.getRuntimeVersion()).thenReturn(2L);

        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElector = Mockito.mockStatic(RuntimeLeaderElector.class)) {
            runtimeLeaderElector.when(() -> isDumperMasterOrX(2, TaskType.Dumper, "Dumper_1")).thenReturn(false);

            Assert.assertTrue(binlogDumpReader.validRequestBinlogPosition() < 0);

            prepareDumperInfo();
            Assert.assertTrue(binlogDumpReader.validRequestBinlogPosition() < 0);

            prepareNodeInfo();
            setConfig(ConfigKeys.BINLOG_DUMP_WAIT_SYNC_RETRY_TIMES, "2");
            Assert.assertTrue(binlogDumpReader.validRequestBinlogPosition() < 0);

            when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000085", 455698979L));
            Assert.assertEquals(0, binlogDumpReader.validRequestBinlogPosition());

            when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000085", 455698980L));
            Assert.assertTrue(binlogDumpReader.validRequestBinlogPosition() > 0);
        }

    }

    @Test
    public void testHasNext() {
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);

        BinlogDumpReader binlogDumpReader =
            new BinlogDumpReader(logFileManager, "binlog.000010", 4, 0, 0, null);
        BinlogCursor lastCursor = new BinlogCursor("binlog.000020", 4L, 20);
        when(logFileManager.getLatestFileCursor()).thenReturn(lastCursor);
        Assert.assertTrue(binlogDumpReader.hasNext());
    }

    /**
     * 跑完没有报错就说明修复有效
     */
    @Test
    @SneakyThrows
    public void testRead() {
        byte[] eventData = new byte[2 * 65536];
        String rowsQuery = RandomStringUtils.randomAlphabetic(65536);
        EventGenerator.makeRowsQuery(0, 0, rowsQuery, 0, eventData, 4);
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);
        when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000010", 425L, 10));
        // 从eventData创建一个Channel
        File tempFile = File.createTempFile("zm_test_dump_read_binlog", ".tmp");
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile);
            FileChannel fileChannel = fos.getChannel()) {
            ByteBuffer buffer = ByteBuffer.wrap(eventData);
            fileChannel.write(buffer);
        }
        try (FileInputStream fis = new FileInputStream(tempFile);
            FileChannel fileChannel = fis.getChannel()) {
            BinlogFileReadChannel binlogFileReadChannel = new BinlogFileReadChannel(fileChannel, null);
            BinlogDumpReader binlogDumpReader =
                new BinlogDumpReader(logFileManager, "binlog.000001", 4, 65536, 65536, null);
            binlogDumpReader.setChannel(binlogFileReadChannel);
            // 模拟start(), init buffer
            binlogDumpReader.read();
            // 再读数据
            binlogDumpReader.nextDumpPack();
        }
    }

    private void prepareDumperInfo() {
        DumperInfo dumperInfo = new DumperInfo();
        dumperInfo.setIp("127.0.0.1");
        dumperInfo.setPort(1201);
        dumperInfo.setRole("M");
        dumperInfo.setStatus(0);
        dumperInfo.setGmtCreated(new Date(System.currentTimeMillis()));
        dumperInfo.setGmtModified(new Date(System.currentTimeMillis()));
        dumperInfo.setGmtHeartbeat(new Date(System.currentTimeMillis()));
        dumperInfo.setClusterId("cluster-test-get-dumper-target");
        dumperInfo.setTaskName("Dumper-1");
        dumperInfo.setPolarxInstId("pxc-test-get-dumper-target");
        dumperInfo.setDelay(0L);
        dumperInfo.setContainerId("45862");
        dumperInfo.setVersion(3L);
        dumperInfoMapper.insert(dumperInfo);
        dumperInfo.setIp("127.0.0.2");
        dumperInfo.setTaskName("Dumper-2");
        dumperInfo.setPort(1202);
        dumperInfo.setRole("S");
        dumperInfoMapper.insert(dumperInfo);
        dumperInfo.setIp("127.0.0.3");
        dumperInfo.setTaskName("Dumper-3");
        dumperInfo.setPort(1203);
        dumperInfoMapper.insert(dumperInfo);
    }

    private void prepareNodeInfo() {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setIp("127.0.0.1");
        nodeInfo.setDaemonPort(1201);
        nodeInfo.setAvailablePorts("1,2,3");
        nodeInfo.setRole("M");
        nodeInfo.setStatus(0);
        nodeInfo.setGmtCreated(new Date(System.currentTimeMillis()));
        nodeInfo.setGmtModified(new Date(System.currentTimeMillis()));
        nodeInfo.setGmtHeartbeat(new Date(System.currentTimeMillis()));
        nodeInfo.setLastTsoHeartbeat(new Date(System.currentTimeMillis()));
        nodeInfo.setClusterId("cluster-test-get-dumper-target");
        nodeInfo.setCore(16L);
        nodeInfo.setMem(16384L);
        nodeInfo.setPolarxInstId("pxc-test-get-dumper-target");
        nodeInfo.setLatestCursor(
            "{\"fileName\":\"binlog.000085\",\"filePosition\":455698979,\"timestamp\":1733896768615}");
        nodeInfo.setContainerId("45862");
        nodeInfo.setClusterRole("master");
        nodeInfoMapper.insert(nodeInfo);
        nodeInfo.setIp("127.0.0.2");
        nodeInfo.setRole("S");
        nodeInfo.setLatestCursor(
            "{\"fileName\":\"binlog.000085\",\"filePosition\":455698970,\"timestamp\":1733896768615}");
        nodeInfo.setContainerId("45863");
        nodeInfoMapper.insert(nodeInfo);
    }
}
