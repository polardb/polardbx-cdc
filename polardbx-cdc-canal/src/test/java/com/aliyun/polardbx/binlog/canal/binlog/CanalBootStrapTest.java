/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.CanalBootstrap;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.SearchMetricsManager;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.OssConnection;
import com.aliyun.polardbx.binlog.canal.core.handle.BinarySearchTsoEventHandle;
import com.aliyun.polardbx.binlog.canal.core.handle.DefaultBinlogEventHandle;
import com.aliyun.polardbx.binlog.canal.core.handle.ISearchTsoEventHandle;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.ConsumeOSSBinlogEndException;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.canal.exception.ServerIdNotMatchException;
import com.aliyun.polardbx.binlog.canal.unit.SearchRecorder;
import com.aliyun.polardbx.binlog.domain.DnHost;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class CanalBootStrapTest extends BaseTest {

    @Test
    public void testBinarySearch() throws Exception {
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        String polarxServerVersion = "";
        String localBinlogDir = "";
        Long preferHostId = null;
        String startCmdTSO = 7305964338139889730L +"000000000000000000";
        CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class, withSettings().useConstructor(authenticationInfo, polarxServerVersion, localBinlogDir, preferHostId, startCmdTSO));
        when(canalBootstrap.binarySearch(any(), anyLong(), any(), anyString())).thenCallRealMethod();
        when(canalBootstrap.searchPosition(any(), anyString())).thenCallRealMethod();
        when(canalBootstrap.extractPhysicalTso(anyString())).thenCallRealMethod();
        when(canalBootstrap.buildRecorder(any(), anyLong())).thenReturn(new SearchRecorder("test"));
        when(canalBootstrap.isRunning()).thenReturn(true);
        BinarySearchTsoEventHandle binarySearchTsoEventHandle = mock(BinarySearchTsoEventHandle.class);
        when(binarySearchTsoEventHandle.isInQuickMode()).thenReturn(true);
        when(canalBootstrap.prepareBinarySearchHandler(anyLong(), anyString())).thenReturn(binarySearchTsoEventHandle);
        when(canalBootstrap.prepareSearchHandler(anyLong())).thenReturn(binarySearchTsoEventHandle);
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        ErosaConnection connection = Mockito.mock(ErosaConnection.class);
        when(connection.binlogList()).thenReturn(Arrays.asList("mysql-bin.000001"));
        canalBootstrap.searchPosition(connection, startCmdTSO);
        verify(canalBootstrap, times(1)).doSearchFile(any(), anyString(), any(), any(), anyLong());
    }

    @Test
    public void testSearchPositionFound() throws Exception {
        String requestTso = "1651782957000";
        long searchTso = 1651782957000L;
        String searchFile = "mysql-bin.000002";
        BinlogPosition startPosition = new BinlogPosition("mysql-bin.000001", "12345");

        CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class);
        OssConnection ossConnection = mock(OssConnection.class);
        ISearchTsoEventHandle searchTsoEventHandle = mock(ISearchTsoEventHandle.class);
        SearchRecorder searchRecorder = mock(SearchRecorder.class);

        // 模拟 prepareSearchHandler 方法返回 mock 对象
        when(canalBootstrap.prepareSearchHandler(anyLong())).thenReturn(searchTsoEventHandle);
        when(canalBootstrap.extractPhysicalTso(anyString())).thenReturn(1651782957000L);
        doNothing().when(canalBootstrap).initSearchProcessor(any(), anyString());

        // 模拟 buildRecorder 方法返回 mock 对象
        doReturn(searchRecorder).when(canalBootstrap).buildRecorder(any(), anyLong());
        // 模拟连接成功
        doNothing().when(ossConnection).connect();

        when(canalBootstrap.buildOssConnection(requestTso)).thenReturn(ossConnection);

        // 模拟构建搜索文件
        when(canalBootstrap.buildSearchFile(any(), any(), anyLong())).thenReturn(searchFile);

        // 模拟获取 binlog 文件大小
        when(canalBootstrap.getProcessorFile()).thenReturn(searchFile);
        when(ossConnection.binlogFileSize(searchFile)).thenReturn(1024L);

        // 模拟搜索结果
        when(searchTsoEventHandle.searchResult()).thenReturn(startPosition);

        when(canalBootstrap.searchPosition(any(), anyString())).thenCallRealMethod();
        when(canalBootstrap.doSearchFile(any(), anyString(), any(), any(), anyLong())).thenCallRealMethod();

        when(canalBootstrap.isRunning()).thenReturn(true);
        // 执行测试
        BinlogPosition result = canalBootstrap.searchPosition(ossConnection, requestTso);

        // 验证结果
        assertNotNull(result);
        assertEquals(startPosition.getFileName(), result.getFileName());
        assertEquals(startPosition.getPosition(), result.getPosition());

        // 验证调用次数
        verify(searchTsoEventHandle, times(2)).searchResult();
    }

    @Test
    public void testSearchPositionNotFound() throws Exception {
        String requestTso = "1651782957000";
        long searchTso = 1651782957000L;
        String searchFile = "mysql-bin.000001";

        CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class);
        ErosaConnection erosaConnection = mock(ErosaConnection.class);
        ISearchTsoEventHandle searchTsoEventHandle = mock(ISearchTsoEventHandle.class);
        SearchRecorder searchRecorder = mock(SearchRecorder.class);

        // 模拟 prepareSearchHandler 方法返回 mock 对象
        when(canalBootstrap.prepareSearchHandler(anyLong())).thenReturn(searchTsoEventHandle);
        when(canalBootstrap.extractPhysicalTso(anyString())).thenReturn(1651782957000L);
        when(canalBootstrap.doSearchFile(any(), anyString(), any(), any(), anyLong())).thenCallRealMethod();
        doNothing().when(canalBootstrap).initSearchProcessor(any(), anyString());

        // 模拟 buildRecorder 方法返回 mock 对象
        doReturn(searchRecorder).when(canalBootstrap).buildRecorder(any(), anyLong());
        // 模拟连接成功
        doNothing().when(erosaConnection).connect();

        // 模拟构建搜索文件
        when(canalBootstrap.buildSearchFile(any(), any(), anyLong())).thenReturn(searchFile);
        when(canalBootstrap.getProcessorFile()).thenReturn(searchFile);

        // 模拟获取 binlog 文件大小
        when(erosaConnection.binlogFileSize(searchFile)).thenReturn(1024L);

        // 模拟搜索结果为空
        when(searchTsoEventHandle.searchResult()).thenReturn(null);
        doNothing().when(canalBootstrap).doProcessor(any(), any(), anyString(), anyLong());

        // 模拟获取前一个文件名为空
        when(erosaConnection.preFileName(searchFile)).thenReturn(null);

        when(canalBootstrap.isRunning()).thenReturn(true);

        when(canalBootstrap.searchPosition(any(), anyString())).thenCallRealMethod();

        // 执行测试
        BinlogPosition result = canalBootstrap.searchPosition(erosaConnection, requestTso);

        // 验证结果
        assertNull(result);

        // 验证调用次数
        verify(searchTsoEventHandle, times(2)).searchResult();
        verify(erosaConnection, times(1)).preFileName(searchFile);
    }

    @Test
    public void testSearchPositionFileNotFound() throws Exception {
        String requestTso = "1651782957000";
        long searchTso = 1651782957000L;
        String searchFile = "mysql-bin.000001";

        CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class);
        ErosaConnection erosaConnection = mock(ErosaConnection.class);
        ISearchTsoEventHandle searchTsoEventHandle = mock(ISearchTsoEventHandle.class);
        SearchRecorder searchRecorder = mock(SearchRecorder.class);

        // 模拟 prepareSearchHandler 方法返回 mock 对象
        when(canalBootstrap.prepareSearchHandler(anyLong())).thenReturn(searchTsoEventHandle);
        when(canalBootstrap.extractPhysicalTso(anyString())).thenReturn(1651782957000L);
        doNothing().when(canalBootstrap).initSearchProcessor(any(), anyString());

        // 模拟 buildRecorder 方法返回 mock 对象
        doReturn(searchRecorder).when(canalBootstrap).buildRecorder(any(), anyLong());
        // 模拟连接成功
        doNothing().when(erosaConnection).connect();

        // 模拟构建搜索文件
        when(canalBootstrap.buildSearchFile(any(), any(), anyLong())).thenReturn(searchFile);

        // 模拟获取 binlog 文件大小为 -1
        when(erosaConnection.binlogFileSize(searchFile)).thenReturn(-1L);

        when(canalBootstrap.searchPosition(any(), anyString())).thenCallRealMethod();
        when(canalBootstrap.getProcessorFile()).thenReturn(searchFile);
        when(canalBootstrap.doSearchFile(any(), anyString(), any(), any(), anyLong())).thenCallRealMethod();

        when(canalBootstrap.isRunning()).thenReturn(true);

        // 执行测试
        BinlogPosition result = canalBootstrap.searchPosition(erosaConnection, requestTso);

        // 验证结果
        assertNull(result);

        // 验证调用次数
        verify(searchTsoEventHandle, times(1)).searchResult();
        verify(erosaConnection, times(0)).preFileName(anyString());
    }

    /**
     * server id 不匹配时，会切换到follower尝试继续
     */
    @Test
    public void testSearchServerIdNotMatchOss() throws Exception {
        String requestTso = "1651782957000";
        long searchTso = 1651782957000L;
        String searchFile = "mysql-bin.000001";

        CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class);
        ErosaConnection erosaConnection = mock(ErosaConnection.class);
        MysqlConnection mysqlConnection = mock(MysqlConnection.class);
        ISearchTsoEventHandle searchTsoEventHandle = mock(ISearchTsoEventHandle.class);
        SearchRecorder searchRecorder = mock(SearchRecorder.class);

        // 模拟 prepareSearchHandler 方法返回 mock 对象
        when(canalBootstrap.prepareSearchHandler(anyLong())).thenReturn(searchTsoEventHandle);
        when(canalBootstrap.extractPhysicalTso(anyString())).thenReturn(1651782957000L);
        when(canalBootstrap.getStorageMasterInstId()).thenReturn("test-rds");
        doNothing().when(canalBootstrap).initSearchProcessor(any(), anyString());

        // 模拟 buildRecorder 方法返回 mock 对象
        doReturn(searchRecorder).when(canalBootstrap).buildRecorder(any(), anyLong());
        // 模拟连接成功
        doNothing().when(erosaConnection).connect();
        doNothing().when(mysqlConnection).connect();

        // 模拟构建搜索文件
        when(canalBootstrap.buildSearchFile(any(), any(), anyLong())).thenReturn(searchFile);

        // 模拟获取 binlog 文件大小为 -1
        when(erosaConnection.binlogFileSize(searchFile)).thenReturn(-1L);
        when(canalBootstrap.getPositionRegion()).thenReturn("[a,b]");

        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        DnHost master = new DnHost("127.0.0.1", 3306, "test", "test", "utf8", "test-rds");
        DnHost follower = new DnHost("127.0.0.1", 3307, "test", "test", "utf8", "test-rds");
        authenticationInfo.setDnNodeList(Arrays.asList(master, follower));
        authenticationInfo.setLeader(master);
        authenticationInfo.switchLeader();
        when(mysqlConnection.getAuthInfo()).thenReturn(authenticationInfo);
        when(canalBootstrap.searchPosition(mysqlConnection, requestTso)).thenReturn(null);
        doCallRealMethod().when(mysqlConnection).switchNextFollower();
        doThrow(new ServerIdNotMatchException()).when(canalBootstrap).consumeOss(requestTso, true);
        when(canalBootstrap.getProcessorFile()).thenReturn(searchFile);
        when(mysqlConnection.hasMoreNode()).thenCallRealMethod();
        doCallRealMethod().when(canalBootstrap).consumeMysql(any(), anyString());

        when(canalBootstrap.isRunning()).thenReturn(true);

        canalBootstrap.consumeMysql(mysqlConnection, requestTso);

        verify(mysqlConnection, times(1)).switchNextFollower();
        verify(canalBootstrap, times(2)).searchPosition(mysqlConnection, requestTso);
        verify(canalBootstrap, times(1)).consumeOss(requestTso, true);
        verify(canalBootstrap, times(1)).consumeOss(requestTso, false);
    }

    /**
     * server id 匹配且找不到位点，会切换一次再次执行
     */
    @Test
    public void testSearchServerIdMatchOss() throws Exception {
        String requestTso = "1651782957000";
        long searchTso = 1651782957000L;
        String searchFile = "mysql-bin.000001";

        CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class);
        OssConnection ossConnection = mock(OssConnection.class);
        MysqlConnection mysqlConnection = mock(MysqlConnection.class);
        ISearchTsoEventHandle searchTsoEventHandle = mock(ISearchTsoEventHandle.class);
        SearchRecorder searchRecorder = mock(SearchRecorder.class);

        // 模拟 prepareSearchHandler 方法返回 mock 对象
        when(canalBootstrap.prepareSearchHandler(anyLong())).thenReturn(searchTsoEventHandle);
        when(canalBootstrap.extractPhysicalTso(anyString())).thenReturn(1651782957000L);
        when(canalBootstrap.getStorageMasterInstId()).thenReturn("test-rds");
        doNothing().when(canalBootstrap).initSearchProcessor(any(), anyString());

        // 模拟 buildRecorder 方法返回 mock 对象
        doReturn(searchRecorder).when(canalBootstrap).buildRecorder(any(), anyLong());
        // 模拟连接成功
        doNothing().when(ossConnection).connect();
        doNothing().when(mysqlConnection).connect();

        // 模拟构建搜索文件
        when(canalBootstrap.buildSearchFile(any(), any(), anyLong())).thenReturn(searchFile);

        // 模拟获取 binlog 文件大小为 -1
        when(ossConnection.binlogFileSize(searchFile)).thenReturn(-1L);
        when(canalBootstrap.getPositionRegion()).thenReturn("[a,b]");
        when(canalBootstrap.buildOssConnection(requestTso)).thenReturn(ossConnection);

        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        DnHost master = new DnHost("127.0.0.1", 3306, "test", "test", "utf8", "test-rds");
        DnHost follower = new DnHost("127.0.0.1", 3307, "test", "test", "utf8", "test-rds");
        authenticationInfo.setDnNodeList(Arrays.asList(master, follower));
        authenticationInfo.setLeader(master);
        authenticationInfo.switchLeader();
        when(mysqlConnection.getAuthInfo()).thenReturn(authenticationInfo);
        when(canalBootstrap.searchPosition(any(), anyString())).thenReturn(null);
        doCallRealMethod().when(mysqlConnection).switchNextFollower();
        doCallRealMethod().when(canalBootstrap).consumeOss(requestTso, true);
        when(ossConnection.isServerIdMatch()).thenReturn(true);
        when(canalBootstrap.getProcessorFile()).thenReturn(searchFile);
        when(mysqlConnection.hasMoreNode()).thenCallRealMethod();
        doCallRealMethod().when(canalBootstrap).consumeMysql(any(), anyString());
        doCallRealMethod().when(ossConnection).tryOtherHost();
        doNothing().when(ossConnection).release();

        when(canalBootstrap.isRunning()).thenReturn(true);
        Exception t = null;
        try {
            canalBootstrap.consumeMysql(mysqlConnection, requestTso);
        } catch (Exception e) {
            t = e;
        }

        Assert.assertNotNull(t);
        Assert.assertEquals(PositionNotFoundException.class, t.getClass());
        Assert.assertEquals("try other host also can not find position", t.getMessage());

        verify(mysqlConnection, times(0)).switchNextFollower();
        verify(canalBootstrap, times(1)).searchPosition(mysqlConnection, requestTso);
        verify(canalBootstrap, times(1)).consumeOss(requestTso, true);
        verify(canalBootstrap, times(0)).consumeOss(requestTso, false);
    }

    @Test
    public void testInitRegion() throws Exception {
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_FORCED, "true");
        try (MockedStatic<DriverManager> mockedStatic = Mockito.mockStatic(DriverManager.class)) {
            Connection conn = Mockito.mock(Connection.class);
            Statement st = Mockito.mock(Statement.class);
            ResultSet serverIdRs = Mockito.mock(ResultSet.class);
            when(serverIdRs.next()).thenReturn(true, false);
            when(serverIdRs.getObject(2)).thenReturn("1");
            when(st.executeQuery("show variables like 'server_id'")).thenReturn(serverIdRs);
            ResultSet showMasterStatusRs = Mockito.mock(ResultSet.class);
            when(showMasterStatusRs.next()).thenReturn(true, false);
            when(showMasterStatusRs.getString(1)).thenReturn("mysql-bin.000001");
            when(showMasterStatusRs.getString(2)).thenReturn("1");
            when(st.executeQuery("show master status")).thenReturn(showMasterStatusRs);
            ResultSet showBinlogEventsRs = Mockito.mock(ResultSet.class);
            when(showBinlogEventsRs.next()).thenReturn(true, false);
            when(showBinlogEventsRs.getString(1)).thenReturn("mysql-bin.000002");
            when(showBinlogEventsRs.getString(2)).thenReturn("1");
            when(st.executeQuery("show binlog events limit 1")).thenReturn(showBinlogEventsRs);
            ResultSet binlogFormat = Mockito.mock(ResultSet.class);
            when(binlogFormat.next()).thenReturn(true, false);
            when(binlogFormat.getString(2)).thenReturn("ROW");
            when(st.executeQuery("show variables like 'binlog_format'")).thenReturn(binlogFormat);
            ResultSet rowImage = Mockito.mock(ResultSet.class);
            when(rowImage.next()).thenReturn(true, false);
            when(rowImage.getString(2)).thenReturn("FULL");
            when(st.executeQuery("show variables like 'binlog_row_image'")).thenReturn(rowImage);
            ResultSet showCharacter = Mockito.mock(ResultSet.class);
            when(showCharacter.next()).thenReturn(true, false);
            when(showCharacter.getString(1)).thenReturn("character_set_client");
            when(showCharacter.getString(2)).thenReturn("utf8");
            when(st.executeQuery("show variables like '%character%'")).thenReturn(showCharacter);
            ResultSet showLowerCaseTableNames = Mockito.mock(ResultSet.class);
            when(showLowerCaseTableNames.next()).thenReturn(true, false);
            when(showLowerCaseTableNames.getObject(2)).thenReturn("1");
            when(st.executeQuery("show variables like 'lower_case_table_names'")).thenReturn(showLowerCaseTableNames);
            ResultSet checksumRs = Mockito.mock(ResultSet.class);
            when(checksumRs.next()).thenReturn(true, false);
            when(checksumRs.getString(1)).thenReturn("CRC32");
            when(st.executeQuery("select @@global.binlog_checksum")).thenReturn(checksumRs);
            ResultSet sqlModeRs = Mockito.mock(ResultSet.class);
            when(sqlModeRs.next()).thenReturn(true, false);
            when(sqlModeRs.getString(1)).thenReturn(
                "STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION");
            when(st.executeQuery("select @@global.sql_mode")).thenReturn(sqlModeRs);
            when(conn.createStatement()).thenReturn(st);
            mockedStatic.when(() -> DriverManager.getConnection(anyString(), any())).thenReturn(conn);
            AuthenticationInfo authenticationInfo = new AuthenticationInfo();
            DnHost dnHost = new DnHost("127.0.0.1", 3306, "test", "test", "utf8", "test-rds");
            authenticationInfo.setLeader(dnHost);
            authenticationInfo.switchLeader();
            CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class,
                withSettings().useConstructor(authenticationInfo, "8.0.21", "test", 1L, "test"));
            doCallRealMethod().when(canalBootstrap).doStart(anyString());
            when(canalBootstrap.getPositionRegion()).thenCallRealMethod();
            doNothing().when(canalBootstrap).consumeOss(anyString(), anyBoolean());
            canalBootstrap.doStart("1651782957000");
            String region = canalBootstrap.getPositionRegion();
            Assert.assertEquals("[mysql-bin.000002:0000000001#-2,mysql-bin.000001:0000000001#-2]", region);
            verify(canalBootstrap, times(1)).consumeOss(anyString(), anyBoolean());
        }
    }

    @Test
    public void consumeMysqlTestWithDirectConsume() throws Exception {
        try (MockedStatic<SearchMetricsManager> mockedStatic = Mockito.mockStatic(SearchMetricsManager.class)) {
            SearchMetricsManager metricsManager = Mockito.mock(SearchMetricsManager.class);
            mockedStatic.when(() -> SearchMetricsManager.getInstance()).thenReturn(metricsManager);

            CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class);
            BinlogPosition position = new BinlogPosition("mysql-bin.000001", 1, -1, -1);
            when(canalBootstrap.searchPosition(any(MysqlConnection.class), anyString())).thenReturn(position);
            doCallRealMethod().when(canalBootstrap).consumeMysql(any(MysqlConnection.class), anyString());
            doNothing().when(canalBootstrap)
                .consume(any(ErosaConnection.class), any(BinlogPosition.class), anyString());
            MysqlConnection connection = Mockito.mock(MysqlConnection.class);
            when(canalBootstrap.getStorageMasterInstId()).thenReturn("test-inst-id");

            canalBootstrap.consumeMysql(connection, "1651782957000");

            verify(canalBootstrap, times(1)).consume(any(ErosaConnection.class), any(BinlogPosition.class),
                anyString());
            verify(metricsManager, times(1)).startSearch();
            verify(metricsManager, times(1)).stopSearch("test-inst-id");
        }
    }

    @Test
    public void consumeMysqlTestWithOssEndException() throws Exception {
        try (MockedStatic<SearchMetricsManager> mockedStatic = Mockito.mockStatic(SearchMetricsManager.class)) {
            SearchMetricsManager metricsManager = Mockito.mock(SearchMetricsManager.class);
            mockedStatic.when(() -> SearchMetricsManager.getInstance()).thenReturn(metricsManager);

            CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class);
            when(canalBootstrap.searchPosition(any(MysqlConnection.class), anyString())).thenReturn(null);
            doCallRealMethod().when(canalBootstrap).consumeMysql(any(MysqlConnection.class), anyString());
            doThrow(new ConsumeOSSBinlogEndException()).when(canalBootstrap).consumeOss(anyString(), anyBoolean());
            MysqlConnection connection = Mockito.mock(MysqlConnection.class);
            when(canalBootstrap.getStorageMasterInstId()).thenReturn("test-inst-id");
            when(canalBootstrap.consumeMysqlDirect(any(MysqlConnection.class))).thenReturn(false);
            when(canalBootstrap.isRunning()).thenReturn(true);

            Exception t = null;
            try {
                canalBootstrap.consumeMysql(connection, "1651782957000");
            } catch (Exception e) {
                t = e;
            }

            Assert.assertNotNull(t);
            Assert.assertEquals(ConsumeOSSBinlogEndException.class, t.getClass());
            verify(canalBootstrap, times(0)).consume(any(ErosaConnection.class), any(BinlogPosition.class),
                anyString());
            verify(metricsManager, times(1)).startSearch();
            verify(metricsManager, times(0)).stopSearch("test-inst-id");
            verify(canalBootstrap, times(1)).consumeMysqlDirect(any(MysqlConnection.class));
            verify(canalBootstrap, times(1)).stopProcessor();
        }
    }

    @Test
    public void consumeMysqlTestWithOssEndExceptionRedirectSuccess() throws Exception {
        try (MockedStatic<SearchMetricsManager> mockedStatic = Mockito.mockStatic(SearchMetricsManager.class)) {
            SearchMetricsManager metricsManager = Mockito.mock(SearchMetricsManager.class);
            mockedStatic.when(() -> SearchMetricsManager.getInstance()).thenReturn(metricsManager);

            CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class);
            when(canalBootstrap.searchPosition(any(MysqlConnection.class), anyString())).thenReturn(null);
            doCallRealMethod().when(canalBootstrap).consumeMysql(any(MysqlConnection.class), anyString());
            doThrow(new ConsumeOSSBinlogEndException()).when(canalBootstrap).consumeOss(anyString(), anyBoolean());
            MysqlConnection connection = Mockito.mock(MysqlConnection.class);
            when(canalBootstrap.getStorageMasterInstId()).thenReturn("test-inst-id");
            when(canalBootstrap.consumeMysqlDirect(any(MysqlConnection.class))).thenReturn(true);
            when(canalBootstrap.isRunning()).thenReturn(true);

            canalBootstrap.consumeMysql(connection, "1651782957000");

            verify(canalBootstrap, times(0)).consume(any(ErosaConnection.class), any(BinlogPosition.class),
                anyString());
            verify(metricsManager, times(1)).startSearch();
            verify(metricsManager, times(1)).stopSearch("test-inst-id");
            verify(canalBootstrap, times(1)).consumeMysqlDirect(any(MysqlConnection.class));
            verify(canalBootstrap, times(0)).stopProcessor();
        }
    }

    @Test
    public void getStorageMasterInstIdTest() {
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        DnHost dnHost = new DnHost("127.0.0.1", 3306, "test", "test", "utf8", "test-rds");
        authenticationInfo.setLeader(dnHost);
        authenticationInfo.setStorageMasterInstId("test-rds");
        authenticationInfo.switchLeader();
        CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class,
            withSettings().useConstructor(authenticationInfo, "8.0.21", "test", 1L, "test"));
        when(canalBootstrap.getStorageMasterInstId()).thenCallRealMethod();
        Assert.assertEquals("test-rds", canalBootstrap.getStorageMasterInstId());
    }

    @Test
    public void stopProcessorTest() {
        CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class, withSettings());
        BinlogEventProcessor processor = Mockito.mock(BinlogEventProcessor.class);
        when(canalBootstrap.getProcessor()).thenReturn(processor);
        doCallRealMethod().when(canalBootstrap).stopProcessor();
        canalBootstrap.stopProcessor();
        verify(processor, times(1)).stop();
    }

    @Test
    public void resetProcessorTest() {
        CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class, withSettings());
        BinlogEventProcessor processor = Mockito.mock(BinlogEventProcessor.class);
        when(canalBootstrap.getProcessor()).thenReturn(processor);
        doCallRealMethod().when(canalBootstrap).resetProcessorHandle();
        canalBootstrap.resetProcessorHandle();
        verify(processor, times(1)).setHandle(null);
    }

    @Test
    public void consumeMysqlDirectTest() throws Exception {
        try (MockedStatic<DriverManager> mockedStatic = Mockito.mockStatic(DriverManager.class)) {
            Connection conn = Mockito.mock(Connection.class);
            Statement st = Mockito.mock(Statement.class);
            ResultSet serverIdRs = Mockito.mock(ResultSet.class);
            when(serverIdRs.next()).thenReturn(true, false);
            when(serverIdRs.getObject(2)).thenReturn("1");
            when(st.executeQuery("show variables like 'server_id'")).thenReturn(serverIdRs);
            ResultSet showMasterStatusRs = Mockito.mock(ResultSet.class);
            when(showMasterStatusRs.next()).thenReturn(true, false);
            when(showMasterStatusRs.getString(1)).thenReturn("mysql-bin.000001");
            when(showMasterStatusRs.getString(2)).thenReturn("1");
            when(st.executeQuery("show master status")).thenReturn(showMasterStatusRs);
            ResultSet showBinlogEventsRs = Mockito.mock(ResultSet.class);
            when(showBinlogEventsRs.next()).thenReturn(true, false);
            when(showBinlogEventsRs.getString(1)).thenReturn("mysql-bin.000002");
            when(showBinlogEventsRs.getString(2)).thenReturn("1");
            when(st.executeQuery("show binlog events limit 1")).thenReturn(showBinlogEventsRs);
            ResultSet binlogFormat = Mockito.mock(ResultSet.class);
            when(binlogFormat.next()).thenReturn(true, false);
            when(binlogFormat.getString(2)).thenReturn("ROW");
            when(st.executeQuery("show variables like 'binlog_format'")).thenReturn(binlogFormat);
            ResultSet rowImage = Mockito.mock(ResultSet.class);
            when(rowImage.next()).thenReturn(true, false);
            when(rowImage.getString(2)).thenReturn("FULL");
            when(st.executeQuery("show variables like 'binlog_row_image'")).thenReturn(rowImage);
            ResultSet showCharacter = Mockito.mock(ResultSet.class);
            when(showCharacter.next()).thenReturn(true, false);
            when(showCharacter.getString(1)).thenReturn("character_set_client");
            when(showCharacter.getString(2)).thenReturn("utf8");
            when(st.executeQuery("show variables like '%character%'")).thenReturn(showCharacter);
            ResultSet showLowerCaseTableNames = Mockito.mock(ResultSet.class);
            when(showLowerCaseTableNames.next()).thenReturn(true, false);
            when(showLowerCaseTableNames.getObject(2)).thenReturn("1");
            when(st.executeQuery("show variables like 'lower_case_table_names'")).thenReturn(showLowerCaseTableNames);
            ResultSet checksumRs = Mockito.mock(ResultSet.class);
            when(checksumRs.next()).thenReturn(true, false);
            when(checksumRs.getString(1)).thenReturn("CRC32");
            when(st.executeQuery("select @@global.binlog_checksum")).thenReturn(checksumRs);
            ResultSet sqlModeRs = Mockito.mock(ResultSet.class);
            when(sqlModeRs.next()).thenReturn(true, false);
            when(sqlModeRs.getString(1)).thenReturn(
                "STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION");
            when(st.executeQuery("select @@global.sql_mode")).thenReturn(sqlModeRs);
            when(conn.createStatement()).thenReturn(st);
            mockedStatic.when(() -> DriverManager.getConnection(anyString(), any())).thenReturn(conn);
            AuthenticationInfo authenticationInfo = new AuthenticationInfo();
            DnHost dnHost = new DnHost("127.0.0.1", 3306, "test", "test", "utf8", "test-rds");
            authenticationInfo.setLeader(dnHost);
            authenticationInfo.switchLeader();
            CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class,
                withSettings().useConstructor(authenticationInfo, "8.0.21", "test", 1L, "test"));
            doCallRealMethod().when(canalBootstrap).doStart(anyString());
            when(canalBootstrap.getPositionRegion()).thenCallRealMethod();
            doNothing().when(canalBootstrap).consumeOss(anyString(), anyBoolean());
            canalBootstrap.doStart("1651782957000");
            String region = canalBootstrap.getPositionRegion();
            Assert.assertEquals("[mysql-bin.000002:0000000001#-2,mysql-bin.000001:0000000001#-2]", region);
            when(canalBootstrap.consumeMysqlDirect(any())).thenCallRealMethod();
            doCallRealMethod().when(canalBootstrap).doProcessor(any(), any(), anyString(), anyLong());
            when(canalBootstrap.getProcessor()).thenCallRealMethod();
            ISearchTsoEventHandle searchTsoEventHandle = Mockito.mock(ISearchTsoEventHandle.class);
            SearchRecorder searchRecorder = Mockito.mock(SearchRecorder.class);
            canalBootstrap.getProcessor().setHandle(searchTsoEventHandle);
            LogFetcher fetcher = Mockito.mock(LogFetcher.class);
            MysqlConnection mysqlConnection = Mockito.mock(MysqlConnection.class);
            when(mysqlConnection.providerFetcher(anyString(), anyLong(), anyBoolean())).thenReturn(fetcher);
            doCallRealMethod().when(canalBootstrap)
                .consume(any(ErosaConnection.class), any(BinlogPosition.class), anyString());
            canalBootstrap.getProcessor().init(mysqlConnection, "b.2", 100, false, null, null, 0);
            try {
                canalBootstrap.doProcessor(searchTsoEventHandle, searchRecorder, "b.1", 100);
            } catch (Exception ignored) {
            }
            try {
                canalBootstrap.doProcessor(searchTsoEventHandle, searchRecorder, "b.2", 100);
            } catch (Exception ignored) {
            }
            try {
                canalBootstrap.doProcessor(searchTsoEventHandle, searchRecorder, "b.3", 100);
            } catch (Exception ignored) {
            }

            try {
                canalBootstrap.consume(mysqlConnection, new BinlogPosition("b.2", 4, -1, -1), "1651782957000");
            } catch (Exception ignored) {
            }
            DefaultBinlogEventHandle handle1 = (DefaultBinlogEventHandle) canalBootstrap.getProcessor().getHandle();
            handle1.addFilter(new LogEventFilter<LogEvent>() {
                @Override
                public void handle(LogEvent event, HandlerContext context) throws Exception {

                }

                @Override
                public void onStart(HandlerContext context) {

                }

                @Override
                public void onStop() {

                }

                @Override
                public void onStartConsume(HandlerContext context) {

                }
            });

            DefaultBinlogEventHandle handler = Mockito.mock(DefaultBinlogEventHandle.class);
            canalBootstrap.getProcessor().setHandle(handler);
            canalBootstrap.consumeMysqlDirect(mysqlConnection);

            verify(mysqlConnection, times(3)).providerFetcher(anyString(), anyLong(), anyBoolean());
            verify(handler, times(1)).onEnd();

        }

    }

//    @Test
//    public void buildOssConnectionTest(){
//        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
//        DnHost dnHost = new DnHost("127.0.0.1", 3306, "test", "test", "utf8", "test-rds");
//        authenticationInfo.setMaster(dnHost);
//        authenticationInfo.setStorageMasterInstId("test-rds");
//        authenticationInfo.switchMaster();
//        CanalBootstrap canalBootstrap = Mockito.mock(CanalBootstrap.class, withSettings().useConstructor(authenticationInfo, "8.0.21", "test", 1L, "test"));
//
//
//    }
}
