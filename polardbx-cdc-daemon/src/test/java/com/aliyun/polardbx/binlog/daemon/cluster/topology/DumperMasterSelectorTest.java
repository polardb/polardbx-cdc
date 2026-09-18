/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DumperMasterSelectorTest extends BaseTest {

    private NodeInfoMapper nodeInfoMapper;
    private DumperInfoMapper dumperInfoMapper;
    private DumperMasterSelector selector;

    @Before
    public void setUp() {
        nodeInfoMapper = mock(NodeInfoMapper.class);
        dumperInfoMapper = mock(DumperInfoMapper.class);
        selector = new DumperMasterSelector("test-cluster", nodeInfoMapper, dumperInfoMapper);
    }

    @Test
    public void testSelectByForceMode_NodeExists() {
        Set<String> containers = new HashSet<>(Arrays.asList("node1", "node2"));
        mockConfig(CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY, "node1");

        ClusterSnapshot snapshot = mock(ClusterSnapshot.class);

        String result = selector.selectDumperMasterNode(containers, snapshot);

        assertEquals("node1", result);
    }

    @Test
    public void testSelectByForceMode_NodeNotExists() {
        Set<String> containers = new HashSet<>(Arrays.asList("node1", "node2"));
        mockConfig(CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY, "node3");

        ClusterSnapshot snapshot = mock(ClusterSnapshot.class);
        when(snapshot.getDumperMasterNode()).thenReturn("node2");

        List<Pair<BinlogCursor, String>> cursorList = Arrays.asList(
            buildCursorPair("binlog.000002", 1620000000000L, "node1"),
            buildCursorPair("binlog.000001", 1610000000000L, "node2")
        );

        when(nodeInfoMapper.select(any())).thenReturn(cursorList.stream()
            .map(p -> this.buildNodeInfo(p.getRight(), p.getLeft()))
            .collect(Collectors.toList()));

        String result = selector.selectDumperMasterNode(containers, snapshot);

        assertNotNull(result);
        assertTrue(Arrays.asList("node1", "node2").contains(result));
        assertEquals("node1", result);
    }

    @Test
    public void testSelectByMaxCursor_WithPreviousInCandidates() {
        Set<String> containers = new HashSet<>(Arrays.asList("node1", "node2"));
        ClusterSnapshot snapshot = mock(ClusterSnapshot.class);

        List<Pair<BinlogCursor, String>> cursorList = Arrays.asList(
            buildCursorPair("binlog.000002", 1620000000000L, "node1"),
            buildCursorPair("binlog.000002", 1620000000000L, "node2")
        );

        when(nodeInfoMapper.select(any())).thenReturn(cursorList.stream()
            .map(p -> this.buildNodeInfo(p.getRight(), p.getLeft()))
            .collect(Collectors.toList()));

        when(snapshot.getDumperMasterNode()).thenReturn("node2");
        String result = selector.selectDumperMasterNode(containers, snapshot);
        assertEquals("node2", result);

        when(snapshot.getDumperMasterNode()).thenReturn("node1");
        result = selector.selectDumperMasterNode(containers, snapshot);
        assertEquals("node1", result);
    }

    @Test
    public void testSelectByMaxCursor_RandomIfNoPrevious() {
        Set<String> containers = new HashSet<>(Arrays.asList("node1", "node2"));
        ClusterSnapshot snapshot = mock(ClusterSnapshot.class);
        when(snapshot.getDumperMasterNode()).thenReturn(null);

        List<Pair<BinlogCursor, String>> cursorList = Arrays.asList(
            buildCursorPair("binlog.000001", 1620000000000L, "node1"),
            buildCursorPair("binlog.000001", 1620000000000L, "node2")
        );

        when(nodeInfoMapper.select(any())).thenReturn(cursorList.stream()
            .map(p -> this.buildNodeInfo(p.getRight(), p.getLeft()))
            .collect(Collectors.toList()));

        String result = selector.selectDumperMasterNode(containers, snapshot);

        assertNotNull(result);
        assertTrue(containers.contains(result));
    }

    @Test
    public void testSelectByPreviousMasterIfNoMaxCursor() {
        Set<String> containers = new HashSet<>(Arrays.asList("node1", "node2"));
        ClusterSnapshot snapshot = mock(ClusterSnapshot.class);

        when(nodeInfoMapper.select(any())).thenReturn(Collections.emptyList());

        when(snapshot.getDumperMasterNode()).thenReturn("node2");
        String result = selector.selectDumperMasterNode(containers, snapshot);
        assertEquals("node2", result);

        when(snapshot.getDumperMasterNode()).thenReturn("node1");
        result = selector.selectDumperMasterNode(containers, snapshot);
        assertEquals("node1", result);
    }

    @Test
    public void testSelectRandomlyIfAllElseFail() {
        Set<String> containers = new HashSet<>(Arrays.asList("node1", "node2"));
        ClusterSnapshot snapshot = mock(ClusterSnapshot.class);
        when(snapshot.getDumperMasterNode()).thenReturn(null);

        when(nodeInfoMapper.select(any())).thenReturn(Collections.emptyList());

        String result = selector.selectDumperMasterNode(containers, snapshot);

        assertNotNull(result);
        assertTrue(containers.contains(result));
    }

    // --- Helper Methods ---

    private Pair<BinlogCursor, String> buildCursorPair(String fileName, long filePosition, String containerId) {
        BinlogCursor cursor = new BinlogCursor(fileName, filePosition);
        return new ImmutablePair<>(cursor, containerId);
    }

    private NodeInfo buildNodeInfo(String containerId, BinlogCursor binlogCursor) {
        NodeInfo info = new NodeInfo();
        info.setContainerId(containerId);
        info.setLatestCursor(JSONObject.toJSONString(binlogCursor));
        return info;
    }
}
