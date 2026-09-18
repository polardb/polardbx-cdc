/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.filesys.LocalFileSystem;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author zimian
 * @since 2025/8/22 18:00
 **/
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class BinlogUploadManagerRecoverTest extends BaseTest {
    private final List<CdcFile> localCdcFiles = new ArrayList<>();
    private final BinlogOssRecord remoteRecord = new BinlogOssRecord();
    private final List<BinlogOssRecord> binlogOssRecords = new ArrayList<>();
    private final Map<String, LocalFileSystem> fileSystemMap = new HashMap<>();
    private final Set<String> streamList = new TreeSet<>();
    private final String stream = "stream_global";
    private final String group = "group_global";
    private final String clusterId = "cluster_global";
    BinlogUploadManager manager = Mockito.mock(BinlogUploadManager.class, CALLS_REAL_METHODS);
    LocalFileSystem localFileSystem = Mockito.mock(LocalFileSystem.class);
    BinlogOssRecordService binlogOssRecordService = Mockito.mock(BinlogOssRecordService.class);
    RemoteBinlogProxy remoteBinlogProxy = Mockito.mock(RemoteBinlogProxy.class);

    @Test
    public void testGetFilesToUpload() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMockedStatic = mockStatic(RemoteBinlogProxy.class)) {
            try (
                MockedStatic<SpringContextHolder> springContextHolderMockedStatic = mockStatic(
                    SpringContextHolder.class)) {
                remoteBinlogProxyMockedStatic.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
                // when(remoteBinlogProxy.isObjectsExistForPrefix(anyString())).thenReturn(true);
                // when(remoteBinlogProxy.getSize(anyString())).thenReturn(100L);
                springContextHolderMockedStatic.when(() -> SpringContextHolder.getObject(BinlogOssRecordService.class))
                    .thenReturn(binlogOssRecordService);
                // when(localFileSystem.size(anyString())).thenReturn(100L);
                localCdcFiles.add(new CdcFile("binlog.000002", null));
                remoteRecord.setBinlogFile("binlog.000002");
                remoteRecord.setGroupId(group);
                remoteRecord.setStreamId(stream);
                binlogOssRecords.add(remoteRecord);
                when(binlogOssRecordService.getRecordsForUpload(group, stream, clusterId)).thenReturn(binlogOssRecords);
                when(localFileSystem.listFiles()).thenReturn(localCdcFiles);
                fileSystemMap.put("stream_global", localFileSystem);
                streamList.add("stream_global");
                manager.setStreamSet(streamList);
                manager.setFileSystemMap(fileSystemMap);
                manager.setGroup(group);
                manager.setClusterId(clusterId);
                List<BinlogOssRecord> recordsToUpload = manager.getFilesToUpload();
                assertEquals("binlog.000002", recordsToUpload.get(0).getBinlogFile());
                // when(localFileSystem.size(anyString())).thenReturn(90L);
                assertFalse(manager.getFilesToUpload().isEmpty());
            }
        }
    }
}