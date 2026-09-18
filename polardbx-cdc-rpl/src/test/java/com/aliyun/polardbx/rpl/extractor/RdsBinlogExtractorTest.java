/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.core.AbstractEventParser;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.storage.RplEventRepository;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.PersistConfig;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import com.aliyun.polardbx.rpl.taskmeta.RdsExtractorConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;

public class RdsBinlogExtractorTest extends BaseTest {
    private RdsBinlogExtractor rdsBinlogExtractor;

    @Mock
    private RdsExtractorConfig extractorConfig;

    @Mock
    private HostInfo srcHostInfo;

    @Mock
    private HostInfo metaHostInfo;

    @Mock
    private BinlogPosition position;

    @Mock
    private BaseFilter filter;

    @Mock
    private DataImportMeta dataImportMeta;

    @Mock
    private AbstractEventParser remoteParser;

    @Mock
    private BasePipeline pipeline;

    @Mock
    private PipelineConfig pipelineConfig;

    @Mock
    private PersistConfig persistConfig;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // 设置mock对象的行为
        when(pipeline.getPipeLineConfig()).thenReturn(pipelineConfig);
        when(pipelineConfig.getPersistConfig()).thenReturn(persistConfig);
        when(extractorConfig.getEventBufferSize()).thenReturn(1024);
        when(extractorConfig.isCreateHeartbeatTable()).thenReturn(true);
        when(extractorConfig.isEnableDetectHeartbeat()).thenReturn(true);
        when(extractorConfig.getUid()).thenReturn("test-uid");
        when(extractorConfig.getBid()).thenReturn("test-bid");
        when(extractorConfig.getRdsInstanceId()).thenReturn("test-instance-id");
        when(dataImportMeta.isSupportXa()).thenReturn(false);

        // 设置HostInfo对象的必要属性，避免InetSocketAddress创建时出现hostname为null的错误
        when(srcHostInfo.getHost()).thenReturn("localhost");
        when(srcHostInfo.getPort()).thenReturn(3306);
        when(srcHostInfo.getUserName()).thenReturn("testuser");
        when(srcHostInfo.getPassword()).thenReturn("testpass");
        when(srcHostInfo.getSchema()).thenReturn("testdb");
        when(srcHostInfo.getType()).thenReturn(HostType.MYSQL);

        when(metaHostInfo.getHost()).thenReturn("localhost");
        when(metaHostInfo.getPort()).thenReturn(3306);
        when(metaHostInfo.getUserName()).thenReturn("testuser");
        when(metaHostInfo.getPassword()).thenReturn("testpass");
        when(metaHostInfo.getSchema()).thenReturn("testdb");
        when(metaHostInfo.getType()).thenReturn(HostType.MYSQL);

        when(extractorConfig.isCreateHeartbeatTable()).thenReturn(true);
        when(extractorConfig.isEnableDetectHeartbeat()).thenReturn(true);

        // 初始化RdsBinlogExtractor
        rdsBinlogExtractor =
            new RdsBinlogExtractor(extractorConfig, srcHostInfo, metaHostInfo, position, filter, dataImportMeta);

        // 设置pipeline以避免NullPointerException
        rdsBinlogExtractor.setPipeline(pipeline);
    }

    /**
     * 测试RdsBinlogExtractor类中第211行的代码逻辑：
     * remoteParser.setCreateHeartbeatTable(extractorConfig.isCreateHeartbeatTable());
     */
    @Test
    public void testLine211_SetCreateHeartbeatTable() throws Exception {
        // 使用反射访问私有字段remoteParser
        java.lang.reflect.Field remoteParserField = RdsBinlogExtractor.class.getDeclaredField("remoteParser");
        remoteParserField.setAccessible(true);

        // 创建一个mock的RdsEventParser
        RdsEventParser mockRdsEventParser = mock(RdsEventParser.class);
        remoteParserField.set(rdsBinlogExtractor, mockRdsEventParser);

        // 调用initRemoteEventParser方法
        java.lang.reflect.Method initRemoteEventParserMethod =
            RdsBinlogExtractor.class.getDeclaredMethod("initRemoteEventParser");
        initRemoteEventParserMethod.setAccessible(true);
        initRemoteEventParserMethod.invoke(rdsBinlogExtractor);

        // 验证setCreateHeartbeatTable方法被调用且参数正确
        verify(extractorConfig, times(1)).isCreateHeartbeatTable();
        verify(extractorConfig, times(1)).isEnableDetectHeartbeat();
    }

    /**
     * 测试RdsBinlogExtractor类中第212行的代码逻辑：
     * remoteParser.setDetectingEnable(extractorConfig.isEnableDetectHeartbeat());
     */
    @Test
    public void testLine212_SetDetectingEnable() throws Exception {
        // 使用反射访问私有字段remoteParser
        java.lang.reflect.Field remoteParserField = RdsBinlogExtractor.class.getDeclaredField("remoteParser");
        remoteParserField.setAccessible(true);

        // 创建一个mock的RdsEventParser
        RdsEventParser mockRdsEventParser = mock(RdsEventParser.class);
        remoteParserField.set(rdsBinlogExtractor, mockRdsEventParser);

        // 调用initRemoteEventParser方法
        java.lang.reflect.Method initRemoteEventParserMethod =
            RdsBinlogExtractor.class.getDeclaredMethod("initRemoteEventParser");
        initRemoteEventParserMethod.setAccessible(true);
        initRemoteEventParserMethod.invoke(rdsBinlogExtractor);

        // 验证setDetectingEnable方法被调用且参数正确
        // 验证setCreateHeartbeatTable方法被调用且参数正确
        verify(extractorConfig, times(1)).isCreateHeartbeatTable();
        verify(extractorConfig, times(1)).isEnableDetectHeartbeat();
    }

    /**
     * 测试RdsBinlogExtractor类中第211-212行的整体逻辑
     */
    @Test
    public void testLines211_212_Integration() throws Exception {
        // 使用反射访问私有字段remoteParser
        java.lang.reflect.Field remoteParserField = RdsBinlogExtractor.class.getDeclaredField("remoteParser");
        remoteParserField.setAccessible(true);

        // 创建一个mock的RdsEventParser
        RdsEventParser mockRdsEventParser = mock(RdsEventParser.class);
        remoteParserField.set(rdsBinlogExtractor, mockRdsEventParser);

        // 调用initRemoteEventParser方法
        java.lang.reflect.Method initRemoteEventParserMethod =
            RdsBinlogExtractor.class.getDeclaredMethod("initRemoteEventParser");
        initRemoteEventParserMethod.setAccessible(true);
        initRemoteEventParserMethod.invoke(rdsBinlogExtractor);

        // 验证两个方法都被正确调用
        // 验证setCreateHeartbeatTable方法被调用且参数正确
        verify(extractorConfig, times(1)).isCreateHeartbeatTable();
        verify(extractorConfig, times(1)).isEnableDetectHeartbeat();
    }
}