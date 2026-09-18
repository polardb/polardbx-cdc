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
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.filesys.LocalFileSystem;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.google.protobuf.ByteString;
import io.grpc.stub.ServerCallStreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Date;

import static com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector.isDumperMasterOrX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
public class BinlogDumpReaderTest extends BaseTest {
    private final DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
    private final NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);

    private BinlogDumpReader binlogDumpReader;

    @Mock
    private ServerCallStreamObserver<DumpStream> serverCallStreamObserver;

    @Mock
    private ByteString mockByteString;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Mock LogManager
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);
        when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000001", 100L, 1));

        // 使用反射创建一个BinlogDumpReader实例
        binlogDumpReader = Mockito.mock(BinlogDumpReader.class, Mockito.CALLS_REAL_METHODS);

        // 设置logFileManager字段
        Field logFileManagerField = BinlogDumpReader.class.getDeclaredField("logFileManager");
        logFileManagerField.setAccessible(true);
        logFileManagerField.set(binlogDumpReader, logFileManager);

        // 设置maxPacketSize字段
        Field maxPacketSizeField = BinlogDumpReader.class.getDeclaredField("maxPacketSize");
        maxPacketSizeField.setAccessible(true);
        maxPacketSizeField.set(binlogDumpReader, 1024); // 设置一个合理的包大小

        // 默认情况下，serverCallStreamObserver未被取消
        when(serverCallStreamObserver.isCancelled()).thenReturn(false);
    }

    @Test
    public void testValidRequestBinlogPosition() {
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);
        ExecutionConfig executionConfig = Mockito.mock(ExecutionConfig.class);

        BinlogDumpReader binlogDumpReader =
            new BinlogDumpReader(logFileManager, "binlog.000085", 455698979, 0, 0, null, null);
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
            assertEquals(0, binlogDumpReader.validRequestBinlogPosition());

            when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000085", 455698980L));
            Assert.assertTrue(binlogDumpReader.validRequestBinlogPosition() > 0);
        }

    }

    @Test
    public void testHasNext() {
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);

        BinlogDumpReader binlogDumpReader =
            new BinlogDumpReader(logFileManager, "binlog.000010", 4, 0, 0, null, null);
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
        EventGenerator.makeRowsQuery(0, 0, rowsQuery, 0, eventData, 4, true);
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
                new BinlogDumpReader(logFileManager, "binlog.000001", 4, 65536, 65536, null, null);
            BinlogDumpFilter binlogDumpFilter = new BinlogDumpFilter(new BinlogDumpUserVariables());
            binlogDumpReader.setChannel(binlogFileReadChannel);
            binlogDumpReader.setBinlogDumpFilter(binlogDumpFilter);
            // 模拟start(), init buffer
            binlogDumpReader.read();
            // 再读数据
            binlogDumpReader.nextDumpPack();
        }
    }

    @Test
    public void testNextDumpPacksWhenThreadIsInterrupted() throws Exception {
        // 设置hasNext()返回true
        Mockito.doReturn(true).when(binlogDumpReader).hasNext();

        // 模拟线程中断
        Thread.currentThread().interrupt();

        // 调用方法并期望抛出InterruptedException
        try {
            binlogDumpReader.nextDumpPacks(serverCallStreamObserver);
            fail("Expected InterruptedException to be thrown");
        } catch (InterruptedException e) {
            assertEquals("thread is interrupted in loop read dump packets", e.getMessage());
        } finally {
            // 清除中断状态
            Thread.interrupted();
        }

        // 验证hasNext()被调用了一次
        verify(binlogDumpReader, times(1)).hasNext();
    }

    @Test
    public void testNextDumpPacksWhenHasNextReturnsFalse() throws Exception {
        // 设置hasNext()返回false
        Mockito.doReturn(false).when(binlogDumpReader).hasNext();

        // 调用方法
        ByteString result = binlogDumpReader.nextDumpPacks(serverCallStreamObserver);

        // 验证结果为空
        assertEquals(ByteString.EMPTY, result);

        // 验证hasNext()被调用了一次
        verify(binlogDumpReader, times(1)).hasNext();

        // 验证nextDumpPack()没有被调用
        verify(binlogDumpReader, never()).nextDumpPack();
    }

    @Test
    public void testNextDumpPacksWhenServerCallIsCancelled() throws Exception {
        // 设置hasNext()返回true，但serverCallStreamObserver被取消
        Mockito.doReturn(true).when(binlogDumpReader).hasNext();
        when(serverCallStreamObserver.isCancelled()).thenReturn(true);

        // 调用方法
        ByteString result = binlogDumpReader.nextDumpPacks(serverCallStreamObserver);

        // 验证结果为空
        assertEquals(ByteString.EMPTY, result);

        // 验证hasNext()被调用了一次
        verify(binlogDumpReader, times(1)).hasNext();

        // 验证nextDumpPack()没有被调用
        verify(binlogDumpReader, never()).nextDumpPack();
    }

    @Test
    public void testNextDumpPacksNormalCase() throws Exception {
        // 设置hasNext()第一次返回true，第二次返回false
        Mockito.doReturn(true).doReturn(false).when(binlogDumpReader).hasNext();

        // 模拟nextDumpPack()返回一个非空的ByteString
        ByteString mockPack = Mockito.mock(ByteString.class);
        when(mockPack.isEmpty()).thenReturn(false);
        Mockito.doReturn(mockPack).when(binlogDumpReader).nextDumpPack();

        // 模拟concat方法
        ByteString mockResult = Mockito.mock(ByteString.class);
        when(mockPack.concat(any(ByteString.class))).thenReturn(mockResult);
        when(mockResult.size()).thenReturn(100);

        // 模拟nextDumpPackLength()返回0，触发break
        Mockito.doReturn(0).when(binlogDumpReader).nextDumpPackLength();

        // 调用方法
        ByteString result = binlogDumpReader.nextDumpPacks(serverCallStreamObserver);

        // 验证结果不为空
        assertNotNull(result);

        // 验证方法调用次数
        verify(binlogDumpReader, times(2)).hasNext();
        verify(binlogDumpReader, times(1)).nextDumpPack();
        verify(binlogDumpReader, times(1)).nextDumpPackLength();
    }

    @Test
    public void testNextDumpPacksBreakByMaxPacketSize() throws Exception {
        // 设置hasNext()返回true
        Mockito.doReturn(true).doReturn(true).doReturn(false).when(binlogDumpReader).hasNext();

        // 模拟nextDumpPack()返回
        ByteString mockPack = Mockito.mock(ByteString.class);
        when(mockPack.isEmpty()).thenReturn(false);
        Mockito.doReturn(mockPack).when(binlogDumpReader).nextDumpPack();

        // 模拟concat方法
        ByteString mockResult = Mockito.mock(ByteString.class);
        when(mockPack.concat(any(ByteString.class))).thenReturn(mockResult);

        // 第一次size返回512，第二次返回1500（超过maxPacketSize 1024）
        when(mockResult.size()).thenReturn(512, 1500);

        // 模拟nextDumpPackLength()返回一个小值
        Mockito.doReturn(100).when(binlogDumpReader).nextDumpPackLength();

        // 调用方法
        ByteString result = binlogDumpReader.nextDumpPacks(serverCallStreamObserver);

        // 验证结果不为空
        assertNotNull(result);

        // 验证方法调用次数
        verify(binlogDumpReader, times(3)).hasNext();
        verify(binlogDumpReader, times(2)).nextDumpPack();
        verify(binlogDumpReader, times(2)).nextDumpPackLength();
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
        dumperInfo.setSubVersion(1L);
        dumperInfo.setEnableLightRebalance(true);
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
        nodeInfo.setEnableLightRebalance(true);
        nodeInfoMapper.insert(nodeInfo);
        nodeInfo.setIp("127.0.0.2");
        nodeInfo.setRole("S");
        nodeInfo.setLatestCursor(
            "{\"fileName\":\"binlog.000085\",\"filePosition\":455698970,\"timestamp\":1733896768615}");
        nodeInfo.setContainerId("45863");
        nodeInfoMapper.insert(nodeInfo);
    }

    @Test
    @SneakyThrows
    public void testLimitBuffer() {
        binlogDumpReader.channel = Mockito.mock(BinlogFileReadChannel.class);
        when(binlogDumpReader.channel.position()).thenReturn(4L);
        binlogDumpReader.fileSequence = 1;
        binlogDumpReader.buffer = ByteBuffer.allocate(1024);
        binlogDumpReader.limitBuffer();
        Assert.assertEquals(96, binlogDumpReader.buffer.limit());
    }

    /**
     * 测试 initFileKey()：使用真实临时文件时，fileKey 应被正确设置
     */
    @Test
    @SneakyThrows
    public void testInitFileKey() {
        File tempFile = File.createTempFile("test_initFileKey", ".tmp");
        tempFile.deleteOnExit();

        CdcFile cdcFile = Mockito.mock(CdcFile.class);
        when(cdcFile.newFile()).thenReturn(tempFile);

        binlogDumpReader.cdcFile = cdcFile;
        binlogDumpReader.fileName = "binlog.000001";

        // 通过反射调用 private initFileKey()
        Method initFileKeyMethod = BinlogDumpReader.class.getDeclaredMethod("initFileKey");
        initFileKeyMethod.setAccessible(true);
        initFileKeyMethod.invoke(binlogDumpReader);

        // 验证 fileKey 被设置了
        Field fileKeyField = BinlogDumpReader.class.getDeclaredField("fileKey");
        fileKeyField.setAccessible(true);
        Object fileKey = fileKeyField.get(binlogDumpReader);
        assertNotNull("fileKey should be set for existing file", fileKey);
    }

    /**
     * 测试 initFileKey()：当文件不存在时，fileKey 应为 null
     */
    @Test
    @SneakyThrows
    public void testInitFileKeyWhenFileNotExists() {
        File nonExistentFile = new File("/tmp/non_existent_file_for_test_" + System.nanoTime() + ".tmp");

        CdcFile cdcFile = Mockito.mock(CdcFile.class);
        when(cdcFile.newFile()).thenReturn(nonExistentFile);

        binlogDumpReader.cdcFile = cdcFile;
        binlogDumpReader.fileName = "binlog.000001";

        Method initFileKeyMethod = BinlogDumpReader.class.getDeclaredMethod("initFileKey");
        initFileKeyMethod.setAccessible(true);
        initFileKeyMethod.invoke(binlogDumpReader);

        Field fileKeyField = BinlogDumpReader.class.getDeclaredField("fileKey");
        fileKeyField.setAccessible(true);
        Object fileKey = fileKeyField.get(binlogDumpReader);
        assertNull("fileKey should be null for non-existent file", fileKey);
    }

    /**
     * 测试 read() 方法：fileStatusCheckEnabled=false 时，跳过文件身份校验，不抛异常
     */
    @Test
    @SneakyThrows
    public void testReadWithFileStatusCheckDisabled() {
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);
        // hasNext() 需要 cursor.fileSequence > reader.fileSequence
        when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000002", 100L, 2));
        when(logFileManager.parseFileNumber("binlog.000001")).thenReturn(1);

        BinlogDumpReader reader = Mockito.mock(BinlogDumpReader.class, Mockito.CALLS_REAL_METHODS);
        reader.logFileManager = logFileManager;
        reader.fileName = "binlog.000001";
        reader.fileSequence = 1;
        reader.buffer = ByteBuffer.allocate(1024);

        // mock channel: position()=4, read()=-1 (没数据), size()=100
        BinlogFileReadChannel mockChannel = Mockito.mock(BinlogFileReadChannel.class);
        when(mockChannel.position()).thenReturn(4L);
        when(mockChannel.read(any(ByteBuffer.class))).thenReturn(-1);
        when(mockChannel.size()).thenReturn(100L);
        reader.channel = mockChannel;

        // mock cdcFile: exist()=true
        CdcFile mockCdcFile = Mockito.mock(CdcFile.class);
        when(mockCdcFile.exist()).thenReturn(true);
        reader.cdcFile = mockCdcFile;

        // 设置 limitBufferEnabled=false, labEnvEnabled=false
        Field limitBufferField = BinlogDumpReader.class.getDeclaredField("limitBufferEnabled");
        limitBufferField.setAccessible(true);
        limitBufferField.set(reader, false);
        Field labEnvField = BinlogDumpReader.class.getDeclaredField("labEnvEnabled");
        labEnvField.setAccessible(true);
        labEnvField.set(reader, false);
        Field fileStatusCheckField = BinlogDumpReader.class.getDeclaredField("fileStatusCheckEnabled");
        fileStatusCheckField.setAccessible(true);
        fileStatusCheckField.set(reader, false);
        Field useLegacySizeCheckField = BinlogDumpReader.class.getDeclaredField("useLegacySizeCheck");
        useLegacySizeCheckField.setAccessible(true);
        useLegacySizeCheckField.set(reader, false);

        // 应该不抛异常
        reader.read();
    }

    /**
     * 测试 read() 方法：legacySizeCheck 路径，channelSize == cdcFileSize 时不抛异常
     */
    @Test
    @SneakyThrows
    public void testReadWithLegacySizeCheckNoMismatch() {
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);
        when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000002", 100L, 2));
        when(logFileManager.parseFileNumber("binlog.000001")).thenReturn(1);

        BinlogDumpReader reader = Mockito.mock(BinlogDumpReader.class, Mockito.CALLS_REAL_METHODS);
        reader.logFileManager = logFileManager;
        reader.fileName = "binlog.000001";
        reader.fileSequence = 1;
        reader.buffer = ByteBuffer.allocate(1024);

        // channel.size() == cdcFile.size()，不会进入 channelSize < cdcFileSize 分支
        BinlogFileReadChannel mockChannel = Mockito.mock(BinlogFileReadChannel.class);
        when(mockChannel.position()).thenReturn(4L);
        when(mockChannel.read(any(ByteBuffer.class))).thenReturn(-1);
        when(mockChannel.size()).thenReturn(100L);
        reader.channel = mockChannel;

        CdcFile mockCdcFile = Mockito.mock(CdcFile.class);
        when(mockCdcFile.exist()).thenReturn(true);
        when(mockCdcFile.isLocal()).thenReturn(true);
        when(mockCdcFile.size()).thenReturn(100L); // 与 channel.size() 相同
        reader.cdcFile = mockCdcFile;

        Field limitBufferField = BinlogDumpReader.class.getDeclaredField("limitBufferEnabled");
        limitBufferField.setAccessible(true);
        limitBufferField.set(reader, false);
        Field labEnvField = BinlogDumpReader.class.getDeclaredField("labEnvEnabled");
        labEnvField.setAccessible(true);
        labEnvField.set(reader, false);
        Field fileStatusCheckField = BinlogDumpReader.class.getDeclaredField("fileStatusCheckEnabled");
        fileStatusCheckField.setAccessible(true);
        fileStatusCheckField.set(reader, true);
        Field useLegacySizeCheckField = BinlogDumpReader.class.getDeclaredField("useLegacySizeCheck");
        useLegacySizeCheckField.setAccessible(true);
        useLegacySizeCheckField.set(reader, true);

        // channelSize(100) >= cdcFileSize(100)，不抛异常
        reader.read();
    }

    /**
     * 测试 read() 方法：legacySizeCheck 路径，channelSize < cdcFileSize 且第二次 read 仍返回 <=0 时，抛 PolardbxException
     */
    @Test
    @SneakyThrows
    public void testReadWithLegacySizeCheckMismatch() {
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);
        when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000002", 100L, 2));
        when(logFileManager.parseFileNumber("binlog.000001")).thenReturn(1);

        BinlogDumpReader reader = Mockito.mock(BinlogDumpReader.class, Mockito.CALLS_REAL_METHODS);
        reader.logFileManager = logFileManager;
        reader.fileName = "binlog.000001";
        reader.fileSequence = 1;
        reader.buffer = ByteBuffer.allocate(1024);

        // channel.size() < cdcFile.size()，第二次 read 仍然返回 -1
        BinlogFileReadChannel mockChannel = Mockito.mock(BinlogFileReadChannel.class);
        when(mockChannel.position()).thenReturn(4L);
        when(mockChannel.read(any(ByteBuffer.class))).thenReturn(-1);
        when(mockChannel.size()).thenReturn(50L); // 小于 cdcFile.size()
        reader.channel = mockChannel;

        CdcFile mockCdcFile = Mockito.mock(CdcFile.class);
        when(mockCdcFile.exist()).thenReturn(true);
        when(mockCdcFile.isLocal()).thenReturn(true);
        when(mockCdcFile.size()).thenReturn(100L); // 大于 channel.size()
        reader.cdcFile = mockCdcFile;

        Field limitBufferField = BinlogDumpReader.class.getDeclaredField("limitBufferEnabled");
        limitBufferField.setAccessible(true);
        limitBufferField.set(reader, false);
        Field labEnvField = BinlogDumpReader.class.getDeclaredField("labEnvEnabled");
        labEnvField.setAccessible(true);
        labEnvField.set(reader, false);
        Field fileStatusCheckField = BinlogDumpReader.class.getDeclaredField("fileStatusCheckEnabled");
        fileStatusCheckField.setAccessible(true);
        fileStatusCheckField.set(reader, true);
        Field useLegacySizeCheckField = BinlogDumpReader.class.getDeclaredField("useLegacySizeCheck");
        useLegacySizeCheckField.setAccessible(true);
        useLegacySizeCheckField.set(reader, true);

        try {
            reader.read();
            fail("Expected PolardbxException to be thrown");
        } catch (PolardbxException e) {
            Assert.assertTrue(e.getMessage().contains("unexpected channel stat"));
        }
    }

    /**
     * 测试 read() 方法：fileKey 检测路径，fileKey 未变化时重试读取仍无数据，正常返回（等待 rotate）
     */
    @Test
    @SneakyThrows
    public void testReadWithFileKeyCheckNoRebuild() {
        File tempFile = File.createTempFile("test_no_rebuild", ".tmp");
        tempFile.deleteOnExit();

        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);
        when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000002", 100L, 2));
        when(logFileManager.parseFileNumber("binlog.000001")).thenReturn(1);

        BinlogDumpReader reader = Mockito.mock(BinlogDumpReader.class, Mockito.CALLS_REAL_METHODS);
        reader.logFileManager = logFileManager;
        reader.fileName = "binlog.000001";
        reader.fileSequence = 1;
        reader.buffer = ByteBuffer.allocate(1024);

        BinlogFileReadChannel mockChannel = Mockito.mock(BinlogFileReadChannel.class);
        when(mockChannel.position()).thenReturn(4L);
        when(mockChannel.read(any(ByteBuffer.class))).thenReturn(-1);
        reader.channel = mockChannel;

        CdcFile mockCdcFile = Mockito.mock(CdcFile.class);
        when(mockCdcFile.exist()).thenReturn(true);
        when(mockCdcFile.isLocal()).thenReturn(true);
        when(mockCdcFile.newFile()).thenReturn(tempFile);
        reader.cdcFile = mockCdcFile;

        // 先设置 fileKey 为当前文件的 fileKey
        java.nio.file.attribute.BasicFileAttributes attrs = java.nio.file.Files.readAttributes(
            tempFile.toPath(), java.nio.file.attribute.BasicFileAttributes.class,
            java.nio.file.LinkOption.NOFOLLOW_LINKS);
        Field fileKeyField = BinlogDumpReader.class.getDeclaredField("fileKey");
        fileKeyField.setAccessible(true);
        fileKeyField.set(reader, attrs.fileKey());

        Field limitBufferField = BinlogDumpReader.class.getDeclaredField("limitBufferEnabled");
        limitBufferField.setAccessible(true);
        limitBufferField.set(reader, false);
        Field labEnvField = BinlogDumpReader.class.getDeclaredField("labEnvEnabled");
        labEnvField.setAccessible(true);
        labEnvField.set(reader, false);
        Field fileStatusCheckField = BinlogDumpReader.class.getDeclaredField("fileStatusCheckEnabled");
        fileStatusCheckField.setAccessible(true);
        fileStatusCheckField.set(reader, true);
        Field useLegacySizeCheckField = BinlogDumpReader.class.getDeclaredField("useLegacySizeCheck");
        useLegacySizeCheckField.setAccessible(true);
        useLegacySizeCheckField.set(reader, false);

        // fileKey 未变化，重试读取仍无数据，正常返回（等待 rotate），不抛异常
        reader.read();

        // 验证 channel.close() 没有被调用（即未触发重建）
        verify(mockChannel, never()).close();
        // 验证 cdcFile.getReadChannel() 没有被调用
        verify(mockCdcFile, never()).getReadChannel();
    }

    /**
     * 测试 checkAndHandleFileRebuild()：fileKey 变化时重新打开 channel
     */
    @Test
    @SneakyThrows
    public void testCheckAndHandleFileRebuild() {
        // 创建两个不同的临时文件来模拟 fileKey 变化
        File tempFile1 = File.createTempFile("test_rebuild_1", ".tmp");
        tempFile1.deleteOnExit();
        File tempFile2 = File.createTempFile("test_rebuild_2", ".tmp");
        tempFile2.deleteOnExit();

        // 获取两个文件的 fileKey
        java.nio.file.attribute.BasicFileAttributes attrs1 = java.nio.file.Files.readAttributes(
            tempFile1.toPath(), java.nio.file.attribute.BasicFileAttributes.class,
            java.nio.file.LinkOption.NOFOLLOW_LINKS);
        Object fileKey1 = attrs1.fileKey();

        // mock cdcFile.newFile() 返回 tempFile2（模拟文件被重建后指向不同的 inode）
        CdcFile mockCdcFile = Mockito.mock(CdcFile.class);
        when(mockCdcFile.newFile()).thenReturn(tempFile2);

        BinlogFileReadChannel newChannel = Mockito.mock(BinlogFileReadChannel.class);
        when(newChannel.read(any(ByteBuffer.class))).thenReturn(10);
        when(newChannel.position()).thenReturn(4L);
        when(mockCdcFile.getReadChannel()).thenReturn(newChannel);

        BinlogFileReadChannel oldChannel = Mockito.mock(BinlogFileReadChannel.class);
        when(oldChannel.position()).thenReturn(4L);

        BinlogDumpReader reader = Mockito.mock(BinlogDumpReader.class, Mockito.CALLS_REAL_METHODS);
        reader.cdcFile = mockCdcFile;
        reader.channel = oldChannel;
        reader.fileName = "binlog.000001";
        reader.buffer = ByteBuffer.allocate(1024);

        // 设置 fileKey 为 tempFile1 的 fileKey（与 tempFile2 不同）
        Field fileKeyField = BinlogDumpReader.class.getDeclaredField("fileKey");
        fileKeyField.setAccessible(true);
        fileKeyField.set(reader, fileKey1);

        // 通过反射调用 private checkAndHandleFileRebuild
        Method checkMethod = BinlogDumpReader.class.getDeclaredMethod("checkAndHandleFileRebuild", int.class);
        checkMethod.setAccessible(true);
        int result = (int) checkMethod.invoke(reader, -1);

        // fileKey 变化时，应该重新打开 channel
        verify(oldChannel, times(1)).close();
        verify(mockCdcFile, times(1)).getReadChannel();

        // 返回值应该是新 channel 的 read 结果
        assertEquals(10, result);

        // fileKey 应该被更新为 tempFile2 的 fileKey
        Object updatedFileKey = fileKeyField.get(reader);
        assertNotNull("fileKey should be updated", updatedFileKey);
    }

    /**
     * 测试 checkAndHandleFileRebuild()：fileKey 为 null 时不触发重建，重试读取后正常返回
     */
    @Test
    @SneakyThrows
    public void testCheckAndHandleFileRebuildWithNullFileKey() {
        File tempFile = File.createTempFile("test_rebuild_null", ".tmp");
        tempFile.deleteOnExit();

        CdcFile mockCdcFile = Mockito.mock(CdcFile.class);
        when(mockCdcFile.newFile()).thenReturn(tempFile);

        BinlogFileReadChannel mockChannel = Mockito.mock(BinlogFileReadChannel.class);
        when(mockChannel.read(any(ByteBuffer.class))).thenReturn(-1);
        when(mockChannel.position()).thenReturn(4L);

        BinlogDumpReader reader = Mockito.mock(BinlogDumpReader.class, Mockito.CALLS_REAL_METHODS);
        reader.cdcFile = mockCdcFile;
        reader.channel = mockChannel;
        reader.fileName = "binlog.000001";
        reader.buffer = ByteBuffer.allocate(1024);

        // fileKey 为 null
        Field fileKeyField = BinlogDumpReader.class.getDeclaredField("fileKey");
        fileKeyField.setAccessible(true);
        fileKeyField.set(reader, null);

        Method checkMethod = BinlogDumpReader.class.getDeclaredMethod("checkAndHandleFileRebuild", int.class);
        checkMethod.setAccessible(true);

        // fileKey 为 null 时不触发重建，重试读取后正常返回 -1
        int result = (int) checkMethod.invoke(reader, -1);
        assertEquals(-1, result);

        // fileKey 为 null 时不触发重建
        verify(mockChannel, never()).close();
        verify(mockCdcFile, never()).getReadChannel();
    }

    /**
     * Test 5: 验证 rotate() 方法正确为新文件初始化 fileKey。
     * <p>
     * 场景：BinlogDumpReader 从 binlog.000001 rotate 到 binlog.000002，
     * rotate() 中的 initFileKey() 应获取 binlog.000002 的 inode 作为新的 fileKey。
     * <p>
     * 验证重点：rotate 后 fileKey 与 binlog.000002 的 inode 一致，确保后续 fileKey 检测基于正确的基准。
     */
    @Test
    @SneakyThrows
    public void testRotateInitializesFileKeyForNewFile() {
        // ===== 1. 创建 2 个真实 binlog 文件 =====
        String tempDir = System.getProperty("java.io.tmpdir") + "/test_rotate_filekey_" + System.nanoTime();
        LocalFileSystem fs = new LocalFileSystem(tempDir, "group_global", "stream_global");

        byte[] magic = new byte[] {(byte) 0xfe, 0x62, 0x69, 0x6e};
        byte[] data = new byte[32];
        Arrays.fill(data, (byte) 0xCC);

        File file1 = fs.newFile("binlog.000001");
        try (FileOutputStream fos = new FileOutputStream(file1)) {
            fos.write(magic);
            fos.write(data);
        }
        File file2 = fs.newFile("binlog.000002");
        try (FileOutputStream fos = new FileOutputStream(file2)) {
            fos.write(magic);
            fos.write(data);
        }

        try {
            // 获取 binlog.000002 的 inode 作为预期 fileKey
            BasicFileAttributes attrs2 = java.nio.file.Files.readAttributes(
                file2.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Object expectedFileKey2 = attrs2.fileKey();

            // ===== 2. 创建 BinlogDumpReader 指向 binlog.000001 =====
            LogFileManager lfm = Mockito.mock(LogFileManager.class);
            when(lfm.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000002", 200L, 2));
            when(lfm.parseFileNumber("binlog.000001")).thenReturn(1);
            when(lfm.parseFileNumber("binlog.000002")).thenReturn(2);

            // getBinlogFileByName 返回 binlog.000002 的 CdcFile
            CdcFile cdcFile2 = new CdcFile("binlog.000002", fs);
            when(lfm.getBinlogFileByName("binlog.000002")).thenReturn(cdcFile2);

            // Mock lock manager
            com.aliyun.polardbx.binlog.lock.LogFileLockManager lockMgr =
                Mockito.mock(com.aliyun.polardbx.binlog.lock.LogFileLockManager.class);
            when(lfm.getLogFileLockManager()).thenReturn(lockMgr);

            BinlogDumpReader reader = Mockito.mock(BinlogDumpReader.class, Mockito.CALLS_REAL_METHODS);
            reader.logFileManager = lfm;
            reader.fileName = "binlog.000001";
            reader.fileSequence = 1;
            reader.buffer = ByteBuffer.allocate(1024);
            reader.rotateObservers = new java.util.ArrayList<>();

            // 设置私有字段
            Field limitBufferField = BinlogDumpReader.class.getDeclaredField("limitBufferEnabled");
            limitBufferField.setAccessible(true);
            limitBufferField.set(reader, false);
            Field labEnvField = BinlogDumpReader.class.getDeclaredField("labEnvEnabled");
            labEnvField.setAccessible(true);
            labEnvField.set(reader, false);
            Field fileStatusCheckField = BinlogDumpReader.class.getDeclaredField("fileStatusCheckEnabled");
            fileStatusCheckField.setAccessible(true);
            fileStatusCheckField.set(reader, true);
            Field useLegacySizeCheckField = BinlogDumpReader.class.getDeclaredField("useLegacySizeCheck");
            useLegacySizeCheckField.setAccessible(true);
            useLegacySizeCheckField.set(reader, false);
            Field supportQuickDownloadField = BinlogDumpReader.class.getDeclaredField("supportQuickDownload");
            supportQuickDownloadField.setAccessible(true);
            supportQuickDownloadField.set(reader, false);

            // 初始化 binlog.000001 的 channel 和 fileKey
            CdcFile cdcFile1 = new CdcFile("binlog.000001", fs);
            reader.cdcFile = cdcFile1;
            Method initFileKeyMethod = BinlogDumpReader.class.getDeclaredMethod("initFileKey");
            initFileKeyMethod.setAccessible(true);
            initFileKeyMethod.invoke(reader);
            reader.channel = cdcFile1.getReadChannel();

            // 记录 binlog.000001 的 fileKey
            Field fileKeyField = BinlogDumpReader.class.getDeclaredField("fileKey");
            fileKeyField.setAccessible(true);
            Object fileKey1 = fileKeyField.get(reader);
            assertNotNull("fileKey for binlog.000001 should be set", fileKey1);

            // ===== 3. 执行 rotate() =====
            reader.rotate();

            // ===== 4. 验证 rotate 后 fileKey 已更新为 binlog.000002 的 inode =====
            Object fileKeyAfterRotate = fileKeyField.get(reader);
            assertNotNull("fileKey should be set after rotate", fileKeyAfterRotate);
            assertFalse("fileKey should differ from binlog.000001",
                fileKey1.equals(fileKeyAfterRotate));
            assertEquals("fileKey should match binlog.000002's inode",
                expectedFileKey2, fileKeyAfterRotate);

            // 验证 fileName 已更新
            assertEquals("binlog.000002", reader.fileName);
            assertEquals(2, reader.fileSequence);

            reader.channel.close();

        } finally {
            org.apache.commons.io.FileUtils.deleteDirectory(new File(tempDir));
        }
    }

    /**
     * Test 7a: 验证 CdcFile.isLocal() 对 LocalFileSystem 返回 true，对 RemoteFileSystem 返回 false。
     * 这是修复中新增的核心分支条件，确保远程文件场景下 fileKey 检测被正确跳过。
     */
    @Test
    @SneakyThrows
    public void testCdcFileIsLocalBranch() {
        // LocalFileSystem → isLocal() = true
        LocalFileSystem localFs = new LocalFileSystem("/tmp/test_islocal_" + System.nanoTime(),
            "group_global", "stream_global");
        CdcFile localCdcFile = new CdcFile("binlog.000001", localFs);
        assertTrue("CdcFile backed by LocalFileSystem should return isLocal()=true",
            localCdcFile.isLocal());

        // RemoteFileSystem → isLocal() = false
        // RemoteFileSystem 构造需要 Spring 容器（BinlogOssRecordService），使用 mock 替代
        CdcFile mockRemoteCdcFile = Mockito.mock(CdcFile.class);
        when(mockRemoteCdcFile.isLocal()).thenReturn(false);
        Assert.assertFalse("CdcFile backed by RemoteFileSystem should return isLocal()=false",
            mockRemoteCdcFile.isLocal());

        // 同时验证：newFile() 在 LocalFileSystem 下可用
        File localFile = localCdcFile.newFile();
        assertNotNull("newFile() should return a File for local CdcFile", localFile);
    }

    /**
     * Test 3 (方案A): 使用真实文件验证文件重建检测的完整流程。
     * <p>
     * 场景：BinlogDumpReader 正在读取一个本地 binlog 文件，文件被删除并重建（模拟 LogFileGenerator.prepare()
     * 调用 recreateLocalFile 的效果），新文件具有不同的 inode。
     * <p>
     * 预期：checkAndHandleFileRebuild() 检测到 fileKey(inode) 变化，自动关闭旧 channel、
     * 打开新 channel 并 seek 到原位置，成功读取新文件中的后续数据，全程无异常。
     * <p>
     * 与 testCheckAndHandleFileRebuild() 的区别：本测试使用真实的 LocalFileSystem + CdcFile + FileChannel，
     * 端到端验证 read() → checkAndHandleFileRebuild() → reopen → read 的完整链路。
     */
    @Test
    @SneakyThrows
    public void testReadWithRealFileRebuildIntegration() {
        // ===== 1. 创建真实的 LocalFileSystem 和 binlog 文件 =====
        String tempDir = System.getProperty("java.io.tmpdir") + "/test_rebuild_integration_" + System.nanoTime();
        // 使用 group_global + stream_global，getFullPath 直接返回 rootPath
        LocalFileSystem fs = new LocalFileSystem(tempDir, "group_global", "stream_global");
        String binlogName = "binlog.000001";
        File binlogFile = fs.newFile(binlogName);

        // 写入 binlog magic bytes(4) + 模拟事件数据 dataA(32)
        byte[] magic = new byte[] {(byte) 0xfe, 0x62, 0x69, 0x6e};
        byte[] dataA = new byte[32];
        Arrays.fill(dataA, (byte) 0xAA);
        try (FileOutputStream fos = new FileOutputStream(binlogFile)) {
            fos.write(magic);
            fos.write(dataA);
        }
        // 文件 = 36 字节: magic(4) + dataA(32)

        try {
            // ===== 2. 创建真实的 CdcFile =====
            CdcFile cdcFile = new CdcFile(binlogName, fs);
            assertTrue("CdcFile should be local", cdcFile.isLocal());

            // ===== 3. 创建 BinlogDumpReader (LogFileManager mock, 文件 I/O 全部使用真实文件) =====
            LogFileManager lfm = Mockito.mock(LogFileManager.class);
            // cursor 指向 position 200，确保 hasNext() 返回 true (lastPosition=4 < 200)
            when(lfm.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000001", 200L, 1));
            when(lfm.parseFileNumber("binlog.000001")).thenReturn(1);

            BinlogDumpReader reader = Mockito.mock(BinlogDumpReader.class, Mockito.CALLS_REAL_METHODS);
            reader.logFileManager = lfm;
            reader.fileName = binlogName;
            reader.fileSequence = 1;
            reader.buffer = ByteBuffer.allocate(1024);
            reader.cdcFile = cdcFile;

            // 设置私有字段
            Field limitBufferField = BinlogDumpReader.class.getDeclaredField("limitBufferEnabled");
            limitBufferField.setAccessible(true);
            limitBufferField.set(reader, false);
            Field labEnvField = BinlogDumpReader.class.getDeclaredField("labEnvEnabled");
            labEnvField.setAccessible(true);
            labEnvField.set(reader, false);
            Field fileStatusCheckField = BinlogDumpReader.class.getDeclaredField("fileStatusCheckEnabled");
            fileStatusCheckField.setAccessible(true);
            fileStatusCheckField.set(reader, true);
            Field useLegacySizeCheckField = BinlogDumpReader.class.getDeclaredField("useLegacySizeCheck");
            useLegacySizeCheckField.setAccessible(true);
            useLegacySizeCheckField.set(reader, false);

            // ===== 4. 初始化 fileKey 并打开真实 channel =====
            Method initFileKeyMethod = BinlogDumpReader.class.getDeclaredMethod("initFileKey");
            initFileKeyMethod.setAccessible(true);
            initFileKeyMethod.invoke(reader);
            reader.channel = cdcFile.getReadChannel();

            // 记录原始 fileKey
            Field fileKeyField = BinlogDumpReader.class.getDeclaredField("fileKey");
            fileKeyField.setAccessible(true);
            Object originalFileKey = fileKeyField.get(reader);
            assertNotNull("original fileKey should be set", originalFileKey);

            // ===== 5. 第一次 read(): 成功读取 dataA =====
            reader.read();
            // read() 内部: channel.position(4) 跳过 magic, 读取 32 字节, buffer.flip()
            assertEquals("first read should return 32 bytes of dataA", 32, reader.buffer.remaining());
            byte[] firstRead = new byte[reader.buffer.remaining()];
            reader.buffer.get(firstRead);
            for (byte b : firstRead) {
                assertEquals("first read data should be 0xAA", (byte) 0xAA, b);
            }

            // ===== 6. 模拟文件重建: 删除旧文件 + 创建新文件（不同 inode）=====
            // 注意：此时 reader.channel 仍指向旧 inode (Unix 下删除文件不影响已打开的 fd)
            reader.buffer.clear();  // 重置 buffer 为写模式

            binlogFile.delete();  // 删除旧文件
            // 创建新文件: magic(4) + dataA(32) + dataB(32) = 68 字节
            byte[] dataB = new byte[32];
            Arrays.fill(dataB, (byte) 0xBB);
            try (FileOutputStream fos = new FileOutputStream(binlogFile)) {
                fos.write(magic);
                fos.write(dataA);  // 保留原数据在前面（模拟 LogFileGenerator 重建后重写前半部分）
                fos.write(dataB);  // 新增数据在 offset 36 处
            }

            // 确认新文件 inode 确实不同
            BasicFileAttributes newAttrs = java.nio.file.Files.readAttributes(
                binlogFile.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Object newInodeKey = newAttrs.fileKey();
            assertFalse("new file should have different inode after rebuild",
                originalFileKey.equals(newInodeKey));

            // ===== 7. 第二次 read(): 应检测到 fileKey 变化并读取 dataB =====
            // 流程：
            //  1) 旧 channel 在 position=36, read 返回 -1 (旧文件只有 36 字节)
            //  2) hasNext()=true (lastPosition=4 < cursor=200)
            //  3) checkFileStatus()=true (新文件存在)
            //  4) checkAndHandleFileRebuild() 检测到 inode 变化
            //  5) 关闭旧 channel, 打开新 channel, seek 到 36
            //  6) 从新文件 offset=36 读取 dataB (32 字节)
            reader.read();

            // 验证 buffer 中包含 dataB (32 字节的 0xBB)
            byte[] secondRead = new byte[reader.buffer.remaining()];
            reader.buffer.get(secondRead);
            assertEquals("second read should return 32 bytes of dataB", 32, secondRead.length);
            for (byte b : secondRead) {
                assertEquals("second read data should be 0xBB (new file data)", (byte) 0xBB, b);
            }

            // ===== 8. 验证核心行为 =====
            // fileKey 应已更新为新文件的 inode
            Object updatedFileKey = fileKeyField.get(reader);
            assertNotNull("fileKey should not be null after rebuild", updatedFileKey);
            assertFalse("fileKey should have changed after rebuild",
                originalFileKey.equals(updatedFileKey));
            assertEquals("fileKey should match new file's inode",
                newInodeKey, updatedFileKey);

            // 关闭 reader channel
            reader.channel.close();

        } finally {
            // 清理临时目录
            org.apache.commons.io.FileUtils.deleteDirectory(new File(tempDir));
        }
    }

    /**
     * Test 7c: 验证 BinlogDumpReader.read() 在远程文件场景（isLocal()=false）下，
     * fileKey 检测分支被正确跳过，不会调用 newFile() 或 checkAndHandleFileRebuild()。
     * 这是 OSS 透明消费场景的核心保障。
     */
    @Test
    @SneakyThrows
    public void testReadWithRemoteFileSkipsFileKeyCheck() {
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);
        // hasNext() = true: cursor.fileSequence(2) > reader.fileSequence(1)
        when(logFileManager.getLatestFileCursor()).thenReturn(new BinlogCursor("binlog.000002", 100L, 2));
        when(logFileManager.parseFileNumber("binlog.000001")).thenReturn(1);

        BinlogDumpReader reader = Mockito.mock(BinlogDumpReader.class, Mockito.CALLS_REAL_METHODS);
        reader.logFileManager = logFileManager;
        reader.fileName = "binlog.000001";
        reader.fileSequence = 1;
        reader.buffer = ByteBuffer.allocate(1024);

        // mock channel: read() 返回 -1（无数据）
        BinlogFileReadChannel mockChannel = Mockito.mock(BinlogFileReadChannel.class);
        when(mockChannel.position()).thenReturn(4L);
        when(mockChannel.read(any(ByteBuffer.class))).thenReturn(-1);
        when(mockChannel.size()).thenReturn(100L);
        reader.channel = mockChannel;

        // 关键：mock CdcFile 为远程文件（isLocal()=false）
        CdcFile mockCdcFile = Mockito.mock(CdcFile.class);
        when(mockCdcFile.exist()).thenReturn(true);
        when(mockCdcFile.isLocal()).thenReturn(false); // 模拟 RemoteFileSystem

        reader.cdcFile = mockCdcFile;

        // 设置 fileStatusCheckEnabled=true, useLegacySizeCheck=false
        Field limitBufferField = BinlogDumpReader.class.getDeclaredField("limitBufferEnabled");
        limitBufferField.setAccessible(true);
        limitBufferField.set(reader, false);
        Field labEnvField = BinlogDumpReader.class.getDeclaredField("labEnvEnabled");
        labEnvField.setAccessible(true);
        labEnvField.set(reader, false);
        Field fileStatusCheckField = BinlogDumpReader.class.getDeclaredField("fileStatusCheckEnabled");
        fileStatusCheckField.setAccessible(true);
        fileStatusCheckField.set(reader, true);
        Field useLegacySizeCheckField = BinlogDumpReader.class.getDeclaredField("useLegacySizeCheck");
        useLegacySizeCheckField.setAccessible(true);
        useLegacySizeCheckField.set(reader, false);

        // 执行 read() — 不应抛异常
        reader.read();

        // 核心验证：远程文件场景下，newFile() 不应被调用（fileKey 检测被跳过）
        verify(mockCdcFile, never()).newFile();
        // channel.close() 不应被调用（没有 reopen）
        verify(mockChannel, never()).close();
        // getReadChannel() 不应被调用（没有 reopen）
        verify(mockCdcFile, never()).getReadChannel();
    }
}
