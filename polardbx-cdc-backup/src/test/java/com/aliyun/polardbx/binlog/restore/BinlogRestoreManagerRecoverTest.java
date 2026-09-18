/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.restore;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author zimian
 * @since 2025/8/22 18:00
 **/
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class BinlogRestoreManagerRecoverTest {

    @Mock
    private BinlogOssRecordService recordService;
    @Mock
    private BinlogOssRecordMapperExtend mapperExtend;
    @Mock
    private BinlogOssRecord record1;
    @Mock
    private BinlogOssRecord record2;

    private MockedStatic<SpringContextHolder> springContextHolder;
    private MockedStatic<DynamicApplicationConfig> dynamicApplicationConfig;

    private static final String groupName = "group1";
    private static final String streamName = "stream1";
    private static final String clusterId = "cluster_1";
    private static final String binlogFileName1 = "binlog.000001";
    private static final String binlogFileName2 = "binlog.000002";

    @Before
    @SneakyThrows
    public void before() {
        springContextHolder = mockStatic(SpringContextHolder.class);
        dynamicApplicationConfig = mockStatic(DynamicApplicationConfig.class);

        springContextHolder.when(() -> SpringContextHolder.getObject(BinlogOssRecordService.class))
            .thenReturn(recordService);
        springContextHolder.when(() -> SpringContextHolder.getObject(BinlogOssRecordMapperExtend.class))
            .thenReturn(mapperExtend);

        dynamicApplicationConfig.when(() -> DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID))
            .thenReturn(clusterId);

        when(record1.getBinlogFile()).thenReturn(binlogFileName1);
        when(record2.getBinlogFile()).thenReturn(binlogFileName2);
    }

    @After
    public void after() {
        springContextHolder.close();
        dynamicApplicationConfig.close();
    }

    @Test
    public void testGetDownloadFiles_WithFirstUploadingFile() {
        // 测试存在第一个上传中文件的情况
        List<BinlogOssRecord> recordsBefore = new ArrayList<>();
        recordsBefore.add(record2);

        when(recordService.getFirstUploadingRecord(groupName, streamName, clusterId))
            .thenReturn(Optional.of(record1));
        when(mapperExtend.getRecordsBefore(groupName, streamName, clusterId, 1, 2))
            .thenReturn(recordsBefore);

        BinlogRestoreManager manager = new BinlogRestoreManager(groupName, streamName, "/tmp");
        List<String> result = manager.getDownloadFiles(2);

        assertEquals(2, result.size());
        assertTrue(result.contains(binlogFileName1));
        assertTrue(result.contains(binlogFileName2));
    }
}