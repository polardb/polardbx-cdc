/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_SPLIT_APPLY_GROUP_BY_ACTION_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_SPLIT_APPLY_IN_MULTI_STAGE_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_SPLIT_APPLY_IN_TRANSACTION_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SplitApplierTest extends RplWithGmsTablesBaseTest {

    @Test
    public void testLogSerialExecuteInfo() {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());
        Map<String, List<DefaultRowChange>> map = new HashMap<>();
        map.put("t1", Lists.newArrayList(new DefaultRowChange()));
        splitApplier.logSerialExecuteInfo(map);
        Assert.assertFalse(splitApplier.logSerialExecuteInfo(map));
    }

    @Test
    public void testSplit_WithoutMultiStage() throws Exception {
        // Mock dependencies
        ApplierConfig applierConfig = new ApplierConfig();
        HostInfo hostInfo = new HostInfo();
        HostInfo srcHostInfo = new HostInfo();

        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, hostInfo, srcHostInfo));

        // Mock configuration values
        mockConfig(RPL_SPLIT_APPLY_IN_MULTI_STAGE_ENABLED, "false");

        // Create mock DbMetaCache
        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(dbMetaCache);

        // Create mock TableInfo
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(dbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);

        // Mock table info responses
        when(tableInfo.getPks()).thenReturn(Lists.newArrayList("id"));
        when(tableInfo.getIdentifyKeyList()).thenReturn(Lists.newArrayList("id"));

        // Test case 1: Empty list of events
        List<DBMSEvent> emptyEvents = new ArrayList<>();
        List<SplitStage> result = splitApplier.split(emptyEvents);
        Assert.assertTrue("Split result should be empty for empty input", result.isEmpty());

        // Test case 2: Single INSERT event
        DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
        when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent.getSchema()).thenReturn("test_schema");
        when(insertEvent.getTable()).thenReturn("test_table");

        List<DBMSEvent> singleInsertEvents = Lists.newArrayList(insertEvent);
        result = splitApplier.split(singleInsertEvents);
        Assert.assertEquals("Should have one stage for single event", 1, result.size());
        Assert.assertEquals("Should have one parallel row change", 1, result.get(0).getParallelRowCount());
        Assert.assertTrue("Should have no serial row changes", result.get(0).getAllSerialRowChanges().isEmpty());

        // Test case 3: Multiple events for same table
        DefaultRowChange insertEvent2 = Mockito.mock(DefaultRowChange.class);
        when(insertEvent2.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent2.getSchema()).thenReturn("test_schema");
        when(insertEvent2.getTable()).thenReturn("test_table");

        List<DBMSEvent> multipleEvents = Lists.newArrayList(insertEvent, insertEvent2);
        result = splitApplier.split(multipleEvents);
        Assert.assertEquals("Should have one stage for multiple events of same table", 1, result.size());
        Assert.assertEquals("Should have two parallel row changes", 2, result.get(0).getParallelRowCount());
        Assert.assertEquals("Should have one tables", 1, result.get(0).getAllSplitRowChanges().size());
        Assert.assertTrue("Should have no serial row changes", result.get(0).getAllSerialRowChanges().isEmpty());

        // Test case 4: Events for different tables
        DefaultRowChange insertEvent3 = Mockito.mock(DefaultRowChange.class);
        when(insertEvent3.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent3.getSchema()).thenReturn("test_schema2");
        when(insertEvent3.getTable()).thenReturn("test_table2");

        List<DBMSEvent> differentTableEvents = Lists.newArrayList(insertEvent, insertEvent3);
        result = splitApplier.split(differentTableEvents);
        Assert.assertEquals("Should have one stage for events of different tables", 1, result.size());
        Assert.assertEquals("Should have two parallel row changes", 2, result.get(0).getParallelRowCount());
        Assert.assertEquals("Should have two tables", 2, result.get(0).getAllSplitRowChanges().size());
        Assert.assertTrue("Should have no serial row changes", result.get(0).getAllSerialRowChanges().isEmpty());

        // Test case 5: Event that should be executed serially (no PK table with UPDATE action)
        when(tableInfo.getPks()).thenReturn(new ArrayList<>()); // No primary keys
        DefaultRowChange updateEvent = mock(DefaultRowChange.class);
        when(updateEvent.getAction()).thenReturn(DBMSAction.UPDATE);
        when(updateEvent.getSchema()).thenReturn("test_schema");
        when(updateEvent.getTable()).thenReturn("test_table_no_pk");

        List<DBMSEvent> serialEvents = Lists.newArrayList(updateEvent);
        result = splitApplier.split(serialEvents);
        Assert.assertEquals("Should have one stage", 1, result.size());
        Assert.assertEquals("Should have no parallel row changes", 0, result.get(0).getParallelRowCount());
        Assert.assertEquals("Should have one serial row change", 1, result.get(0).getAllSerialRowChanges().size());

        // Test case 6: Event that should be executed serially (PK table with UPDATE action)
        when(tableInfo.getPks()).thenReturn(Lists.newArrayList("id"));
        DefaultRowChange updateEvent2 = mock(DefaultRowChange.class);
        when(updateEvent2.getAction()).thenReturn(DBMSAction.UPDATE);
        when(updateEvent2.getTable()).thenReturn("test_table");
        when(updateEvent2.getSchema()).thenReturn("test_schema");
        when(updateEvent2.hasChangeColumn(anyInt())).thenReturn(true);

        List<DBMSEvent> serialEvents2 = Lists.newArrayList(updateEvent2);
        result = splitApplier.split(serialEvents2);
        Assert.assertEquals("Should have one stage", 1, result.size());
        Assert.assertEquals("Should have no parallel row changes", 0, result.get(0).getParallelRowCount());
        Assert.assertEquals("Should have one serial row change", 1, result.get(0).getAllSerialRowChanges().size());
    }

    @Test
    public void testGetCurrentStage() {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());

        Map<String, AtomicInteger> tableStageIndex = new HashMap<>();
        Map<Integer, SplitStage> allStages = new HashMap<>();
        String fullTbName = "test_schema.test_table";

        // Test initial stage creation
        SplitStage stage1 = splitApplier.getCurrentStage(fullTbName, false, tableStageIndex, allStages);
        Assert.assertNotNull(stage1);
        Assert.assertEquals(1, allStages.size());
        Assert.assertEquals(1, tableStageIndex.size());
        Assert.assertEquals(0, tableStageIndex.get(fullTbName).get());

        // Test retrieving existing stage
        SplitStage stage2 = splitApplier.getCurrentStage(fullTbName, false, tableStageIndex, allStages);
        Assert.assertSame(stage1, stage2);
        Assert.assertEquals(1, allStages.size());
        Assert.assertEquals(1, tableStageIndex.size());

        // Test creating new stage for multi-stage mode
        tableStageIndex.get(fullTbName).incrementAndGet();
        SplitStage stage3 = splitApplier.getCurrentStage(fullTbName, true, tableStageIndex, allStages);
        Assert.assertNotSame(stage1, stage3);
        Assert.assertEquals(2, allStages.size());
        Assert.assertEquals(1, tableStageIndex.size());
        Assert.assertEquals(1, tableStageIndex.get(fullTbName).get());
    }

    @Test
    public void testTreeMap() {
        Map<Integer, Integer> map = new TreeMap<>();
        map.put(10, 10);
        map.put(4, 4);
        map.put(1, 1);
        map.put(5, 5);
        map.put(3, 3);
        map.put(7, 7);
        map.put(6, 6);
        map.put(9, 9);
        map.put(8, 8);
        map.put(2, 2);
        List<Integer> list = Lists.newArrayList(map.values());
        for (int i = 0; i < list.size(); i++) {
            Assert.assertEquals(i + 1, list.get(i).intValue());
        }
    }

    // ... existing code ...

    @Test
    public void testDmlApply_NullEvents() throws Exception {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());
        splitApplier.dmlApply(null);
        // Should not throw exception
    }

    @Test
    public void testDmlApply_EmptyEvents() throws Exception {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());
        splitApplier.dmlApply(new ArrayList<>());
        // Should not throw exception
    }

    @Test
    public void testDmlApply_WithEvents() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        applierConfig.setMaxPoolSize(2);
        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()));

        // Mock configuration
        mockConfig(RPL_SPLIT_APPLY_IN_MULTI_STAGE_ENABLED, "false");
        mockConfig(RPL_SPLIT_APPLY_GROUP_BY_ACTION_ENABLED, "false");
        mockConfig(RPL_SPLIT_APPLY_IN_TRANSACTION_ENABLED, "false");

        // Mock DbMetaCache
        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(dbMetaCache);

        // Mock TableInfo
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(dbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getPks()).thenReturn(Lists.newArrayList("id"));
        when(tableInfo.getIdentifyKeyList()).thenReturn(Lists.newArrayList("id"));
        when(tableInfo.getEngine()).thenReturn("InnoDB");
        when(tableInfo.getGsiNum()).thenReturn(0);

        // Mock DataSource
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        when(dbMetaCache.getDataSource(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);

        // Create test event
        DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
        when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent.getSchema()).thenReturn("test_schema");
        when(insertEvent.getTable()).thenReturn("test_table");

        List<DBMSEvent> events = Lists.newArrayList(insertEvent);

        // Mock the executeOneStage to avoid actual execution
        Mockito.doNothing().when(splitApplier).executeOneStage(any(SplitStage.class));

        splitApplier.dmlApply(events);

        Mockito.verify(splitApplier, Mockito.times(1)).executeOneStage(any(SplitStage.class));
    }

    @Test
    public void testExecuteOneStage_WithGroupByAction() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        applierConfig.setMaxPoolSize(2);

        mockConfig(RPL_SPLIT_APPLY_GROUP_BY_ACTION_ENABLED, "true");
        mockConfig(RPL_SPLIT_APPLY_IN_TRANSACTION_ENABLED, "false");

        // Mock dependencies
        DbMetaCache mockDbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(mockDbMetaCache);
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(mockDbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getEngine()).thenReturn("InnoDB");
        when(tableInfo.getGsiNum()).thenReturn(0);
        when(tableInfo.getSchema()).thenReturn("test_schema");
        when(tableInfo.getName()).thenReturn("test_table");

        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        when(mockDbMetaCache.getDataSource(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(Mockito.mock(Statement.class));

        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, newHostInfo(), newHostInfo()) {
            @Override
            public void init() {
                this.dbMetaCache = mockDbMetaCache;
                this.executorService = Executors.newSingleThreadExecutor();
            }
        });
        splitApplier.init();

        // Create test stage
        SplitStage stage = new SplitStage(false);
        DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
        when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent.getSchema()).thenReturn("test_schema");
        when(insertEvent.getTable()).thenReturn("test_table");
        when(insertEvent.getRowSize()).thenReturn(1);

        stage.addRowChange4ParallelApply("test_schema.test_table", insertEvent, Lists.newArrayList(0));

        splitApplier.executeOneStage(stage);
    }

    @Test
    public void testExecuteOneStage_WithoutGroupByAction() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        applierConfig.setMaxPoolSize(2);

        mockConfig(RPL_SPLIT_APPLY_GROUP_BY_ACTION_ENABLED, "false");
        mockConfig(RPL_SPLIT_APPLY_IN_TRANSACTION_ENABLED, "false");

        // Mock dependencies
        DbMetaCache mockDbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(mockDbMetaCache);
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(mockDbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getEngine()).thenReturn("InnoDB");
        when(tableInfo.getGsiNum()).thenReturn(0);
        when(tableInfo.getSchema()).thenReturn("test_schema");
        when(tableInfo.getName()).thenReturn("test_table");

        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        when(mockDbMetaCache.getDataSource(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(Mockito.mock(Statement.class));

        // Create test stage
        SplitStage stage = new SplitStage(false);
        DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
        when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent.getSchema()).thenReturn("test_schema");
        when(insertEvent.getTable()).thenReturn("test_table");
        when(insertEvent.getRowSize()).thenReturn(1);
        when(insertEvent.getColumnSet()).thenReturn(Mockito.mock(DBMSColumnSet.class));

        stage.addRowChange4ParallelApply("test_schema.test_table", insertEvent, Lists.newArrayList(0));

        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()) {
            @Override
            public void init() {
                this.dbMetaCache = mockDbMetaCache;
                this.executorService = Executors.newSingleThreadExecutor();
            }
        });
        splitApplier.init();
        splitApplier.executeOneStage(stage);
    }

    @Test
    public void testBuildStagingRowQueue() {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());

        Map<String, Map<RowKey, List<DefaultRowChange>>> allSplitRowChanges = new HashMap<>();

        // Create test data
        DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
        when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent.getSchema()).thenReturn("test_schema");
        when(insertEvent.getTable()).thenReturn("test_table");
        when(insertEvent.getRowSize()).thenReturn(1);

        RowKey rowKey = new RowKey(insertEvent, Lists.newArrayList(0));
        Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges = new HashMap<>();
        tbSplitRowChanges.put(rowKey, Lists.newArrayList(insertEvent));
        allSplitRowChanges.put("test_schema.test_table", tbSplitRowChanges);

        StagingRowQueue stagingRowQueue = splitApplier.buildStagingRowQueue(allSplitRowChanges);

        Assert.assertNotNull(stagingRowQueue);
        Assert.assertEquals(1, stagingRowQueue.getPureInsertRowChangeSize());
    }

    @Test
    public void testExecutePureInsertOrDeleteRowChanges_Empty() {
        SplitApplier splitApplier = Mockito.spy(new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo()));

        Map<String, List<DefaultRowChange>> stagedRowChanges = new HashMap<>();

        splitApplier.executePureInsertOrDeleteRowChanges(stagedRowChanges, 0, false);
        // Should not throw exception
    }

    @Test
    public void testExecutePureInsertOrDeleteRowChanges_WithData() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        applierConfig.setMaxPoolSize(2);

        // Mock dependencies
        DbMetaCache mockDbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(mockDbMetaCache);
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(mockDbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getEngine()).thenReturn("InnoDB");
        when(tableInfo.getGsiNum()).thenReturn(0);
        when(tableInfo.getSchema()).thenReturn("test_schema");
        when(tableInfo.getName()).thenReturn("test_table");

        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        when(mockDbMetaCache.getDataSource(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(Mockito.mock(Statement.class));

        // Create test data
        DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
        when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent.getSchema()).thenReturn("test_schema");
        when(insertEvent.getTable()).thenReturn("test_table");
        when(insertEvent.getRowSize()).thenReturn(1);
        when(insertEvent.getColumnSet()).thenReturn(Mockito.mock(DBMSColumnSet.class));

        Map<String, List<DefaultRowChange>> stagedRowChanges = new HashMap<>();
        stagedRowChanges.put("test_schema.test_table", Lists.newArrayList(insertEvent));

        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()) {
            @Override
            public void init() {
                this.dbMetaCache = mockDbMetaCache;
                this.executorService = Executors.newSingleThreadExecutor();
            }
        });
        splitApplier.init();
        splitApplier.executePureInsertOrDeleteRowChanges(stagedRowChanges, 1, false);
    }

    @Test
    public void testExecuteMixedRowChanges_Empty() {
        SplitApplier splitApplier = Mockito.spy(new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo()));

        Map<String, Map<RowKey, List<DefaultRowChange>>> stagedRowChanges = new HashMap<>();

        splitApplier.executeMixedRowChanges(stagedRowChanges, 0, false);
        // Should not throw exception
    }

    @Test
    public void testExecuteMixedRowChanges_WithData() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        applierConfig.setMaxPoolSize(2);

        // Mock dependencies
        DbMetaCache mockDbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(mockDbMetaCache);
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(mockDbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getEngine()).thenReturn("InnoDB");
        when(tableInfo.getGsiNum()).thenReturn(0);
        when(tableInfo.getSchema()).thenReturn("test_schema");
        when(tableInfo.getName()).thenReturn("test_table");

        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        when(mockDbMetaCache.getDataSource(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(Mockito.mock(Statement.class));

        // Create test data
        DefaultRowChange updateEvent = Mockito.mock(DefaultRowChange.class);
        when(updateEvent.getAction()).thenReturn(DBMSAction.UPDATE);
        when(updateEvent.getSchema()).thenReturn("test_schema");
        when(updateEvent.getTable()).thenReturn("test_table");
        when(updateEvent.getRowSize()).thenReturn(1);
        when(updateEvent.getColumnSet()).thenReturn(Mockito.mock(DBMSColumnSet.class));

        RowKey rowKey = new RowKey(updateEvent, Lists.newArrayList(0));
        Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges = new HashMap<>();
        tbSplitRowChanges.put(rowKey, Lists.newArrayList(updateEvent));

        Map<String, Map<RowKey, List<DefaultRowChange>>> stagedRowChanges = new HashMap<>();
        stagedRowChanges.put("test_schema.test_table", tbSplitRowChanges);

        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()) {
            @Override
            public void init() {
                this.dbMetaCache = mockDbMetaCache;
                this.executorService = Executors.newSingleThreadExecutor();
            }
        });
        splitApplier.init();
        splitApplier.executeMixedRowChanges(stagedRowChanges, 1, false);
    }

    @Test
    public void testSplit_WithMultiStage() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()));

        mockConfig(RPL_SPLIT_APPLY_IN_MULTI_STAGE_ENABLED, "true");

        // Mock DbMetaCache
        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(dbMetaCache);

        // Mock TableInfo
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(dbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getPks()).thenReturn(Lists.newArrayList("id"));
        when(tableInfo.getIdentifyKeyList()).thenReturn(Lists.newArrayList("id"));

        // Test with UPDATE event that changes identify column
        DefaultRowChange updateEvent1 = Mockito.mock(DefaultRowChange.class);
        when(updateEvent1.getAction()).thenReturn(DBMSAction.UPDATE);
        when(updateEvent1.getSchema()).thenReturn("test_schema");
        when(updateEvent1.getTable()).thenReturn("test_table");
        when(updateEvent1.hasChangeColumn(anyInt())).thenReturn(true);

        // Add another event after the identify column change
        DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
        when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent.getSchema()).thenReturn("test_schema");
        when(insertEvent.getTable()).thenReturn("test_table");

        List<DBMSEvent> events = Lists.newArrayList(updateEvent1, insertEvent);
        List<SplitStage> result = splitApplier.split(events);

        Assert.assertEquals("Should have two stages in multi-stage mode", 2, result.size());
        Assert.assertEquals("First stage should have one serial row change", 1,
            result.get(0).getAllSerialRowChanges().size());
        Assert.assertEquals("Second stage should have one parallel row change", 1, result.get(1).getParallelRowCount());
    }

    @Test
    public void testSplit_WithDeleteEvent() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()));

        mockConfig(RPL_SPLIT_APPLY_IN_MULTI_STAGE_ENABLED, "false");

        // Mock DbMetaCache
        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(dbMetaCache);

        // Mock TableInfo with unique key
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(dbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getPks()).thenReturn(Lists.newArrayList("id"));
        when(tableInfo.getIdentifyKeyList()).thenReturn(Lists.newArrayList("id"));
        when(tableInfo.getUks()).thenReturn(Lists.newArrayList("uk1"));

        // Test with DELETE event on table with unique key
        DefaultRowChange deleteEvent = Mockito.mock(DefaultRowChange.class);
        when(deleteEvent.getAction()).thenReturn(DBMSAction.DELETE);
        when(deleteEvent.getSchema()).thenReturn("test_schema");
        when(deleteEvent.getTable()).thenReturn("test_table");

        List<DBMSEvent> events = Lists.newArrayList(deleteEvent);
        List<SplitStage> result = splitApplier.split(events);

        Assert.assertEquals("Should have one stage", 1, result.size());
    }

    @Test
    public void testSplit_WithInsertAfterDelete() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()));

        mockConfig(RPL_SPLIT_APPLY_IN_MULTI_STAGE_ENABLED, "false");

        // Mock DbMetaCache
        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(dbMetaCache);

        // Mock TableInfo with unique key
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(dbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getPks()).thenReturn(Lists.newArrayList("id"));
        when(tableInfo.getIdentifyKeyList()).thenReturn(Lists.newArrayList("id"));
        when(tableInfo.getUks()).thenReturn(Lists.newArrayList("uk1"));

        // Test with DELETE followed by INSERT on table with unique key
        DefaultRowChange deleteEvent = Mockito.mock(DefaultRowChange.class);
        when(deleteEvent.getAction()).thenReturn(DBMSAction.DELETE);
        when(deleteEvent.getSchema()).thenReturn("test_schema");
        when(deleteEvent.getTable()).thenReturn("test_table");

        DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
        when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent.getSchema()).thenReturn("test_schema");
        when(insertEvent.getTable()).thenReturn("test_table");

        List<DBMSEvent> events = Lists.newArrayList(deleteEvent, insertEvent);
        List<SplitStage> result = splitApplier.split(events);

        Assert.assertEquals("Should have one stage", 1, result.size());
        Assert.assertTrue("INSERT should be serial after DELETE",
            result.get(0).getAllSerialRowChanges().size() > 0);
    }

    @Test
    public void testGetLogger() {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());
        Assert.assertNotNull(splitApplier.getLogger());
    }

    @Test
    public void testExecuteOneStage_WithSerialRowChanges() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        applierConfig.setMaxPoolSize(2);

        mockConfig(RPL_SPLIT_APPLY_GROUP_BY_ACTION_ENABLED, "false");
        mockConfig(RPL_SPLIT_APPLY_IN_TRANSACTION_ENABLED, "false");

        // Mock dependencies
        DbMetaCache mockDbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(mockDbMetaCache);
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(mockDbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getEngine()).thenReturn("InnoDB");
        when(tableInfo.getGsiNum()).thenReturn(0);
        when(tableInfo.getSchema()).thenReturn("test_schema");
        when(tableInfo.getName()).thenReturn("test_table");

        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        when(mockDbMetaCache.getDataSource(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(Mockito.mock(Statement.class));

        // Create test stage with serial row changes
        SplitStage stage = new SplitStage(false);
        DefaultRowChange updateEvent = Mockito.mock(DefaultRowChange.class);
        when(updateEvent.getAction()).thenReturn(DBMSAction.UPDATE);
        when(updateEvent.getSchema()).thenReturn("test_schema");
        when(updateEvent.getTable()).thenReturn("test_table");
        when(updateEvent.getRowSize()).thenReturn(1);
        when(updateEvent.getColumnSet()).thenReturn(Mockito.mock(DBMSColumnSet.class));

        stage.addRowChange4SerialApply("test_schema.test_table", updateEvent);
        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()) {
            @Override
            public void init() {
                this.dbMetaCache = mockDbMetaCache;
                this.executorService = Executors.newSingleThreadExecutor();
            }
        });
        splitApplier.init();

        splitApplier.executeOneStage(stage);
    }

    @Test
    public void testBuildStagingRowQueue_WithMultipleTables() {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());

        Map<String, Map<RowKey, List<DefaultRowChange>>> allSplitRowChanges = new HashMap<>();

        // Create test data for multiple tables
        DefaultRowChange insertEvent1 = Mockito.mock(DefaultRowChange.class);
        when(insertEvent1.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent1.getSchema()).thenReturn("test_schema");
        when(insertEvent1.getTable()).thenReturn("test_table1");
        when(insertEvent1.getRowSize()).thenReturn(1);

        DefaultRowChange insertEvent2 = Mockito.mock(DefaultRowChange.class);
        when(insertEvent2.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent2.getSchema()).thenReturn("test_schema");
        when(insertEvent2.getTable()).thenReturn("test_table2");
        when(insertEvent2.getRowSize()).thenReturn(1);

        RowKey rowKey1 = new RowKey(insertEvent1, Lists.newArrayList(0));
        RowKey rowKey2 = new RowKey(insertEvent2, Lists.newArrayList(0));

        Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges1 = new HashMap<>();
        tbSplitRowChanges1.put(rowKey1, Lists.newArrayList(insertEvent1));
        allSplitRowChanges.put("test_schema.test_table1", tbSplitRowChanges1);

        Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges2 = new HashMap<>();
        tbSplitRowChanges2.put(rowKey2, Lists.newArrayList(insertEvent2));
        allSplitRowChanges.put("test_schema.test_table2", tbSplitRowChanges2);

        StagingRowQueue stagingRowQueue = splitApplier.buildStagingRowQueue(allSplitRowChanges);

        Assert.assertNotNull(stagingRowQueue);
        Assert.assertEquals(2, stagingRowQueue.getPureInsertRowChangeSize());
    }

    @Test
    public void testBuildStagingRowQueue_WithDeleteEvents() {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());

        Map<String, Map<RowKey, List<DefaultRowChange>>> allSplitRowChanges = new HashMap<>();

        // Create test data with DELETE events
        DefaultRowChange deleteEvent = Mockito.mock(DefaultRowChange.class);
        when(deleteEvent.getAction()).thenReturn(DBMSAction.DELETE);
        when(deleteEvent.getSchema()).thenReturn("test_schema");
        when(deleteEvent.getTable()).thenReturn("test_table");
        when(deleteEvent.getRowSize()).thenReturn(1);

        RowKey rowKey = new RowKey(deleteEvent, Lists.newArrayList(0));
        Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges = new HashMap<>();
        tbSplitRowChanges.put(rowKey, Lists.newArrayList(deleteEvent));
        allSplitRowChanges.put("test_schema.test_table", tbSplitRowChanges);

        StagingRowQueue stagingRowQueue = splitApplier.buildStagingRowQueue(allSplitRowChanges);

        Assert.assertNotNull(stagingRowQueue);
        Assert.assertEquals(1, stagingRowQueue.getPureDeleteRowChangeSize());
    }

    @Test
    public void testBuildStagingRowQueue_WithMixedEvents() {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());

        Map<String, Map<RowKey, List<DefaultRowChange>>> allSplitRowChanges = new HashMap<>();

        // Create test data with mixed events (INSERT and UPDATE)
        DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
        when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent.getSchema()).thenReturn("test_schema");
        when(insertEvent.getTable()).thenReturn("test_table");
        when(insertEvent.getRowSize()).thenReturn(1);

        DefaultRowChange updateEvent = Mockito.mock(DefaultRowChange.class);
        when(updateEvent.getAction()).thenReturn(DBMSAction.UPDATE);
        when(updateEvent.getSchema()).thenReturn("test_schema");
        when(updateEvent.getTable()).thenReturn("test_table");
        when(updateEvent.getRowSize()).thenReturn(1);

        RowKey rowKey = new RowKey(insertEvent, Lists.newArrayList(0));
        Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges = new HashMap<>();
        tbSplitRowChanges.put(rowKey, Lists.newArrayList(insertEvent, updateEvent));
        allSplitRowChanges.put("test_schema.test_table", tbSplitRowChanges);

        StagingRowQueue stagingRowQueue = splitApplier.buildStagingRowQueue(allSplitRowChanges);

        Assert.assertNotNull(stagingRowQueue);
        Assert.assertEquals(2, stagingRowQueue.getHoldingRowChangeSize());
    }

    @Test
    public void testExecuteInGroupByActionMode_WithAllTypes() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        applierConfig.setMaxPoolSize(2);

        mockConfig(RPL_SPLIT_APPLY_IN_TRANSACTION_ENABLED, "false");

        // Mock dependencies
        DbMetaCache mockDbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(mockDbMetaCache);
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(mockDbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getEngine()).thenReturn("InnoDB");
        when(tableInfo.getGsiNum()).thenReturn(0);
        when(tableInfo.getSchema()).thenReturn("test_schema");
        when(tableInfo.getName()).thenReturn("test_table");

        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        when(mockDbMetaCache.getDataSource(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(Mockito.mock(Statement.class));

        // Create test stage with INSERT, DELETE, and UPDATE events
        SplitStage stage = new SplitStage(false);

        DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
        when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent.getSchema()).thenReturn("test_schema");
        when(insertEvent.getTable()).thenReturn("test_table");
        when(insertEvent.getRowSize()).thenReturn(1);
        when(insertEvent.getColumnSet()).thenReturn(Mockito.mock(DBMSColumnSet.class));

        DefaultRowChange deleteEvent = Mockito.mock(DefaultRowChange.class);
        when(deleteEvent.getAction()).thenReturn(DBMSAction.DELETE);
        when(deleteEvent.getSchema()).thenReturn("test_schema");
        when(deleteEvent.getTable()).thenReturn("test_table");
        when(deleteEvent.getRowSize()).thenReturn(1);
        when(deleteEvent.getColumnSet()).thenReturn(Mockito.mock(DBMSColumnSet.class));

        DefaultRowChange updateEvent = Mockito.mock(DefaultRowChange.class);
        when(updateEvent.getAction()).thenReturn(DBMSAction.UPDATE);
        when(updateEvent.getSchema()).thenReturn("test_schema");
        when(updateEvent.getTable()).thenReturn("test_table");
        when(updateEvent.getRowSize()).thenReturn(1);
        when(updateEvent.getColumnSet()).thenReturn(Mockito.mock(DBMSColumnSet.class));

        stage.addRowChange4ParallelApply("test_schema.test_table", insertEvent, Lists.newArrayList(0));
        stage.addRowChange4ParallelApply("test_schema.test_table", deleteEvent, Lists.newArrayList(0));
        stage.addRowChange4ParallelApply("test_schema.test_table", updateEvent, Lists.newArrayList(0));

        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()) {
            @Override
            public void init() {
                this.dbMetaCache = mockDbMetaCache;
                this.executorService = Executors.newSingleThreadExecutor();
            }
        });
        splitApplier.init();

        splitApplier.executeInGroupByActionMode(stage.getAllSplitRowChanges(), false);
    }

    @Test
    public void testSplit_WithMultipleTablesAndEvents() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()));

        mockConfig(RPL_SPLIT_APPLY_IN_MULTI_STAGE_ENABLED, "false");

        // Mock DbMetaCache
        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(dbMetaCache);

        // Mock TableInfo
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(dbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getPks()).thenReturn(Lists.newArrayList("id"));
        when(tableInfo.getIdentifyKeyList()).thenReturn(Lists.newArrayList("id"));

        // Test with multiple events for multiple tables
        DefaultRowChange insertEvent1 = Mockito.mock(DefaultRowChange.class);
        when(insertEvent1.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent1.getSchema()).thenReturn("test_schema");
        when(insertEvent1.getTable()).thenReturn("test_table1");

        DefaultRowChange insertEvent2 = Mockito.mock(DefaultRowChange.class);
        when(insertEvent2.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent2.getSchema()).thenReturn("test_schema");
        when(insertEvent2.getTable()).thenReturn("test_table2");

        DefaultRowChange insertEvent3 = Mockito.mock(DefaultRowChange.class);
        when(insertEvent3.getAction()).thenReturn(DBMSAction.INSERT);
        when(insertEvent3.getSchema()).thenReturn("test_schema");
        when(insertEvent3.getTable()).thenReturn("test_table1");

        List<DBMSEvent> events = Lists.newArrayList(insertEvent1, insertEvent2, insertEvent3);
        List<SplitStage> result = splitApplier.split(events);

        Assert.assertEquals("Should have one stage", 1, result.size());
        Assert.assertEquals("Should have three parallel row changes", 3, result.get(0).getParallelRowCount());
        Assert.assertEquals("Should have two tables", 2, result.get(0).getAllSplitRowChanges().size());
    }

    @Test
    public void testSplit_WithIdentifyColumnChange() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()));

        mockConfig(RPL_SPLIT_APPLY_IN_MULTI_STAGE_ENABLED, "false");

        // Mock DbMetaCache
        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(dbMetaCache);

        // Mock TableInfo
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(dbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getPks()).thenReturn(Lists.newArrayList("id"));
        when(tableInfo.getIdentifyKeyList()).thenReturn(Lists.newArrayList("id", "name"));

        // Test with UPDATE event that changes identify column
        DefaultRowChange updateEvent = Mockito.mock(DefaultRowChange.class);
        when(updateEvent.getAction()).thenReturn(DBMSAction.UPDATE);
        when(updateEvent.getSchema()).thenReturn("test_schema");
        when(updateEvent.getTable()).thenReturn("test_table");
        when(updateEvent.hasChangeColumn(anyInt())).thenReturn(true);

        List<DBMSEvent> events = Lists.newArrayList(updateEvent);
        List<SplitStage> result = splitApplier.split(events);

        Assert.assertEquals("Should have one stage", 1, result.size());
        Assert.assertEquals("Should have one serial row change", 1, result.get(0).getAllSerialRowChanges().size());
    }

    @Test
    public void testExecutePureInsertOrDeleteRowChanges_WithMultipleQueues() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        applierConfig.setMaxPoolSize(3);

        // Mock dependencies
        DbMetaCache mockDbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(mockDbMetaCache);
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(mockDbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getEngine()).thenReturn("InnoDB");
        when(tableInfo.getGsiNum()).thenReturn(0);
        when(tableInfo.getSchema()).thenReturn("test_schema");
        when(tableInfo.getName()).thenReturn("test_table");

        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        when(mockDbMetaCache.getDataSource(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(Mockito.mock(Statement.class));

        // Create test data with multiple events
        Map<String, List<DefaultRowChange>> stagedRowChanges = new HashMap<>();
        List<DefaultRowChange> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            DefaultRowChange insertEvent = Mockito.mock(DefaultRowChange.class);
            when(insertEvent.getAction()).thenReturn(DBMSAction.INSERT);
            when(insertEvent.getSchema()).thenReturn("test_schema");
            when(insertEvent.getTable()).thenReturn("test_table");
            when(insertEvent.getRowSize()).thenReturn(1);
            when(insertEvent.getColumnSet()).thenReturn(Mockito.mock(DBMSColumnSet.class));
            events.add(insertEvent);
        }
        stagedRowChanges.put("test_schema.test_table", events);

        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, new HostInfo(), new HostInfo()) {
            @Override
            public void init() {
                this.dbMetaCache = mockDbMetaCache;
                this.executorService = Executors.newSingleThreadExecutor();
            }
        });
        splitApplier.init();
        splitApplier.executePureInsertOrDeleteRowChanges(stagedRowChanges, 10, false);
    }

    @Test
    public void testExecuteMixedRowChanges_WithMultipleQueues() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        applierConfig.setMaxPoolSize(3);

        // Mock dependencies
        DbMetaCache mockDbMetaCache = Mockito.mock(DbMetaCache.class);
        DmlApplyHelper.setDbMetaCache(mockDbMetaCache);
        TableInfo tableInfo = Mockito.mock(TableInfo.class);
        when(mockDbMetaCache.getTableInfo(anyString(), anyString())).thenReturn(tableInfo);
        when(tableInfo.getEngine()).thenReturn("InnoDB");
        when(tableInfo.getGsiNum()).thenReturn(0);
        when(tableInfo.getSchema()).thenReturn("test_schema");
        when(tableInfo.getName()).thenReturn("test_table");

        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        when(mockDbMetaCache.getDataSource(anyString())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);

        SplitApplier splitApplier = Mockito.spy(new SplitApplier(applierConfig, newHostInfo(), newHostInfo()) {
            @Override
            public void init() {
                this.dbMetaCache = mockDbMetaCache;
                this.executorService = Executors.newSingleThreadExecutor();
            }
        });
        splitApplier.init();

        // Create test data with multiple events
        Map<String, Map<RowKey, List<DefaultRowChange>>> stagedRowChanges = new HashMap<>();
        Map<RowKey, List<DefaultRowChange>> tbSplitRowChanges = new HashMap<>();

        for (int i = 0; i < 10; i++) {
            DefaultRowChange updateEvent = Mockito.mock(DefaultRowChange.class);
            when(updateEvent.getAction()).thenReturn(DBMSAction.UPDATE);
            when(updateEvent.getSchema()).thenReturn("test_schema");
            when(updateEvent.getTable()).thenReturn("test_table");
            when(updateEvent.getRowSize()).thenReturn(1);
            when(updateEvent.getColumnSet()).thenReturn(Mockito.mock(DBMSColumnSet.class));

            RowKey rowKey = new RowKey(updateEvent, Lists.newArrayList(0));
            tbSplitRowChanges.put(rowKey, Lists.newArrayList(updateEvent));
        }
        stagedRowChanges.put("test_schema.test_table", tbSplitRowChanges);

        splitApplier.executeMixedRowChanges(stagedRowChanges, 10, false);
    }

    HostInfo newHostInfo() {
        HostInfo hostInfo = new HostInfo();
        hostInfo.setHost("127.0.0.1");
        hostInfo.setPort(3306);
        hostInfo.setUserName("root");
        hostInfo.setType(HostType.POLARX2);
        hostInfo.setSchema("test");
        hostInfo.setUsePolarxPoolCN(true);
        hostInfo.setServerId(111L);
        hostInfo.setPassword("xxx");
        return hostInfo;
    }
}
