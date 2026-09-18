/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.ServerInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class DruidDataSourceWrapperTest {

    private MockedStatic<DynamicApplicationConfig> dynamicConfig;
    private TestableDruidDataSourceWrapper dataSource;

    @Before
    public void setUp() throws Exception {
        // DruidDataSourceWrapper 的既有静态连接参数会在类初始化时读取这两个动态配置。
        dynamicConfig = Mockito.mockStatic(DynamicApplicationConfig.class);
        dynamicConfig.when(() -> DynamicApplicationConfig.getString(
            ConfigKeys.RPL_SHORT_SOCKET_TIMEOUT_MILLS)).thenReturn("900000");
        dynamicConfig.when(() -> DynamicApplicationConfig.getString(
            ConfigKeys.RPL_LONG_SOCKET_TIMEOUT_MILLS)).thenReturn("3600000");
        dataSource = new TestableDruidDataSourceWrapper();
    }

    @After
    public void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (dynamicConfig != null) {
            dynamicConfig.close();
        }
    }

    @Test
    public void metadataUsesInformationSchemaForBothConnectionPaths() {
        Assert.assertEquals("true", DataSourceUtil.DEFAULT_MYSQL_CONNECTION_PROPERTIES.get("useInformationSchema"));
        Assert.assertEquals("true",
            DruidDataSourceWrapper.DEFAULT_MYSQL_CONNECTION_PROPERTIES.get("useInformationSchema"));
        // Literal quote characters in database names must not be stripped either.
        Assert.assertEquals("true", DataSourceUtil.DEFAULT_MYSQL_CONNECTION_PROPERTIES.get("pedantic"));
        Assert.assertEquals("true", DruidDataSourceWrapper.DEFAULT_MYSQL_CONNECTION_PROPERTIES.get("pedantic"));
    }

    @Test
    public void initialActivationUsesTaskStableCandidate() throws Exception {
        dataSource.taskId = 1L;
        dataSource.snapshot = snapshot("a:3306", "b:3306", "c:3306");
        DruidDataSourceWrapper.ActivePool selected = pool("b:3306");
        dataSource.pools.put("b:3306", selected);

        dataSource.init();

        Assert.assertSame(selected, dataSource.activePool.get());
        Assert.assertEquals(Collections.singletonList("b:3306"), dataSource.createdAddresses);
        Assert.assertTrue(dataSource.alarms.isEmpty());
        Assert.assertNotNull(dataSource.scheduledExecutorService);
        Assert.assertFalse(dataSource.scheduledExecutorService.isShutdown());
    }

    @Test
    public void consecutiveFailuresReachThresholdBeforeSwitch() throws Exception {
        dataSource.healthFailureThreshold = 3;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidDataSourceWrapper.ActivePool replacement = pool("b:3306");
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306", "b:3306");
        dataSource.health.put(old, false);
        dataSource.pools.put("b:3306", replacement);

        dataSource.scan();
        dataSource.scan();

        Assert.assertSame(old, dataSource.activePool.get());
        Assert.assertTrue(dataSource.createdAddresses.isEmpty());
        verify(old.businessDataSource, never()).close();

        dataSource.scan();

        Assert.assertSame(replacement, dataSource.activePool.get());
        Assert.assertEquals(Collections.singletonList("b:3306"), dataSource.createdAddresses);
        Assert.assertTrue(old.retired.get());
        Assert.assertTrue(dataSource.retiredPools.contains(old));
        verify(old.businessDataSource, never()).close();
    }

    @Test
    public void successfulProbeResetsConsecutiveFailureCount() throws Exception {
        dataSource.healthFailureThreshold = 3;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306", "b:3306");
        dataSource.healthSequence = new boolean[] {false, false, true, false, false};

        for (int i = 0; i < dataSource.healthSequence.length; i++) {
            dataSource.scan();
        }

        Assert.assertSame(old, dataSource.activePool.get());
        Assert.assertTrue(dataSource.createdAddresses.isEmpty());
        Assert.assertTrue(dataSource.alarms.isEmpty());
    }

    @Test
    public void candidatePreparationDoesNotCreateEmptyActiveWindow() throws Exception {
        dataSource.healthFailureThreshold = 1;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidDataSourceWrapper.ActivePool replacement = pool("b:3306");
        DruidPooledConnection oldConnection = mock(DruidPooledConnection.class);
        when(old.businessDataSource.getConnection()).thenReturn(oldConnection);
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306", "b:3306");
        dataSource.health.put(old, false);

        CountDownLatch candidateStarted = new CountDownLatch(1);
        CountDownLatch allowCandidate = new CountDownLatch(1);
        dataSource.poolCreator = address -> {
            candidateStarted.countDown();
            Assert.assertTrue("candidate creation was not released",
                allowCandidate.await(5, TimeUnit.SECONDS));
            return replacement;
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> scanFuture = executor.submit(dataSource::scan);
            Assert.assertTrue(candidateStarted.await(5, TimeUnit.SECONDS));

            Assert.assertSame(old, dataSource.activePool.get());
            Assert.assertTrue(old.acceptingBorrows.get());
            Assert.assertSame(oldConnection, dataSource.getConnection());

            allowCandidate.countDown();
            scanFuture.get(5, TimeUnit.SECONDS);
            Assert.assertSame(replacement, dataSource.activePool.get());
        } finally {
            allowCandidate.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void blockedBusinessBorrowDoesNotBlockAtomicSwitch() throws Exception {
        dataSource.healthFailureThreshold = 1;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidDataSourceWrapper.ActivePool replacement = pool("b:3306");
        DruidPooledConnection oldConnection = mock(DruidPooledConnection.class);
        DruidPooledConnection newConnection = mock(DruidPooledConnection.class);
        CountDownLatch oldBorrowStarted = new CountDownLatch(1);
        CountDownLatch allowOldBorrow = new CountDownLatch(1);
        doAnswer(invocation -> {
            oldBorrowStarted.countDown();
            Assert.assertTrue("old borrow was not released", allowOldBorrow.await(5, TimeUnit.SECONDS));
            return oldConnection;
        }).when(old.businessDataSource).getConnection();
        when(replacement.businessDataSource.getConnection()).thenReturn(newConnection);

        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306", "b:3306");
        dataSource.health.put(old, false);
        dataSource.pools.put("b:3306", replacement);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Connection> connectionFuture = executor.submit(() -> dataSource.getConnection());
            Assert.assertTrue(oldBorrowStarted.await(5, TimeUnit.SECONDS));

            Future<?> scanFuture = executor.submit(dataSource::scan);
            scanFuture.get(5, TimeUnit.SECONDS);
            Assert.assertSame("scanner must publish replacement while old pool borrow is blocked",
                replacement, dataSource.activePool.get());

            allowOldBorrow.countDown();
            Assert.assertSame(newConnection, connectionFuture.get(5, TimeUnit.SECONDS));
            verify(oldConnection).close();
        } finally {
            allowOldBorrow.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void healthCheckUsesIsolatedPoolInsteadOfBusinessPool() throws Exception {
        DruidDataSource business = mock(DruidDataSource.class);
        DruidDataSource health = mock(DruidDataSource.class);
        DruidPooledConnection healthConnection = mock(DruidPooledConnection.class);
        when(health.getConnection()).thenReturn(healthConnection);
        when(healthConnection.isValid(1)).thenReturn(true);
        DruidDataSourceWrapper.ActivePool old =
            new DruidDataSourceWrapper.ActivePool("a:3306", business, health, 1L);

        dataSource.useProductionHealthCheck = true;
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306");

        dataSource.scan();

        Assert.assertSame(old, dataSource.activePool.get());
        verify(health).getConnection();
        verify(healthConnection).isValid(1);
        verifyNoInteractions(business);
    }

    @Test
    public void allCandidatesFailKeepsOldPoolAndTriggersAlarm() throws Exception {
        dataSource.healthFailureThreshold = 1;
        dataSource.switchRetryIntervalMillis = 1;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidDataSourceWrapper.ActivePool failedNew = pool("b:3306");
        DruidDataSourceWrapper.ActivePool failedRebuild = pool("a:3306");
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306", "b:3306");
        dataSource.health.put(old, false);
        dataSource.pools.put("b:3306", failedNew);
        dataSource.pools.put("a:3306", failedRebuild);
        dataSource.health.put(failedNew, false);
        dataSource.health.put(failedRebuild, false);

        dataSource.scan();

        Assert.assertSame(old, dataSource.activePool.get());
        Assert.assertTrue(old.acceptingBorrows.get());
        Assert.assertFalse(old.closed.get());
        Assert.assertEquals(Arrays.asList("b:3306", "a:3306"), dataSource.createdAddresses);
        Assert.assertEquals(1, dataSource.alarms.size());
        Assert.assertTrue(dataSource.alarms.get(0).contains("No healthy CN replacement"));
        verify(failedNew.businessDataSource).close();
        verify(failedRebuild.businessDataSource).close();
        verify(old.businessDataSource, never()).close();
    }

    @Test
    public void topologyRemovalStopsNewBorrowsButPreservesOldPool() throws Exception {
        dataSource.healthFailureThreshold = 1;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidPooledConnection oldConnection = mock(DruidPooledConnection.class);
        when(old.businessDataSource.getConnection()).thenReturn(oldConnection);
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("b:3306");
        dataSource.poolCreator = address -> {
            throw new SQLException("replacement unavailable");
        };

        dataSource.scan();

        Assert.assertSame(old, dataSource.activePool.get());
        Assert.assertTrue(old.acceptingBorrows.get());
        Assert.assertFalse(old.closed.get());
        Assert.assertSame(oldConnection, dataSource.getConnection());
        Assert.assertEquals(1, dataSource.alarms.size());
        verify(old.businessDataSource, never()).close();
    }

    @Test
    public void emptyTopologyAlarmsOnceWithoutDisablingCurrentPool() throws Exception {
        dataSource.healthFailureThreshold = 2;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidPooledConnection oldConnection = mock(DruidPooledConnection.class);
        when(old.businessDataSource.getConnection()).thenReturn(oldConnection);
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot();

        dataSource.scan();
        dataSource.scan();
        dataSource.scan();

        Assert.assertSame(old, dataSource.activePool.get());
        Assert.assertTrue(old.acceptingBorrows.get());
        Assert.assertSame(oldConnection, dataSource.getConnection());
        Assert.assertEquals(1, dataSource.alarms.size());
        Assert.assertTrue(dataSource.alarms.get(0).contains("topology remained empty"));
        Assert.assertTrue(dataSource.createdAddresses.isEmpty());
    }

    @Test
    public void retiredPoolClosesOnlyAfterActiveConnectionsDrain() throws Exception {
        dataSource.healthFailureThreshold = 1;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidDataSourceWrapper.ActivePool replacement = pool("b:3306");
        when(old.businessDataSource.getActiveCount()).thenReturn(1, 0);
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306", "b:3306");
        dataSource.health.put(old, false);
        dataSource.health.put(replacement, true);
        dataSource.pools.put("b:3306", replacement);

        dataSource.scan();
        dataSource.health.put(replacement, true);
        dataSource.scan();

        Assert.assertTrue(dataSource.retiredPools.contains(old));
        verify(old.businessDataSource, never()).close();

        dataSource.scan();

        Assert.assertFalse(dataSource.retiredPools.contains(old));
        verify(old.healthDataSource).close();
        verify(old.businessDataSource).close();
    }

    @Test
    public void drainTimeoutRaisesAlarmWithoutForceClosingOldPool() throws Exception {
        dataSource.healthFailureThreshold = 1;
        dataSource.drainTimeoutMillis = 1;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidDataSourceWrapper.ActivePool replacement = pool("b:3306");
        when(old.businessDataSource.getActiveCount()).thenReturn(1);
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306", "b:3306");
        dataSource.health.put(old, false);
        dataSource.pools.put("b:3306", replacement);

        dataSource.scan();
        old.retiredAtMillis = System.currentTimeMillis() - 10;
        dataSource.scan();

        Assert.assertTrue(dataSource.alarms.stream().anyMatch(s -> s.contains("drain timeout")));
        verify(old.businessDataSource, never()).close();
    }

    @Test
    public void forceCloseTimeoutReleasesUndrainedOldPool() throws Exception {
        dataSource.healthFailureThreshold = 1;
        dataSource.drainTimeoutMillis = 1;
        dataSource.forceCloseTimeoutMillis = 5;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidDataSourceWrapper.ActivePool replacement = pool("b:3306");
        when(old.businessDataSource.getActiveCount()).thenReturn(1);
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306", "b:3306");
        dataSource.health.put(old, false);
        dataSource.pools.put("b:3306", replacement);

        dataSource.scan();
        old.retiredAtMillis = System.currentTimeMillis() - 10;
        dataSource.scan();

        Assert.assertFalse(dataSource.retiredPools.contains(old));
        Assert.assertTrue(old.closed.get());
        Assert.assertTrue(dataSource.alarms.stream().anyMatch(s -> s.contains("force-close timeout")));
        verify(old.healthDataSource).close();
        verify(old.businessDataSource).close();
    }

    @Test
    public void closeRacingWithCandidateCreationCannotRepublishPool() throws Exception {
        dataSource.healthFailureThreshold = 1;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidDataSourceWrapper.ActivePool candidate = pool("b:3306");
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306", "b:3306");
        dataSource.health.put(old, false);

        CountDownLatch candidateStarted = new CountDownLatch(1);
        CountDownLatch allowCandidate = new CountDownLatch(1);
        dataSource.poolCreator = address -> {
            candidateStarted.countDown();
            Assert.assertTrue(allowCandidate.await(5, TimeUnit.SECONDS));
            return candidate;
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> scanFuture = executor.submit(dataSource::scan);
            Assert.assertTrue(candidateStarted.await(5, TimeUnit.SECONDS));
            dataSource.close();
            allowCandidate.countDown();
            scanFuture.get(5, TimeUnit.SECONDS);

            Assert.assertNull(dataSource.activePool.get());
            Assert.assertTrue(candidate.closed.get());
            verify(candidate.businessDataSource).close();
            verify(old.businessDataSource).close();
        } finally {
            allowCandidate.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void topologyQueryFailureKeepsActiveAndAlarms() throws Exception {
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        dataSource.activePool.set(old);
        dataSource.snapshotFailure = new IllegalStateException("metadata unavailable");

        dataSource.scan();

        Assert.assertSame(old, dataSource.activePool.get());
        Assert.assertTrue(old.acceptingBorrows.get());
        Assert.assertEquals(1, dataSource.alarms.size());
        Assert.assertTrue(dataSource.alarms.get(0).contains("scanner failed"));
        verify(old.businessDataSource, never()).close();
    }

    @Test
    public void failedSwitchIsRateLimitedWithoutAlarmStorm() throws Exception {
        dataSource.healthFailureThreshold = 1;
        dataSource.switchRetryIntervalMillis = 60000;
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        dataSource.activePool.set(old);
        dataSource.snapshot = snapshot("a:3306", "b:3306");
        dataSource.health.put(old, false);
        dataSource.poolCreator = address -> {
            throw new SQLException("candidate unavailable");
        };

        dataSource.scan();
        dataSource.scan();

        Assert.assertSame(old, dataSource.activePool.get());
        Assert.assertEquals(Arrays.asList("b:3306", "a:3306"), dataSource.createdAddresses);
        Assert.assertEquals(1, dataSource.alarms.size());
        Assert.assertTrue(old.acceptingBorrows.get());
    }

    @Test
    public void blacklistedActiveWithoutReplacementKeepsAtomicSnapshot() throws Exception {
        DruidDataSourceWrapper.ActivePool old = pool("10.0.0.1:3306");
        dataSource.activePool.set(old);
        dataSource.snapshot = new DruidDataSourceWrapper.ServerSnapshot(
            Collections.singleton(old.address), Collections.emptySet(), Collections.singleton("10.0.0.1"));

        dataSource.scan();

        Assert.assertSame(old, dataSource.activePool.get());
        Assert.assertTrue(old.acceptingBorrows.get());
        Assert.assertEquals(1, dataSource.alarms.size());
        Assert.assertTrue(dataSource.alarms.get(0).contains("candidates=[]"));
    }

    @Test
    public void connectionWaitHasBoundedTimeoutAndPreservesInterrupt() throws Exception {
        dataSource.maxWaitTimeMills = 1;
        try {
            dataSource.getConnection();
            Assert.fail("expected timeout without an active CN pool");
        } catch (PolardbxException e) {
            Assert.assertTrue(e.getMessage().contains("timeout"));
        }

        Thread.currentThread().interrupt();
        try {
            dataSource.waitNestedAddressReady();
            Assert.fail("expected interrupted wait to fail");
        } catch (PolardbxException e) {
            Assert.assertTrue(e.getMessage().contains("interrupted"));
            Assert.assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void closedWrapperRejectsNewConnections() {
        dataSource.close();

        try {
            dataSource.getConnection();
            Assert.fail("closed wrapper must reject borrows");
        } catch (PolardbxException | SQLException e) {
            Assert.assertTrue(e.getMessage().contains("closed"));
        }

        try {
            dataSource.waitNestedAddressReady();
            Assert.fail("closed wrapper must reject readiness waits");
        } catch (PolardbxException e) {
            Assert.assertTrue(e.getMessage().contains("closed"));
        }
    }

    @Test
    public void businessBorrowFailureIsPropagatedWhenSnapshotDidNotChange() throws Exception {
        DruidDataSourceWrapper.ActivePool active = pool("a:3306");
        SQLException expected = new SQLException("pool exhausted");
        when(active.businessDataSource.getConnection()).thenThrow(expected);
        dataSource.activePool.set(active);

        try {
            dataSource.getConnection();
            Assert.fail("borrow error from the active snapshot must be propagated");
        } catch (SQLException actual) {
            Assert.assertSame(expected, actual);
        }
        Assert.assertEquals(0, active.pendingBorrows.get());
    }

    @Test
    public void borrowFailureDuringSwitchRetriesNewSnapshot() throws Exception {
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidDataSourceWrapper.ActivePool replacement = pool("b:3306");
        DruidPooledConnection newConnection = mock(DruidPooledConnection.class);
        doAnswer(invocation -> {
            dataSource.activePool.set(replacement);
            throw new SQLException("old pool closed during switch");
        }).when(old.businessDataSource).getConnection();
        when(replacement.businessDataSource.getConnection()).thenReturn(newConnection);
        dataSource.activePool.set(old);

        Assert.assertSame(newConnection, dataSource.getConnection());
        Assert.assertEquals(0, old.pendingBorrows.get());
        verify(replacement.businessDataSource).getConnection();
    }

    @Test
    public void failedCloseOfRetiredBorrowDoesNotBlockRetry() throws Exception {
        DruidDataSourceWrapper.ActivePool old = pool("a:3306");
        DruidDataSourceWrapper.ActivePool replacement = pool("b:3306");
        DruidPooledConnection oldConnection = mock(DruidPooledConnection.class);
        DruidPooledConnection newConnection = mock(DruidPooledConnection.class);
        doAnswer(invocation -> {
            dataSource.activePool.set(replacement);
            return oldConnection;
        }).when(old.businessDataSource).getConnection();
        doThrow(new SQLException("close failed")).when(oldConnection).close();
        when(replacement.businessDataSource.getConnection()).thenReturn(newConnection);
        dataSource.activePool.set(old);

        Assert.assertSame(newConnection, dataSource.getConnection());
        verify(oldConnection).close();
        verify(replacement.businessDataSource).getConnection();
    }

    @Test
    public void credentialAndProxyBorrowsDelegateToExpectedPool() throws Exception {
        DruidDataSourceWrapper.ActivePool active = pool("a:3306");
        Connection credentialConnection = mock(Connection.class);
        when(active.businessDataSource.getConnection("alice", "secret")).thenReturn(credentialConnection);
        dataSource.activePool.set(active);

        Assert.assertSame(credentialConnection, dataSource.getConnection("alice", "secret"));
        verify(active.businessDataSource).getConnection("alice", "secret");

        DruidDataSource proxy = mock(DruidDataSource.class);
        DruidPooledConnection proxyConnection = mock(DruidPooledConnection.class);
        Connection proxyCredentialConnection = mock(Connection.class);
        when(proxy.getConnection()).thenReturn(proxyConnection);
        when(proxy.getConnection("bob", "pwd")).thenReturn(proxyCredentialConnection);
        dataSource.proxyDataSource = proxy;

        Assert.assertSame(proxyConnection, dataSource.getConnection());
        Assert.assertSame(proxyCredentialConnection, dataSource.getConnection("bob", "pwd"));
        Assert.assertSame(proxyConnection, dataSource.getPooledConnection("ignored", "ignored"));
        verify(proxy, times(2)).getConnection();
        verify(proxy).getConnection("bob", "pwd");
    }

    @Test
    public void productionPoolCreationBuildsSeparateBusinessAndHealthPools() throws Exception {
        ProductionPathWrapper production = new ProductionPathWrapper("app_db");
        DruidDataSource business = mock(DruidDataSource.class);
        DruidDataSource health = mock(DruidDataSource.class);
        production.clones.add(business);
        production.clones.add(health);

        DruidDataSourceWrapper.ActivePool created = production.createActivePool("127.0.0.1:3306");
        production.activePool.set(created);

        Assert.assertSame(business, created.businessDataSource);
        Assert.assertSame(health, created.healthDataSource);
        Assert.assertNotSame(created.businessDataSource, created.healthDataSource);
        Assert.assertEquals(1L, created.generation);
        verify(business).setUrl(
            "jdbc:mysql://127.0.0.1:3306/app_db?allowPublicKeyRetrieval=true&useSSL=false");
        verify(health).setUrl(
            "jdbc:mysql://127.0.0.1:3306/app_db?allowPublicKeyRetrieval=true&useSSL=false");
        verify(business).init();
        verify(health).setInitialSize(0);
        verify(health).setMinIdle(0);
        verify(health).setMaxActive(1);
        verify(health).setMaxWait(1000L);
        verify(health).setTestWhileIdle(false);
        verify(health).init();

        production.close();
        verify(business).close();
        verify(health).close();
    }

    @Test
    public void failedHealthPoolInitializationClosesBothCandidatePools() throws Exception {
        ProductionPathWrapper production = new ProductionPathWrapper("");
        DruidDataSource business = mock(DruidDataSource.class);
        DruidDataSource health = mock(DruidDataSource.class);
        doThrow(new SQLException("health init failed")).when(health).init();
        production.clones.add(business);
        production.clones.add(health);

        try {
            production.createActivePool("127.0.0.1:3306");
            Assert.fail("candidate creation must fail when its health pool cannot initialize");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("127.0.0.1:3306"));
            Assert.assertTrue(e.getCause() instanceof SQLException);
        }
        verify(business).setUrl("jdbc:mysql://127.0.0.1:3306?allowPublicKeyRetrieval=true&useSSL=false");
        verify(health).close();
        verify(business).close();
        production.close();
    }

    @Test
    public void topologySnapshotNormalizesBlacklistAndKeepsReadySet() throws Exception {
        ProductionPathWrapper production = new ProductionPathWrapper("db");
        dynamicConfig.when(() -> DynamicApplicationConfig.getString(
            ConfigKeys.RPL_POOL_CN_BLACK_IP_LIST)).thenReturn(" 10.0.0.1, ,HOST-A ");
        ServerInfoMapper mapper = mock(ServerInfoMapper.class);
        ServerInfo first = server("10.0.0.1", 3306);
        ServerInfo second = server("host-b", 3307);
        when(mapper.select(Mockito.<SelectDSLCompleter>any())).thenReturn(Arrays.asList(first, second));

        try (MockedStatic<SpringContextHolder> spring = Mockito.mockStatic(SpringContextHolder.class)) {
            spring.when(() -> SpringContextHolder.getObject(ServerInfoMapper.class)).thenReturn(mapper);
            DruidDataSourceWrapper.ServerSnapshot result = production.getLatestServerSnapshot();

            Assert.assertEquals(new HashSet<>(Arrays.asList("10.0.0.1:3306", "host-b:3307")),
                result.readyAddresses);
            Assert.assertEquals(Collections.singleton("host-b:3307"), result.availableAddresses);
            Assert.assertEquals(new HashSet<>(Arrays.asList("10.0.0.1", "host-a")), result.blacklistedIps);
            Assert.assertTrue(result.isBlacklisted("10.0.0.1:3306"));
            Assert.assertFalse(result.isBlacklisted("host-b:3307"));
        } finally {
            production.close();
        }
    }

    @Test
    public void dynamicPoolConfigUsesPositiveValuesAndSafeDefaults() throws Exception {
        ProductionPathWrapper production = new ProductionPathWrapper("db");
        dynamicConfig.when(() -> DynamicApplicationConfig.getInt(
            ConfigKeys.DATASOURCE_CHECK_VALID_TIMEOUT_SEC)).thenReturn(4);
        dynamicConfig.when(() -> DynamicApplicationConfig.getInt(
            ConfigKeys.RPL_POOL_CN_HEALTH_FAILURE_THRESHOLD)).thenReturn(5);
        dynamicConfig.when(() -> DynamicApplicationConfig.getLong(
            ConfigKeys.RPL_POOL_CN_SWITCH_RETRY_INTERVAL_MILLIS)).thenReturn(6000L);
        dynamicConfig.when(() -> DynamicApplicationConfig.getLong(
            ConfigKeys.RPL_POOL_CN_DRAIN_TIMEOUT_MILLIS)).thenThrow(new IllegalArgumentException("bad value"));
        dynamicConfig.when(() -> DynamicApplicationConfig.getLong(
            ConfigKeys.RPL_POOL_CN_FORCE_CLOSE_TIMEOUT_MILLIS)).thenReturn(900000L);

        Assert.assertEquals(4, production.validationTimeoutSeconds());
        Assert.assertEquals(5, production.healthFailureThreshold());
        Assert.assertEquals(6000L, production.switchRetryIntervalMillis());
        Assert.assertEquals(300000L, production.drainTimeoutMillis());
        Assert.assertEquals(900000L, production.forceCloseTimeoutMillis());

        dynamicConfig.when(() -> DynamicApplicationConfig.getInt(
            ConfigKeys.RPL_POOL_CN_HEALTH_FAILURE_THRESHOLD)).thenReturn(0);
        Assert.assertEquals(3, production.healthFailureThreshold());
        production.close();
    }

    @Test
    public void taskIdentityUsesContextThenPropertyThenSafeFallback() throws Exception {
        ProductionPathWrapper production = new ProductionPathWrapper("db");
        RplTask originalTask = TaskContext.getInstance().getTask();
        String originalProperty = System.getProperty("taskId");
        try {
            RplTask task = new RplTask();
            task.setId(47L);
            TaskContext.getInstance().setTask(task);
            Assert.assertEquals(47L, production.getTaskId());

            TaskContext.getInstance().setTask(null);
            System.setProperty("taskId", "53");
            Assert.assertEquals(53L, production.getTaskId());

            System.setProperty("taskId", "not-a-number");
            Assert.assertEquals(0L, production.getTaskId());
        } finally {
            TaskContext.getInstance().setTask(originalTask);
            restoreSystemProperty("taskId", originalProperty);
            production.close();
        }
    }

    @Test
    public void productionAlarmCarriesTaskIdentityAndDoesNotBreakOnAlarmFailure() throws Exception {
        ProductionPathWrapper production = new ProductionPathWrapper("db");
        RplTask originalTask = TaskContext.getInstance().getTask();
        RplTask task = new RplTask();
        task.setId(61L);
        TaskContext.getInstance().setTask(task);
        StatisticalProxy proxy = mock(StatisticalProxy.class);
        try (MockedStatic<StatisticalProxy> statistics = Mockito.mockStatic(StatisticalProxy.class)) {
            statistics.when(StatisticalProxy::getInstance).thenReturn(proxy);

            production.triggerPoolAlarm("first alarm");
            verify(proxy).triggerAlarmSync(MonitorType.IMPORT_INC_ERROR, 61L, "first alarm");

            doThrow(new IllegalStateException("alarm backend unavailable")).when(proxy)
                .triggerAlarmSync(MonitorType.IMPORT_INC_ERROR, 61L, "second alarm");
            production.triggerPoolAlarm("second alarm");
            verify(proxy).triggerAlarmSync(MonitorType.IMPORT_INC_ERROR, 61L, "second alarm");
            Assert.assertFalse(production.closed.get());
        } finally {
            TaskContext.getInstance().setTask(originalTask);
            production.close();
        }
    }

    private DruidDataSourceWrapper.ActivePool pool(String address) throws SQLException {
        DruidDataSource business = mock(DruidDataSource.class);
        DruidDataSource health = mock(DruidDataSource.class);
        when(business.getActiveCount()).thenReturn(0);
        return new DruidDataSourceWrapper.ActivePool(address, business, health,
            TestableDruidDataSourceWrapper.GENERATION.incrementAndGet());
    }

    private static DruidDataSourceWrapper.ServerSnapshot snapshot(String... addresses) {
        Set<String> ready = new HashSet<>(Arrays.asList(addresses));
        return new DruidDataSourceWrapper.ServerSnapshot(ready, new HashSet<>(ready), Collections.emptySet());
    }

    private static ServerInfo server(String ip, int port) {
        ServerInfo server = new ServerInfo();
        server.setIp(ip);
        server.setPort(port);
        return server;
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private interface PoolCreator {
        DruidDataSourceWrapper.ActivePool create(String address) throws Exception;
    }

    private static class ProductionPathWrapper extends DruidDataSourceWrapper {
        final Queue<DruidDataSource> clones = new ArrayDeque<>();

        ProductionPathWrapper(String dbName) throws Exception {
            super(dbName, "user", "passwd", "utf8", 0, 20, null, null);
        }

        @Override
        public DruidDataSource cloneDruidDataSource() {
            return clones.remove();
        }
    }

    private static class TestableDruidDataSourceWrapper extends DruidDataSourceWrapper {
        private static final AtomicLong GENERATION = new AtomicLong();

        volatile long taskId;
        volatile int healthFailureThreshold = 3;
        volatile long switchRetryIntervalMillis = 1;
        volatile long drainTimeoutMillis = 300000;
        volatile long forceCloseTimeoutMillis = 900000;
        volatile ServerSnapshot snapshot = snapshot();
        volatile RuntimeException snapshotFailure;
        volatile PoolCreator poolCreator;
        volatile boolean useProductionHealthCheck;
        volatile boolean[] healthSequence;
        volatile int healthSequenceIndex;
        final Map<String, ActivePool> pools = new ConcurrentHashMap<>();
        final Map<ActivePool, Boolean> health = new ConcurrentHashMap<>();
        final List<String> createdAddresses = new CopyOnWriteArrayList<>();
        final List<String> alarms = new CopyOnWriteArrayList<>();

        TestableDruidDataSourceWrapper() throws Exception {
            super("db", "user", "passwd", "utf8", 0, 20, null, null);
            maxWaitTimeMills = 500;
        }

        @Override
        protected ServerSnapshot getLatestServerSnapshot() {
            if (snapshotFailure != null) {
                throw snapshotFailure;
            }
            return snapshot;
        }

        @Override
        protected ActivePool createActivePool(String address) throws Exception {
            createdAddresses.add(address);
            if (poolCreator != null) {
                return poolCreator.create(address);
            }
            ActivePool result = pools.get(address);
            if (result == null) {
                throw new SQLException("no test pool for " + address);
            }
            return result;
        }

        @Override
        protected boolean isHealthy(ActivePool pool) {
            if (useProductionHealthCheck) {
                return super.isHealthy(pool);
            }
            if (healthSequence != null && healthSequenceIndex < healthSequence.length) {
                return healthSequence[healthSequenceIndex++];
            }
            Boolean result = health.get(pool);
            return result == null || result;
        }

        @Override
        protected int validationTimeoutSeconds() {
            return 1;
        }

        @Override
        protected int healthFailureThreshold() {
            return healthFailureThreshold;
        }

        @Override
        protected long switchRetryIntervalMillis() {
            return switchRetryIntervalMillis;
        }

        @Override
        protected long drainTimeoutMillis() {
            return drainTimeoutMillis;
        }

        @Override
        protected long forceCloseTimeoutMillis() {
            return forceCloseTimeoutMillis;
        }

        @Override
        protected long getTaskId() {
            return taskId;
        }

        @Override
        protected void triggerPoolAlarm(String message) {
            alarms.add(message);
        }
    }
}
