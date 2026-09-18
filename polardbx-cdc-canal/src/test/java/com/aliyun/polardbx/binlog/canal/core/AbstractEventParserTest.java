/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core;

import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.gtid.GTIDSet;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * AbstractEventParser测试类
 */
public class AbstractEventParserTest extends BaseTest {

    private TestableAbstractEventParser eventParser;

    @Before
    public void setUp() {
        eventParser = new TestableAbstractEventParser();
    }

    /**
     * 测试startHeartBeat方法 - 验证第181行调用startHeartBeat的逻辑
     */
    @Test
    public void testStartHeartBeatInvocation() {
        // 创建一个mock的连接对象
        ErosaConnection mockConnection = new MockErosaConnection();

        // 调用startHeartBeat方法，这会触发timer初始化（第181行附近的逻辑）
        eventParser.testStartHeartBeat(mockConnection);

        // 验证timer已经被初始化
        Assert.assertNotNull("Timer should be initialized after startHeartBeat", eventParser.getTimer());
    }

    /**
     * 测试stop方法中调用stopHeartBeat - 验证第324行的逻辑
     */
    @Test
    public void testStopHeartBeatInvocation() {
        // 创建一个mock的连接对象
        ErosaConnection mockConnection = new MockErosaConnection();

        // 先调用startHeartBeat初始化timer
        eventParser.testStartHeartBeat(mockConnection);

        // 验证timer已经被初始化
        Assert.assertNotNull("Timer should be initialized after startHeartBeat", eventParser.getTimer());

        // 调用testStopHeartBeat方法，这会触发stopHeartBeat调用（第324行附近的逻辑）
        eventParser.testStopHeartBeat();

        // 验证timer已经被清理
        Assert.assertNull("Timer should be null after stopHeartBeat", eventParser.getTimer());
        Assert.assertNull("HeartBeatTimerTask should be null after stopHeartBeat", eventParser.getHeartBeatTimerTask());
    }

    /**
     * 测试startHeartBeat方法中timer.schedule调用 - 验证第390行的逻辑
     */
    @Test
    public void testTimerScheduleInvocation() {
        // 创建一个mock的连接对象
        ErosaConnection mockConnection = new MockErosaConnection();

        // 设置eventParser的属性
        eventParser.setTimer(new Timer("testTimer", true));
        eventParser.setHeartBeatTimerTask(new TimerTask() {
            @Override
            public void run() {
                // 空实现
            }
        });
        eventParser.setDetectingIntervalInSeconds(1); // 设置为1秒

        // 调用startHeartBeat方法，这会触发timer.schedule调用（第390行）
        eventParser.testStartHeartBeat(mockConnection);

        // 验证timer不为null
        Assert.assertNotNull("Timer should not be null", eventParser.getTimer());
    }

    /**
     * 测试stopHeartBeat方法 - 验证方法的完整逻辑（第399-406行）
     */
    @Test
    public void testStopHeartBeat() {
        // 设置eventParser的属性
        eventParser.setTimer(new Timer("testTimer", true));
        eventParser.setHeartBeatTimerTask(new TimerTask() {
            @Override
            public void run() {
                // 空实现
            }
        });
        eventParser.setLastEntryTime(1000L);

        // 调用stopHeartBeat方法
        eventParser.testStopHeartBeat();

        // 验证属性被正确重置
        Assert.assertEquals("lastEntryTime should be reset to 0", 0L, eventParser.getLastEntryTime());
        Assert.assertNull("Timer should be null after stopHeartBeat", eventParser.getTimer());
        Assert.assertNull("HeartBeatTimerTask should be null after stopHeartBeat", eventParser.getHeartBeatTimerTask());
    }

    /**
     * 可测试的AbstractEventParser子类
     */
    private static class TestableAbstractEventParser extends AbstractEventParser {

        @Override
        protected ErosaConnection buildErosaConnection() {
            return new MockErosaConnection();
        }

        @Override
        protected BinlogPosition findStartPosition(ErosaConnection connection, BinlogPosition position) {
            return position;
        }

        // 暴露protected方法用于测试
        public void testStartHeartBeat(ErosaConnection connection) {
            startHeartBeat(connection);
        }

        public void testStopHeartBeat() {
            stopHeartBeat();
        }

        // 提供getter和setter方法用于测试
        public Timer getTimer() {
            return timer;
        }

        public void setTimer(Timer timer) {
            this.timer = timer;
        }

        public TimerTask getHeartBeatTimerTask() {
            return heartBeatTimerTask;
        }

        public void setHeartBeatTimerTask(TimerTask heartBeatTimerTask) {
            this.heartBeatTimerTask = heartBeatTimerTask;
        }

        public long getLastEntryTime() {
            return lastEntryTime;
        }

        public void setLastEntryTime(long lastEntryTime) {
            this.lastEntryTime = lastEntryTime;
        }

        public void setDetectingIntervalInSeconds(Integer detectingIntervalInSeconds) {
            this.detectingIntervalInSeconds = detectingIntervalInSeconds;
        }

        @Override
        protected TimerTask buildHeartBeatTimeTask(ErosaConnection connection) {
            return new TimerTask() {
                @Override
                public void run() {
                    // 空实现
                }
            };
        }
    }

    /**
     * 简单的ErosaConnection模拟实现
     */
    private static class MockErosaConnection implements ErosaConnection {
        @Override
        public void connect() throws IOException {
            // 空实现
        }

        @Override
        public void reconnect() throws IOException {
            // 空实现
        }

        @Override
        public void disconnect() throws IOException {
            // 空实现
        }

        @Override
        public void seek(String binlogfilename, Long binlogPosition, SinkFunction func) throws Exception {
            // 空实现
        }

        @Override
        public void dump(String binlogfilename, Long binlogPosition, Long startTimestampMills, SinkFunction func)
            throws Exception {
            // 空实现
        }

        @Override
        public void dump(long timestamp, SinkFunction func) throws Exception {
            // 空实现
        }

        @Override
        public void dump(GTIDSet gtidSet, SinkFunction func) throws Exception {
            // 空实现
        }

        @Override
        public ErosaConnection fork() {
            return new MockErosaConnection();
        }

        @Override
        public LogFetcher providerFetcher(String binlogfilename, long binlogPosition, boolean search)
            throws IOException {
            return null;
        }

        @Override
        public BinlogPosition findEndPosition(Long tso) {
            return null;
        }

        @Override
        public long binlogFileSize(String searchFileName) throws IOException {
            return 0;
        }

        @Override
        public String preFileName(String currentFileName) {
            return null;
        }

        @Override
        public List<String> binlogList() {
            return new ArrayList<>();
        }
    }
}