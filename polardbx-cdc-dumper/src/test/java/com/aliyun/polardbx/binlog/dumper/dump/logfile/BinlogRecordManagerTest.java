/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_BACKUP_UPLOAD_PART_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_FILE_SEEK_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_BUFFER_DIRECT_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BinlogRecordManager单元测试类
 */
public class BinlogRecordManagerTest extends BaseTest {

    @Mock
    private BinlogOssRecordMapper recordMapper;

    @Mock
    private BinlogOssRecordService recordService;

    @Mock
    private RemoteBinlogProxy remoteBinlogProxy;

    private BinlogRecordManager binlogRecordManager;

    private final long version = 1L;
    private final String group = "testGroup";
    private final String stream = "testStream";
    private final String taskName = "testTask";
    private final TaskType taskType = TaskType.Dumper;
    private final String rootPath = "/test/path";

    static {
        // 在类加载之前设置配置项，以避免RemoteBinlogProxy初始化失败
        System.setProperty("binlog_backup_upload_part_size", "1024");
        System.setProperty("binlog_backup_type", "NULL");
        System.setProperty("is_lab_env", "false");
        System.setProperty("oss_bucket_name", "test-bucket");
        System.setProperty("polardbx_instance_id", "test-instance");
        System.setProperty("test_open_binlog_lab_event_support", "false");
    }

