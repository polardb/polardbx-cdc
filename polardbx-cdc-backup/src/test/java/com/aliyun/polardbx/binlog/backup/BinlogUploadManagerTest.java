/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.MetaDbDataSource;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author yudong
 * @since 2024/7/25 13:51
 **/
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class BinlogUploadManagerTest {
    @Mock
    private MetaDbDataSource metaDs;
    @Mock
    private BinlogOssRecordMapper mapper;
    @Mock
    private Connection conn;
    @Mock
    private Statement stmt;
    @Mock
    private BinlogOssRecord record;
    @Mock
    private BinlogUploadManager manager;
    @Mock
    private ResultSet resultSet;
    private MockedStatic<SpringContextHolder> springContextHolder;
    private MockedStatic<DynamicApplicationConfig> dynamicApplicationConfig;
    private static final ScheduledThreadPoolExecutor keepAliveExecutor = new ScheduledThreadPoolExecutor(1);
    private static final String binlogFileName = "binlog.000001";
    private static final String clusterId = "cluster_1";
    private static final String lockName = "uploadFile1";
    private static final String LOCK_SQL = "SELECT GET_LOCK('" + lockName + "',1)";

    @Before
    @SneakyThrows
    public void before() {
        springContextHolder = mockStatic(SpringContextHolder.class);
        dynamicApplicationConfig = mockStatic(DynamicApplicationConfig.class);
        springContextHolder.when(() -> SpringContextHolder.getObject("metaDataSource")).thenReturn(metaDs);
        springContextHolder.when(() -> SpringContextHolder.getObject(BinlogOssRecordMapper.class)).thenReturn(mapper);
        dynamicApplicationConfig.when(() -> getString(ConfigKeys.INST_IP)).thenReturn("127.1");
        dynamicApplicationConfig.when(() -> getString(ConfigKeys.CLUSTER_ID)).thenReturn(clusterId);
        dynamicApplicationConfig.when(() -> getInt(ConfigKeys.BINLOG_BACKUP_UPLOAD_MAX_THREAD_NUM)).thenReturn(10);
        when(metaDs.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        when(record.getBinlogFile()).thenReturn(binlogFileName);
        when(record.getId()).thenReturn(1);
        when(manager.getRecordMapper()).thenReturn(mapper);
        when(manager.getKeepAliveExecutor()).thenReturn(keepAliveExecutor);
        Mockito.doCallRealMethod().when(manager).getLockName(1);
        Mockito.doCallRealMethod().when(manager).globalLock(record, conn);
        Mockito.doCallRealMethod().when(manager).globalUnLock(record, conn);
        Mockito.doCallRealMethod().when(manager).processUpload(Mockito.any(BinlogOssRecord.class));
        Mockito.doCallRealMethod().when(manager).setUploadStatusToUploading(record);
        Mockito.doCallRealMethod().when(manager).setUploadStatusToSuccess(record);
    }

    @After
    public void after() {
        springContextHolder.close();
        dynamicApplicationConfig.close();
    }

    @Test
    @SneakyThrows
    public void testProcessUpload_lock_conflict() {
        Connection connection = buildConnection();
        when(manager.getConnection()).thenReturn(connection);

        SQLException lockException =
            new SQLException("Lock wait timeout exceeded; try restarting transaction", "", 1205);
        when(stmt.executeQuery(LOCK_SQL)).thenThrow(lockException);
        manager.processUpload(record);
        verify(stmt, times(1)).executeQuery(LOCK_SQL);
        verify(manager, times(0)).setUploadStatusToUploading(record);
    }

    @Test(expected = SQLException.class)
    @SneakyThrows
    public void testProcessUpload_meet_other_exception() {
        Connection connection = buildConnection();
        when(manager.getConnection()).thenReturn(connection);

        SQLException lockException =
            new SQLException("Lock wait timeout exceeded; try restarting transaction", "", 1111);
        when(stmt.executeQuery(LOCK_SQL)).thenThrow(lockException);
        manager.processUpload(record);
    }

    @Test
    @SneakyThrows
    public void testProcessUpload_all_ok() {
        Connection connection = buildConnection();
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);
        when(stmt.executeQuery(LOCK_SQL)).thenReturn(resultSet);
        when(manager.getConnection()).thenReturn(connection);

        Mockito.doNothing().when(manager).deleteFileOnRemote(record);
        Mockito.doNothing().when(manager).doUpload(record);

        Mockito.doCallRealMethod().when(manager).processUpload(Mockito.any(BinlogOssRecord.class));
        manager.processUpload(record);
        verify(stmt, times(2)).executeQuery(anyString());
        verify(manager, times(1)).setUploadStatusToUploading(record);
        verify(manager, times(1)).setUploadStatusToSuccess(record);
    }

    @Test
    public void testStartWithStreams() {
        // 准备测试数据
        Set<String> newStreams = new HashSet<>();
        newStreams.add("stream3");
        newStreams.add("stream4");

        Map<String, MetricsObserver> metricsMap = new HashMap<>();
        MetricsObserver observer3 = Mockito.mock(MetricsObserver.class);
        MetricsObserver observer4 = Mockito.mock(MetricsObserver.class);
        metricsMap.put("stream3", observer3);
        metricsMap.put("stream4", observer4);

        // 创建manager实例
        StreamContext context = Mockito.mock(StreamContext.class);
        when(context.getTaskType()).thenReturn(TaskType.DumperX);
        when(context.getVersion()).thenReturn(1L);
        when(context.getGroup()).thenReturn("group1");
        when(context.getTaskName()).thenReturn("task1");

        Set<String> originalStreams = new HashSet<>();
        originalStreams.add("stream1");
        originalStreams.add("stream2");
        when(context.getStreamSet()).thenReturn(originalStreams);

        BinlogUploadManager realManager = new BinlogUploadManager(context, new HashMap<>());

        // 调用测试方法
        realManager.start(newStreams, metricsMap);

        // 验证结果
        // 1. 验证streamSet已添加新流
        assertTrue(realManager.getStreamSet().contains("stream3"));
        assertTrue(realManager.getStreamSet().contains("stream4"));
        assertTrue(realManager.getStreamSet().contains("stream1")); // 原有流仍然存在

        // 2. 验证fileSystemMap已添加新流对应的文件系统
        assertNotNull(realManager.getFileSystemMap().get("stream3"));
        assertNotNull(realManager.getFileSystemMap().get("stream4"));

        // 3. 验证metricsObserverMap已添加新流对应的观察者
        assertEquals(observer3, realManager.getMetricsObserverMap().get("stream3"));
        assertEquals(observer4, realManager.getMetricsObserverMap().get("stream4"));
    }

    @Test
    public void testStopWithStream() {
        // 准备测试数据
        Map<String, MetricsObserver> metricsMap = new HashMap<>();
        MetricsObserver observer1 = Mockito.mock(MetricsObserver.class);
        MetricsObserver observer2 = Mockito.mock(MetricsObserver.class);
        metricsMap.put("stream1", observer1);
        metricsMap.put("stream2", observer2);

        // 创建manager实例
        StreamContext context = Mockito.mock(StreamContext.class);
        when(context.getTaskType()).thenReturn(TaskType.DumperX);
        when(context.getVersion()).thenReturn(1L);
        when(context.getGroup()).thenReturn("group1");
        when(context.getTaskName()).thenReturn("task1");

        Set<String> originalStreams = new HashSet<>();
        originalStreams.add("stream1");
        originalStreams.add("stream2");
        when(context.getStreamSet()).thenReturn(originalStreams);

        BinlogUploadManager realManager = new BinlogUploadManager(context, metricsMap);

        // 确保初始状态正确
        assertTrue(realManager.getStreamSet().contains("stream1"));
        assertTrue(realManager.getStreamSet().contains("stream2"));
        assertNotNull(realManager.getFileSystemMap().get("stream1"));
        assertNotNull(realManager.getFileSystemMap().get("stream2"));
        assertNotNull(realManager.getMetricsObserverMap().get("stream1"));
        assertNotNull(realManager.getMetricsObserverMap().get("stream2"));

        // 调用测试方法 - 移除stream1
        realManager.stop("stream1");

        // 验证结果
        // 1. 验证stream1已从streamSet中移除
        assertFalse(realManager.getStreamSet().contains("stream1"));
        assertTrue(realManager.getStreamSet().contains("stream2"));

        // 2. 验证stream1已从fileSystemMap中移除
        assertNull(realManager.getFileSystemMap().get("stream1"));
        assertNotNull(realManager.getFileSystemMap().get("stream2"));

        // 3. 验证stream1已从metricsObserverMap中移除
        assertNull(realManager.getMetricsObserverMap().get("stream1"));
        assertNotNull(realManager.getMetricsObserverMap().get("stream2"));
    }

    @Test
    public void testStopWithNonExistentStream() {
        // 准备测试数据
        Map<String, MetricsObserver> metricsMap = new HashMap<>();

        // 创建manager实例
        StreamContext context = Mockito.mock(StreamContext.class);
        when(context.getTaskType()).thenReturn(TaskType.DumperX);
        when(context.getVersion()).thenReturn(1L);
        when(context.getGroup()).thenReturn("group1");
        when(context.getTaskName()).thenReturn("task1");

        Set<String> originalStreams = new HashSet<>();
        originalStreams.add("stream1");
        originalStreams.add("stream2");
        when(context.getStreamSet()).thenReturn(originalStreams);

        BinlogUploadManager realManager = new BinlogUploadManager(context, metricsMap);

        // 确保初始状态正确
        assertEquals(2, realManager.getStreamSet().size());

        // 调用测试方法 - 尝试移除不存在的stream
        realManager.stop("nonExistentStream");

        // 验证结果 - 现有streams应该保持不变
        assertEquals(2, realManager.getStreamSet().size());
        assertTrue(realManager.getStreamSet().contains("stream1"));
        assertTrue(realManager.getStreamSet().contains("stream2"));
    }

    @Test
    public void testDispatchUploadJobs() {
        // 准备测试数据
        Map<String, MetricsObserver> metricsMap = new HashMap<>();

        // 创建manager实例
        StreamContext context = Mockito.mock(StreamContext.class);
        when(context.getTaskType()).thenReturn(TaskType.DumperX);
        when(context.getVersion()).thenReturn(1L);
        when(context.getGroup()).thenReturn("group1");
        when(context.getTaskName()).thenReturn("task1");

        Set<String> originalStreams = new HashSet<>();
        originalStreams.add("stream1");
        when(context.getStreamSet()).thenReturn(originalStreams);

        BinlogUploadManager realManager = spy(new BinlogUploadManager(context, metricsMap));
        doReturn(false).when(realManager).uploadFinished(anyInt());

        // 创建测试记录
        BinlogOssRecord record1 = new BinlogOssRecord();
        record1.setId(1);
        record1.setBinlogFile("binlog.000001");
        record1.setStreamId("stream1");

        BinlogOssRecord record2 = new BinlogOssRecord();
        record2.setId(2);
        record2.setBinlogFile("binlog.000002");
        record2.setStreamId("stream1");

        List<BinlogOssRecord> records = new ArrayList<>();
        records.add(record1);
        records.add(record2);

        // 使用反射访问私有字段
        Set<String> uploadingFiles = realManager.getUploadingFiles();
        Map<String, Map<String, java.util.concurrent.Future<?>>> runningUploadTasks =
            realManager.getRunningUploadTasks();

        // 验证初始状态
        assertTrue(uploadingFiles.isEmpty());
        assertTrue(runningUploadTasks.isEmpty());

        // 调用测试方法
        realManager.dispatchUploadJobs(records);

        // 验证结果
        // 1. 验证文件已添加到上传中集合
        assertTrue(uploadingFiles.contains("binlog.000001"));
        assertTrue(uploadingFiles.contains("binlog.000002"));

        // 2. 验证任务已添加到运行任务映射中
        assertNotNull(runningUploadTasks.get("stream1"));
        assertEquals(2, runningUploadTasks.get("stream1").size());
        assertNotNull(runningUploadTasks.get("stream1").get("binlog.000001"));
        assertNotNull(runningUploadTasks.get("stream1").get("binlog.000002"));
    }

    @Test
    public void testDispatchUploadJobs_SkipUploadingFiles() {
        // 准备测试数据
        Map<String, MetricsObserver> metricsMap = new HashMap<>();

        // 创建manager实例
        StreamContext context = Mockito.mock(StreamContext.class);
        when(context.getTaskType()).thenReturn(TaskType.DumperX);
        when(context.getVersion()).thenReturn(1L);
        when(context.getGroup()).thenReturn("group1");
        when(context.getTaskName()).thenReturn("task1");

        Set<String> originalStreams = new HashSet<>();
        originalStreams.add("stream1");
        when(context.getStreamSet()).thenReturn(originalStreams);

        BinlogUploadManager realManager = spy(new BinlogUploadManager(context, metricsMap));
        doReturn(false).when(realManager).uploadFinished(anyInt());

        // 创建测试记录
        BinlogOssRecord record1 = new BinlogOssRecord();
        record1.setId(1);
        record1.setBinlogFile("binlog.000001");
        record1.setStreamId("stream1");

        BinlogOssRecord record2 = new BinlogOssRecord();
        record2.setId(2);
        record2.setBinlogFile("binlog.000002");
        record2.setStreamId("stream1");

        List<BinlogOssRecord> records = new ArrayList<>();
        records.add(record1);
        records.add(record2);

        // 模拟record1已经在上传中
        Set<String> uploadingFiles = realManager.getUploadingFiles();
        uploadingFiles.add("binlog.000001");

        Map<String, Map<String, java.util.concurrent.Future<?>>> runningUploadTasks =
            realManager.getRunningUploadTasks();

        // 调用测试方法
        realManager.dispatchUploadJobs(records);

        // 验证结果
        // 1. 验证只有record2被添加到上传任务中
        assertTrue(uploadingFiles.contains("binlog.000001")); // 原本就在上传中
        assertTrue(uploadingFiles.contains("binlog.000002")); // 新添加的

        // 2. 验证只有record2的任务被创建
        assertNotNull(runningUploadTasks.get("stream1"));
        assertEquals(1, runningUploadTasks.get("stream1").size());
        assertNull(runningUploadTasks.get("stream1").get("binlog.000001")); // 没有被处理
        assertNotNull(runningUploadTasks.get("stream1").get("binlog.000002")); // 被处理了
        verify(realManager, times(1)).uploadFinished(anyInt());
    }

    @Test
    public void testDispatchUploadJobs_SkipFinishedUploads() throws Exception {
        // 准备测试数据
        Map<String, MetricsObserver> metricsMap = new HashMap<>();

        // 创建manager实例
        StreamContext context = Mockito.mock(StreamContext.class);
        when(context.getTaskType()).thenReturn(TaskType.DumperX);
        when(context.getVersion()).thenReturn(1L);
        when(context.getGroup()).thenReturn("group1");
        when(context.getTaskName()).thenReturn("task1");

        Set<String> originalStreams = new HashSet<>();
        originalStreams.add("stream1");
        when(context.getStreamSet()).thenReturn(originalStreams);

        BinlogUploadManager realManager = spy(new BinlogUploadManager(context, metricsMap));

        // 创建测试记录
        BinlogOssRecord record1 = new BinlogOssRecord();
        record1.setId(1);
        record1.setBinlogFile("binlog.000001");
        record1.setStreamId("stream1");

        BinlogOssRecord record2 = new BinlogOssRecord();
        record2.setId(2);
        record2.setBinlogFile("binlog.000002");
        record2.setStreamId("stream1");

        List<BinlogOssRecord> records = new ArrayList<>();
        records.add(record1);
        records.add(record2);

        // 模拟record1已经上传完成
        Mockito.doReturn(true).when(realManager).uploadFinished(1);
        Mockito.doReturn(false).when(realManager).uploadFinished(2);

        Map<String, Map<String, java.util.concurrent.Future<?>>> runningUploadTasks =
            realManager.getRunningUploadTasks();

        // 调用测试方法
        realManager.dispatchUploadJobs(records);

        // 验证结果
        // 1. 验证只有record2被添加到上传任务中
        assertEquals(1, runningUploadTasks.get("stream1").size());
        assertNotNull(runningUploadTasks.get("stream1").get("binlog.000002")); // 被处理了
        assertNull(runningUploadTasks.get("stream1").get("binlog.000001")); // 没有被处理
    }

    private Connection buildConnection() {
        MetaDbDataSource metaDs = getObject("metaDataSource");
        return metaDs.getConnection();
    }
}
