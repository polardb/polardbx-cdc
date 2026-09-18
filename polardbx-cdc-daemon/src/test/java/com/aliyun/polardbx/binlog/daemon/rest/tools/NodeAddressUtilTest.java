/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.tools;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;

/**
 * NodeAddressUtil 单元测试类，验证 getDumperAddressList 的 DumperX 过滤逻辑。
 */
public class NodeAddressUtilTest extends BaseTest {

    private DumperInfoMapper dumperInfoMapper;
    private static final String INST_ID = "pxc-test-node-addr";

    @Before
    public void setUp() {
        mockConfig(ConfigKeys.CLUSTER_ID, "test-cluster");
        dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
    }

    /**
     * 构造一个 DumperInfo 记录并插入数据库。
     */
    private void insertDumperInfo(String taskName, String ip, int port, String role) {
        DumperInfo info = new DumperInfo();
        info.setGmtCreated(new Date());
        info.setGmtModified(new Date());
        info.setClusterId("test-cluster");
        info.setTaskName(taskName);
        info.setIp(ip);
        info.setPort(port);
        info.setRole(role);
        info.setStatus(0);
        info.setGmtHeartbeat(new Date());
        info.setContainerId("container-1");
        info.setVersion(1L);
        info.setSubVersion(1L);
        info.setPolarxInstId(INST_ID);
        info.setDelay(0L);
        info.setEnableLightRebalance(true);
        dumperInfoMapper.insert(info);
    }

    /**
     * 验证 getDumperAddressList 能正确过滤 DumperX 节点，仅返回 Dumper 类型节点。
     */
    @Test
    public void testGetDumperAddressListFiltersDumperX() {
        // 插入 Dumper 类型节点（taskName 以 "Dumper-" 开头）
        insertDumperInfo("Dumper-12345", "10.0.0.1", 8001, "M");
        insertDumperInfo("Dumper-12346", "10.0.0.2", 8002, "S");

        // 插入 DumperX 类型节点（role 为 "X"，即 DumperType.XSTREAM）
        insertDumperInfo("DumperX-67890", "10.0.0.3", 9001, "X");
        insertDumperInfo("DumperX-67891", "10.0.0.4", 9002, "X");

        List<NodeAddressUtil.NodeAddress> addressList =
            NodeAddressUtil.getDumperAddressList(INST_ID);

        // 应该只包含 2 个 Dumper 节点，不包含 DumperX
        Assert.assertEquals("Should only contain Dumper nodes, not DumperX", 2, addressList.size());

        // 验证返回的地址都是 Dumper 节点的
        boolean hasDumper1 = addressList.stream()
            .anyMatch(a -> "10.0.0.1".equals(a.getIp()) && a.getPort() == 8001);
        boolean hasDumper2 = addressList.stream()
            .anyMatch(a -> "10.0.0.2".equals(a.getIp()) && a.getPort() == 8002);
        boolean hasDumperX = addressList.stream()
            .anyMatch(a -> "10.0.0.3".equals(a.getIp()) || "10.0.0.4".equals(a.getIp()));

        Assert.assertTrue("Should contain Dumper node 10.0.0.1:8001", hasDumper1);
        Assert.assertTrue("Should contain Dumper node 10.0.0.2:8002", hasDumper2);
        Assert.assertFalse("Should NOT contain any DumperX node", hasDumperX);
    }

    /**
     * 验证当没有 DumperX 节点时，所有 Dumper 节点都被正确返回。
     */
    @Test
    public void testGetDumperAddressListWithoutDumperX() {
        insertDumperInfo("Dumper-11111", "10.0.1.1", 8001, "M");
        insertDumperInfo("Dumper-22222", "10.0.1.2", 8002, "S");

        List<NodeAddressUtil.NodeAddress> addressList =
            NodeAddressUtil.getDumperAddressList(INST_ID);

        Assert.assertEquals(2, addressList.size());
    }

    /**
     * 验证 Master 节点会被正确记录到 MASTER_ADDRESS_MAP 中。
     */
    @Test
    public void testMasterAddressMapUpdated() {
        NodeAddressUtil.MASTER_ADDRESS_MAP.clear();
        insertDumperInfo("Dumper-33333", "10.0.2.1", 8001, "M");

        NodeAddressUtil.getDumperAddressList(INST_ID);

        NodeAddressUtil.NodeAddress master = NodeAddressUtil.MASTER_ADDRESS_MAP.get(INST_ID);
        Assert.assertNotNull("Master address should be recorded", master);
        Assert.assertEquals("10.0.2.1", master.getIp());
        Assert.assertEquals(8001, master.getPort());
    }

    /**
     * 验证 taskName 为空字符串的节点不会被当作 DumperX 过滤掉。
     */
    @Test
    public void testEmptyTaskNameNotFiltered() {
        insertDumperInfo("", "10.0.3.1", 8001, "S");

        List<NodeAddressUtil.NodeAddress> addressList =
            NodeAddressUtil.getDumperAddressList(INST_ID);

        boolean hasNode = addressList.stream()
            .anyMatch(a -> "10.0.3.1".equals(a.getIp()) && a.getPort() == 8001);
        Assert.assertTrue("Node with empty taskName should NOT be filtered", hasNode);
    }
}
