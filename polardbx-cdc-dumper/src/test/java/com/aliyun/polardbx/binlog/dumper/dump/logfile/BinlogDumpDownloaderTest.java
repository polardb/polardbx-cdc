/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.filesys.LocalFileSystem;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import io.grpc.stub.ServerCallStreamObserver;
import lombok.SneakyThrows;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_DOWNLOAD_MAX_WAIT_TIME_SECONDS;
import static com.aliyun.polardbx.binlog.ConfigKeys.IS_LAB_ENV;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

/**
 * BinlogDumpDownloader 单元测试，覆盖 wait() 方法中 skipSizeCheck 相关的增量代码
 */
public class BinlogDumpDownloaderTest extends BaseTest {

    /**
     * 通过反射设置 final 字段值（Java 8 兼容）
     */
    private void setFinalField(Object instance, String fieldName, Object value) throws Exception {
        Field field = BinlogDumpDownloader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        int modifiers = field.getModifiers();
        if (Modifier.isFinal(modifiers)) {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, modifiers & ~Modifier.FINAL);
        }
        field.set(instance, value);
    }

    @SuppressWarnings("unchecked")
    private BinlogDumpDownloader createMockDownloader(File testFile,
                                                      Map<String, Throwable> errorMap,
                                                      boolean skipSizeCheck) throws Exception {
        BinlogDumpDownloader downloader = Mockito.mock(BinlogDumpDownloader.class, Mockito.CALLS_REAL_METHODS);

        // Mock fileSystem
        LocalFileSystem mockFileSystem = Mockito.mock(LocalFileSystem.class);
        when(mockFileSystem.newFile("binlog.000001")).thenReturn(testFile);
        setFinalField(downloader, "fileSystem", mockFileSystem);

        // Mock observer - not cancelled
        @SuppressWarnings("unchecked")
        ServerCallStreamObserver<DumpStream> mockObserver = Mockito.mock(ServerCallStreamObserver.class);
        when(mockObserver.isCancelled()).thenReturn(false);
        setFinalField(downloader, "observer", mockObserver);

        // Mock dumpReader (for heartbeat)
        BinlogDumpReader mockReader = Mockito.mock(BinlogDumpReader.class);
        setFinalField(downloader, "dumpReader", mockReader);

        // Set masterHeartbeatPeriod (30s in nanoseconds)
        setFinalField(downloader, "masterHeartbeatPeriod", 30000000000L);

        // Set fileDownLoadErrorMap
        setFinalField(downloader, "fileDownLoadErrorMap", errorMap);

        // Set skipSizeCheck（构造函数中初始化的 final 字段，CALLS_REAL_METHODS 不执行构造函数）
        setFinalField(downloader, "skipSizeCheck", skipSizeCheck);

        return downloader;
    }

    /**
     * 测试 wait()：skipSizeCheck=true 且文件已存在时，
     * 应正常返回，不调用 getFileSize()
     */
    @Test
    @SneakyThrows
    public void testWaitWithSkipSizeCheckFileExists() {
        mockConfig(BINLOG_DUMP_DOWNLOAD_MAX_WAIT_TIME_SECONDS, "60");
        mockConfig(IS_LAB_ENV, "false");

        File tempFile = File.createTempFile("test_wait_skip", ".tmp");
        tempFile.deleteOnExit();

        Map<String, Throwable> errorMap = new ConcurrentHashMap<>();
        BinlogDumpDownloader downloader = createMockDownloader(tempFile, errorMap, true);

        Method waitMethod = BinlogDumpDownloader.class.getDeclaredMethod("wait", String.class);
        waitMethod.setAccessible(true);

        // skipSizeCheck=true, 文件已存在, 无错误 → 正常返回
        waitMethod.invoke(downloader, "binlog.000001");
    }

    /**
     * 测试 wait()：循环退出后的 post-loop error check，
     * 文件已存在但 fileDownLoadErrorMap 包含错误时应抛异常
     */
    @Test
    @SneakyThrows
    public void testWaitWithPostLoopDownloadError() {
        mockConfig(BINLOG_DUMP_DOWNLOAD_MAX_WAIT_TIME_SECONDS, "60");
        mockConfig(IS_LAB_ENV, "false");

        File tempFile = File.createTempFile("test_wait_error", ".tmp");
        tempFile.deleteOnExit();

        // 文件已存在，但 errorMap 包含错误 → while 循环不进入，post-loop check 触发
        Map<String, Throwable> errorMap = new ConcurrentHashMap<>();
        errorMap.put("binlog.000001", new RuntimeException("simulated download error"));
        BinlogDumpDownloader downloader = createMockDownloader(tempFile, errorMap, true);

        Method waitMethod = BinlogDumpDownloader.class.getDeclaredMethod("wait", String.class);
        waitMethod.setAccessible(true);

        try {
            waitMethod.invoke(downloader, "binlog.000001");
            fail("Expected exception to be thrown");
        } catch (InvocationTargetException e) {
            // wait() 中 IOException 被 catch(Exception e) 捕获后 throw new Exception(e)
            Throwable cause = e.getCause();
            assertTrue("Root cause should contain IOException",
                cause.getCause() instanceof IOException);
            assertTrue("Error message should mention download error",
                cause.getCause().getMessage().contains("download file binlog.000001 from oss error"));
        }
    }

    /**
     * 测试 wait()：skipSizeCheck=true，文件不存在，errorMap 包含错误，
     * while 循环进入后 in-loop error check 触发异常
     */
    @Test
    @SneakyThrows
    public void testWaitWithInLoopDownloadError() {
        mockConfig(BINLOG_DUMP_DOWNLOAD_MAX_WAIT_TIME_SECONDS, "60");
        mockConfig(IS_LAB_ENV, "false");

        // 使用不存在的文件，使 while 循环条件为 true
        File nonExistentFile = new File("/tmp/non_existent_" + System.nanoTime() + ".tmp");

        // errorMap 包含错误 → 进入循环后第一次检查即触发
        Map<String, Throwable> errorMap = new ConcurrentHashMap<>();
        errorMap.put("binlog.000001", new RuntimeException("simulated in-loop error"));
        BinlogDumpDownloader downloader = createMockDownloader(nonExistentFile, errorMap, true);

        Method waitMethod = BinlogDumpDownloader.class.getDeclaredMethod("wait", String.class);
        waitMethod.setAccessible(true);

        try {
            waitMethod.invoke(downloader, "binlog.000001");
            fail("Expected exception to be thrown");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue("Root cause should contain IOException",
                cause.getCause() instanceof IOException);
            assertTrue("Error message should mention download error",
                cause.getCause().getMessage().contains("download file binlog.000001 from oss error"));
        }
    }
}
