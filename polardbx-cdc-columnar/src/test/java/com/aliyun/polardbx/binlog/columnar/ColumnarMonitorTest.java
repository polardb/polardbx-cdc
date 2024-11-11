/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.columnar;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.FullMasterStatus;

import io.grpc.ManagedChannel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

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
            doCallRealMethod().when(columnarMonitor).checkFilePos(anyBoolean(), anyString(), anyLong(), anyBoolean());

            when(columnarMonitor.getLastUpdateTime()).thenReturn(new AtomicLong(System.currentTimeMillis()));
            columnarMonitor.checkFilePos(true, "binlog.001", 123L, false);

            when(columnarMonitor.getLastUpdateTime()).thenReturn(new AtomicLong(System.currentTimeMillis() - 70000));
            columnarMonitor.checkFilePos(true, "binlog.001", 123L, false);

            columnarMonitor.checkFilePos(true, "binlog.001", 123L, true);

            columnarMonitor.checkFilePos(false, "binlog.001", 123L, true);
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
}


