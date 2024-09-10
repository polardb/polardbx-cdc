/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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


