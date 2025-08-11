/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

public class DbTaskMetaManagerTest extends BaseTest {

    @Test
    public void listChosenXStreams_ConfigFlagFalse_ReturnsEmptyList() {
        mockConfig(ConfigKeys.RPL_BACK_FLOW_X_STREAM_OPTION, "FALSE");
        List<XStream> result = DbTaskMetaManager.listChosenXStreams();
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void listChosenXStreams_GroupNameExists_ReturnsMatchingXStreams() {
        mockConfig(ConfigKeys.RPL_BACK_FLOW_X_STREAM_OPTION, "TRUE");

        XStreamMapper xStreamMapper = Mockito.mock(XStreamMapper.class);

        List<XStream> xStreams = Arrays.asList(new XStream(), new XStream());
        Mockito.when(xStreamMapper.select(Mockito.any())).thenReturn(xStreams);

        XStreamMapper raw = DbTaskMetaManager.getXStreamMapper();
        DbTaskMetaManager.setXStreamMapper(xStreamMapper);
        List<XStream> result = DbTaskMetaManager.listChosenXStreams();
        Assert.assertEquals(xStreams, result);
        DbTaskMetaManager.setXStreamMapper(raw);
    }
}