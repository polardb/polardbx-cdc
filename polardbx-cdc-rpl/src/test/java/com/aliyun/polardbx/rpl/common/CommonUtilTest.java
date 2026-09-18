/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommonUtilTest {

    @Test
    public void testHostSafeCheck1() {
        CommonUtil.hostSafeCheck("acbdabdasdhkj.com");
    }

    @Test
    public void testHostSafeCheck2() {
        boolean exception = false;
        try {
            CommonUtil.hostSafeCheck("acbdabdasdhkj.com==");
        } catch (Exception e) {
            exception = true;
        }
        assertTrue(exception);
    }

    // ===== getStreamLatestPositions 多流位点查询测试 =====

    /**
     * 测试getStreamLatestPositions - 成功获取多流位点，按group过滤
     */
    @Test
    public void testGetStreamLatestPositions_Success() throws SQLException {
        Connection mockConn = mock(Connection.class);
        Statement mockStmt = mock(Statement.class);
        ResultSet mockRs = mock(ResultSet.class);

        when(mockConn.createStatement()).thenReturn(mockStmt);
        when(mockStmt.executeQuery("show binary streams")).thenReturn(mockRs);

        // 模拟3行数据，其中2行属于目标group
        when(mockRs.next()).thenReturn(true, true, true, false);
        when(mockRs.getString("GROUP")).thenReturn("group1", "group1", "group2");
        when(mockRs.getString("STREAM")).thenReturn("stream_0", "stream_1", "stream_x");
        when(mockRs.getString("FILE")).thenReturn("binlog.000001", "binlog.000002", "binlog.000003");
        when(mockRs.getString("POSITION")).thenReturn("4", "120", "256");

        List<Pair<String, String>> positions = CommonUtil.getStreamLatestPositions(mockConn, "group1");

        assertEquals(2, positions.size());
        // 验证第一个stream
        assertEquals("stream_0", positions.get(0).getLeft());
        assertEquals("binlog.000001:4", positions.get(0).getRight());
        // 验证第二个stream
        assertEquals("stream_1", positions.get(1).getLeft());
        assertEquals("binlog.000002:120", positions.get(1).getRight());
    }

    /**
     * 测试getStreamLatestPositions - 只有一个匹配group的stream
     */
    @Test
    public void testGetStreamLatestPositions_SingleStream() throws SQLException {
        Connection mockConn = mock(Connection.class);
        Statement mockStmt = mock(Statement.class);
        ResultSet mockRs = mock(ResultSet.class);

        when(mockConn.createStatement()).thenReturn(mockStmt);
        when(mockStmt.executeQuery("show binary streams")).thenReturn(mockRs);

        when(mockRs.next()).thenReturn(true, false);
        when(mockRs.getString("GROUP")).thenReturn("mygroup");
        when(mockRs.getString("STREAM")).thenReturn("stream_only");
        when(mockRs.getString("FILE")).thenReturn("mysql-bin.000010");
        when(mockRs.getString("POSITION")).thenReturn("999");

        List<Pair<String, String>> positions = CommonUtil.getStreamLatestPositions(mockConn, "mygroup");

        assertEquals(1, positions.size());
        assertEquals("stream_only", positions.get(0).getLeft());
        assertEquals("mysql-bin.000010:999", positions.get(0).getRight());
    }

    /**
     * 测试getStreamLatestPositions - 没有匹配的group抛出异常
     */
    @Test
    public void testGetStreamLatestPositions_NoMatchingGroup() throws SQLException {
        Connection mockConn = mock(Connection.class);
        Statement mockStmt = mock(Statement.class);
        ResultSet mockRs = mock(ResultSet.class);

        when(mockConn.createStatement()).thenReturn(mockStmt);
        when(mockStmt.executeQuery("show binary streams")).thenReturn(mockRs);

        // 有数据但group不匹配
        when(mockRs.next()).thenReturn(true, false);
        when(mockRs.getString("GROUP")).thenReturn("other_group");

        boolean exceptionThrown = false;
        try {
            CommonUtil.getStreamLatestPositions(mockConn, "target_group");
        } catch (PolardbxException e) {
            assertTrue(e.getMessage().contains("can not find position for this stream"));
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    /**
     * 测试getStreamLatestPositions - 空结果集抛出异常
     */
    @Test
    public void testGetStreamLatestPositions_EmptyResultSet() throws SQLException {
        Connection mockConn = mock(Connection.class);
        Statement mockStmt = mock(Statement.class);
        ResultSet mockRs = mock(ResultSet.class);

        when(mockConn.createStatement()).thenReturn(mockStmt);
        when(mockStmt.executeQuery("show binary streams")).thenReturn(mockRs);
        when(mockRs.next()).thenReturn(false);

        boolean exceptionThrown = false;
        try {
            CommonUtil.getStreamLatestPositions(mockConn, "any_group");
        } catch (PolardbxException e) {
            assertTrue(e.getMessage().contains("can not find position for this stream"));
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    /**
     * 测试getStreamLatestPositions - SQL异常包装为PolardbxException
     */
    @Test(expected = PolardbxException.class)
    public void testGetStreamLatestPositions_SQLException() throws SQLException {
        Connection mockConn = mock(Connection.class);
        when(mockConn.createStatement()).thenThrow(new SQLException("connection failed"));

        CommonUtil.getStreamLatestPositions(mockConn, "any_group");
    }
}
