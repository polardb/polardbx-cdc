/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.core.AbstractEventParser;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.TimerTask;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
public class MysqlEventParserTest extends BaseTest {
    @Test
    @SneakyThrows
    public void testCheckPosition() {
        try (MockedStatic<LabEventManager> labEventManagerMockedStatic = Mockito.mockStatic(LabEventManager.class)) {
            MysqlEventParser parser = new MysqlEventParser(1024, null);
            LogEventConvert logEventConvert = Mockito.mock(LogEventConvert.class);
            LogEvent logEvent = Mockito.mock(LogEvent.class);
            LogHeader logHeader = Mockito.mock(LogHeader.class);
            Mockito.when(logEvent.getHeader()).thenReturn(logHeader);
            Mockito.when(logEvent.getLogPos()).thenReturn(125L);
            Mockito.when(logEventConvert.getBinlogFileName()).thenReturn("b.1");
            parser.setBinlogParser(logEventConvert);

            // 第一次check，还没有初始化lastFile和lastPosition
            parser.checkPosition(logEvent);
            parser.setLastFile("b.1");
            parser.setLastPosition(4);
            // 正确的lastPosition < logEvent.pos，检查通过
            parser.checkPosition(logEvent);
            // rotate event 不检查
            Mockito.when(logHeader.getType()).thenReturn(LogEvent.ROTATE_EVENT);
            parser.checkPosition(logEvent);
            // 错误的lastPosition > logEvent.pos，检查失败
            Mockito.when(logHeader.getType()).thenReturn(LogEvent.UPDATE_ROWS_EVENT);
            parser.setLastPosition(127);
            parser.checkPosition(logEvent);
            labEventManagerMockedStatic.verify(() -> LabEventManager.logEvent(any(), any()), Mockito.times(1));
        }
    }

    /**
     * 测试buildHeartBeatTimeTask方法 - 当connection是MysqlConnection且detectingEnable为true时
     */
    @Test
    public void testBuildHeartBeatTimeTask_MysqlConnectionWithDetectingEnable() {
        // 准备测试数据
        MysqlEventParser parser = new MysqlEventParser(1024, null);
        parser.setDetectingEnable(true);
        parser.setCreateHeartbeatTable(true);

        MysqlConnection mockConnection = mock(MysqlConnection.class);
        MysqlConnection mockForkedConnection = mock(MysqlConnection.class);
        when(mockConnection.fork()).thenReturn(mockForkedConnection);

        // 调用被测试的方法
        TimerTask result = parser.buildHeartBeatTimeTask(mockConnection);

        // 验证结果
        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof MysqlDetectingTimeTask);

