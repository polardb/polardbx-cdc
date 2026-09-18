/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ConnectionInfo;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ImportTaskConfig;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ImportTaskConfigList;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.ServerInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.common.ResTypeEnum;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ImportApiResourceGeneratePhysicalMetaTest extends BaseTest {

    private ImportApiResource importApiResource;

    private ImportTaskConfigList config;
    private DataImportMeta importMeta;

    private ServerInfoMapper serverInfoMapper;

    private final int drdsServerId = 1001;
    private final int polarxServerId = 1002;

    @Before
    public void setUp() {
        importApiResource = new ImportApiResource();
        config = new ImportTaskConfigList();
        config.setImportTaskConfigs(new ArrayList<>());
        importMeta = new DataImportMeta();
        importMeta.setSrcLogicalTableList(new HashMap<>());
        importMeta.setLogicalDbMappings(new HashMap<>());
        importMeta.setRules(new HashMap<>());
        serverInfoMapper = mock(ServerInfoMapper.class);
        // 模拟ServerInfoMapper.select调用
        List<ServerInfo> serverInfoList = new ArrayList<>();
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setIp("127.0.0.3");
        serverInfo.setPort(3308);
        serverInfoList.add(serverInfo);
        when(serverInfoMapper.select(any())).thenReturn(serverInfoList);
        importApiResource.setServerInfoMapper(serverInfoMapper);

        mockConfig(ConfigKeys.POLARX_USERNAME, "testUser");
        mockConfig(ConfigKeys.POLARX_PASSWORD, "testPassword");
        mockConfig(ConfigKeys.RPL_MERGE_SAME_RDS_TASK, "false");
        mockConfig(ConfigKeys.RPL_FULL_FROM_DN, "false");
        // Setup test data
//        setupTestData();
    }

    private void setupTestData() {
        // Create test configuration
        config = new ImportTaskConfigList();
        List<ImportTaskConfig> importTaskConfigs = new ArrayList<>();

        // Create first import task config
        ImportTaskConfig taskConfig1 = new ImportTaskConfig();
        taskConfig1.setSrcDbName("src_db1");
        taskConfig1.setDstDbName("dst_db1");
        taskConfig1.setRdsBid("bid1");
        taskConfig1.setRdsUid("uid1");

        // Setup source connection
        ConnectionInfo srcConn = new ConnectionInfo("192.168.1.100", 3306, "src_user", "src_password", "instance0",
            ResTypeEnum.POLARX1.getValue(), new ArrayList<>());
        taskConfig1.setSrcConn(srcConn);

        // Setup physical connections
        List<ConnectionInfo> srcPhyConnList = new ArrayList<>();
        ConnectionInfo phyConn1 =
            new ConnectionInfo("192.168.1.101", 3306, "phy_user1", "phy_password1", "instance1", "RDS",
                Arrays.asList("phy_db1", "phy_db2"));
        srcPhyConnList.add(phyConn1);

        taskConfig1.setSrcPhyConnList(srcPhyConnList);
        taskConfig1.setTableList(Arrays.asList("table1", "table2"));
        taskConfig1.setRules("");

        importTaskConfigs.add(taskConfig1);
        config.setImportTaskConfigs(importTaskConfigs);
        config.setClusterId("test_cluster");

        // Create import meta
        importMeta = new DataImportMeta();
        importMeta.setSrcLogicalTableList(new HashMap<>());
        importMeta.getSrcLogicalTableList().put("src_db1", Arrays.asList("table1", "table2"));
        importMeta.setRules(new HashMap<>());
        importMeta.setLogicalDbMappings(new HashMap<>());
    }

    public void setUpTestGeneratePhysicalMetaWithPolarx1DbType() throws Exception {
        // 准备测试数据
        ImportTaskConfig taskConfig = new ImportTaskConfig();
        ConnectionInfo srcConn = new ConnectionInfo("127.0.0.1", 3306, "user", "password", "instance1",
            ResTypeEnum.POLARX1.getValue(), null);

        taskConfig.setSrcConn(srcConn);
        taskConfig.setSrcDbName("src_db");
        taskConfig.setDstDbName("dst_db");
        taskConfig.setTableList(new ArrayList<>());
        taskConfig.getTableList().add("table1");

        ConnectionInfo phyConn = new ConnectionInfo("127.0.0.1", 3307, "phy_user", "phy_password", "instance1",
            "RDS", new ArrayList<>());
        phyConn.getDbNameList().add("phy_db1");

        taskConfig.setSrcPhyConnList(new ArrayList<>());
        taskConfig.getSrcPhyConnList().add(phyConn);

        config.getImportTaskConfigs().add(taskConfig);

        importMeta.getSrcLogicalTableList().put("src_db", new ArrayList<>());
        importMeta.getSrcLogicalTableList().get("src_db").add("table1");
        importMeta.getRules().put("src_db", "");

        try (MockedStatic<DriverManager> driverManagerMockedStatic = mockStatic(DriverManager.class)) {
            Connection conn = mock(Connection.class);
            Statement st = mock(Statement.class);
            Mockito.when(conn.createStatement()).thenReturn(st);
            ResultSet rs = mock(ResultSet.class);
            Mockito.when(st.executeQuery("show datasources")).thenReturn(rs);
            Mockito.when(rs.next()).thenReturn(true, false);
            Mockito.when(rs.getString("GROUP")).thenReturn("group-1");
            Mockito.when(rs.getString("URL")).thenReturn("test/phy_db1?aaa=aaa");
            ResultSet rs1 = mock(ResultSet.class);
            Mockito.when(st.executeQuery("show tables")).thenReturn(rs1);
            ResultSet rs2 = mock(ResultSet.class);
            Mockito.when(st.executeQuery("show create table `table1`")).thenReturn(rs2);
            Mockito.when(rs1.next()).thenReturn(true, false);
            Mockito.when(rs1.getString(1)).thenReturn("table1");
            Mockito.when(rs2.next()).thenReturn(true, false);
            Mockito.when(rs2.getString(2)).thenReturn("create table table1(id bigint primary key auto_increment)");

            ResultSet rs3 = mock(ResultSet.class);
            Mockito.when(st.executeQuery("show topology from `table1`")).thenReturn(rs3);
            Mockito.when(rs3.next()).thenReturn(true, false);
            Mockito.when(rs3.getString("GROUP_NAME")).thenReturn("group-1");
            Mockito.when(rs3.getString("TABLE_NAME")).thenReturn("test_tb_1");
            driverManagerMockedStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                .thenReturn(conn);
            // 调用测试方法
            importApiResource.generatePhysicalMeta(config, importMeta, 123, 456);
        }

        // 验证结果
        assertNotNull(importMeta.getMetaList());
        assertEquals(1, importMeta.getMetaList().size());

        DataImportMeta.PhysicalMeta meta = importMeta.getMetaList().get(0);
        assertEquals("127.0.0.1", meta.getSrcHost());
        assertEquals(3307, meta.getSrcPort());
        assertEquals("phy_user", meta.getSrcUser());
        assertEquals("phy_password", meta.getSrcPassword());
    }

    @Test
    public void testGeneratePhysicalMetaWithPolarx1DbTypeMerge() throws Exception {
        mockConfig(ConfigKeys.RPL_MERGE_SAME_RDS_TASK, "true");
        setUpTestGeneratePhysicalMetaWithPolarx1DbType();
    }

    @Test
    public void testGeneratePhysicalMetaWithPolarx1DbTypeNotMerge() throws Exception {
        mockConfig(ConfigKeys.RPL_MERGE_SAME_RDS_TASK, "false");
        setUpTestGeneratePhysicalMetaWithPolarx1DbType();
    }

    @Test
    public void testGeneratePhysicalMetaWithPolarx2DbType() {
        // 准备测试数据
        ImportTaskConfig taskConfig = new ImportTaskConfig();
        ConnectionInfo srcConn = new ConnectionInfo("127.0.0.1", 3306, "user", "password", "instance1",
            ResTypeEnum.POLARX2.getValue(), null);

        taskConfig.setSrcConn(srcConn);
        taskConfig.setSrcDbName("src_db");
        taskConfig.setDstDbName("dst_db");
        taskConfig.setTableList(new ArrayList<>());
        taskConfig.getTableList().add("table1");

        ConnectionInfo phyConn = new ConnectionInfo("127.0.0.1", 3307, "phy_user", "phy_password", "instance1",
            ResTypeEnum.RDS_MYSQL.getValue(), new ArrayList<>()); // 物理连接应该是RDS类型
        phyConn.getDbNameList().add("phy_db1");

        taskConfig.setSrcPhyConnList(new ArrayList<>());
        taskConfig.getSrcPhyConnList().add(phyConn);

        config.getImportTaskConfigs().add(taskConfig);

        importMeta.getSrcLogicalTableList().put("src_db", new ArrayList<>());
        importMeta.getSrcLogicalTableList().get("src_db").add("table1");
        importMeta.getRules().put("src_db", "");

        // 调用测试方法
        importApiResource.generatePhysicalMeta(config, importMeta, 123, 456);

        // 验证结果
        assertNotNull(importMeta.getMetaList());
        assertEquals(1, importMeta.getMetaList().size());

        DataImportMeta.PhysicalMeta meta = importMeta.getMetaList().get(0);
        assertEquals("127.0.0.1", meta.getSrcHost());
        assertEquals(3307, meta.getSrcPort());
        assertEquals("phy_user", meta.getSrcUser());
        assertEquals("phy_password", meta.getSrcPassword());
    }

    public void setUpTestGeneratePhysicalMetaWithRDSType() {
        // 准备测试数据 - 对于RDS类型，不应该创建TopologyManager
        ImportTaskConfig taskConfig = new ImportTaskConfig();
        ConnectionInfo srcConn = new ConnectionInfo("127.0.0.1", 3306, "user", "password", "instance1",
            ResTypeEnum.RDS_MYSQL.getValue(), null); // 源连接是RDS类型

        taskConfig.setSrcConn(srcConn);
        taskConfig.setSrcDbName("src_db");
        taskConfig.setDstDbName("dst_db");
        taskConfig.setTableList(new ArrayList<>());
        taskConfig.getTableList().add("table1");

        ConnectionInfo phyConn = new ConnectionInfo("127.0.0.1", 3307, "phy_user", "phy_password", "instance1",
            ResTypeEnum.RDS_MYSQL.getValue(), new ArrayList<>());
        phyConn.getDbNameList().add("phy_db1");

        taskConfig.setSrcPhyConnList(new ArrayList<>());
        taskConfig.getSrcPhyConnList().add(phyConn);

        config.getImportTaskConfigs().add(taskConfig);

        importMeta.getSrcLogicalTableList().put("src_db", new ArrayList<>());
        importMeta.getSrcLogicalTableList().get("src_db").add("table1");
        importMeta.getRules().put("src_db", "");

        // 调用测试方法 - 对于RDS类型，不应该尝试连接数据库创建TopologyManager
        importApiResource.generatePhysicalMeta(config, importMeta, 123, 456);

        // 验证结果
        assertNotNull(importMeta.getMetaList());
        assertEquals(1, importMeta.getMetaList().size());

        DataImportMeta.PhysicalMeta meta = importMeta.getMetaList().get(0);
        assertEquals("127.0.0.1", meta.getSrcHost());
        assertEquals(3307, meta.getSrcPort());
        assertEquals("phy_user", meta.getSrcUser());
        assertEquals("phy_password", meta.getSrcPassword());
    }

    @Test
    public void testGeneratePhysicalMetaWithRDSTypeMerge() {
        mockConfig(ConfigKeys.RPL_MERGE_SAME_RDS_TASK, "true");
        setUpTestGeneratePhysicalMetaWithRDSType();
    }

    @Test
    public void testGeneratePhysicalMetaWithRDSTypeNotMerge() {
        mockConfig(ConfigKeys.RPL_MERGE_SAME_RDS_TASK, "false");
        setUpTestGeneratePhysicalMetaWithRDSType();
    }

    @Test
    public void testGeneratePhysicalMetaWithOtherDbType() {
        // 准备测试数据
        ImportTaskConfig taskConfig = new ImportTaskConfig();
        ConnectionInfo srcConn = new ConnectionInfo("127.0.0.1", 3306, "user", "password", "instance1",
            ResTypeEnum.POLARDB_M.getValue(), null);

        taskConfig.setSrcConn(srcConn);
        taskConfig.setSrcDbName("src_db");
        taskConfig.setDstDbName("dst_db");
        taskConfig.setTableList(new ArrayList<>());
        taskConfig.getTableList().add("table1");

        ConnectionInfo phyConn = new ConnectionInfo("127.0.0.1", 3307, "phy_user", "phy_password", "instance1",
            ResTypeEnum.RDS_MYSQL.getValue(), new ArrayList<>()); // 物理连接应该是RDS类型
        phyConn.getDbNameList().add("phy_db1");

        taskConfig.setSrcPhyConnList(new ArrayList<>());
        taskConfig.getSrcPhyConnList().add(phyConn);

        config.getImportTaskConfigs().add(taskConfig);

        importMeta.getSrcLogicalTableList().put("src_db", new ArrayList<>());
        importMeta.getSrcLogicalTableList().get("src_db").add("table1");
        importMeta.getRules().put("src_db", "");

        // 调用测试方法
        importApiResource.generatePhysicalMeta(config, importMeta, 123, 456);

        // 验证结果
        assertNotNull(importMeta.getMetaList());
        assertEquals(1, importMeta.getMetaList().size());

        DataImportMeta.PhysicalMeta meta = importMeta.getMetaList().get(0);
        assertEquals("127.0.0.1", meta.getSrcHost());
        assertEquals(3307, meta.getSrcPort());
        assertEquals("phy_user", meta.getSrcUser());
        assertEquals("phy_password", meta.getSrcPassword());
    }

    @Test
    public void testGeneratePhysicalMeta_WithMerging() {
        // Mock configuration to enable merging
        mockConfig(ConfigKeys.RPL_MERGE_SAME_RDS_TASK, "true");

        // Add another task config with same RDS instance ID to test merging
        ImportTaskConfig taskConfig2 = new ImportTaskConfig();
        taskConfig2.setSrcDbName("src_db2");
        taskConfig2.setDstDbName("dst_db2");
        taskConfig2.setRdsBid("bid1");
        taskConfig2.setRdsUid("uid1");

        ConnectionInfo srcConn2 = new ConnectionInfo("192.168.1.100", 3306, "src_user", "src_password", "instance1",
            ResTypeEnum.POLARX1.getValue(), Collections.singletonList("src_db2"));
        taskConfig2.setSrcConn(srcConn2);

        List<ConnectionInfo> srcPhyConnList2 = new ArrayList<>();
        ConnectionInfo phyConn2 = new ConnectionInfo("192.168.1.101", 3306, "phy_user1", "phy_password1", "instance1",
            ResTypeEnum.POLARX1.getValue(), Collections.singletonList("phy_db3"));
        srcPhyConnList2.add(phyConn2);

        taskConfig2.setSrcPhyConnList(srcPhyConnList2);
        taskConfig2.setTableList(Arrays.asList("table3", "table4"));
        taskConfig2.setRules("");

        config.getImportTaskConfigs().add(taskConfig2);

        // Update import meta with second task's table list
        importMeta.getSrcLogicalTableList().put("src_db2", Arrays.asList("table3", "table4"));

        try (MockedStatic<DriverManager> driverManagerMockedStatic = mockStatic(DriverManager.class)) {
            Connection conn = mock(Connection.class);
            Statement st = mock(Statement.class);
            Mockito.when(conn.createStatement()).thenReturn(st);
            ResultSet rs = mock(ResultSet.class);
            Mockito.when(st.executeQuery("show datasources")).thenReturn(rs);
            Mockito.when(rs.next()).thenReturn(true, false);
            Mockito.when(rs.getString("GROUP")).thenReturn("group-1");
            Mockito.when(rs.getString("URL")).thenReturn("group-1");
            ResultSet rs1 = mock(ResultSet.class);
            Mockito.when(st.executeQuery("show tables")).thenReturn(rs1);
            ResultSet rs2 = mock(ResultSet.class);
            Mockito.when(st.executeQuery("show create table `table3`")).thenReturn(rs2);
            Mockito.when(st.executeQuery("show create table `table4`")).thenReturn(rs2);
            Mockito.when(rs1.next()).thenReturn(true, true, false);
            Mockito.when(rs1.getString(1)).thenReturn("table3", "table4");
            Mockito.when(rs2.next()).thenReturn(true, false);
            Mockito.when(rs2.getString(2)).thenReturn("create table table3(id bigint primary key auto_increment)");

            ResultSet rs3 = mock(ResultSet.class);
            Mockito.when(st.executeQuery("show topology from `table3`")).thenReturn(rs3);
            Mockito.when(st.executeQuery("show topology from `table4`")).thenReturn(rs3);
            Mockito.when(rs3.next()).thenReturn(true, false);
            Mockito.when(rs3.getString("GROUP_NAME")).thenReturn("group-1");
            Mockito.when(rs3.getString("TABLE_NAME")).thenReturn("table3_1");
            driverManagerMockedStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                .thenReturn(conn);

            // Execute the method
            importApiResource.generatePhysicalMeta(config, importMeta, drdsServerId, polarxServerId);
        } catch (SQLException e) {
            fail("SQLException occurred: " + e.getMessage());
        }

        // Verify results - should be merged into 1 physical meta
        assertNotNull(importMeta.getMetaList());
        assertEquals(1, importMeta.getMetaList().size());

        DataImportMeta.PhysicalMeta physicalMeta = importMeta.getMetaList().get(0);

        // Verify merged source database list
        Set<String> srcDbList = physicalMeta.getSrcDbList();
        assertTrue(srcDbList.contains("phy_db3"));
        assertEquals(1, srcDbList.size());

        // Verify merged destination database mappings
        Map<String, String> dstDbMapping = physicalMeta.getDstDbMapping();
        assertEquals("dst_db2", dstDbMapping.get("phy_db3"));

        // Verify merged physical table lists
        Map<String, Set<String>> physicalDoTableList = physicalMeta.getPhysicalDoTableList();
        Set<String> phyDb3Tables = physicalDoTableList.get("phy_db3");

        assertNotNull(phyDb3Tables);

    }

    @Test
    public void testGeneratePhysicalMeta_WithPolarX2DbType() {

        setupTestData();
        // Change db type to POLARX2
        ConnectionInfo srcConn = config.getImportTaskConfigs().get(0).getSrcConn();
        srcConn.setDbType(ResTypeEnum.POLARX2.getValue());

        // Execute the method
        importApiResource.generatePhysicalMeta(config, importMeta, drdsServerId, polarxServerId);

        // Verify results
        assertNotNull(importMeta.getMetaList());
        assertEquals(1, importMeta.getMetaList().size());

        // Should still work correctly even with POLARX2 db type
        DataImportMeta.PhysicalMeta physicalMeta = importMeta.getMetaList().get(0);
        assertEquals("127.0.0.3", physicalMeta.getDstHost());
        assertEquals(3308, physicalMeta.getDstPort());
    }

    @Test
    public void testGeneratePhysicalMeta_WithRdsDbType() {
        setupTestData();
        // Change db type to RDS (default)
        ConnectionInfo srcConn = config.getImportTaskConfigs().get(0).getSrcConn();
        srcConn.setDbType(ResTypeEnum.RDS_MYSQL.getValue());

        // Execute the method
        importApiResource.generatePhysicalMeta(config, importMeta, drdsServerId, polarxServerId);

        // Verify results
        assertNotNull(importMeta.getMetaList());
        assertEquals(1, importMeta.getMetaList().size());

        // When db type is RDS, no topology manager should be created
        DataImportMeta.PhysicalMeta physicalMeta = importMeta.getMetaList().get(0);
        assertEquals("127.0.0.3", physicalMeta.getDstHost());
        assertEquals(3308, physicalMeta.getDstPort());
    }
}