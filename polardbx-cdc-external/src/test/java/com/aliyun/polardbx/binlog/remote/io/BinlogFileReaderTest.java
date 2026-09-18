/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.io;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
public class BinlogFileReaderTest extends BaseTest {

    @Mock
    private BinlogFileStatusChecker mockChecker;

    @Mock
    private IFileCursorProvider mockCursorProvider;

    private BinlogFileReader binlogFileReader;
    private Path tempFile;
    private AutoCloseable closeable;

    @Before
    public void setUp() throws Exception {
        closeable = MockitoAnnotations.openMocks(this);

        // 创建符合规范的binlog文件名
        Path tempDir = Files.createTempDirectory("binlog-test");
        tempFile = tempDir.resolve("binlog.000001");
        byte[] testData = new byte[1024];
        for (int i = 0; i < testData.length; i++) {
            testData[i] = (byte) (i % 256);
        }
        Files.write(tempFile, testData);

        // 初始化BinlogFileReader实例
        binlogFileReader = new BinlogFileReader(
            tempFile.getFileName().toString(),
            tempFile.getParent().toString(),
            mockChecker
        );
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
        if (tempFile != null && Files.exists(tempFile)) {
            Files.delete(tempFile);
            Files.delete(tempFile.getParent());
        }
    }

    @Test
    public void testRead_Success() throws IOException, InterruptedException {
        // 准备测试数据
        byte[] buffer = new byte[512];

        // 设置mock行为
        when(mockChecker.needWait(anyInt(), anyString())).thenReturn(false);

        // 执行测试
        int bytesRead = binlogFileReader.read(buffer);

        // 验证结果
        assertEquals(512, bytesRead);
        verify(mockChecker).needWait(0, tempFile.getFileName().toString());
    }

    @Test
    @SneakyThrows
    public void testReadFileDelete() {
        // 准备测试数据
        byte[] buffer = new byte[1024];

        // 设置mock行为
        when(mockChecker.needWait(anyInt(), anyString())).thenReturn(false);

        // 执行测试
        int bytesRead = binlogFileReader.read(buffer);

        // 验证结果
        assertEquals(1024, bytesRead);

        // 重新创建文件
        recreateFile();

        // 再次读取
        bytesRead = binlogFileReader.read(buffer);
        assertEquals(1024, bytesRead);
    }

    @Test
    public void testRead_PartialRead() throws IOException, InterruptedException {
        // 准备测试数据
        byte[] buffer = new byte[2048]; // 比文件大

        // 设置mock行为
        when(mockChecker.needWait(anyInt(), anyString())).thenReturn(false);

        // 执行测试
        int bytesRead = binlogFileReader.read(buffer);

        // 验证结果 - 应该只读取到文件末尾
        assertEquals(1024, bytesRead);
        verify(mockChecker, atLeastOnce()).needWait(anyInt(), anyString());
    }

    @Test
    public void testRead_WithWait() throws IOException, InterruptedException {
        // 准备测试数据
        byte[] buffer = new byte[256];

        // 设置mock行为: 第一次需要等待，第二次不需要
        when(mockChecker.needWait(anyInt(), anyString()))
            .thenReturn(true)  // 第一次检查需要等待
            .thenReturn(false); // 第二次检查不需要等待

        // 执行测试
        int bytesRead = binlogFileReader.read(buffer);

        // 验证结果
        assertEquals(256, bytesRead);
        verify(mockChecker, times(2)).needWait(anyInt(), anyString());
    }

    @Test(expected = InterruptedException.class)
    public void testRead_Interrupted() throws IOException, InterruptedException {
        // 准备测试数据
        byte[] buffer = new byte[128];

        // 模拟线程中断
        Thread.currentThread().interrupt();

        // 执行测试 - 应该抛出InterruptedException
        binlogFileReader.read(buffer);
    }

    @Test
    public void testRead_EmptyFile() throws IOException, InterruptedException {
        // 创建一个空文件
        Path emptyDir = Files.createTempDirectory("binlog-empty-test");
        Path emptyFile = emptyDir.resolve("binlog.000002");
        Files.write(emptyFile, new byte[0]);

        // 创建一个新的BinlogFileReader实例用于空文件
        BinlogFileReader emptyFileReader = new BinlogFileReader(
            emptyFile.getFileName().toString(),
            emptyFile.getParent().toString(),
            mockChecker
        );

        // 准备测试数据
        byte[] buffer = new byte[128];

        // 设置mock行为: 第一次检查需要等待，第二次不需要等待
        when(mockChecker.needWait(anyInt(), anyString()))
            .thenReturn(true)   // 第一次检查需要等待
            .thenReturn(false); // 第二次检查不需要等待

        // 在第二次调用时向文件写入数据
        Thread writeThread = new Thread(() -> {
            try {
                Thread.sleep(100); // 等待read方法开始执行
                Files.write(emptyFile, new byte[] {1, 2, 3, 4, 5});
            } catch (IOException | InterruptedException e) {
                // 忽略异常
            }
        });
        writeThread.start();

        // 执行测试
        int bytesRead = emptyFileReader.read(buffer);

        // 验证结果 - 应该读取到数据
        assertEquals(5, bytesRead);

        // 清理
        writeThread.join();
        Files.deleteIfExists(emptyFile);
        Files.deleteIfExists(emptyDir);
    }

    @Test
    public void testLength() {
        long length = binlogFileReader.length();
        assertEquals(1024, length);
    }

    @Test
    public void testGetName() {
        String name = binlogFileReader.getName();
        assertEquals(tempFile.getFileName().toString(), name);
    }

    @Test
    public void testIsComplete() {
        // 设置mock行为
        when(mockChecker.isCompleteFile(anyInt())).thenReturn(true);

        boolean isComplete = binlogFileReader.isComplete();

        assertTrue(isComplete);
        verify(mockChecker).isCompleteFile(anyInt());
    }

    @Test
    public void testClose() throws IOException, InterruptedException {
        // 创建一个带有真实RandomAccessFile的BinlogFileReader
        Path testDir = Files.createTempDirectory("binlog-close-test");
        Path testFile = testDir.resolve("binlog.000003");
        Files.write(testFile, new byte[] {1, 2, 3, 4, 5});

        BinlogFileReader reader = new BinlogFileReader(
            testFile.getFileName().toString(),
            testFile.getParent().toString(),
            mockChecker
        );

        // 强制创建RandomAccessFile
        byte[] buffer = new byte[3];
        when(mockChecker.needWait(anyInt(), anyString())).thenReturn(false);
        reader.read(buffer);

        // 关闭
        reader.close();

        // 再次读取应该重新打开文件
        int bytesRead = reader.read(buffer);
        assertEquals(3, bytesRead);

        // 清理
        Files.deleteIfExists(testFile);
        Files.deleteIfExists(testDir);
    }

    @SneakyThrows
    public void recreateFile() {
        // 创建符合规范的binlog文件名
        Files.delete(tempFile);
        Files.createFile(tempFile);
        byte[] testData = new byte[2048];
        for (int i = 0; i < testData.length; i++) {
            testData[i] = (byte) ((i * 2) % 256);
        }
        Files.write(tempFile, testData);
    }
}
