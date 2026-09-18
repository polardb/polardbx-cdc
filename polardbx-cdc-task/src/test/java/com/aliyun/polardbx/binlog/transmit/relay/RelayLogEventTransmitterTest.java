/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.testing.h2.H2Util;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.aliyun.polardbx.binlog.CommonConstants.VERSION_PATH_PREFIX;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_ROCKSDB_BASE_PATH;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_COUNT;
import static com.aliyun.polardbx.binlog.Constants.RELAY_DATA_FORCE_CLEAN_FLAG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RelayLogEventTransmitterTest extends BaseTest {
    private static final String TEST_TASK_NAME = "Dispatcher-1";

    private RelayLogEventTransmitter transmitter;
    private String testBasePath;
    private String testTaskBasePath;
    private String testPersistPath;

    @Before
    public void setUp() throws Exception {
        // 创建临时测试目录
        Path tempDir = Files.createTempDirectory("relay_test_");
        testBasePath = tempDir.toString();
        testTaskBasePath = testBasePath + File.separator + TEST_TASK_NAME + File.separator;
        testPersistPath = testTaskBasePath + VERSION_PATH_PREFIX + "1_1";

        // mock
        setConfig(ConfigKeys.TASK_NAME, TEST_TASK_NAME);
        mockConfig(BINLOGX_STREAM_COUNT, "3");
        mockConfig(BINLOGX_ROCKSDB_BASE_PATH, testBasePath);

        // 使用反射创建一个测试用的 RelayLogEventTransmitter 实例
        createTestTransmitter();
    }

    @After
    public void tearDown() throws IOException {
        // 清理测试目录
        if (testBasePath != null) {
            FileUtils.deleteDirectory(new File(testBasePath));
        }
    }

    @Test
    public void testTryCleanDirectory_CreateDirectoryWhenNotExists() throws IOException {
        // 确保目录不存在
        FileUtils.deleteDirectory(new File(testBasePath));
        assertFalse(new File(transmitter.getPersistPath()).exists());

        // 执行方法
        transmitter.tryCleanDirectory();

        // 验证目录已创建
        assertTrue(new File(transmitter.getPersistPath()).exists());
        assertTrue(new File(transmitter.getPersistPath()).isDirectory());
    }

    @Test
    public void testTryCleanDirectory_ForceCleanWhenFlagExists() throws IOException {
        // 创建目录和文件
        FileUtils.forceMkdir(new File(transmitter.getPersistPath()));
        File testFile = new File(transmitter.getPersistPath(), "test_file.txt");
        testFile.createNewFile();
        assertTrue(testFile.exists());

        // 创建强制清理标志文件
        File forceCleanFlag = new File(transmitter.getPersistPath(), RELAY_DATA_FORCE_CLEAN_FLAG);
        forceCleanFlag.createNewFile();
        assertTrue(forceCleanFlag.exists());

        // 执行方法
        transmitter.tryCleanDirectory();

        // 验证目录仍然存在但文件已被清理（除了强制清理标志文件外）
        assertTrue(new File(transmitter.getPersistPath()).exists());
        assertFalse(testFile.exists());
    }

    @Test
    public void testTryCleanDirectory_NoCleanWhenNoFlag() throws IOException {
        // 创建目录和文件
        FileUtils.forceMkdir(new File(transmitter.getPersistPath()));
        File testFile = new File(transmitter.getPersistPath(), "test_file.txt");
        testFile.createNewFile();
        assertTrue(testFile.exists());

        // 确保没有强制清理标志文件
        File forceCleanFlag = new File(transmitter.getPersistPath(), RELAY_DATA_FORCE_CLEAN_FLAG);
        assertFalse(forceCleanFlag.exists());

        // 执行方法
        transmitter.tryCleanDirectory();

        // 验证目录和文件仍然存在
        assertTrue(new File(transmitter.getPersistPath()).exists());
        assertTrue(testFile.exists());
    }

    @Test
    public void testTryCleanDirectory_CleanOldVersionDirs() throws IOException {
        // 创建当前版本目录
        FileUtils.forceMkdir(new File(transmitter.getPersistPath()));

        // 创建旧版本目录
        String oldVersionPath = testTaskBasePath + VERSION_PATH_PREFIX + "0_1";
        FileUtils.forceMkdir(new File(oldVersionPath));
        File oldVersionFile = new File(oldVersionPath, "old_file.txt");
        oldVersionFile.createNewFile();
        assertTrue(oldVersionFile.exists());

        // 创建另一个旧版本目录
        String anotherOldVersionPath = testTaskBasePath + VERSION_PATH_PREFIX + "0_2";
        FileUtils.forceMkdir(new File(anotherOldVersionPath));
        File anotherOldVersionFile = new File(anotherOldVersionPath, "another_old_file.txt");
        anotherOldVersionFile.createNewFile();
        assertTrue(anotherOldVersionFile.exists());

        // 执行方法
        transmitter.tryCleanDirectory();

        // 验证当前版本目录仍然存在
        assertTrue(new File(transmitter.getPersistPath()).exists());

        // 验证旧版本目录已被删除
        assertFalse(new File(oldVersionPath).exists());
        assertFalse(new File(anotherOldVersionPath).exists());
    }

    @Test
    public void testTryCleanDirectory_HandleIOException() throws Exception {
        // 创建当前版本目录
        FileUtils.forceMkdir(new File(testPersistPath));

        // 创建旧版本目录和其中的文件
        String oldVersionPath = testTaskBasePath + VERSION_PATH_PREFIX + "0_1";
        FileUtils.forceMkdir(new File(oldVersionPath));

        // 创建一个文件并设置为只读，以模拟删除时的IOException
        File oldVersionFile = new File(oldVersionPath, "old_file.txt");
        oldVersionFile.createNewFile();
        // 在支持的系统上设置文件为只读
        oldVersionFile.setReadOnly();
        assertTrue(oldVersionFile.exists());

        // 执行方法并验证抛出预期异常
        try {
            transmitter.tryCleanDirectory();
            // 如果没有抛出异常，检查是否确实存在删除失败的情况
            assertFalse("Should have thrown PolardbxException due to read-only file",
                new File(oldVersionPath).exists());
        } catch (PolardbxException e) {
            // 如果删除失败应该抛出PolardbxException
            assertTrue("Exception message should contain 'delete relay data directory failed'",
                e.getMessage().contains("delete relay data directory failed"));
        }
    }

    @Test
    public void testBuildStartTso() throws Exception {
        try (MockedStatic<RelayStreamUtils> mockedRelayStreamUtils = mockStatic(RelayStreamUtils.class)) {
            // Mock RelayStreamUtils.getStreamListAndCheck() 返回流列表
            List<String> mockStreams = Arrays.asList("stream_0", "stream_1", "stream_2");
            mockedRelayStreamUtils.when(RelayStreamUtils::getStreamListAndCheck).thenReturn(mockStreams);

            // 创建测试用的 RelayLogEventTransmitter 实例
            TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
            ExecutionConfig executionConfig = mock(ExecutionConfig.class);

            when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
            when(executionConfig.getRuntimeVersion()).thenReturn(1L);
            when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);

            // Mock StoreEngineManager 和 StoreEngine
            try (MockedStatic<StoreEngineManager> mockedStoreEngineManager = mockStatic(StoreEngineManager.class)) {
                StoreEngine mockStoreEngine0 = mock(StoreEngine.class);
                StoreEngine mockStoreEngine1 = mock(StoreEngine.class);
                StoreEngine mockStoreEngine2 = mock(StoreEngine.class);

                // 设置每个StoreEngine的seekMaxTso返回值
                when(mockStoreEngine0.seekMaxTso()).thenReturn("tso_100");
                when(mockStoreEngine1.seekMaxTso()).thenReturn("tso_300");
                when(mockStoreEngine2.seekMaxTso()).thenReturn("tso_200");

                mockedStoreEngineManager.when(() -> StoreEngineManager.newInstance(anyString(), anyInt()))
                    .thenReturn(mockStoreEngine0)
                    .thenReturn(mockStoreEngine1)
                    .thenReturn(mockStoreEngine2);

                RelayLogEventTransmitter testTransmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
                    @Override
                    public void init() {
                        // 覆盖init方法避免实际初始化
                        try {
                            // 使用反射设置storeEngineMap
                            Field storeEngineMapField =
                                RelayLogEventTransmitter.class.getDeclaredField("storeEngineMap");
                            storeEngineMapField.setAccessible(true);
                            Map<Integer, StoreEngine> storeEngineMap =
                                (Map<Integer, StoreEngine>) storeEngineMapField.get(this);
                            storeEngineMap.put(0, mockStoreEngine0);
                            storeEngineMap.put(1, mockStoreEngine1);
                            storeEngineMap.put(2, mockStoreEngine2);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };

                // 执行 buildStartTso 方法
                testTransmitter.buildStartTso();

                // 验证结果 - 应该是三个TSO中的最小值
                assertEquals("tso_100", testTransmitter.getStartTso());
            }
        }
    }

    @Test
    public void testParallelSearchStartTso() throws Exception {
        try (MockedStatic<RelayStreamUtils> mockedRelayStreamUtils = mockStatic(RelayStreamUtils.class)) {
            // Mock RelayStreamUtils.getStreamListAndCheck() 返回流列表
            List<String> mockStreams = Arrays.asList("stream_0", "stream_1", "stream_2");
            mockedRelayStreamUtils.when(RelayStreamUtils::getStreamListAndCheck).thenReturn(mockStreams);

            // 创建测试用的 RelayLogEventTransmitter 实例
            TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
            ExecutionConfig executionConfig = mock(ExecutionConfig.class);

            when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
            when(executionConfig.getRuntimeVersion()).thenReturn(1L);
            when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);

            // 创建mock的StoreEngine实例
            StoreEngine mockStoreEngine0 = mock(StoreEngine.class);
            StoreEngine mockStoreEngine1 = mock(StoreEngine.class);
            StoreEngine mockStoreEngine2 = mock(StoreEngine.class);

            // 设置每个StoreEngine的seekMaxTso返回值
            when(mockStoreEngine0.seekMaxTso()).thenReturn("tso_100");
            when(mockStoreEngine1.seekMaxTso()).thenReturn("tso_200");
            when(mockStoreEngine2.seekMaxTso()).thenReturn("tso_150");

            RelayLogEventTransmitter testTransmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
                @Override
                public void init() {
                    // 覆盖init方法避免实际初始化
                    try {
                        // 使用反射设置storeEngineMap
                        Field storeEngineMapField = RelayLogEventTransmitter.class.getDeclaredField("storeEngineMap");
                        storeEngineMapField.setAccessible(true);
                        Map<Integer, StoreEngine> storeEngineMap =
                            (Map<Integer, StoreEngine>) storeEngineMapField.get(this);
                        storeEngineMap.put(0, mockStoreEngine0);
                        storeEngineMap.put(1, mockStoreEngine1);
                        storeEngineMap.put(2, mockStoreEngine2);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };

            // 执行 parallelSearchStartTso 方法
            testTransmitter.parallelSearchStartTso(mockStreams);

            // 验证每个StoreEngine的seekMaxTso方法被调用
            verify(mockStoreEngine0, times(1)).seekMaxTso();
            verify(mockStoreEngine1, times(1)).seekMaxTso();
            verify(mockStoreEngine2, times(1)).seekMaxTso();

            // 验证streamMaxTsoMap被正确填充
            Field streamMaxTsoMapField = RelayLogEventTransmitter.class.getDeclaredField("streamMaxTsoMap");
            streamMaxTsoMapField.setAccessible(true);
            Map<Integer, String> streamMaxTsoMap = (Map<Integer, String>) streamMaxTsoMapField.get(testTransmitter);

            assertEquals("tso_100", streamMaxTsoMap.get(0));
            assertEquals("tso_200", streamMaxTsoMap.get(1));
            assertEquals("tso_150", streamMaxTsoMap.get(2));
        }
    }

    @Test
    public void testCalcStartTso() throws Exception {
        // 创建测试用的 RelayLogEventTransmitter 实例
        TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
        ExecutionConfig executionConfig = mock(ExecutionConfig.class);

        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);

        // 创建mock的StoreEngine实例
        StoreEngine mockStoreEngine0 = mock(StoreEngine.class);

        RelayLogEventTransmitter testTransmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
            @Override
            public void init() {
                // 覆盖init方法避免实际初始化
                try {
                    // 使用反射设置storeEngineMap
                    Field storeEngineMapField = RelayLogEventTransmitter.class.getDeclaredField("storeEngineMap");
                    storeEngineMapField.setAccessible(true);
                    Map<Integer, StoreEngine> storeEngineMap =
                        (Map<Integer, StoreEngine>) storeEngineMapField.get(this);
                    storeEngineMap.put(0, mockStoreEngine0);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        // 设置streamMaxTsoMap的值
        Field streamMaxTsoMapField = RelayLogEventTransmitter.class.getDeclaredField("streamMaxTsoMap");
        streamMaxTsoMapField.setAccessible(true);
        Map<Integer, String> streamMaxTsoMap = (Map<Integer, String>) streamMaxTsoMapField.get(testTransmitter);
        streamMaxTsoMap.put(0, "tso_100");
        streamMaxTsoMap.put(1, "tso_200");
        streamMaxTsoMap.put(2, "tso_150");

        // 执行 calcStartTso 方法
        testTransmitter.calcStartTso();

        // 验证结果 - 应该是三个TSO中的最小值
        assertEquals("tso_100", testTransmitter.getStartTso());

        // 验证setOriginStartTso方法被调用
        verify(mockStoreEngine0, times(1)).setOriginStartTso("tso_100");
    }

    @Test
    public void testCalcStartTsoWithEmptyStream() throws Exception {
        // 创建测试用的 RelayLogEventTransmitter 实例
        TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
        ExecutionConfig executionConfig = mock(ExecutionConfig.class);

        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);

        // 创建mock的StoreEngine实例
        StoreEngine mockStoreEngine0 = mock(StoreEngine.class);

        RelayLogEventTransmitter testTransmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
            @Override
            public void init() {
                // 覆盖init方法避免实际初始化
                try {
                    // 使用反射设置storeEngineMap
                    Field storeEngineMapField = RelayLogEventTransmitter.class.getDeclaredField("storeEngineMap");
                    storeEngineMapField.setAccessible(true);
                    Map<Integer, StoreEngine> storeEngineMap =
                        (Map<Integer, StoreEngine>) storeEngineMapField.get(this);
                    storeEngineMap.put(0, mockStoreEngine0);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        // 设置streamMaxTsoMap的值，其中一个为空
        Field streamMaxTsoMapField = RelayLogEventTransmitter.class.getDeclaredField("streamMaxTsoMap");
        streamMaxTsoMapField.setAccessible(true);
        Map<Integer, String> streamMaxTsoMap = (Map<Integer, String>) streamMaxTsoMapField.get(testTransmitter);
        streamMaxTsoMap.put(0, "tso_100");
        streamMaxTsoMap.put(1, ""); // 空值
        streamMaxTsoMap.put(2, "tso_150");

        // 执行 calcStartTso 方法
        testTransmitter.calcStartTso();

        // 验证结果 - 当任何一个流的TSO为空时，startTso应该为空
        assertEquals("", testTransmitter.getStartTso());

        // 验证setOriginStartTso方法被调用
        verify(mockStoreEngine0, times(1)).setOriginStartTso("");
    }

    /**
     * 通过反射创建测试用的 RelayLogEventTransmitter 实例
     */
    private void createTestTransmitter() {
        // 创建 TaskRuntimeConfig 和 ExecutionConfig
        TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
        ExecutionConfig executionConfig = mock(ExecutionConfig.class);

        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);

        transmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
            @Override
            public void init() {

            }
        };

        assertEquals(testTaskBasePath, transmitter.getTaskBasePath());
        assertEquals(testPersistPath, transmitter.getPersistPath());
    }

    @Test
    public void testCheckVersion_SuccessWithMatchingVersions() throws Exception {
        // 创建测试用的 RelayLogEventTransmitter 实例
        TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
        ExecutionConfig executionConfig = mock(ExecutionConfig.class);

        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);

        RelayLogEventTransmitter testTransmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
            @Override
            public void init() {
                // 覆盖init方法避免实际初始化
            }
        };

        // 创建BinlogCursor，版本匹配
        BinlogCursor cursor = new BinlogCursor("binlog.000001", 100L, "group", "stream", "tso", 1L, 1L, 1);

        // 执行checkVersion方法
        boolean result = testTransmitter.checkVersion(cursor);

        // 验证结果为true
        assertTrue(result);
    }

    @Test
    public void testCheckVersion_FailureWithMismatchedMainVersion() throws Exception {
        // 创建测试用的 RelayLogEventTransmitter 实例
        TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
        ExecutionConfig executionConfig = mock(ExecutionConfig.class);

        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);

        RelayLogEventTransmitter testTransmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
            @Override
            public void init() {
                // 覆盖init方法避免实际初始化
            }
        };

        // 创建BinlogCursor，主版本不匹配
        BinlogCursor cursor = new BinlogCursor("binlog.000001", 100L, "group", "stream", "tso", 2L, 1L, 1);

        // 执行checkVersion方法
        boolean result = testTransmitter.checkVersion(cursor);

        // 验证结果为false
        assertFalse(result);
    }

    @Test
    public void testCheckVersion_SuccessWithNullSubVersion() throws Exception {
        // 创建测试用的 RelayLogEventTransmitter 实例
        TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
        ExecutionConfig executionConfig = mock(ExecutionConfig.class);

        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);

        RelayLogEventTransmitter testTransmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
            @Override
            public void init() {
                // 覆盖init方法避免实际初始化
            }
        };

        // 创建BinlogCursor，subVersion为null
        BinlogCursor cursor = new BinlogCursor("binlog.000001", 100L, "group", "stream", "tso", 1L, null, 1);

        // 执行checkVersion方法
        boolean result = testTransmitter.checkVersion(cursor);

        // 验证结果为true
        assertTrue(result);
    }

    @Test
    public void testCheckVersion_FailureWithMismatchedSubVersion() throws Exception {
        // 创建测试用的 RelayLogEventTransmitter 实例
        TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
        ExecutionConfig executionConfig = mock(ExecutionConfig.class);

        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);

        RelayLogEventTransmitter testTransmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
            @Override
            public void init() {
                // 覆盖init方法避免实际初始化
            }
        };

        // 创建BinlogCursor，subVersion不匹配
        BinlogCursor cursor = new BinlogCursor("binlog.000001", 100L, "group", "stream", "tso", 1L, 2L, 1);

        // 执行checkVersion方法
        boolean result = testTransmitter.checkVersion(cursor);

        // 验证结果为false
        assertFalse(result);
    }

    @Test(expected = PolardbxException.class)
    public void testCheckVersion_ExceptionWhenSubVersionNullAndSubRuntimeVersionGreaterThanOne() throws Exception {
        // 创建测试用的 RelayLogEventTransmitter 实例
        TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
        ExecutionConfig executionConfig = mock(ExecutionConfig.class);

        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(2L); // 大于1

        RelayLogEventTransmitter testTransmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
            @Override
            public void init() {
                // 覆盖init方法避免实际初始化
            }
        };

        // 创建BinlogCursor，subVersion为null
        BinlogCursor cursor = new BinlogCursor("binlog.000001", 100L, "group", "stream", "tso", 1L, null, 1);

        // 执行checkVersion方法，应该抛出异常
        testTransmitter.checkVersion(cursor);
    }

    @Test
    public void testCheckVersion_SuccessWithNullCursorVersion() throws Exception {
        // 创建测试用的 RelayLogEventTransmitter 实例
        TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
        ExecutionConfig executionConfig = mock(ExecutionConfig.class);

        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);

        RelayLogEventTransmitter testTransmitter = new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
            @Override
            public void init() {
                // 覆盖init方法避免实际初始化
            }
        };

        // 创建BinlogCursor，version为null
        BinlogCursor cursor = new BinlogCursor("binlog.000001", 100L, "group", "stream", "tso", null, 1L, 1);

        // 执行checkVersion方法
        boolean result = testTransmitter.checkVersion(cursor);

        // 验证结果为false
        assertFalse(result);
    }

    // ========== computeTsoBefore tests ==========

    @Test
    public void testComputeTsoBefore_Basic() {
        long nowMs = System.currentTimeMillis();
        String tso = buildTso(nowMs);

        String result = RelayLogEventTransmitter.computeTsoBefore(tso, 10);

        // 结果应该比原始TSO小（在排序上靠前）
        assertTrue(result.compareTo(tso) < 0);
    }

    @Test
    public void testComputeTsoBefore_BufferMinutes() {
        long nowMs = System.currentTimeMillis();
        String tso5 = buildTso(nowMs - 5 * 60 * 1000L);
        String tso10 = buildTso(nowMs - 10 * 60 * 1000L);

        // 从当前时间回退5分钟，应该接近tso5
        String result5 = RelayLogEventTransmitter.computeTsoBefore(buildTso(nowMs), 5);
        // 从当前时间回退10分钟，应该接近tso10
        String result10 = RelayLogEventTransmitter.computeTsoBefore(buildTso(nowMs), 10);

        // result10 < result5 < now
        assertTrue(result10.compareTo(result5) < 0);
    }

    // ========== computeTsoBefore TSO length comparison tests ==========

    @Test
    public void testComputeTsoBefore_Returns19Digits() {
        long nowMs = System.currentTimeMillis();
        String tso38 = buildTso(nowMs);

        String result = RelayLogEventTransmitter.computeTsoBefore(tso38, 10);

        assertEquals("computeTsoBefore should return 19-digit string", 19, result.length());
    }

    @Test
    public void testComputeTsoBefore_CompareWith38DigitTso_SameTimestamp() {
        // Same physical timestamp: 19-digit < 38-digit in string comparison
        long nowMs = System.currentTimeMillis();
        String tso38 = buildTso(nowMs);
        // Extract the first 19 digits which represent the same timestamp
        String tso19Prefix = tso38.substring(0, 19);

        // computeTsoBefore with 0 buffer returns the same physical timestamp as input
        String result19 = RelayLogEventTransmitter.computeTsoBefore(tso38, 0);

        // The 19-digit result has the same physical timestamp as the 38-digit TSO
        // In string comparison: shorter string < longer string when prefixes match
        assertTrue("19-digit TSO should be less than 38-digit TSO with same timestamp prefix",
            result19.compareTo(tso38) < 0);
    }

    @Test
    public void testComputeTsoBefore_CompareWith38DigitTso_EarlierTimestamp() {
        // computeTsoBefore result (later) vs 38-digit earlier TSO → 19-digit > 38-digit
        long nowMs = System.currentTimeMillis();
        long earlierMs = nowMs - 60 * 60 * 1000L; // 1 hour earlier

        String result19 = RelayLogEventTransmitter.computeTsoBefore(buildTso(nowMs), 10);
        String earlierTso38 = buildTso(earlierMs);

        // result19 represents a later time than earlierTso38
        // Different timestamps → first differing digit decides → correct ordering
        assertTrue("19-digit later TSO should be greater than 38-digit earlier TSO",
            result19.compareTo(earlierTso38) > 0);
    }

    @Test
    public void testComputeTsoBefore_CompareWith38DigitTso_LaterTimestamp() {
        // computeTsoBefore result (earlier) vs 38-digit later TSO → 19-digit < 38-digit
        long nowMs = System.currentTimeMillis();
        long laterMs = nowMs + 60 * 60 * 1000L; // 1 hour later

        String result19 = RelayLogEventTransmitter.computeTsoBefore(buildTso(nowMs), 10);
        String laterTso38 = buildTso(laterMs);

        // result19 represents an earlier time than laterTso38
        // Different timestamps → first differing digit decides → correct ordering
        assertTrue("19-digit earlier TSO should be less than 38-digit later TSO",
            result19.compareTo(laterTso38) < 0);
    }

    @Test
    public void testComputeTsoBefore_CleanupProgressScenario() {
        // Simulate the actual cleanup scenario from RelayLogEventCleaner:
        // First run: Rule 1 sets maxCleanTso to 38-digit checkpointLastTso
        // Second run: Rule 2/3 computes 19-digit cleanupTso
        // If cleanupTso represents a later time → should advance (compareTo > 0)

        long nowMs = System.currentTimeMillis();
        String maxCleanTso38 = buildTso(nowMs - 60 * 60 * 1000L); // 1 hour ago (38-digit)
        String cleanupTso19 = RelayLogEventTransmitter.computeTsoBefore(
            buildTso(nowMs), 10); // 10 min ago (19-digit)

        // cleanupTso19 is ~10 min ago, maxCleanTso38 is ~60 min ago
        // cleanupTso19 > maxCleanTso38 → progress should be made
        assertTrue("19-digit cleanupTso (later) should be greater than 38-digit maxCleanTso (earlier)",
            cleanupTso19.compareTo(maxCleanTso38) > 0);
    }

    @Test
    public void testComputeTsoBefore_NoProgressScenario() {
        // Simulate: maxCleanTso already at a later time than cleanupTso
        long nowMs = System.currentTimeMillis();
        String maxCleanTso38 = buildTso(nowMs - 5 * 60 * 1000L); // 5 min ago (38-digit)
        String cleanupTso19 = RelayLogEventTransmitter.computeTsoBefore(
            buildTso(nowMs), 120); // 120 min ago (19-digit)

        // cleanupTso19 is ~120 min ago, maxCleanTso38 is ~5 min ago
        // cleanupTso19 < maxCleanTso38 → no progress
        assertTrue("19-digit cleanupTso (earlier) should be less than 38-digit maxCleanTso (later)",
            cleanupTso19.compareTo(maxCleanTso38) < 0);
    }

    private String buildTso(long physicalTimeMs) {
        long tsoTimestamp = physicalTimeMs << 22;
        return String.format("%019d%019d", tsoTimestamp, 0L);
    }

    private void insertOssRecord(Connection conn, String binlogFile, String streamId,
                                 String lastTso, int uploadStatus, int purgeStatus) throws Exception {
        String sql = String.format(
            "INSERT INTO binlog_oss_record (binlog_file, stream_id, last_tso, upload_status, purge_status, cluster_id) "
                + "VALUES ('%s', '%s', %s, %d, %d, '0')",
            binlogFile, streamId,
            lastTso == null ? "NULL" : "'" + lastTso + "'",
            uploadStatus, purgeStatus);
        H2Util.executeUpdate(conn, sql);
    }

    private RelayLogEventTransmitter createSimpleTransmitter() {
        TaskRuntimeConfig taskRuntimeConfig = mock(TaskRuntimeConfig.class);
        ExecutionConfig executionConfig = mock(ExecutionConfig.class);
        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(1L);
        return new RelayLogEventTransmitter(null, taskRuntimeConfig, null) {
            @Override
            public void init() {
            }
        };
    }
}