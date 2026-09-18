/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.mapping.TableNameMapper;
import com.aliyun.polardbx.binlog.cdc.topology.LogicBasicInfo;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.function.Supplier;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_CHECK_VIRTUAL_TABLE_CONSISTENCY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PolarDbXTableMetaManagerTest_Basic_2 extends BaseTest {
    private final Supplier<Boolean> hiddenPkSupplier = () -> false;
    private final Supplier<String> dnVersionSupplier = () -> "5.7";
    private PolarDbXTableMetaManager metaManager;
    private PolarDbXTableMetaManager spyMetaManager;
    private TopologyManager topologyManager;

    @Before
    public void setUp() {
        // 初始化被测类
        metaManager = new PolarDbXTableMetaManager("testStorageInstId", hiddenPkSupplier, dnVersionSupplier);
        spyMetaManager = Mockito.spy(metaManager);

        // Mock依赖的TopologyManager
        topologyManager = mock(TopologyManager.class, Mockito.RETURNS_MOCKS);
        spyMetaManager.setTopologyManager(topologyManager);
    }

    @Test
    public void testGetLogicBasicInfo_WithMapper() {
        String phySchema = "physical_db";
        String phyTable = "physical_table";

        // 构造返回值
        LogicBasicInfo logicBasicInfo = new LogicBasicInfo();
        logicBasicInfo.setSchemaName("logical_db");
        logicBasicInfo.setTableName("logical_table");

        when(topologyManager.getLogicBasicInfo(phySchema, phyTable)).thenReturn(logicBasicInfo);

        TableNameMapper mapper = mock(TableNameMapper.class);
        Pair<String, String> original = Pair.of("logical_db", "logical_table");
        Pair<String, String> mapped = Pair.of("virtual_db", "virtual_table");
        when(mapper.mapToVirtualTableName(original)).thenReturn(mapped);

        LogicBasicInfo result = spyMetaManager.getLogicBasicInfo(phySchema, phyTable, mapper);

        assertNotNull(result);
        assertEquals("virtual_db", result.getVirtualSchemaName());
        assertEquals("virtual_table", result.getVirtualTableName());
    }

    /**
     * mapper为null，验证返回原始结果，不发生映射
     */
    @Test
    public void testGetLogicBasicInfo_NullMapper() {
        String phySchema = "physical_db";
        String phyTable = "physical_table";

        LogicBasicInfo logicBasicInfo = new LogicBasicInfo();
        logicBasicInfo.setSchemaName("logical_db");
        logicBasicInfo.setTableName("logical_table");

        when(topologyManager.getLogicBasicInfo(phySchema, phyTable)).thenReturn(logicBasicInfo);

        LogicBasicInfo result = spyMetaManager.getLogicBasicInfo(phySchema, phyTable, null);

        assertNotNull(result);
        assertNull("logical_db", result.getVirtualSchemaName());
        assertNull("logical_table", result.getVirtualTableName());
    }

    /**
     * logicBasicInfo不存在，返回null
     */
    @Test
    public void testGetLogicBasicInfo_NoLogicInfo() {
        String phySchema = "physical_db";
        String phyTable = "physical_table";

        when(topologyManager.getLogicBasicInfo(phySchema, phyTable)).thenReturn(null);

        TableNameMapper mapper = mock(TableNameMapper.class);
        LogicBasicInfo result = spyMetaManager.getLogicBasicInfo(phySchema, phyTable, mapper);

        assertNull(result);
    }

    /**
     * 正常情况：存在逻辑表，无虚拟表
     */
    @Test
    public void testFindLogicTableMeta_Normal() {
        String phySchema = "phy_db";
        String phyTable = "phy_table";
        String logicSchema = "logic_db";
        String logicTable = "logic_table";

        // 构造逻辑信息
        LogicBasicInfo logicBasicInfo = new LogicBasicInfo();
        logicBasicInfo.setSchemaName(logicSchema);
        logicBasicInfo.setTableName(logicTable);

        // Mock getLogicBasicInfo 返回值
        doReturn(logicBasicInfo).when(spyMetaManager).getLogicBasicInfo(phySchema, phyTable, null);

        // 构造逻辑表 TableMeta
        TableMeta logicTableMeta = mock(TableMeta.class);
        doReturn(logicTableMeta).when(spyMetaManager).findLogicTable(logicSchema, logicTable);

        // 调用方法
        TableMeta result = spyMetaManager.findLogicTableMeta(phySchema, phyTable);

        // 验证结果
        assertNotNull(result);
        assertSame(logicTableMeta, result);
    }

    /**
     * 存在虚拟表，且与逻辑表一致
     */
    @Test
    public void testFindLogicTableMeta_WithVirtualTable() {
        String phySchema = "phy_db";
        String phyTable = "phy_table";
        String logicSchema = "logic_db";
        String logicTable = "logic_table";
        String virtualSchema = "virtual_db";
        String virtualTable = "virtual_table";

        // 构造逻辑信息
        LogicBasicInfo logicBasicInfo = new LogicBasicInfo();
        logicBasicInfo.setSchemaName(logicSchema);
        logicBasicInfo.setTableName(logicTable);
        logicBasicInfo.setVirtualSchemaName(virtualSchema);
        logicBasicInfo.setVirtualTableName(virtualTable);

        // Mock getLogicBasicInfo
        doReturn(logicBasicInfo).when(spyMetaManager).getLogicBasicInfo(phySchema, phyTable, null);

        // 构造逻辑表和虚拟表的 TableMeta
        TableMeta logicTableMeta = mock(TableMeta.class);
        TableMeta virtualTableMeta = mock(TableMeta.class);
        when(logicTableMeta.basicEquals(virtualTableMeta)).thenReturn(true);

        doReturn(logicTableMeta).when(spyMetaManager).findLogicTable(logicSchema, logicTable);
        doReturn(virtualTableMeta).when(spyMetaManager).findLogicTable(virtualSchema, virtualTable);

        // 调用方法
        TableMeta result = spyMetaManager.findLogicTableMeta(phySchema, phyTable);

        // 验证结果
        assertNotNull(result);
        assertSame(virtualTableMeta, result);
    }

    /**
     * 异常情况：logicBasicInfo 为 null
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFindLogicTableMeta_LogicBasicInfoNull() {
        doReturn(null).when(spyMetaManager).getLogicBasicInfo(anyString(), anyString(), any());

        spyMetaManager.findLogicTableMeta("phy_db", "phy_table");
    }

    /**
     * 异常情况：logicBasicInfo.getTableName() 为空
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFindLogicTableMeta_LogicTableNameEmpty() {
        LogicBasicInfo logicBasicInfo = new LogicBasicInfo();
        logicBasicInfo.setSchemaName("logic_db");
        logicBasicInfo.setTableName(StringUtils.EMPTY);

        doReturn(logicBasicInfo).when(spyMetaManager).getLogicBasicInfo(anyString(), anyString(), any());

        spyMetaManager.findLogicTableMeta("phy_db", "phy_table");
    }

    /**
     * 异常情况：虚拟表不存在
     */
    @Test(expected = NullPointerException.class)
    public void testFindLogicTableMeta_VirtualTableNotFound() {
        String phySchema = "phy_db";
        String phyTable = "phy_table";
        String logicSchema = "logic_db";
        String logicTable = "logic_table";
        String virtualSchema = "virtual_db";
        String virtualTable = "virtual_table";

        LogicBasicInfo logicBasicInfo = new LogicBasicInfo();
        logicBasicInfo.setSchemaName(logicSchema);
        logicBasicInfo.setTableName(logicTable);
        logicBasicInfo.setVirtualSchemaName(virtualSchema);
        logicBasicInfo.setVirtualTableName(virtualTable);

        doReturn(logicBasicInfo).when(spyMetaManager).getLogicBasicInfo(phySchema, phyTable, null);

        TableMeta logicTableMeta = mock(TableMeta.class);
        doReturn(logicTableMeta).when(spyMetaManager).findLogicTable(logicSchema, logicTable);

        // 虚拟表返回 null
        doReturn(null).when(spyMetaManager).findLogicTable(virtualSchema, virtualTable);

        spyMetaManager.findLogicTableMeta(phySchema, phyTable);
    }

    /**
     * 异常情况：虚拟表与逻辑表不一致
     */
    @Test(expected = PolardbxException.class)
    public void testFindLogicTableMeta_VirtualTableNotEqual_Error() {
        mockConfig(META_BUILD_CHECK_VIRTUAL_TABLE_CONSISTENCY, "true");
        notEqual();
    }

    @Test
    public void testFindLogicTableMeta_VirtualTableNotEqual_Normal() {
        mockConfig(META_BUILD_CHECK_VIRTUAL_TABLE_CONSISTENCY, "false");
        notEqual();
    }

    private void notEqual() {
        String phySchema = "phy_db";
        String phyTable = "phy_table";
        String logicSchema = "logic_db";
        String logicTable = "logic_table";
        String virtualSchema = "virtual_db";
        String virtualTable = "virtual_table";

        LogicBasicInfo logicBasicInfo = new LogicBasicInfo();
        logicBasicInfo.setSchemaName(logicSchema);
        logicBasicInfo.setTableName(logicTable);
        logicBasicInfo.setVirtualSchemaName(virtualSchema);
        logicBasicInfo.setVirtualTableName(virtualTable);

        doReturn(logicBasicInfo).when(spyMetaManager).getLogicBasicInfo(phySchema, phyTable, null);

        TableMeta logicTableMeta = mock(TableMeta.class);
        TableMeta virtualTableMeta = mock(TableMeta.class);
        when(logicTableMeta.basicEquals(virtualTableMeta)).thenReturn(false);

        doReturn(logicTableMeta).when(spyMetaManager).findLogicTable(logicSchema, logicTable);
        doReturn(virtualTableMeta).when(spyMetaManager).findLogicTable(virtualSchema, virtualTable);

        spyMetaManager.findLogicTableMeta(phySchema, phyTable);
    }
}