    @Before
    public void setUp() {
        // 初始化mock对象
        recordMapper = mock(BinlogOssRecordMapper.class);
        recordService = mock(BinlogOssRecordService.class);
        remoteBinlogProxy = mock(RemoteBinlogProxy.class);

        // 注册Spring对象
        registerSpringObject(BinlogOssRecordMapper.class, recordMapper);
        registerSpringObject(BinlogOssRecordService.class, recordService);

        // mock配置
        mockConfig(CLUSTER_ID, "testCluster");
        mockConfig(BINLOG_FILE_SEEK_BUFFER_SIZE, "1024");
        mockConfig(BINLOG_WRITE_BUFFER_DIRECT_ENABLE, "false");
        mockConfig(BINLOG_BACKUP_UPLOAD_PART_SIZE, "1024"); // 添加这行以修复RemoteBinlogProxy初始化问题

        // mock RemoteBinlogProxy
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMockedStatic = mockStatic(RemoteBinlogProxy.class)) {
            remoteBinlogProxyMockedStatic.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            Mockito.doReturn(false).when(remoteBinlogProxy).isBackupOn();

            // mock RuntimeLeaderElector
            try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
                RuntimeLeaderElector.class)) {
                runtimeLeaderElectorMockedStatic.when(
                    () -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString())).thenReturn(true);

                // 创建BinlogRecordManager实例
                binlogRecordManager = new BinlogRecordManager(version, group, stream, taskName, taskType, rootPath);
            }
        }
    }

    @Test
    public void testDoCompensationWithNoLocalFiles() {
        // mock BinlogFileUtil
        try (MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = mockStatic(BinlogFileUtil.class)) {
            binlogFileUtilMockedStatic.when(
                    () -> BinlogFileUtil.listLocalBinlogFiles(anyString(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());

            // 执行测试
            binlogRecordManager.doCompensation();

            // 验证结果
            // 不抛出异常即为成功
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testDoCompensationWithLocalFiles() {
        // 准备测试数据
        List<File> localFiles = new ArrayList<>();
        localFiles.add(new File("binlog.000001"));
        localFiles.add(new File("binlog.000002"));
        localFiles.add(new File("binlog.000003"));

        BinlogOssRecord record = new BinlogOssRecord();
        record.setLogSize(0L);

        // mock BinlogFileUtil
        try (MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = mockStatic(BinlogFileUtil.class)) {
            binlogFileUtilMockedStatic.when(
                    () -> BinlogFileUtil.listLocalBinlogFiles(anyString(), anyString(), anyString()))
                .thenReturn(localFiles);
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.compareBinlogFileName(anyString(), anyString()))
                .thenReturn(1);
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getFullPath(anyString(), anyString(), anyString()))
                .thenReturn("/test/path");
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getPrevBinlogFileName(anyString()))
                .thenReturn("binlog.000000");

            // mock recordService
            when(recordService.getRecordByName(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(record));
            when(recordService.getMaxPurgedRecord(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

            // 执行测试
            binlogRecordManager.doCompensation();

            // 验证结果
            // 不抛出异常即为成功
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testDoCompensationWithLocalFilesAndExistingRecord() throws Exception {
        // 准备测试数据
        List<File> localFiles = new ArrayList<>();
        File file1 = new File("binlog.000001");
        File file2 = new File("binlog.000002");
        localFiles.add(file1);
        localFiles.add(file2);

        BinlogOssRecord record = new BinlogOssRecord();
        record.setLogSize(0L);

        // mock BinlogFileUtil
        try (MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = mockStatic(BinlogFileUtil.class)) {
            binlogFileUtilMockedStatic.when(
                    () -> BinlogFileUtil.listLocalBinlogFiles(anyString(), anyString(), anyString()))
                .thenReturn(localFiles);
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.compareBinlogFileName(anyString(), anyString()))
                .thenReturn(1);
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getFullPath(anyString(), anyString(), anyString()))
                .thenReturn("/test/path");
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getPrevBinlogFileName(anyString()))
                .thenReturn("binlog.000000");

            // mock recordService
            when(recordService.getRecordByName(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(record));
            when(recordService.getMaxPurgedRecord(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

            // 执行测试
            binlogRecordManager.doCompensation();

            // 验证结果
            // 不抛出异常即为成功
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testDoCompensationWithMaxPurgedRecord() throws Exception {
        // 准备测试数据
        List<File> localFiles = new ArrayList<>();
        File file1 = new File("binlog.000001");
        File file2 = new File("binlog.000002");
        localFiles.add(file1);
        localFiles.add(file2);

        BinlogOssRecord maxPurgedRecord = new BinlogOssRecord();
        maxPurgedRecord.setBinlogFile("binlog.000000");

        // mock BinlogFileUtil
        try (MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = mockStatic(BinlogFileUtil.class)) {
            binlogFileUtilMockedStatic.when(
                    () -> BinlogFileUtil.listLocalBinlogFiles(anyString(), anyString(), anyString()))
                .thenReturn(localFiles);
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.compareBinlogFileName(anyString(), anyString()))
                .thenReturn(1);
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getFullPath(anyString(), anyString(), anyString()))
                .thenReturn("/test/path");

            // mock recordService
            when(recordService.getRecordByName(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
            when(recordService.getMaxPurgedRecord(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(maxPurgedRecord));

            // 执行测试
            binlogRecordManager.doCompensation();

            // 验证结果
            // 不抛出异常即为成功
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testOnCreateFile() {
        // 准备测试数据
        File file = new File("binlog.000001");

        // mock BinlogFileUtil
        try (MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = mockStatic(BinlogFileUtil.class)) {
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getPrevBinlogFileName(anyString()))
                .thenReturn("binlog.000000");
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getFullPath(anyString(), anyString(), anyString()))
                .thenReturn("/test/path");

            // mock recordService
            doReturn(Optional.empty()).when(recordService)
                .getRecordByName(anyString(), anyString(), anyString(), anyString());

            // mock RuntimeLeaderElector
            try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
                RuntimeLeaderElector.class)) {
                runtimeLeaderElectorMockedStatic.when(
                    () -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString())).thenReturn(true);

                // 执行测试
                binlogRecordManager.onCreateFile(file);

                // 验证结果
                verify(recordMapper, times(1)).insert(any(BinlogOssRecord.class));
            }
        }
    }

    @Test
    public void testOnCreateFileWithExistingLastFile() throws Exception {
        // 准备测试数据
        File file = new File("binlog.000001");

        // mock BinlogFileUtil
        try (MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = mockStatic(BinlogFileUtil.class)) {
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getPrevBinlogFileName(anyString()))
                .thenReturn("binlog.000000");
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getFullPath(anyString(), anyString(), anyString()))
                .thenReturn("/test/path");

            // mock recordService
            doReturn(Optional.empty()).when(recordService)
                .getRecordByName(anyString(), anyString(), anyString(), anyString());

            // mock File
            File lastFile = mock(File.class);
            doReturn(true).when(lastFile).exists();
            doReturn("binlog.000000").when(lastFile).getName();

            // mock RuntimeLeaderElector
            try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
                RuntimeLeaderElector.class)) {
                runtimeLeaderElectorMockedStatic.when(
                    () -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString())).thenReturn(true);

                // 执行测试
                binlogRecordManager.onCreateFile(file);

                // 验证结果
                verify(recordMapper, times(1)).insert(any(BinlogOssRecord.class));
            }
        }
    }

    @Test
    public void testOnCreateFileWithNonExistingLastFile() throws Exception {
        // 准备测试数据
        File file = new File("binlog.000001");

        // mock BinlogFileUtil
        try (MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = mockStatic(BinlogFileUtil.class)) {
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getPrevBinlogFileName(anyString()))
                .thenReturn("binlog.000000");
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.getFullPath(anyString(), anyString(), anyString()))
                .thenReturn("/test/path");

            // mock recordService
            doReturn(Optional.empty()).when(recordService)
                .getRecordByName(anyString(), anyString(), anyString(), anyString());

            // mock File
            File lastFile = mock(File.class);
            doReturn(false).when(lastFile).exists();

            // mock RuntimeLeaderElector
            try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
                RuntimeLeaderElector.class)) {
                runtimeLeaderElectorMockedStatic.when(
                    () -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString())).thenReturn(true);

                // 执行测试
                binlogRecordManager.onCreateFile(file);

                // 验证结果
                verify(recordMapper, times(1)).insert(any(BinlogOssRecord.class));
            }
        }
    }

    @Test
    public void testOnFinishFile() {
        // 准备测试数据
        File file = new File("binlog.000001");
        BinlogEndInfo binlogEndInfo = new BinlogEndInfo(1234567890L, "test_tso", 1L);

        BinlogOssRecord record = new BinlogOssRecord();
        record.setBinlogFile("binlog.000001");
        record.setUploadStatus(BinlogUploadStatus.CREATE.getValue());
        record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        record.setLogBegin(new Date());
        record.setLogSize(0L);
        record.setGroupId(group);
        record.setStreamId(stream);
        record.setClusterId("testCluster");

        // mock recordService
        doReturn(Optional.of(record)).when(recordService)
            .getRecordByName(anyString(), anyString(), anyString(), anyString());

        // mock File
        File mockFile = mock(File.class);
        doReturn(1024L).when(mockFile).length();
        doReturn("binlog.000001").when(mockFile).getName();

        // mock RuntimeLeaderElector
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
            RuntimeLeaderElector.class)) {
            runtimeLeaderElectorMockedStatic.when(
                () -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString())).thenReturn(true);

            // 执行测试
            binlogRecordManager.onFinishFile(mockFile, binlogEndInfo);

            // 验证结果
            verify(recordMapper, times(1)).updateByPrimaryKeySelective(any(BinlogOssRecord.class));
        }
    }

    @Test
    public void testOnFinishFileWithNullBinlogEndInfo() throws Exception {
        // 准备测试数据
        File file = new File("binlog.000001");
        BinlogEndInfo binlogEndInfo = null;

        // mock File
        File mockFile = mock(File.class);
        doReturn(1024L).when(mockFile).length();
        doReturn("binlog.000001").when(mockFile).getName();

        // mock RuntimeLeaderElector
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
            RuntimeLeaderElector.class)) {
            runtimeLeaderElectorMockedStatic.when(
                () -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString())).thenReturn(true);

            // 执行测试
            binlogRecordManager.onFinishFile(mockFile, binlogEndInfo);

            // 验证结果
            verify(recordMapper, times(0)).updateByPrimaryKeySelective(any(BinlogOssRecord.class));
        }
    }

    @Test
    public void testBinlogRecordCreateTaskExecWithExistingRecord() {
        // 准备测试数据
        File file = new File("binlog.000001");

        BinlogOssRecord record = new BinlogOssRecord();
        record.setBinlogFile("binlog.000001");
        record.setUploadStatus(BinlogUploadStatus.CREATE.getValue());
        record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        record.setLogBegin(new Date());
        record.setLogSize(0L);
        record.setGroupId(group);
        record.setStreamId(stream);
        record.setClusterId("testCluster");

        // mock recordService
        doReturn(Optional.of(record)).when(recordService)
            .getRecordByName(anyString(), anyString(), anyString(), anyString());

        // 创建BinlogRecordCreateTask实例
        BinlogRecordManager.BinlogRecordCreateTask createTask = binlogRecordManager.new BinlogRecordCreateTask(file);

        // 执行测试
        createTask.exec();

        // 验证结果
        verify(recordMapper, times(0)).insert(any(BinlogOssRecord.class));
    }

    @Test
    public void testBinlogRecordCreateTaskExecWithoutExistingRecord() {
        // 准备测试数据
        File file = new File("binlog.000001");

        // mock recordService
        when(recordService.getRecordByName(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        // 创建BinlogRecordCreateTask实例
        BinlogRecordManager.BinlogRecordCreateTask createTask = binlogRecordManager.new BinlogRecordCreateTask(file);

        // 执行测试
        createTask.exec();

        // 验证结果
        verify(recordMapper, times(1)).insert(any(BinlogOssRecord.class));
    }

    @Test
    public void testBinlogRecordCreateTaskExecWithNullRecord() throws Exception {
        // 准备测试数据
        File file = new File("binlog.000001");

        BinlogOssRecord record = new BinlogOssRecord();
        record.setBinlogFile("binlog.000001");
        record.setUploadStatus(BinlogUploadStatus.CREATE.getValue());
        record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        record.setLogBegin(new Date());
        record.setLogSize(0L);
        record.setGroupId(group);
        record.setStreamId(stream);
        record.setClusterId("testCluster");

        // mock recordService
        when(recordService.getRecordByName(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Optional.of(record));

        // 创建BinlogRecordCreateTask实例
        BinlogRecordManager.BinlogRecordCreateTask createTask = binlogRecordManager.new BinlogRecordCreateTask(file);

        // 执行测试
        createTask.exec();

        // 验证结果
        verify(recordMapper, times(0)).insert(any(BinlogOssRecord.class));
    }

    @Test
    public void testBinlogRecordFinishTaskExecWithExistingRecord() throws Exception {
        // 准备测试数据
        File file = new File("binlog.000001");
        File mockFile = mock(File.class);
        doReturn(1024L).when(mockFile).length();
        doReturn("binlog.000001").when(mockFile).getName();

        BinlogOssRecord record = new BinlogOssRecord();
        record.setBinlogFile("binlog.000001");
        record.setUploadStatus(BinlogUploadStatus.CREATE.getValue());
        record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        record.setLogBegin(new Date());
        record.setLogSize(0L);
        record.setLogEnd(null);
        record.setGroupId(group);
        record.setStreamId(stream);
        record.setClusterId("testCluster");

        BinlogEndInfo binlogEndInfo = new BinlogEndInfo(1234567890L, "test_tso", 1L);

        // mock recordService
        doReturn(Optional.of(record)).when(recordService)
            .getRecordByName(anyString(), anyString(), anyString(), anyString());

        AtomicReference<BinlogFile> binlogFileRef = new AtomicReference<>();
        // mock BinlogFile
        try (MockedConstruction<BinlogFile> binlogFileMockedStatic = mockConstruction(BinlogFile.class,
            (mock, context) -> {
                doReturn(System.currentTimeMillis()).when(mock).getLogBegin();
                doReturn(binlogEndInfo).when(mock).getLogEndInfo();
                doNothing().when(mock).close();
                binlogFileRef.set(mock);
            })) {

            // 创建BinlogRecordFinishTask实例
            BinlogRecordManager.BinlogRecordFinishTask finishTask =
                binlogRecordManager.new BinlogRecordFinishTask(mockFile);

            // 执行测试
            finishTask.exec();

            // 验证结果
            verify(recordMapper, times(1)).updateByPrimaryKeySelective(any(BinlogOssRecord.class));
        }
    }

    @Test
    public void testBinlogRecordFinishTaskExecWithoutExistingRecord() throws Exception {
        // 准备测试数据
        File file = new File("binlog.000001");
        File mockFile = mock(File.class);
        doReturn(1024L).when(mockFile).length();
        doReturn("binlog.000001").when(mockFile).getName();

        BinlogEndInfo binlogEndInfo = new BinlogEndInfo(1234567890L, "test_tso", 1L);

        // mock recordService
        doReturn(Optional.empty()).when(recordService)
            .getRecordByName(anyString(), anyString(), anyString(), anyString());

        AtomicReference<BinlogFile> binlogFileRef = new AtomicReference<>();
        AtomicBoolean labBackupDisabled = new AtomicBoolean();
        // mock BinlogFile
        try (MockedConstruction<BinlogFile> binlogFileMockedStatic = mockConstruction(BinlogFile.class,
            (mock, context) -> {
                labBackupDisabled.set(Boolean.FALSE.equals(context.arguments().get(6)));
                doReturn(System.currentTimeMillis()).when(mock).getLogBegin();
                doReturn(binlogEndInfo).when(mock).getLogEndInfo();
                doNothing().when(mock).close();
                binlogFileRef.set(mock);
            })) {

            // 创建BinlogRecordFinishTask实例
            BinlogRecordManager.BinlogRecordFinishTask finishTask =
                binlogRecordManager.new BinlogRecordFinishTask(mockFile);

            // 执行测试
            finishTask.exec();

            // 验证结果
            verify(recordMapper, times(1)).insert(any(BinlogOssRecord.class));
            Assert.assertTrue("record compensation must not open a lab backup copy", labBackupDisabled.get());
        }
    }

    @Test
    public void testOnRotateFile() {
        // 准备测试数据
        File currentFile = new File("binlog.000001");
        String nextFile = "binlog.000002";

        // 执行测试
        binlogRecordManager.onRotateFile(currentFile, nextFile);

        // 验证结果 - 不抛出异常即为成功
        Assert.assertTrue(true);
    }

    @Test
    public void testOnDeleteFile() {
        // 准备测试数据
        File file = new File("binlog.000001");

        // 执行测试
        binlogRecordManager.onDeleteFile(file);

        // 验证结果 - 不抛出异常即为成功
        Assert.assertTrue(true);
    }

    @Test
    public void testRunWithNonMaster() {
        // mock RuntimeLeaderElector
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
            RuntimeLeaderElector.class)) {
            runtimeLeaderElectorMockedStatic.when(
                () -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString())).thenReturn(false);

            // 执行测试
            binlogRecordManager.run();

            // 验证结果 - 不抛出异常即为成功
            Assert.assertTrue(true);
        }
    }

    /**
     * 当文件的 uploadStatus 已经是 SUCCESS 时，onFinishFile 不应更新 logSize，
     * 以保持与远端存储（OSS）上文件大小一致。
     */
    @Test
    public void testOnFinishFileSkipsLogSizeWhenUploadStatusSuccess() {
        // 准备测试数据：uploadStatus=SUCCESS，logSize=500（远端文件大小）
        long ossFileSize = 500L;
        long localFileSize = 502L; // 本地文件因 format_description_event 多 2 字节

        BinlogEndInfo binlogEndInfo = new BinlogEndInfo(1234567890L, "test_tso", 1L);

        BinlogOssRecord record = new BinlogOssRecord();
        record.setBinlogFile("binlog.000001");
        record.setUploadStatus(BinlogUploadStatus.SUCCESS.getValue());
        record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        record.setLogBegin(new Date());
        record.setLogSize(ossFileSize);
        record.setGroupId(group);
        record.setStreamId(stream);
        record.setClusterId("testCluster");

        // mock recordService
        doReturn(Optional.of(record)).when(recordService)
            .getRecordByName(anyString(), anyString(), anyString(), anyString());

        // mock File
        File mockFile = mock(File.class);
        doReturn(localFileSize).when(mockFile).length();
        doReturn("binlog.000001").when(mockFile).getName();

        // mock RuntimeLeaderElector
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
            RuntimeLeaderElector.class)) {
            runtimeLeaderElectorMockedStatic.when(
                () -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString())).thenReturn(true);

            // 执行测试
            binlogRecordManager.onFinishFile(mockFile, binlogEndInfo);

            // 验证 updateByPrimaryKeySelective 被调用
            ArgumentCaptor<BinlogOssRecord> captor = ArgumentCaptor.forClass(BinlogOssRecord.class);
            verify(recordMapper, times(1)).updateByPrimaryKeySelective(captor.capture());

            // 验证 logSize 未被更新为本地文件大小，仍保持与 OSS 一致
            BinlogOssRecord updatedRecord = captor.getValue();
            Assert.assertEquals("logSize should not be updated when uploadStatus is SUCCESS",
                ossFileSize, updatedRecord.getLogSize().longValue());
        }
    }

    /**
     * 当文件的 uploadStatus 已经是 SUCCESS 时，BinlogRecordFinishTask.exec() 不应更新 logSize。
     */
    @Test
    public void testBinlogRecordFinishTaskExecSkipsLogSizeWhenUploadStatusSuccess() throws Exception {
        long ossFileSize = 500L;
        long localFileSize = 502L;

        File mockFile = mock(File.class);
        doReturn(localFileSize).when(mockFile).length();
        doReturn("binlog.000001").when(mockFile).getName();

        BinlogOssRecord record = new BinlogOssRecord();
        record.setBinlogFile("binlog.000001");
        record.setUploadStatus(BinlogUploadStatus.SUCCESS.getValue());
        record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        record.setLogBegin(new Date());
        record.setLogSize(ossFileSize);
        record.setLogEnd(null);
        record.setGroupId(group);
        record.setStreamId(stream);
        record.setClusterId("testCluster");

        BinlogEndInfo binlogEndInfo = new BinlogEndInfo(1234567890L, "test_tso", 1L);

        // mock recordService
        doReturn(Optional.of(record)).when(recordService)
            .getRecordByName(anyString(), anyString(), anyString(), anyString());

        // mock BinlogFile
        try (MockedConstruction<BinlogFile> binlogFileMockedStatic = mockConstruction(BinlogFile.class,
            (mock, context) -> {
                doReturn(System.currentTimeMillis()).when(mock).getLogBegin();
                doReturn(binlogEndInfo).when(mock).getLogEndInfo();
                doNothing().when(mock).close();
            })) {

            // 创建 BinlogRecordFinishTask 实例
            BinlogRecordManager.BinlogRecordFinishTask finishTask =
                binlogRecordManager.new BinlogRecordFinishTask(mockFile);

            // 执行测试
            finishTask.exec();

            // 验证 logSize 未被更新
            ArgumentCaptor<BinlogOssRecord> captor = ArgumentCaptor.forClass(BinlogOssRecord.class);
            verify(recordMapper, times(1)).updateByPrimaryKeySelective(captor.capture());

            BinlogOssRecord updatedRecord = captor.getValue();
            Assert.assertEquals("logSize should not be updated when uploadStatus is SUCCESS",
                ossFileSize, updatedRecord.getLogSize().longValue());
        }
    }

    /**
     * 当文件的 uploadStatus 是 SUCCESS 但 logSize=0（非法值，拓扑切换竞态产生）时，
     * onFinishFile 应用本地文件大小回填 logSize，避免 log_size=0 永久固化。
     */
    @Test
    public void testOnFinishFileBackfillsLogSizeWhenUploadStatusSuccessButLogSizeZero() {
        // 准备测试数据：uploadStatus=SUCCESS，logSize=0（旧master的onFinishFile被拦截后的残留状态）
        long localFileSize = 185849L;

        BinlogEndInfo binlogEndInfo = new BinlogEndInfo(1234567890L, "test_tso", 1L);

        BinlogOssRecord record = new BinlogOssRecord();
        record.setBinlogFile("binlog.000001");
        record.setUploadStatus(BinlogUploadStatus.SUCCESS.getValue());
        record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        record.setLogBegin(new Date());
        record.setLogSize(0L);
        record.setGroupId(group);
        record.setStreamId(stream);
        record.setClusterId("testCluster");

        // mock recordService
        doReturn(Optional.of(record)).when(recordService)
            .getRecordByName(anyString(), anyString(), anyString(), anyString());

        // mock File
        File mockFile = mock(File.class);
        doReturn(localFileSize).when(mockFile).length();
        doReturn("binlog.000001").when(mockFile).getName();

        // mock RuntimeLeaderElector
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
            RuntimeLeaderElector.class)) {
            runtimeLeaderElectorMockedStatic.when(
                () -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString())).thenReturn(true);

            // 执行测试
            binlogRecordManager.onFinishFile(mockFile, binlogEndInfo);

            // 验证 logSize 被回填为本地文件大小
            ArgumentCaptor<BinlogOssRecord> captor = ArgumentCaptor.forClass(BinlogOssRecord.class);
            verify(recordMapper, times(1)).updateByPrimaryKeySelective(captor.capture());

            BinlogOssRecord updatedRecord = captor.getValue();
            Assert.assertEquals("logSize should be backfilled when uploadStatus is SUCCESS but logSize is 0",
                localFileSize, updatedRecord.getLogSize().longValue());
        }
    }

    /**
     * 当文件的 uploadStatus 是 SUCCESS 但 logSize 为 null 时，
     * BinlogRecordFinishTask.exec() 应用本地文件大小回填 logSize。
     */
    @Test
    public void testBinlogRecordFinishTaskExecBackfillsLogSizeWhenUploadStatusSuccessButLogSizeNull()
        throws Exception {
        long localFileSize = 185849L;

        File mockFile = mock(File.class);
        doReturn(localFileSize).when(mockFile).length();
        doReturn("binlog.000001").when(mockFile).getName();

        BinlogOssRecord record = new BinlogOssRecord();
        record.setBinlogFile("binlog.000001");
        record.setUploadStatus(BinlogUploadStatus.SUCCESS.getValue());
        record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        record.setLogBegin(new Date());
        record.setLogSize(null);
        record.setLogEnd(null);
        record.setGroupId(group);
        record.setStreamId(stream);
        record.setClusterId("testCluster");

        BinlogEndInfo binlogEndInfo = new BinlogEndInfo(1234567890L, "test_tso", 1L);

        // mock recordService
        doReturn(Optional.of(record)).when(recordService)
            .getRecordByName(anyString(), anyString(), anyString(), anyString());

        // mock BinlogFile
        try (MockedConstruction<BinlogFile> binlogFileMockedStatic = mockConstruction(BinlogFile.class,
            (mock, context) -> {
                doReturn(System.currentTimeMillis()).when(mock).getLogBegin();
                doReturn(binlogEndInfo).when(mock).getLogEndInfo();
                doNothing().when(mock).close();
            })) {

            // 创建 BinlogRecordFinishTask 实例
            BinlogRecordManager.BinlogRecordFinishTask finishTask =
                binlogRecordManager.new BinlogRecordFinishTask(mockFile);

            // 执行测试
            finishTask.exec();

            // 验证 logSize 被回填为本地文件大小
            ArgumentCaptor<BinlogOssRecord> captor = ArgumentCaptor.forClass(BinlogOssRecord.class);
            verify(recordMapper, times(1)).updateByPrimaryKeySelective(captor.capture());

            BinlogOssRecord updatedRecord = captor.getValue();
            Assert.assertEquals("logSize should be backfilled when uploadStatus is SUCCESS but logSize is null",
                localFileSize, updatedRecord.getLogSize().longValue());
        }
    }
}
