/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.flashback;

import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.TimerTask;

import static org.mockito.Mockito.mock;

/**
 * LocalBinlogEventParser 单元测试。
 * 重点保证本地消费 binlog 运行期间不会因心跳检测抛出
 * "Unsupported connection type : LocalBinLogConnection" 异常。
 */
public class LocalBinlogEventParserTest extends BaseTest {

    /**
     * 验证：使用 LocalBinLogConnection 时 buildHeartBeatTimeTask 返回 null，
     * 不再抛出父类 MysqlEventParser 的 PolardbxException，
     * 从而保证本地 binlog 消费运行期间不会因心跳检测报错。
     */
    @Test
    public void testBuildHeartBeatTimeTask_LocalBinLogConnection_NoException() {
        LocalBinlogEventParser parser = new LocalBinlogEventParser(1024, false, null, false, null);
        LocalBinLogConnection connection =
            new LocalBinLogConnection("test", new ArrayList<>(), false, null, 123);

        TimerTask task = parser.buildHeartBeatTimeTask(connection);

        Assert.assertNull(task);
    }

    /**
     * 验证：即使开启了 detectingEnable，本地 binlog 场景下 buildHeartBeatTimeTask 仍返回 null，
     * 不会尝试将 LocalBinLogConnection 强转为 MysqlConnection 而报错。
     */
    @Test
    public void testBuildHeartBeatTimeTask_WithDetectingEnable_NoException() {
        LocalBinlogEventParser parser = new LocalBinlogEventParser(1024, false, null, false, null);
        parser.setDetectingEnable(true);
        parser.setCreateHeartbeatTable(true);
        LocalBinLogConnection connection =
            new LocalBinLogConnection("test", new ArrayList<>(), false, null, 123);

        TimerTask task = parser.buildHeartBeatTimeTask(connection);

        Assert.assertNull(task);
    }

    /**
     * 验证：传入普通 ErosaConnection（非 MysqlConnection）时，子类重写后同样不抛异常，始终返回 null。
     */
    @Test
    public void testBuildHeartBeatTimeTask_GenericErosaConnection_NoException() {
        LocalBinlogEventParser parser = new LocalBinlogEventParser(1024, false, null, false, null);
        ErosaConnection connection = mock(ErosaConnection.class);

        TimerTask task = parser.buildHeartBeatTimeTask(connection);

        Assert.assertNull(task);
    }
}
