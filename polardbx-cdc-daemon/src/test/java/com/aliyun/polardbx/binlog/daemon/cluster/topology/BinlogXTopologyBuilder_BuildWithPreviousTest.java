/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.StreamEntity;
import com.aliyun.polardbx.binlog.scheduler.StreamEntitySet;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.Resource;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BinlogXTopologyBuilder_BuildWithPreviousTest extends BaseTest {
    @Test
    public void testBuildContainerStreamMapWithPrevious_AddContainer() {
        // 准备数据
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");
        ClusterSnapshot preClusterSnapshot = new ClusterSnapshot();

        // 创建初始映射：container1 -> {stream1, stream2}
        Map<String, StreamEntitySet> previousMap = new HashMap<>();
        StreamEntitySet streams1 = new StreamEntitySet();
        streams1.add(new StreamEntity("stream1"));
        streams1.add(new StreamEntity("stream2"));
        previousMap.put("container1", streams1);
        preClusterSnapshot.setContainerStreamMap(previousMap);

        // 当前容器列表：增加了container2
        List<Container> currentContainers = new ArrayList<>();
        currentContainers.add(createContainer("container1"));
        currentContainers.add(createContainer("container2"));

        // 当前流列表：和之前一样
        List<XStream> currentStreams = new ArrayList<>();
        currentStreams.add(createXStream("stream1"));
        currentStreams.add(createXStream("stream2"));

        // 执行测试方法
        Map<String, StreamEntitySet> result = builder.buildContainerStreamMapWithPrevious(
            preClusterSnapshot, currentContainers, currentStreams);

        // 验证结果
        assertEquals(2, result.size());
        assertEquals(1, result.get("container1").size());
        assertEquals(1, result.get("container2").size());
        // container1 应该保留其原有的流
        if (result.get("container1").contains(new StreamEntity("stream1"))) {
            assertTrue(result.get("container2").contains(new StreamEntity("stream2")));
        } else {
            assertTrue(result.get("container2").contains(new StreamEntity("stream1")));
        }
        // 新增的 container2 应该没有分配到流或者从 container1 中重新平衡了一些流
        assertNotNull(result.get("container2"));
    }

    @Test
    public void testBuildContainerStreamMapWithPrevious_RemoveContainer() {
        // 准备数据
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");
        ClusterSnapshot preClusterSnapshot = new ClusterSnapshot();

        // 创建初始映射：container1 -> {stream1}, container2 -> {stream2}
        Map<String, StreamEntitySet> previousMap = new HashMap<>();
        StreamEntitySet streams1 = new StreamEntitySet();
        streams1.add(new StreamEntity("stream1"));
        StreamEntitySet streams2 = new StreamEntitySet();
        streams2.add(new StreamEntity("stream2"));
        previousMap.put("container1", streams1);
        previousMap.put("container2", streams2);
        preClusterSnapshot.setContainerStreamMap(previousMap);

        // 当前容器列表：移除了container2
        List<Container> currentContainers = new ArrayList<>();
        currentContainers.add(createContainer("container1"));

        // 当前流列表：移除了stream2
        List<XStream> currentStreams = new ArrayList<>();
        currentStreams.add(createXStream("stream1"));

        // 执行测试方法
        Map<String, StreamEntitySet> result = builder.buildContainerStreamMapWithPrevious(
            preClusterSnapshot, currentContainers, currentStreams);

        // 验证结果
        assertEquals(1, result.size());
        assertEquals(1, result.get("container1").size());
        assertTrue(result.get("container1").contains(new StreamEntity("stream1")));
    }

    @Test
    public void testBuildContainerStreamMapWithPrevious_AddStream() {
        // 准备数据
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");
        ClusterSnapshot preClusterSnapshot = new ClusterSnapshot();

        // 创建初始映射：container1 -> {stream1}
        Map<String, StreamEntitySet> previousMap = new HashMap<>();
        StreamEntitySet streams1 = new StreamEntitySet();
        streams1.add(new StreamEntity("stream1"));
        previousMap.put("container1", streams1);
        preClusterSnapshot.setContainerStreamMap(previousMap);

        // 当前容器列表：和之前一样
        List<Container> currentContainers = new ArrayList<>();
        currentContainers.add(createContainer("container1"));

        // 当前流列表：增加了stream2
        List<XStream> currentStreams = new ArrayList<>();
        currentStreams.add(createXStream("stream1"));
        currentStreams.add(createXStream("stream2"));

        // 执行测试方法
        Map<String, StreamEntitySet> result = builder.buildContainerStreamMapWithPrevious(
            preClusterSnapshot, currentContainers, currentStreams);

        // 验证结果
        assertEquals(1, result.size());
        assertEquals(2, result.get("container1").size());
        assertTrue(result.get("container1").contains(new StreamEntity("stream1")));
        assertTrue(result.get("container1").contains(new StreamEntity("stream2")));
    }

    @Test
    public void testBuildContainerStreamMapWithPrevious_RemoveStream() {
        // 准备数据
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");
        ClusterSnapshot preClusterSnapshot = new ClusterSnapshot();

        // 创建初始映射：container1 -> {stream1, stream2}
        Map<String, StreamEntitySet> previousMap = new HashMap<>();
        StreamEntitySet streams1 = new StreamEntitySet();
        streams1.add(new StreamEntity("stream1"));
        streams1.add(new StreamEntity("stream2"));
        previousMap.put("container1", streams1);
        preClusterSnapshot.setContainerStreamMap(previousMap);

        // 当前容器列表：和之前一样
        List<Container> currentContainers = new ArrayList<>();
        currentContainers.add(createContainer("container1"));

        // 当前流列表：移除了stream2
        List<XStream> currentStreams = new ArrayList<>();
        currentStreams.add(createXStream("stream1"));

        // 执行测试方法
        Map<String, StreamEntitySet> result = builder.buildContainerStreamMapWithPrevious(
            preClusterSnapshot, currentContainers, currentStreams);

        // 验证结果
        assertEquals(1, result.size());
        assertEquals(1, result.get("container1").size());
        assertTrue(result.get("container1").contains(new StreamEntity("stream1")));
    }

    @Test
    public void testBuildContainerStreamMapWithPrevious_ComplexScenario() {
        // 准备数据
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");
        ClusterSnapshot preClusterSnapshot = new ClusterSnapshot();

        // 创建初始映射：container1 -> {stream1, stream2}, container2 -> {stream3, stream4}
        Map<String, StreamEntitySet> previousMap = new HashMap<>();
        StreamEntitySet streams1 = new StreamEntitySet();
        streams1.add(new StreamEntity("stream1"));
        streams1.add(new StreamEntity("stream2"));
        StreamEntitySet streams2 = new StreamEntitySet();
        streams2.add(new StreamEntity("stream3"));
        streams2.add(new StreamEntity("stream4"));
        previousMap.put("container1", streams1);
        previousMap.put("container2", streams2);
        preClusterSnapshot.setContainerStreamMap(previousMap);

        // 当前容器列表：移除了container2，增加了container3
        List<Container> currentContainers = new ArrayList<>();
        currentContainers.add(createContainer("container1"));
        currentContainers.add(createContainer("container3"));

        // 当前流列表：移除了stream2和stream4，增加了stream5
        List<XStream> currentStreams = new ArrayList<>();
        currentStreams.add(createXStream("stream1"));
        currentStreams.add(createXStream("stream3"));
        currentStreams.add(createXStream("stream5"));

        // 执行测试方法
        Map<String, StreamEntitySet> result = builder.buildContainerStreamMapWithPrevious(
            preClusterSnapshot, currentContainers, currentStreams);

        // 验证结果
        assertEquals(2, result.size());
        assertTrue(result.containsKey("container1"));
        assertTrue(result.containsKey("container3"));
        // container1 原来有 stream1 和 stream2，移除了 stream2，所以现在应该有 stream1
        assertTrue(result.get("container1").contains(new StreamEntity("stream1")));
        // container2 被移除，其中的 stream3 和 stream4 应该被重新分配
        // 但是 stream4 也被移除了，所以只有 stream3 会被重新分配
        // 同时新增了 stream5
        // 总共有 stream1, stream3, stream5 三个流需要分配给 container1 和 container3
        int totalStreams = result.get("container1").size() + result.get("container3").size();
        assertEquals(3, totalStreams);
        assertTrue(result.get("container1").contains(new StreamEntity("stream1")));
        assertTrue(result.values().stream().anyMatch(s -> s.contains(new StreamEntity("stream3"))));
        assertTrue(result.values().stream().anyMatch(s -> s.contains(new StreamEntity("stream5"))));
    }

    @Test
    public void testRebalanceContainerStreamMap_EmptyScenario() {
        // 准备数据
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");
        Map<String, StreamEntitySet> previousContainerStreamMap = new HashMap<>();
        Set<String> currentStreams = new HashSet<>();
        Set<String> currentContainers = new HashSet<>();
        Set<String> addedContainers = new HashSet<>();
        Set<String> removedContainers = new HashSet<>();
        Set<String> addedStreams = new HashSet<>();
        Set<String> removedStreams = new HashSet<>();

        // 执行测试方法
        Map<String, StreamEntitySet> result = builder.rebalanceContainerStreamMap(
            previousContainerStreamMap, currentStreams, currentContainers,
            addedContainers, removedContainers, addedStreams, removedStreams);

        // 验证结果
        assertEquals(0, result.size());
    }

    @Test
    public void testRebalanceContainerStreamMap_BalancedDistribution() {
        // 准备数据
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");
        Map<String, StreamEntitySet> previousContainerStreamMap = new HashMap<>();
        // 添加初始映射：container1 -> {stream1, stream2, stream3}
        StreamEntitySet initialStreams = new StreamEntitySet();
        initialStreams.add(new StreamEntity("stream1"));
        initialStreams.add(new StreamEntity("stream2"));
        initialStreams.add(new StreamEntity("stream3"));
        previousContainerStreamMap.put("container1", initialStreams);

        Set<String> currentStreams = new HashSet<>();
        for (int i = 1; i <= 6; i++) {
            currentStreams.add("stream" + i);
        }
        Set<String> currentContainers = new HashSet<>();
        currentContainers.add("container1");
        currentContainers.add("container2");
        currentContainers.add("container3");
        Set<String> addedContainers = new HashSet<>();
        addedContainers.add("container2");
        addedContainers.add("container3");
        Set<String> removedContainers = new HashSet<>();
        Set<String> addedStreams = new HashSet<>();
        for (int i = 4; i <= 6; i++) {
            addedStreams.add("stream" + i);
        }
        Set<String> removedStreams = new HashSet<>();

        // 执行测试方法
        Map<String, StreamEntitySet> result = builder.rebalanceContainerStreamMap(
            previousContainerStreamMap, currentStreams, currentContainers,
            addedContainers, removedContainers, addedStreams, removedStreams);

        // 验证结果
        assertEquals(3, result.size());
        // 每个容器应该有2个流（6个流分给3个容器）
        for (StreamEntitySet streams : result.values()) {
            assertEquals(2, streams.size());
        }
        // container1 应该保留至少一部分原有的流
        assertTrue(result.get("container1").contains(new StreamEntity("stream1")) ||
            result.get("container1").contains(new StreamEntity("stream2")) ||
            result.get("container1").contains(new StreamEntity("stream3")));
    }

    @Test
    public void testRebalanceContainerStreamMap_MoveStreamWithMaxTimestamp() {
        // 准备数据
        BinlogXTopologyBuilder builder = new BinlogXTopologyBuilder("test-cluster");

        // 创建初始映射：container1 -> {stream1, stream2, stream3}, container2 -> {}
        Map<String, StreamEntitySet> previousContainerStreamMap = new HashMap<>();

        // 创建带有不同时间戳的流实体
        long baseTime = System.currentTimeMillis();
        StreamEntity stream1 = new StreamEntity("stream1", baseTime);           // 最早的时间戳
        StreamEntity stream2 = new StreamEntity("stream2", baseTime + 1000);   // 中等时间戳
        StreamEntity stream3 = new StreamEntity("stream3", baseTime + 2000);   // 最晚的时间戳（最大）

        StreamEntitySet container1Streams = new StreamEntitySet();
        container1Streams.add(stream1);
        container1Streams.add(stream2);
        container1Streams.add(stream3);
        previousContainerStreamMap.put("container1", container1Streams);

        Set<String> currentStreams = new HashSet<>();
        currentStreams.add("stream1");
        currentStreams.add("stream2");
        currentStreams.add("stream3");

        Set<String> currentContainers = new HashSet<>();
        currentContainers.add("container1");
        currentContainers.add("container2");

        Set<String> addedContainers = new HashSet<>();
        addedContainers.add("container2");

        Set<String> removedContainers = new HashSet<>();
        Set<String> addedStreams = new HashSet<>();
        Set<String> removedStreams = new HashSet<>();

        // 执行测试方法
        Map<String, StreamEntitySet> result = builder.rebalanceContainerStreamMap(
            previousContainerStreamMap, currentStreams, currentContainers,
            addedContainers, removedContainers, addedStreams, removedStreams);

        // 验证结果
        assertEquals(2, result.size());

        // 验证负载均衡是否发生，container1和container2应该各有至少一个流
        assertTrue(result.get("container1").size() >= 1);
        assertTrue(result.get("container2").size() >= 1);

        // 验证container1中是否移除了时间戳最大的流(stream3)
        // 由于负载均衡算法会移动时间戳最大的流，所以container1中不应该包含stream3
        boolean container1HasStream3 = result.get("container1").contains(stream3);
        boolean container2HasStream3 = result.get("container2").contains(stream3);

        // 确保stream3被移动到container2
        assertTrue("The stream with max timestamp should be moved to another container",
            !container1HasStream3 && container2HasStream3);

        assertTrue(result.get("container1").contains(stream1));
        assertTrue(result.get("container1").contains(stream2));
        assertTrue(result.get("container2").contains(stream3));
        assertNotEquals(stream3.getTimestamp(), result.get("container2").iterator().next().getTimestamp());

        // 验证所有流都还在
        StreamEntitySet allStreams = new StreamEntitySet();
        allStreams.addAll(result.get("container1"));
        allStreams.addAll(result.get("container2"));
        assertEquals(3, allStreams.size());
        assertTrue(allStreams.contains(stream1));
        assertTrue(allStreams.contains(stream2));
        assertTrue(allStreams.contains(stream3));
    }

    private Container createContainer(String containerId) {
        return Container.builder()
            .containerId(containerId)
            .capability(new Resource(1000, 8, 100))
            .ip("127.0.0.1")
            .daemonPort(8080)
            .availablePorts(new LinkedList<>())
            .build();
    }

    private XStream createXStream(String streamName) {
        XStream xStream = new XStream();
        xStream.setStreamName(streamName);
        xStream.setGroupName("group1");
        return xStream;
    }
}
