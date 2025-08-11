/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ConnectionInfo;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ImportTaskConfig;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ImportTaskConfigList;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class ImportApiResourceTest extends BaseTest {
    @InjectMocks
    private ImportApiResource importApiResource;

    private ImportTaskConfigList config;
    private DataImportMeta importMeta;
    private List<XStream> xStreams;

    @Before
    public void setUp() {
        config = new ImportTaskConfigList();
        config.setImportTaskConfigs(new ArrayList<>());
        importMeta = new DataImportMeta();
        xStreams = new ArrayList<>();

        // Mock XStream list
        XStream xStream1 = new XStream();
        xStream1.setGroupName("group1");
        xStream1.setStreamName("stream1");
        xStreams.add(xStream1);

        XStream xStream2 = new XStream();
        xStream2.setGroupName("group1");
        xStream2.setStreamName("stream2");
        xStreams.add(xStream2);

        // Mock ImportTaskConfigList
        ImportTaskConfig importTaskConfig1 = new ImportTaskConfig();
        importTaskConfig1.setSrcConn(new ConnectionInfo("127.0.0.1", 3306, "user1", "pwd1", "11", new ArrayList<>()));
        importTaskConfig1.setSrcDbName("srcDb1");
        importTaskConfig1.setDstDbName("dstDb1");
        config.getImportTaskConfigs().add(importTaskConfig1);

        ImportTaskConfig importTaskConfig2 = new ImportTaskConfig();
        importTaskConfig2.setSrcConn(new ConnectionInfo("127.0.0.1", 3306, "user1", "pwd1", "11", new ArrayList<>()));
        importTaskConfig2.setSrcDbName("srcDb2");
        importTaskConfig2.setDstDbName("dstDb2");
        config.getImportTaskConfigs().add(importTaskConfig2);

        // Mock importMeta
        Map<String, List<String>> srcLogicalTableList = new HashMap<>();
        srcLogicalTableList.put("srcDb1", Collections.singletonList("table1"));
        srcLogicalTableList.put("srcDb2", Collections.singletonList("table3"));
        importMeta.setSrcLogicalTableList(srcLogicalTableList);
    }

    @Test
    public void generateBackFlowMeta_XStreamsEmpty_GeneratesOnePhysicalMeta() {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(
            DbTaskMetaManager.class)) {
            dbTaskMetaManagerMockedStatic.when(DbTaskMetaManager::listChosenXStreams).thenReturn(new ArrayList<>());
            importApiResource.generateBackFlowMeta(config, importMeta, 1, 2);

            assertEquals(1, importMeta.getBackFlowMetaList().size());
            DataImportMeta.PhysicalMeta backFlowMeta = importMeta.getBackFlowMetaList().get(0);
            assertNotNull(backFlowMeta.getDstHost());
            assertNotNull(backFlowMeta.getDstPort());
            assertNotNull(backFlowMeta.getDstUser());
            assertNotNull(backFlowMeta.getDstPassword());
            assertEquals(HostType.POLARX1, backFlowMeta.getDstType());
            assertEquals(HostType.POLARX2, backFlowMeta.getSrcType());
            assertEquals(2, backFlowMeta.getDstServerId());
            assertEquals("1", backFlowMeta.getIgnoreServerIds());
            assertEquals(2, backFlowMeta.getSrcDbList().size());
            assertEquals(2, backFlowMeta.getDstDbMapping().size());
            assertEquals(2, backFlowMeta.getPhysicalDoTableList().size());
            assertTrue(backFlowMeta.getRewriteTableMapping().isEmpty());
            assertNull(backFlowMeta.getStreamName());
        }
    }

    @Test
    public void generateBackFlowMeta_XStreamsNotEmpty_GeneratesMultiplePhysicalMeta() {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(
            DbTaskMetaManager.class)) {
            dbTaskMetaManagerMockedStatic.when(DbTaskMetaManager::listChosenXStreams).thenReturn(xStreams);
            importApiResource.generateBackFlowMeta(config, importMeta, 1, 2);

            assertEquals(2, importMeta.getBackFlowMetaList().size());

            DataImportMeta.PhysicalMeta backFlowMeta1 = importMeta.getBackFlowMetaList().get(0);
            assertNotNull(backFlowMeta1.getDstHost());
            assertNotNull(backFlowMeta1.getDstPort());
            assertNotNull(backFlowMeta1.getDstUser());
            assertNotNull(backFlowMeta1.getDstPassword());
            assertEquals(HostType.POLARX1, backFlowMeta1.getDstType());
            assertEquals(HostType.POLARX2, backFlowMeta1.getSrcType());
            assertEquals(2, backFlowMeta1.getDstServerId());
            assertEquals("1", backFlowMeta1.getIgnoreServerIds());
            assertEquals(2, backFlowMeta1.getSrcDbList().size());
            assertEquals(2, backFlowMeta1.getDstDbMapping().size());
            assertEquals(2, backFlowMeta1.getPhysicalDoTableList().size());
            assertTrue(backFlowMeta1.getRewriteTableMapping().isEmpty());
            assertEquals("stream1", backFlowMeta1.getStreamName());

            DataImportMeta.PhysicalMeta backFlowMeta2 = importMeta.getBackFlowMetaList().get(1);
            assertNotNull(backFlowMeta2.getDstHost());
            assertNotNull(backFlowMeta2.getDstPort());
            assertNotNull(backFlowMeta2.getDstUser());
            assertNotNull(backFlowMeta2.getDstPassword());
            assertEquals(HostType.POLARX1, backFlowMeta2.getDstType());
            assertEquals(HostType.POLARX2, backFlowMeta2.getSrcType());
            assertEquals(2, backFlowMeta2.getDstServerId());
            assertEquals("1", backFlowMeta2.getIgnoreServerIds());
            assertEquals(2, backFlowMeta2.getSrcDbList().size());
            assertEquals(2, backFlowMeta2.getDstDbMapping().size());
            assertEquals(2, backFlowMeta2.getPhysicalDoTableList().size());
            assertTrue(backFlowMeta2.getRewriteTableMapping().isEmpty());
            assertEquals("stream2", backFlowMeta2.getStreamName());
        }
    }
}
