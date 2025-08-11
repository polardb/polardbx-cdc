/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.columnar;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.dao.ColumnarCheckpointsMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.ColumnarCheckpoints;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.FullMasterStatus;

import io.grpc.ManagedChannel;

import io.grpc.ManagedChannelBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ColumnarMonitorTest {

    @Test
    public void testCheckFilePos() {
        ColumnarMonitor columnarMonitor = mock(ColumnarMonitor.class);
        when(columnarMonitor.getLastFile()).thenReturn("binlog.001");
        when(columnarMonitor.getLastPos()).thenReturn(123L);

        try (MockedStatic<DynamicApplicationConfig> dynamicApplicationConfig = mockStatic(
            DynamicApplicationConfig.class)) {
            dynamicApplicationConfig.when(() -> DynamicApplicationConfig.getInt(anyString())).thenReturn(60000);
            doCallRealMethod().when(columnarMonitor).checkFilePos(anyBoolean(), anyString(), anyLong(), anyBoolean(), anyLong());

            when(columnarMonitor.getLastUpdateTime()).thenReturn(new AtomicLong(System.currentTimeMillis()));
            columnarMonitor.checkFilePos(true, "binlog.001", 123L, false, 70000);

            when(columnarMonitor.getLastUpdateTime()).thenReturn(new AtomicLong(System.currentTimeMillis() - 70000));
            columnarMonitor.checkFilePos(true, "binlog.001", 123L, false, 70000);

            columnarMonitor.checkFilePos(true, "binlog.001", 123L, true, 70000);

            columnarMonitor.checkFilePos(false, "binlog.001", 123L, true, 70000);
        }
    }

    @Test
    public void testGetCdcTso() {
        ColumnarMonitor columnarMonitor = mock(ColumnarMonitor.class);
        ManagedChannel channel = mock(ManagedChannel.class);
        CdcServiceGrpc.CdcServiceBlockingStub cdcServiceStub = mock(CdcServiceGrpc.CdcServiceBlockingStub.class);
        FullMasterStatus fullMasterStatus = mock(FullMasterStatus.class);

        try (MockedStatic<CdcServiceGrpc> cdcServiceGrpc = mockStatic(CdcServiceGrpc.class)) {
            cdcServiceGrpc.when(() -> CdcServiceGrpc.newBlockingStub(any())).thenReturn(cdcServiceStub);
            when(cdcServiceStub.showFullMasterStatus(any())).thenReturn(fullMasterStatus);
            when(fullMasterStatus.getLastTso()).thenReturn("7219284828502360128");
            doCallRealMethod().when(columnarMonitor).getCdcTso(any());
            long tso = columnarMonitor.getCdcTso(channel);
            assertEquals(tso, 7219284828502360128L);
        }
    }

    @Test
    public void testMonitorColumnarOffset() throws Throwable {
        ColumnarMonitor columnarMonitor = mock(ColumnarMonitor.class);
        CdcServiceGrpc.CdcServiceBlockingStub cdcServiceStub = mock(CdcServiceGrpc.CdcServiceBlockingStub.class);
        FullMasterStatus fullMasterStatus = mock(FullMasterStatus.class);

        try (MockedStatic<SpringContextHolder> springContextHolder = mockStatic(SpringContextHolder.class)) {
            DumperInfoMapper mapper = Mockito.mock(DumperInfoMapper.class);
            springContextHolder.when(() -> SpringContextHolder.getObject(DumperInfoMapper.class))
                .thenReturn(mapper);

            try (MockedStatic<RetryTemplate> retryTemplate = mockStatic(RetryTemplate.class)) {
                RetryTemplateBuilder templateBuilder = mock(RetryTemplateBuilder.class);
                retryTemplate.when(RetryTemplate::builder).thenReturn(templateBuilder);
                when(templateBuilder.maxAttempts(anyInt())).thenReturn(templateBuilder);
                when(templateBuilder.fixedBackoff(anyLong())).thenReturn(templateBuilder);
                when(templateBuilder.retryOn(any())).thenReturn(templateBuilder);
                RetryTemplate template = mock(RetryTemplate.class);
                when(templateBuilder.build()).thenReturn(template);
                DumperInfo dumperInfo = mock(DumperInfo.class);
                when(dumperInfo.getIp()).thenReturn("127.0.0.1");
                when(dumperInfo.getPort()).thenReturn(8080);
                when(template.execute(any(RetryCallback.class), any(RecoveryCallback.class))).thenReturn(dumperInfo);

                try (MockedStatic<ManagedChannelBuilder> managedChannelBuilderMockedStatic = mockStatic(
                    ManagedChannelBuilder.class)) {
                    ManagedChannel mockChannel = mock(ManagedChannel.class);
                    ManagedChannelBuilder mockBuilder = mock(ManagedChannelBuilder.class);
                    managedChannelBuilderMockedStatic
                        .when(() -> ManagedChannelBuilder.forAddress(anyString(), anyInt()))
                        .thenReturn(mockBuilder);
                    when(mockBuilder.usePlaintext()).thenReturn(mockBuilder);
                    when(mockBuilder.maxInboundMessageSize(anyInt())).thenReturn(mockBuilder);
                    when(mockBuilder.build()).thenReturn(mockChannel);

                    doCallRealMethod().when(columnarMonitor).getCdcOffset();
                    columnarMonitor.getCdcOffset();

                    // 验证 shutdown 方法是否被调用
                    verify(mockChannel).shutdown();

                    // 可以进一步验证 awaitTermination 是否被调用
                    verify(mockChannel).awaitTermination(5, TimeUnit.SECONDS);
                }
            }
        }
    }
}


