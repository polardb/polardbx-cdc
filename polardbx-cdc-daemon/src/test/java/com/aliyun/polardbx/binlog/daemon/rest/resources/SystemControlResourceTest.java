/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.service.StorageInfoService;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SystemControlResourceTest extends BaseTest {

    @Test
    public void testGetVersion() {
        mockConfig(ConfigKeys.RELEASE_NOTE_PATH, this.getClass().getClassLoader().getResource("releaseNote").getFile());

        SystemControlResource resource = mock(SystemControlResource.class);
        when(resource.getVersion()).thenCallRealMethod();
        String result = resource.getVersion();
        Assert.assertNotNull(result);
        // 返回 x.y.z-date-buildNum
        Assert.assertTrue(result.matches("\\d+\\.\\d+\\.\\d+\\-\\d{8}_\\d{8}"));
    }

    @Test
    public void testClean() throws NoSuchFieldException, IllegalAccessException {
        JdbcTemplate polarxJdbcTemplate = mock(JdbcTemplate.class);
        TransactionTemplate polarxTransactionTemplate = mock(TransactionTemplate.class);
        Mockito.doNothing().when(polarxJdbcTemplate).execute(anyString());
        when(polarxTransactionTemplate.execute(t -> null)).thenReturn(null);

        registerSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
        registerSpringObject("polarxTransactionTemplate", polarxTransactionTemplate);
        mockConfig(ConfigKeys.DAEMON_CLEAN_INTERFACE_FORCE_CHECK_CLUSTER_ENABLED, "true");
        SystemControlResource resource = mock(SystemControlResource.class);
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElector = mockStatic(
            RuntimeLeaderElector.class)) {
            runtimeLeaderElector.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);
            Assert.assertTrue(RuntimeLeaderElector.isDaemonLeader());
            String clusterId = null;
            when(resource.clean(clusterId)).thenCallRealMethod();
            Throwable t = null;
            try {
                resource.clean(clusterId);
            } catch (PolardbxException e) {
                t = e;
            }
            Assert.assertNotNull(t);
            assertEquals("clean operation check cluster id failed!", t.getMessage());
            resource.clean(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));
            mockConfig(ConfigKeys.DAEMON_CLEAN_INTERFACE_FORCE_CHECK_CLUSTER_ENABLED, "false");
            resource.clean(clusterId);
        }
    }

    @Test
    public void testGetDNMaster_WithVipAddress_ShouldReturnMaster() {
        StorageInfo storageInfo = new StorageInfo();
        storageInfo.setStorageInstId("storageInstId1");
        storageInfo.setInstKind(0);
        storageInfo.setIsVip(1);
        storageInfo.setStatus(1);

        StorageInfo expectedMaster = new StorageInfo();
        expectedMaster.setIp("127.0.0.1");
        expectedMaster.setPort(3306);
        expectedMaster.setStorageInstId("storageInstId1");
        expectedMaster.setInstKind(0);
        expectedMaster.setIsVip(1);
        expectedMaster.setStatus(1);

        StorageInfoMapper storageInfoMapper = mock(StorageInfoMapper.class);
        SystemControlResource systemControlResource = new SystemControlResource();
        systemControlResource.setStorageInfoMapper(storageInfoMapper);
        when(storageInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.of(expectedMaster));

        StorageInfo master = systemControlResource.getDNMaster(storageInfo);

        assertEquals(expectedMaster.getIp(), master.getIp());
        assertEquals(expectedMaster.getPort(), master.getPort());
    }

    @Test
    public void testGetDNMaster_WithoutVipAddress_ShouldReturnNormalStorageInfo() {
        StorageInfo storageInfo = new StorageInfo();
        storageInfo.setStorageInstId("storageInstId1");
        storageInfo.setInstKind(1);
        storageInfo.setIsVip(0);

        StorageInfo expectedNormal = new StorageInfo();
        expectedNormal.setIp("127.0.0.2");
        expectedNormal.setPort(3307);
        expectedNormal.setStorageInstId("storageInstId1");

        StorageInfoMapper storageInfoMapper = mock(StorageInfoMapper.class);
        SystemControlResource systemControlResource = new SystemControlResource();
        systemControlResource.setStorageInfoMapper(storageInfoMapper);
        when(storageInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());

        StorageInfoService storageInfoService = mock(StorageInfoService.class);
        systemControlResource.setStorageInfoService(storageInfoService);
        when(storageInfoService.getNormalStorageInfo("storageInstId1")).thenReturn(expectedNormal);

        StorageInfo master = systemControlResource.getDNMaster(storageInfo);

        assertEquals(expectedNormal.getIp(), master.getIp());
        assertEquals(expectedNormal.getPort(), master.getPort());
    }

    @Test
    public void testGetDNMaster_NoMasterFound_ShouldThrowException() {
        StorageInfo storageInfo = new StorageInfo();
        storageInfo.setStorageInstId("storageInstId1");
        storageInfo.setInstKind(1);
        storageInfo.setIsVip(0);

        StorageInfoMapper storageInfoMapper = mock(StorageInfoMapper.class);
        SystemControlResource systemControlResource = new SystemControlResource();
        systemControlResource.setStorageInfoMapper(storageInfoMapper);
        when(storageInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());

        StorageInfoService storageInfoService = mock(StorageInfoService.class);
        systemControlResource.setStorageInfoService(storageInfoService);
        when(storageInfoService.getNormalStorageInfo("storageInstId1")).thenReturn(null);

        PolardbxException exception = new PolardbxException();

        try {
            systemControlResource.getDNMaster(storageInfo);
        } catch (PolardbxException e) {
            exception = e;
        }

        assertEquals("cannot find master storage info for dn storageInstId1", exception.getMessage());
    }

    @Test
    public void testCleanRemoteFiles_BinlogX_WithBackupOn() {
        try (MockedStatic<RemoteBinlogProxy> mockedRemoteBinlogProxy = mockStatic(RemoteBinlogProxy.class)) {

            // 准备mock对象
            RemoteBinlogProxy mockProxy = mock(RemoteBinlogProxy.class);

            // 设置期望行为
            mockedRemoteBinlogProxy.when(RemoteBinlogProxy::getInstance).thenReturn(mockProxy);
            when(mockProxy.isBackupOn()).thenReturn(true);
            mockedAppConfig.when(DynamicApplicationConfig::getClusterType).thenReturn(ClusterType.BINLOG_X.name());
            mockedAppConfig.when(() -> DynamicApplicationConfig.getString(BINLOGX_STREAM_GROUP_NAME))
                .thenReturn("test-group-name");

            // 调用测试方法
            SystemControlResource resource = new SystemControlResource();
            resource.cleanRemoteFiles();

            // 验证方法调用
            verify(mockProxy).deleteAll("test-group-name/");
        }
    }

    @Test
    public void testCleanRemoteFiles_Binlog_WithBackupOn() {
        try (MockedStatic<RemoteBinlogProxy> mockedRemoteBinlogProxy = mockStatic(RemoteBinlogProxy.class)) {

            // 准备mock对象
            RemoteBinlogProxy mockProxy = mock(RemoteBinlogProxy.class);

            // 设置期望行为
            mockedRemoteBinlogProxy.when(RemoteBinlogProxy::getInstance).thenReturn(mockProxy);
            when(mockProxy.isBackupOn()).thenReturn(true);
            mockedAppConfig.when(DynamicApplicationConfig::getClusterType).thenReturn(ClusterType.BINLOG.name());

            // 调用测试方法
            SystemControlResource resource = new SystemControlResource();
            resource.cleanRemoteFiles();

            // 验证方法调用
            verify(mockProxy).deleteAll("binlog.");
        }
    }

    @Test
    public void testCleanRemoteFiles_BackupOff() {
        try (MockedStatic<RemoteBinlogProxy> mockedRemoteBinlogProxy = mockStatic(RemoteBinlogProxy.class)) {

            // 准备mock对象
            RemoteBinlogProxy mockProxy = mock(RemoteBinlogProxy.class);

            // 设置期望行为
            mockedRemoteBinlogProxy.when(RemoteBinlogProxy::getInstance).thenReturn(mockProxy);
            when(mockProxy.isBackupOn()).thenReturn(false);

            // 调用测试方法
            SystemControlResource resource = new SystemControlResource();
            resource.cleanRemoteFiles();

            // 验证deleteAll没有被调用
            verify(mockProxy, never()).deleteAll(anyString());
        }
    }

    @Test
    public void testCleanRemoteFiles_UnsupportedClusterType() {
        try (MockedStatic<RemoteBinlogProxy> mockedRemoteBinlogProxy = mockStatic(RemoteBinlogProxy.class)) {

            // 准备mock对象
            RemoteBinlogProxy mockProxy = mock(RemoteBinlogProxy.class);

            // 设置期望行为
            mockedRemoteBinlogProxy.when(RemoteBinlogProxy::getInstance).thenReturn(mockProxy);
            when(mockProxy.isBackupOn()).thenReturn(true);
            mockedAppConfig.when(DynamicApplicationConfig::getClusterType).thenReturn(ClusterType.IMPORT.name());

            // 调用测试方法
            SystemControlResource resource = new SystemControlResource();
            resource.cleanRemoteFiles();

            // 验证deleteAll没有被调用
            verify(mockProxy, never()).deleteAll(anyString());
        }
    }

}
