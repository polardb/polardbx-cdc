/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler;

import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class ClusterSnapshotTest extends BaseTest {

    @Test
    public void testDefaultConstructor() {
        ClusterSnapshot snapshot = new ClusterSnapshot();
        Assert.assertEquals(1L, snapshot.getVersion());
        Assert.assertEquals(Long.valueOf(1L), snapshot.getSubVersion());
    }

    @Test
    public void testConstructorWithValidParameters() {
        Set<String> containers = new HashSet<>();
        containers.add("container1");
        Set<String> storages = new HashSet<>();
        storages.add("storage1");

        ClusterSnapshot snapshot = new ClusterSnapshot(
            2L, // version
            1000L, // timestamp
            containers,
            storages,
            "dumperMasterNode",
            "dumperMaster",
            "storageHistoryTso",
            ClusterType.BINLOG.name(),
            12345L, // serverId
            2L, // subVersion
            "finalTaskNode"
        );

        Assert.assertEquals(2L, snapshot.getVersion());
        Assert.assertEquals(Long.valueOf(1000L), snapshot.getTimestamp());
        Assert.assertEquals(containers, snapshot.getContainers());
        Assert.assertEquals(storages, snapshot.getStorages());
        Assert.assertEquals("dumperMasterNode", snapshot.getDumperMasterNode());
        Assert.assertEquals("dumperMaster", snapshot.getDumperMaster());
        Assert.assertEquals("storageHistoryTso", snapshot.getStorageHistoryTso());
        Assert.assertEquals(Long.valueOf(12345L), snapshot.getServerId());
        Assert.assertEquals(Long.valueOf(2L), snapshot.getSubVersion());
        Assert.assertEquals("finalTaskNode", snapshot.getFinalTaskNode());
    }

    @Test(expected = PolardbxException.class)
    public void testConstructorWithNullTimestamp() {
        Set<String> containers = new HashSet<>();
        containers.add("container1");
        Set<String> storages = new HashSet<>();
        storages.add("storage1");

        new ClusterSnapshot(
            2L, // version != 1L
            null, // null timestamp should throw exception
            containers,
            storages,
            "dumperMasterNode",
            "dumperMaster",
            "storageHistoryTso",
            ClusterType.BINLOG.name(),
            12345L,
            2L,
            "finalTaskNode"
        );
    }

    @Test(expected = PolardbxException.class)
    public void testConstructorWithEmptyContainers() {
        Set<String> containers = new HashSet<>(); // empty containers
        Set<String> storages = new HashSet<>();
        storages.add("storage1");

        new ClusterSnapshot(
            2L, // version != 1L
            1000L,
            containers, // empty containers should throw exception
            storages,
            "dumperMasterNode",
            "dumperMaster",
            "storageHistoryTso",
            ClusterType.BINLOG.name(),
            12345L,
            2L,
            "finalTaskNode"
        );
    }

    @Test(expected = PolardbxException.class)
    public void testConstructorWithNullStorages() {
        Set<String> containers = new HashSet<>();
        containers.add("container1");
        Set<String> storages = new HashSet<>(); // empty storages

        new ClusterSnapshot(
            2L, // version != 1L
            1000L,
            containers,
            storages, // empty storages should throw exception
            "dumperMasterNode",
            "dumperMaster",
            "storageHistoryTso",
            ClusterType.BINLOG.name(),
            12345L,
            2L,
            "finalTaskNode"
        );
    }

    @Test(expected = PolardbxException.class)
    public void testConstructorWithEmptyDumperMasterForBinlogCluster() {
        Set<String> containers = new HashSet<>();
        containers.add("container1");
        Set<String> storages = new HashSet<>();
        storages.add("storage1");

        new ClusterSnapshot(
            2L, // version != 1L
            1000L,
            containers,
            storages,
            "dumperMasterNode",
            "", // empty dumperMaster for BINLOG cluster should throw exception
            "storageHistoryTso",
            ClusterType.BINLOG.name(),
            12345L,
            2L,
            "finalTaskNode"
        );
    }

    @Test(expected = PolardbxException.class)
    public void testConstructorWithEmptyDumperMasterNodeForBinlogCluster() {
        Set<String> containers = new HashSet<>();
        containers.add("container1");
        Set<String> storages = new HashSet<>();
        storages.add("storage1");

        new ClusterSnapshot(
            2L, // version != 1L
            1000L,
            containers,
            storages,
            "", // empty dumperMasterNode for BINLOG cluster should throw exception
            "dumperMaster",
            "storageHistoryTso",
            ClusterType.BINLOG.name(),
            12345L,
            2L,
            "finalTaskNode"
        );
    }

    @Test(expected = PolardbxException.class)
    public void testConstructorWithEmptyStorageHistoryTso() {
        Set<String> containers = new HashSet<>();
        containers.add("container1");
        Set<String> storages = new HashSet<>();
        storages.add("storage1");

        new ClusterSnapshot(
            2L, // version != 1L
            1000L,
            containers,
            storages,
            "dumperMasterNode",
            "dumperMaster",
            "", // empty storageHistoryTso should throw exception
            ClusterType.BINLOG.name(),
            12345L,
            2L,
            "finalTaskNode"
        );
    }

    @Test(expected = PolardbxException.class)
    public void testConstructorWithNullServerId() {
        Set<String> containers = new HashSet<>();
        containers.add("container1");
        Set<String> storages = new HashSet<>();
        storages.add("storage1");

        new ClusterSnapshot(
            2L, // version != 1L
            1000L,
            containers,
            storages,
            "dumperMasterNode",
            "dumperMaster",
            "storageHistoryTso",
            ClusterType.BINLOG.name(),
            null, // null serverId should throw exception
            2L,
            "finalTaskNode"
        );
    }

    @Test(expected = PolardbxException.class)
    public void testConstructorWithEmptyFinalTaskNodeForBinlogCluster() {
        Set<String> containers = new HashSet<>();
        containers.add("container1");
        Set<String> storages = new HashSet<>();
        storages.add("storage1");

        new ClusterSnapshot(
            2L, // version != 1L
            1000L,
            containers,
            storages,
            "dumperMasterNode",
            "dumperMaster",
            "storageHistoryTso",
            ClusterType.BINLOG.name(),
            12345L,
            2L,
            "" // empty finalTaskNode for BINLOG cluster should throw exception
        );
    }

    @Test
    public void testIsOrigin() {
        ClusterSnapshot snapshot1 = new ClusterSnapshot();
        Assert.assertTrue(snapshot1.isOrigin());

        Set<String> containers = new HashSet<>();
        containers.add("container1");
        Set<String> storages = new HashSet<>();
        storages.add("storage1");

        ClusterSnapshot snapshot2 = new ClusterSnapshot(
            2L,
            1000L,
            containers,
            storages,
            "dumperMasterNode",
            "dumperMaster",
            "storageHistoryTso",
            ClusterType.BINLOG.name(),
            12345L,
            2L,
            "finalTaskNode"
        );
        Assert.assertFalse(snapshot2.isOrigin());
    }
}
