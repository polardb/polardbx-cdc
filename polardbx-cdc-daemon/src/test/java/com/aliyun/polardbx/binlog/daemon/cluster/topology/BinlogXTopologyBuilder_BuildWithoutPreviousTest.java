/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.scheduler.StreamEntity;
import com.aliyun.polardbx.binlog.scheduler.StreamEntitySet;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.Resource;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BinlogXTopologyBuilder_BuildWithoutPreviousTest extends BaseTest {

    @Test
    public void testEvenDistribution() {
        // 准备测试数据
        List<Container> containers = createContainers(3);
        List<XStream> streams = createStreams(6);

        // 创建被测试的对象
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");

        // 调用方法
        Map<String, StreamEntitySet> result = builder.buildContainerStreamMapWithoutPrevious(containers, streams);

        // 验证结果
        assertEquals(3, result.size());
        for (Set<StreamEntity> streamSet : result.values()) {
            assertEquals(2, streamSet.size());
        }

        // 验证所有流都被分配且没有重复
        StreamEntitySet allStreams = new StreamEntitySet();
        for (Set<StreamEntity> streamSet : result.values()) {
            allStreams.addAll(streamSet);
        }
        assertEquals(6, allStreams.size());
        for (int i = 0; i < 6; i++) {
            assertTrue(allStreams.contains(new StreamEntity("stream-" + i)));
        }
    }

    @Test
    public void testUnevenDistribution() {
        // 准备测试数据
        List<Container> containers = createContainers(3);
        List<XStream> streams = createStreams(7);

        // 创建被测试的对象
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");

        // 调用方法
        Map<String, StreamEntitySet> result = builder.buildContainerStreamMapWithoutPrevious(containers, streams);

        // 验证结果
        assertEquals(3, result.size());

        // 验证所有流都被分配且没有重复
        StreamEntitySet allStreams = new StreamEntitySet();
        for (StreamEntitySet streamSet : result.values()) {
            allStreams.addAll(streamSet);
        }
        assertEquals(7, allStreams.size());
        for (int i = 0; i < 7; i++) {
            assertTrue(allStreams.contains(new StreamEntity("stream-" + i)));
        }
    }

    @Test
    public void testMoreContainersThanStreams() {
        // 准备测试数据
        List<Container> containers = createContainers(5);
        List<XStream> streams = createStreams(3);

        // 创建被测试的对象
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");

        // 调用方法
        Map<String, StreamEntitySet> result = builder.buildContainerStreamMapWithoutPrevious(containers, streams);

        // 验证结果：所有容器都应该在结果中
        assertEquals(5, result.size());

        // 验证所有流都被分配且没有重复
        StreamEntitySet allStreams = new StreamEntitySet();
        for (Set<StreamEntity> streamSet : result.values()) {
            allStreams.addAll(streamSet);
        }
        assertEquals(3, allStreams.size());
        for (int i = 0; i < 3; i++) {
            assertTrue(allStreams.contains(new StreamEntity("stream-" + i)));
        }
    }

    @Test
    public void testEmptyStreams() {
        // 准备测试数据
        List<Container> containers = createContainers(3);
        List<XStream> streams = new ArrayList<>();

        // 创建被测试的对象
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");

        // 调用方法
        Map<String, StreamEntitySet> result = builder.buildContainerStreamMapWithoutPrevious(containers, streams);

        // 验证结果
        assertEquals(3, result.size());
        for (Set<StreamEntity> streamSet : result.values()) {
            assertTrue(streamSet.isEmpty());
        }
    }

    @Test
    public void testSingleContainer() {
        // 准备测试数据
        List<Container> containers = createContainers(1);
        List<XStream> streams = createStreams(5);

        // 创建被测试的对象
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");

        // 调用方法
        Map<String, StreamEntitySet> result = builder.buildContainerStreamMapWithoutPrevious(containers, streams);

        // 验证结果
        assertEquals(1, result.size());
        assertEquals(5, result.get("container-0").size());

        // 验证所有流都被分配
        StreamEntitySet allStreams = result.get("container-0");
        for (int i = 0; i < 5; i++) {
            assertTrue(allStreams.contains(new StreamEntity("stream-" + i)));
        }
    }

    private List<Container> createContainers(int count) {
        List<Container> containers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Container container = Container.builder()
                .containerId("container-" + i)
                .capability(new Resource(8, 1024, 0))
                .ip("127.0.0.1")
                .daemonPort(8080)
                .availablePorts(new LinkedList<>(Arrays.asList(9000, 9001, 9002)))
                .build();
            containers.add(container);
        }
        return containers;
    }

    private List<XStream> createStreams(int count) {
        List<XStream> streams = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            XStream stream = new XStream();
            stream.setStreamName("stream-" + i);
            stream.setGroupName("group-" + i);
            streams.add(stream);
        }
        return streams;
    }
}
