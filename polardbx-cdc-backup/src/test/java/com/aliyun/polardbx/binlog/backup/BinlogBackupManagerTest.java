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
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import lombok.SneakyThrows;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author yudong
 * @since 2024/7/26 16:44
 **/
@RunWith(MockitoJUnitRunner.class)
public class BinlogBackupManagerTest {

    @Mock
    private StreamContext streamContext;
    @InjectMocks
    private BinlogBackupManager manager;

    @Test
    @SneakyThrows
    public void testNeedStart_backup_off() {
        try (final MockedStatic<DynamicApplicationConfig> dynamicApplicationConfig =
            mockStatic(DynamicApplicationConfig.class)) {
            dynamicApplicationConfig.when(() ->
                DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_UPLOAD_PART_SIZE)).thenReturn(1024);
            final RemoteBinlogProxy proxy = spy(RemoteBinlogProxy.class);
            try (final MockedStatic<RemoteBinlogProxy> remoteBinlogProxy = mockStatic(RemoteBinlogProxy.class)) {
                remoteBinlogProxy.when(RemoteBinlogProxy::getInstance).thenReturn(proxy);
                when(proxy.isBackupOn()).thenReturn(false);
                assertFalse(manager.needStart());
            }
        }
    }

    @Test
    @SneakyThrows
    public void testNeedStart_is_dumper_slave() {
        try (final MockedStatic<DynamicApplicationConfig> dynamicApplicationConfig =
            mockStatic(DynamicApplicationConfig.class);
            final MockedStatic<RuntimeLeaderElector> runtimeLeaderElector = mockStatic(RuntimeLeaderElector.class)) {
            dynamicApplicationConfig.when(() ->
                DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_UPLOAD_PART_SIZE)).thenReturn(1024);
            final RemoteBinlogProxy proxy = spy(RemoteBinlogProxy.class);
            try (final MockedStatic<RemoteBinlogProxy> remoteBinlogProxy = mockStatic(RemoteBinlogProxy.class)) {
                remoteBinlogProxy.when(RemoteBinlogProxy::getInstance).thenReturn(proxy);
                when(proxy.isBackupOn()).thenReturn(true);
                runtimeLeaderElector.when(() -> RuntimeLeaderElector.isDumperMasterOrX(streamContext.getVersion(),
                    streamContext.getTaskType(), streamContext.getTaskName())).thenReturn(false);
                assertFalse(manager.needStart());
            }
        }
    }

    @Test
    @SneakyThrows
    public void testNeedStart_is_dumper_master() {
        try (final MockedStatic<DynamicApplicationConfig> dynamicApplicationConfig =
            mockStatic(DynamicApplicationConfig.class);
            final MockedStatic<RuntimeLeaderElector> runtimeLeaderElector = mockStatic(RuntimeLeaderElector.class)) {
            dynamicApplicationConfig.when(() ->
                DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_UPLOAD_PART_SIZE)).thenReturn(1024);
            final RemoteBinlogProxy proxy = spy(RemoteBinlogProxy.class);
            try (final MockedStatic<RemoteBinlogProxy> remoteBinlogProxy = mockStatic(RemoteBinlogProxy.class)) {
                remoteBinlogProxy.when(RemoteBinlogProxy::getInstance).thenReturn(proxy);
                when(proxy.isBackupOn()).thenReturn(true);
                runtimeLeaderElector.when(() -> RuntimeLeaderElector.isDumperMasterOrX(streamContext.getVersion(),
                    streamContext.getTaskType(), streamContext.getTaskName())).thenReturn(true);
                assertTrue(manager.needStart());
            }
        }
    }
}