        // 验证fork方法被调用
        verify(mockConnection, times(1)).fork();
    }

    /**
     * 测试buildHeartBeatTimeTask方法 - 当connection是MysqlConnection且detectingEnable为true时
     */
    @Test
    public void testBuildHeartBeatTimeTask_withServerId() throws NoSuchFieldException, IllegalAccessException {
        // 准备测试数据
        MysqlEventParser parser = new MysqlEventParser(1024, null);
        String serverId = "1818";
        parser.setDetectingEnable(true);
        parser.setCreateHeartbeatTable(true);
        parser.setWriteServerId(serverId);

        MysqlConnection mockConnection = mock(MysqlConnection.class);
        MysqlConnection mockForkedConnection = mock(MysqlConnection.class);
        when(mockConnection.fork()).thenReturn(mockForkedConnection);

        // 调用被测试的方法
        TimerTask result = parser.buildHeartBeatTimeTask(mockConnection);

        // 验证结果
        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof MysqlDetectingTimeTask);
        MysqlDetectingTimeTask detectingTimeTask = (MysqlDetectingTimeTask) result;
        Field serverIdField = MysqlDetectingTimeTask.class.getDeclaredField("serverId");
        serverIdField.setAccessible(true);
        Assert.assertEquals(serverId, serverIdField.get(detectingTimeTask));
        // 验证fork方法被调用
        verify(mockConnection, times(1)).fork();
    }

    @Test
    public void testBuildHeartBeatTimeTask_withNull() throws NoSuchFieldException, IllegalAccessException {
        // 准备测试数据
        MysqlEventParser parser = new MysqlEventParser(1024, null);
        String serverId = "1818";
        parser.setDetectingEnable(false);
        parser.setCreateHeartbeatTable(true);
        parser.setWriteServerId(serverId);

        MysqlConnection mockConnection = mock(MysqlConnection.class);
        MysqlConnection mockForkedConnection = mock(MysqlConnection.class);
        when(mockConnection.fork()).thenReturn(mockForkedConnection);

        // 调用被测试的方法
        TimerTask result = parser.buildHeartBeatTimeTask(mockConnection);

        // 验证结果
        Assert.assertNull(result);
        // 验证fork方法被调用
        verify(mockConnection, times(0)).fork();
    }

    /**
     * 测试buildHeartBeatTimeTask方法 - 当connection不是MysqlConnection时抛出异常
     */
    @Test(expected = PolardbxException.class)
    public void testBuildHeartBeatTimeTask_NonMysqlConnection() {
        // 准备测试数据
        MysqlEventParser parser = new MysqlEventParser(1024, null);

        ErosaConnection mockConnection = mock(ErosaConnection.class);

        // 调用被测试的方法，期望抛出异常
        parser.buildHeartBeatTimeTask(mockConnection);
    }

    /**
     * 测试stopHeartBeat方法 - 当heartBeatTimerTask是MysqlDetectingTimeTask时
     */
    @Test
    public void testStopHeartBeat_WithMysqlDetectingTimeTask() throws Exception {
        // 准备测试数据
        MysqlEventParser parser = new MysqlEventParser(1024, null);

        MysqlConnection mockConnection = mock(MysqlConnection.class);
        MysqlDetectingTimeTask mockTimerTask = mock(MysqlDetectingTimeTask.class);
        when(mockTimerTask.getMysqlConnection()).thenReturn(mockConnection);
        doNothing().when(mockConnection).disconnect();

        // 设置parser的heartBeatTimerTask字段
        // 使用反射来设置私有字段
        java.lang.reflect.Field heartBeatTimerTaskField =
            AbstractEventParser.class.getDeclaredField("heartBeatTimerTask");
        heartBeatTimerTaskField.setAccessible(true);
        heartBeatTimerTaskField.set(parser, mockTimerTask);

        // 调用被测试的方法
        parser.stopHeartBeat();

        // 验证mysqlConnection的disconnect方法被调用
        verify(mockConnection, times(1)).disconnect();
    }

    /**
     * 测试stopHeartBeat方法 - 当heartBeatTimerTask不是MysqlDetectingTimeTask时
     */
    @Test
    public void testStopHeartBeat_WithNonMysqlDetectingTimeTask() throws Exception {
        // 准备测试数据
        MysqlEventParser parser = new MysqlEventParser(1024, null);

        TimerTask mockTimerTask = mock(TimerTask.class);

        // 设置parser的heartBeatTimerTask字段
        // 使用反射来设置私有字段
        java.lang.reflect.Field heartBeatTimerTaskField =
            AbstractEventParser.class.getDeclaredField("heartBeatTimerTask");
        heartBeatTimerTaskField.setAccessible(true);
        heartBeatTimerTaskField.set(parser, mockTimerTask);

        // 调用被测试的方法
        parser.stopHeartBeat();

        // 验证没有异常抛出
        // disconnect方法不应该被调用
    }

    /**
     * 测试stopHeartBeat方法 - 当heartBeatTimerTask为null时
     */
    @Test
    public void testStopHeartBeat_WithNullTimerTask() throws NoSuchFieldException, IllegalAccessException, IOException {
        // 准备测试数据
        MysqlEventParser parser = new MysqlEventParser(1024, null);

        // heartBeatTimerTask默认为null
        Field field = AbstractEventParser.class.getDeclaredField("running");
        field.setAccessible(true);
        field.set(parser, true);

        Field parseThreadField = AbstractEventParser.class.getDeclaredField("parseThread");
        parseThreadField.setAccessible(true);
        // 使用真实的未启动Thread替代mock(Thread.class)
        // JDK11模块系统限制下，mock(Thread.class)会导致ByteBuddy访问java.base模块失败，引发JVM崩溃
        Thread t = new Thread(() -> {
        });
        parseThreadField.set(parser, t);

        Field heartBeatTimerTaskField = AbstractEventParser.class.getDeclaredField("heartBeatTimerTask");
        heartBeatTimerTaskField.setAccessible(true);
        MysqlDetectingTimeTask tt = mock(MysqlDetectingTimeTask.class);
        MysqlConnection connection = mock(MysqlConnection.class);
        when(tt.getMysqlConnection()).thenReturn(connection);
        heartBeatTimerTaskField.set(parser, tt);
        // 调用被测试的方法
        parser.stop();

        // 验证没有异常抛出
        verify(tt, times(1)).getMysqlConnection();
        verify(connection, times(1)).disconnect();
    }

    /**
     * 测试MysqlEventParser类中大约576行的代码逻辑：
     * MysqlDetectingTimeTask mysqlConnection = ((MysqlDetectingTimeTask) heartBeatTimerTask).getMysqlConnection();
     */
    @Test
    public void testStopHeartBeat_Line576() throws Exception {
        // 准备测试数据
        MysqlEventParser parser = new MysqlEventParser(1024, null);

        // 创建MysqlDetectingTimeTask mock对象
        MysqlDetectingTimeTask mockTimerTask = mock(MysqlDetectingTimeTask.class);
        MysqlConnection mockConnection = mock(MysqlConnection.class);

        // 设置mock行为
        when(mockTimerTask.getMysqlConnection()).thenReturn(mockConnection);

        // 使用反射设置heartBeatTimerTask字段
        Field heartBeatTimerTaskField = AbstractEventParser.class.getDeclaredField("heartBeatTimerTask");
        heartBeatTimerTaskField.setAccessible(true);
        heartBeatTimerTaskField.set(parser, mockTimerTask);

        // 执行测试 - 这里验证的是代码行576的逻辑是否能正常执行
        parser.stopHeartBeat();

        // 验证getMysqlConnection方法被调用
        verify(mockTimerTask, times(1)).getMysqlConnection();
    }

    /**
     * 测试MysqlEventParser类中大约581行的代码逻辑：
     * mysqlConnection.disconnect();
     */
    @Test
    public void testStopHeartBeat_Line581() throws Exception {
        // 准备测试数据
        MysqlEventParser parser = new MysqlEventParser(1024, null);

        // 创建MysqlDetectingTimeTask mock对象
        MysqlDetectingTimeTask mockTimerTask = mock(MysqlDetectingTimeTask.class);
        MysqlConnection mockConnection = mock(MysqlConnection.class);

        // 设置mock行为
        when(mockTimerTask.getMysqlConnection()).thenReturn(mockConnection);
        doNothing().when(mockConnection).disconnect(); // 确保disconnect不会抛出异常

        // 使用反射设置heartBeatTimerTask字段
        Field heartBeatTimerTaskField = AbstractEventParser.class.getDeclaredField("heartBeatTimerTask");
        heartBeatTimerTaskField.setAccessible(true);
        heartBeatTimerTaskField.set(parser, mockTimerTask);

        // 执行测试
        parser.stopHeartBeat();

        // 验证disconnect方法被调用（对应代码行581）
        verify(mockConnection, times(1)).disconnect();
    }

    /**
     * 完整测试stopHeartBeat方法的功能
     */
    @Test
    public void testStopHeartBeat_FullMethod() throws Exception {
        // 准备测试数据
        MysqlEventParser parser = new MysqlEventParser(1024, null);

        // 创建MysqlDetectingTimeTask mock对象
        MysqlDetectingTimeTask mockTimerTask = mock(MysqlDetectingTimeTask.class);
        MysqlConnection mockConnection = mock(MysqlConnection.class);

        // 设置mock行为
        when(mockTimerTask.getMysqlConnection()).thenReturn(mockConnection);
        doThrow(new IOException()).when(mockConnection).disconnect();

        // 使用反射设置heartBeatTimerTask字段
        Field heartBeatTimerTaskField = AbstractEventParser.class.getDeclaredField("heartBeatTimerTask");
        heartBeatTimerTaskField.setAccessible(true);
        heartBeatTimerTaskField.set(parser, mockTimerTask);

        // 执行测试
        parser.stopHeartBeat();

        // 验证整个流程
        verify(mockTimerTask, times(1)).getMysqlConnection();
        verify(mockConnection, times(1)).disconnect();
    }

    // ===== 多流Binlog位点查找适配测试 =====

    /**
     * 测试getStreamName - 设置了streamName时返回对应值
     */
    @Test
    public void testGetStreamName_WithStreamName() {
        try {
            System.setProperty(ConfigKeys.STREAM_NAME, "stream_0");
            MysqlEventParser parser = new MysqlEventParser(1024, null);
            Assert.assertEquals("stream_0", parser.getStreamName());
        } finally {
            System.clearProperty(ConfigKeys.STREAM_NAME);
        }
    }

    /**
     * 测试getStreamName - 空白streamName返回null
     */
    @Test
    public void testGetStreamName_WithBlankStreamName() {
        try {
            System.setProperty(ConfigKeys.STREAM_NAME, "  ");
            MysqlEventParser parser = new MysqlEventParser(1024, null);
            Assert.assertNull(parser.getStreamName());
        } finally {
            System.clearProperty(ConfigKeys.STREAM_NAME);
        }
    }

    /**
     * 测试getStreamName - 空字符串返回null
     */
    @Test
    public void testGetStreamName_WithEmptyStreamName() {
        try {
            System.setProperty(ConfigKeys.STREAM_NAME, "");
            MysqlEventParser parser = new MysqlEventParser(1024, null);
            Assert.assertNull(parser.getStreamName());
        } finally {
            System.clearProperty(ConfigKeys.STREAM_NAME);
        }
    }

    /**
     * 测试getStreamName - 未设置streamName返回null
     */
    @Test
    public void testGetStreamName_WithoutStreamName() {
        System.clearProperty(ConfigKeys.STREAM_NAME);
        MysqlEventParser parser = new MysqlEventParser(1024, null);
        Assert.assertNull(parser.getStreamName());
    }

    /**
     * 测试findEndPosition - 多流场景，成功获取位点
     */
    @Test
    @SneakyThrows
    public void testFindEndPosition_WithStreamName_Success() {
        try {
            System.setProperty(ConfigKeys.STREAM_NAME, "stream_0");
            MysqlEventParser parser = new MysqlEventParser(1024, null);

            MysqlConnection mockConn = mock(MysqlConnection.class);
            ResultSet mockRs = mock(ResultSet.class);
            when(mockRs.next()).thenReturn(true);
            when(mockRs.getString(1)).thenReturn("binlog.000001");
            when(mockRs.getString(2)).thenReturn("4");

            when(mockConn.query(eq("show master status with 'stream_0'"), any(MysqlConnection.ProcessJdbcResult.class)))
                .thenAnswer(invocation -> {
                    MysqlConnection.ProcessJdbcResult<BinlogPosition> processor = invocation.getArgument(1);
                    return processor.process(mockRs);
                });

            BinlogPosition position = parser.findEndPosition(mockConn);
            Assert.assertNotNull(position);
            Assert.assertEquals("binlog.000001", position.getFileName());
            Assert.assertEquals(4L, position.getPosition());

            verify(mockConn).query(eq("show master status with 'stream_0'"), any());
        } finally {
            System.clearProperty(ConfigKeys.STREAM_NAME);
        }
    }

    /**
     * 测试findEndPosition - 多流场景，结果集为空抛出异常
     */
    @Test(expected = CanalParseException.class)
    @SneakyThrows
    public void testFindEndPosition_WithStreamName_EmptyResult() {
        try {
            System.setProperty(ConfigKeys.STREAM_NAME, "stream_0");
            MysqlEventParser parser = new MysqlEventParser(1024, null);

            MysqlConnection mockConn = mock(MysqlConnection.class);
            ResultSet mockRs = mock(ResultSet.class);
            when(mockRs.next()).thenReturn(false);

            when(mockConn.query(eq("show master status with 'stream_0'"), any(MysqlConnection.ProcessJdbcResult.class)))
                .thenAnswer(invocation -> {
                    MysqlConnection.ProcessJdbcResult<BinlogPosition> processor = invocation.getArgument(1);
                    return processor.process(mockRs);
                });

            parser.findEndPosition(mockConn);
        } finally {
            System.clearProperty(ConfigKeys.STREAM_NAME);
        }
    }

    /**
     * 测试findEndPosition - 非多流场景，委派给父类（使用show master status）
     */
    @Test
    @SneakyThrows
    public void testFindEndPosition_WithoutStreamName() {
        System.clearProperty(ConfigKeys.STREAM_NAME);
        MysqlEventParser parser = new MysqlEventParser(1024, null);

        MysqlConnection mockConn = mock(MysqlConnection.class);
        ResultSet mockRs = mock(ResultSet.class);
        when(mockRs.next()).thenReturn(true);
        when(mockRs.getString(1)).thenReturn("binlog.000001");
        when(mockRs.getString(2)).thenReturn("4");

        when(mockConn.query(eq("show master status"), any(MysqlConnection.ProcessJdbcResult.class)))
            .thenAnswer(invocation -> {
                MysqlConnection.ProcessJdbcResult<BinlogPosition> processor = invocation.getArgument(1);
                return processor.process(mockRs);
            });

        BinlogPosition position = parser.findEndPosition(mockConn);
        Assert.assertNotNull(position);
        Assert.assertEquals("binlog.000001", position.getFileName());
        Assert.assertEquals(4L, position.getPosition());

        // 验证使用的是父类的SQL（非多流SQL）
        verify(mockConn).query(eq("show master status"), any());
    }

    /**
     * 测试findStartPosition - 多流场景，成功获取位点
     */
    @Test
    @SneakyThrows
    public void testFindStartPosition_WithStreamName_Success() {
        try {
            System.setProperty(ConfigKeys.STREAM_NAME, "stream_0");
            MysqlEventParser parser = new MysqlEventParser(1024, null);

            MysqlConnection mockConn = mock(MysqlConnection.class);
            ResultSet mockRs = mock(ResultSet.class);
            when(mockRs.next()).thenReturn(true);
            when(mockRs.getString(1)).thenReturn("binlog.000001");
            when(mockRs.getString(2)).thenReturn("4");

            when(mockConn.query(eq("show binlog events with 'stream_0' limit 1"),
                any(MysqlConnection.ProcessJdbcResult.class)))
                .thenAnswer(invocation -> {
                    MysqlConnection.ProcessJdbcResult<BinlogPosition> processor = invocation.getArgument(1);
                    return processor.process(mockRs);
                });

            BinlogPosition position = parser.findStartPosition(mockConn);
            Assert.assertNotNull(position);
            Assert.assertEquals("binlog.000001", position.getFileName());
            Assert.assertEquals(4L, position.getPosition());

            verify(mockConn).query(eq("show binlog events with 'stream_0' limit 1"), any());
        } finally {
            System.clearProperty(ConfigKeys.STREAM_NAME);
        }
    }

    /**
     * 测试findStartPosition - 多流场景，结果集为空抛出异常
     */
    @Test(expected = CanalParseException.class)
    @SneakyThrows
    public void testFindStartPosition_WithStreamName_EmptyResult() {
        try {
            System.setProperty(ConfigKeys.STREAM_NAME, "stream_0");
            MysqlEventParser parser = new MysqlEventParser(1024, null);

            MysqlConnection mockConn = mock(MysqlConnection.class);
            ResultSet mockRs = mock(ResultSet.class);
            when(mockRs.next()).thenReturn(false);

            when(mockConn.query(eq("show binlog events with 'stream_0' limit 1"),
                any(MysqlConnection.ProcessJdbcResult.class)))
                .thenAnswer(invocation -> {
                    MysqlConnection.ProcessJdbcResult<BinlogPosition> processor = invocation.getArgument(1);
                    return processor.process(mockRs);
                });

            parser.findStartPosition(mockConn);
        } finally {
            System.clearProperty(ConfigKeys.STREAM_NAME);
        }
    }

    /**
     * 测试findStartPosition - 非多流场景，委派给父类（使用show binlog events limit 1）
     */
    @Test
    @SneakyThrows
    public void testFindStartPosition_WithoutStreamName() {
        System.clearProperty(ConfigKeys.STREAM_NAME);
        MysqlEventParser parser = new MysqlEventParser(1024, null);

        MysqlConnection mockConn = mock(MysqlConnection.class);
        ResultSet mockRs = mock(ResultSet.class);
        when(mockRs.next()).thenReturn(true);
        when(mockRs.getString(1)).thenReturn("binlog.000001");
        when(mockRs.getString(2)).thenReturn("4");

        when(mockConn.query(eq("show binlog events limit 1"), any(MysqlConnection.ProcessJdbcResult.class)))
            .thenAnswer(invocation -> {
                MysqlConnection.ProcessJdbcResult<BinlogPosition> processor = invocation.getArgument(1);
                return processor.process(mockRs);
            });

        BinlogPosition position = parser.findStartPosition(mockConn);
        Assert.assertNotNull(position);
        Assert.assertEquals("binlog.000001", position.getFileName());
        Assert.assertEquals(4L, position.getPosition());

        verify(mockConn).query(eq("show binlog events limit 1"), any());
    }
}