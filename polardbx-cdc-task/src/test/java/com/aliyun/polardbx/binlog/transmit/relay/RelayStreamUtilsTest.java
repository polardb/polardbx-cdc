/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RelayStreamUtilsTest extends BaseTest {

    private XStreamMapper xStreamMapper;

    @Before
    public void setUp() {
        // Mock XStreamMapper
        xStreamMapper = mock(XStreamMapper.class);
        registerSpringObject("XStreamMapper", xStreamMapper);

        // Mock configuration values
        mockConfig(ConfigKeys.BINLOGX_STREAM_GROUP_NAME, "test_group");
        mockConfig(ConfigKeys.BINLOGX_STREAM_COUNT, "2");
    }

    @Test
    public void testGetStreamListAndCheck_Success() {
        // Given
        XStream stream1 = new XStream();
        stream1.setStreamName("stream1");

        XStream stream2 = new XStream();
        stream2.setStreamName("stream2");

        List<XStream> mockStreams = Arrays.asList(stream1, stream2);
        when(xStreamMapper.select(any())).thenReturn(mockStreams);

        // When
        List<String> result = RelayStreamUtils.getStreamListAndCheck();

        // Then
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("stream1", result.get(0));
        Assert.assertEquals("stream2", result.get(1));
    }

    @Test(expected = PolardbxException.class)
    public void testGetStreamListAndCheck_MismatchCount() {
        // Given
        XStream stream1 = new XStream();
        stream1.setStreamName("stream1");

        List<XStream> mockStreams = Arrays.asList(stream1); // Only 1 stream
        when(xStreamMapper.select(any())).thenReturn(mockStreams);

        // When
        RelayStreamUtils.getStreamListAndCheck();

        // Then - Exception should be thrown
    }

    @Test
    public void testGetStreamListAndCheck_EmptyList() {
        // Given
        List<XStream> mockStreams = Arrays.asList(); // Empty list
        when(xStreamMapper.select(any())).thenReturn(mockStreams);

        // When & Then
        try {
            RelayStreamUtils.getStreamListAndCheck();
            Assert.fail("Expected PolardbxException to be thrown");
        } catch (PolardbxException e) {
            Assert.assertTrue(e.getMessage().contains("find mismatched stream count"));
            Assert.assertTrue(e.getMessage().contains("configuration count is 2"));
            Assert.assertTrue(e.getMessage().contains("count in binlog_x_stream table is 0"));
        }
    }
}