/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.BinlogFileSeekHandlerV2;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.LabEventType;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

public class SeekTsoTest extends BaseTest {
    @Test
    public void testCheckDeadLoop() {
        mockConfig(ConfigKeys.IS_LAB_ENV, "true");
        BinlogFileSeekHandlerV2 binlogFileSeekHandlerV2 = new BinlogFileSeekHandlerV2();

        // 使用Mockito模拟LabEventManager的静态方法
        try (MockedStatic<LabEventManager> mockedLabEventManager = mockStatic(LabEventManager.class)) {
            for (int i = 0; i < 21; i++) {
                binlogFileSeekHandlerV2.checkDeadLoop(1000L, "binlog.000001");
            }
            // 使用Mockito.verify验证LabEventManager.logEvent方法被调用
            mockedLabEventManager.verify(() ->
                    LabEventManager.logEvent(LabEventType.SEEK_LAST_TSO_CHECK, "binlog.000001:1000:1000"),
                times(1));
        }
    }

    @Test
    public void testCheckForceDownload() {
        mockConfig(ConfigKeys.IS_LAB_ENV, "true");
        mockConfig(ConfigKeys.CLUSTER_ID, "zimian");
        BinlogOssRecordService binlogOssRecordService = Mockito.mock(BinlogOssRecordService.class);
        RemoteBinlogProxy remoteBinlogProxy = Mockito.mock(RemoteBinlogProxy.class);
        BinlogOssRecord binlogOssRecord = new BinlogOssRecord();
        binlogOssRecord.setBinlogFile("binlog.000001");
        binlogOssRecord.setGroupId("zimian_group");
        binlogOssRecord.setStreamId("zimian_stream");
        List<BinlogOssRecord> list = new ArrayList<>();
        list.add(binlogOssRecord);
        LogFileManager logFileManager = Mockito.mock(LogFileManager.class);
        when(logFileManager.isForceDownload()).thenReturn(true);
        ExecutionConfig executionConfig = new ExecutionConfig();
        executionConfig.setSources(new ArrayList<>());
        LogFileGenerator logFileGenerator =
            new LogFileGenerator(logFileManager, 1000, false, null, 0, 0, null, null, "zimian_group", "zimian_stream",
                executionConfig);
        try (MockedStatic<LabEventManager> mockedLabEventManager = mockStatic(LabEventManager.class);
            MockedStatic<SpringContextHolder> mockedSpringContextHolder = mockStatic(SpringContextHolder.class);
            MockedStatic<RemoteBinlogProxy> mockedRemoteBinlogProxy = mockStatic(RemoteBinlogProxy.class)) {
            mockedSpringContextHolder.when(() -> SpringContextHolder.getObject(BinlogOssRecordService.class))
                .thenReturn(binlogOssRecordService);
            mockedRemoteBinlogProxy.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isObjectsExistForPrefix(anyString())).thenReturn(true);
            when(binlogOssRecordService.getRecords("zimian_group", "zimian_stream", "zimian")).thenReturn(list);
            logFileGenerator.checkForceDownload();
            mockedLabEventManager.verify(
                () -> LabEventManager.logEvent(LabEventType.FORCE_DOWNLOAD_BINLOG_CHECK, "binlog.000001"), times(1));
        }
    }
}