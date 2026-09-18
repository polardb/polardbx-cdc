/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;

/**
 * ReplicaMeta 多流字段测试
 */
public class ReplicaMetaTest {

    /**
     * 测试streamName字段的设置和获取
     */
    @Test
    public void testStreamName_SetGet() {
        ReplicaMeta meta = new ReplicaMeta();
        Assert.assertNull(meta.getStreamName());

        meta.setStreamName("stream_0");
        Assert.assertEquals("stream_0", meta.getStreamName());

        meta.setStreamName(null);
        Assert.assertNull(meta.getStreamName());
    }

    /**
     * 测试streamName字段JSON序列化/反序列化
     */
    @Test
    public void testStreamName_JsonRoundTrip() {
        ReplicaMeta meta = new ReplicaMeta();
        meta.setStreamName("stream_test");
        meta.setStreamGroup("group1");
        meta.setMasterHost("127.0.0.1");
        meta.setMasterPort(3306);
        meta.setPosition("binlog.000001:4");

        String json = JSON.toJSONString(meta);
        ReplicaMeta deserialized = JSON.parseObject(json, ReplicaMeta.class);

        Assert.assertEquals("stream_test", deserialized.getStreamName());
        Assert.assertEquals("group1", deserialized.getStreamGroup());
        Assert.assertEquals("127.0.0.1", deserialized.getMasterHost());
        Assert.assertEquals(3306, deserialized.getMasterPort());
        Assert.assertEquals("binlog.000001:4", deserialized.getPosition());
    }

    /**
     * 测试streamName为null时JSON序列化不丢失其他字段
     */
    @Test
    public void testStreamName_NullInJson() {
        ReplicaMeta meta = new ReplicaMeta();
        meta.setStreamGroup("group1");
        // streamName不设置，默认null

        String json = JSON.toJSONString(meta);
        ReplicaMeta deserialized = JSON.parseObject(json, ReplicaMeta.class);

        Assert.assertNull(deserialized.getStreamName());
        Assert.assertEquals("group1", deserialized.getStreamGroup());
    }
}
