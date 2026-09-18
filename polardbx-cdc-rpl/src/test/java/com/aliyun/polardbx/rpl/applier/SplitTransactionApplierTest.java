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
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_SPLIT_APPLY_BATCH_MERGE_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_SPLIT_APPLY_IN_TRANSACTION_WITHOUT_GSI;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

public class SplitTransactionApplierTest extends BaseTest {

    private SplitTransactionApplier applier;
    private ApplierConfig applierConfig;
    private HostInfo hostInfo;
    private HostInfo srcHostInfo;

    @Before
    public void setUp() {
        applierConfig = Mockito.mock(ApplierConfig.class);
        hostInfo = Mockito.mock(HostInfo.class);
        srcHostInfo = Mockito.mock(HostInfo.class);
    }

    @Test
    public void testConstructor() {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {
            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);
            Assert.assertNotNull(applier);
        }
    }

    @Test
    public void testDmlApplyWithEmptyList() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {
            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 测试空列表
            applier.dmlApply(new ArrayList<>());
            // 不应该抛出异常
        }
    }

    @Test
    public void testDmlApplyWithNullList() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {
            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 测试null列表
            applier.dmlApply(null);
            // 不应该抛出异常
        }
    }

    @Test
    public void testSplitByTable() {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {
            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 创建测试数据
            List<DBMSEvent> dbmsEvents = new ArrayList<>();

            DefaultRowChange rowChange1 = Mockito.mock(DefaultRowChange.class);
            when(rowChange1.getSchema()).thenReturn("schema1");
            when(rowChange1.getTable()).thenReturn("table1");

            DefaultRowChange rowChange2 = Mockito.mock(DefaultRowChange.class);
            when(rowChange2.getSchema()).thenReturn("schema1");
            when(rowChange2.getTable()).thenReturn("table1");

            DefaultRowChange rowChange3 = Mockito.mock(DefaultRowChange.class);
            when(rowChange3.getSchema()).thenReturn("schema2");
            when(rowChange3.getTable()).thenReturn("table2");

            dbmsEvents.add(rowChange1);
            dbmsEvents.add(rowChange2);
            dbmsEvents.add(rowChange3);

            Map<String, List<DefaultRowChange>> result = applier.splitByTable(dbmsEvents);

            Assert.assertEquals(2, result.size());
            Assert.assertEquals(2, result.get("schema1.table1").size());
            Assert.assertEquals(1, result.get("schema2.table2").size());
        }
    }

    @Test
    public void testWaitAndCheck() {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {
            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            List<Future<Void>> futures = new ArrayList<>();
            // 测试空列表
            applier.waitAndCheck(futures);
            // 不应该抛出异常
        }
    }

    @Test
    public void testContainsNonInnoDBTableOrGsiTable() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {

            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 模拟MysqlApplier中的dbMetaCache
            SplitTransactionApplier mysqlApplier = Mockito.spy(applier);
            DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
            TableInfo tableInfo = Mockito.mock(TableInfo.class);
            when(tableInfo.getEngine()).thenReturn("InnoDB");
            when(tableInfo.getGsiNum()).thenReturn(0);
            when(dbMetaCache.getTableInfo("schema1", "table1")).thenReturn(tableInfo);
            mysqlApplier.dbMetaCache = dbMetaCache;

            // 创建RowQueue
            RowQueue rowQueue = Mockito.mock(RowQueue.class);
            DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
            when(rowChange.getSchema()).thenReturn("schema1");
            when(rowChange.getTable()).thenReturn("table1");
            LinkedList<DefaultRowChange> allRowChanges = new LinkedList<>();
            allRowChanges.add(rowChange);
            when(rowQueue.getAllRowChanges()).thenReturn(allRowChanges);

            boolean result = mysqlApplier.containsNonInnoDBTableOrGsiTable(rowQueue);
            Assert.assertFalse(result);
        }
    }

    @Test
    public void testContainsNonInnoDBTableOrGsiTableWithNonInnoDB() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {

            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 模拟MysqlApplier中的dbMetaCache
            SplitTransactionApplier mysqlApplier = Mockito.spy(applier);
            DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
            TableInfo tableInfo = Mockito.mock(TableInfo.class);
            when(tableInfo.getEngine()).thenReturn("MyISAM"); // 非InnoDB
            when(tableInfo.getGsiNum()).thenReturn(0);
            when(dbMetaCache.getTableInfo("schema1", "table1")).thenReturn(tableInfo);
            mysqlApplier.dbMetaCache = dbMetaCache;

            // 创建RowQueue
            RowQueue rowQueue = Mockito.mock(RowQueue.class);
            DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
            when(rowChange.getSchema()).thenReturn("schema1");
            when(rowChange.getTable()).thenReturn("table1");
            LinkedList<DefaultRowChange> allRowChanges = new LinkedList<>();
            allRowChanges.add(rowChange);
            when(rowQueue.getAllRowChanges()).thenReturn(allRowChanges);

            boolean result = mysqlApplier.containsNonInnoDBTableOrGsiTable(rowQueue);
            Assert.assertTrue(result);
        }
    }

    @Test
    public void testContainsNonInnoDBTableOrGsiTableWithGsi() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {

            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            // 使用BaseTest中的mockConfig方法来模拟配置
            mockConfig(RPL_SPLIT_APPLY_IN_TRANSACTION_WITHOUT_GSI, "true");

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 模拟MysqlApplier中的dbMetaCache
            SplitTransactionApplier mysqlApplier = Mockito.spy(applier);
            DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
            TableInfo tableInfo = Mockito.mock(TableInfo.class);
            when(tableInfo.getEngine()).thenReturn("InnoDB");
            when(tableInfo.getGsiNum()).thenReturn(2); // 有GSI
            when(dbMetaCache.getTableInfo("schema1", "table1")).thenReturn(tableInfo);
            mysqlApplier.dbMetaCache = dbMetaCache;

            // 创建RowQueue
            RowQueue rowQueue = Mockito.mock(RowQueue.class);
            DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
            when(rowChange.getSchema()).thenReturn("schema1");
            when(rowChange.getTable()).thenReturn("table1");
            LinkedList<DefaultRowChange> allRowChanges = new LinkedList<>();
            allRowChanges.add(rowChange);
            when(rowQueue.getAllRowChanges()).thenReturn(allRowChanges);

            boolean result = mysqlApplier.containsNonInnoDBTableOrGsiTable(rowQueue);
            Assert.assertTrue(result);
        }
    }

    @Test
    public void testExecuteWithExplicitTrans() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {

            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            // 使用BaseTest中的mockConfig方法来模拟配置
            mockConfig(RPL_SPLIT_APPLY_BATCH_MERGE_ENABLED, "false");

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 模拟MysqlApplier中的dbMetaCache和dataSource
            SplitTransactionApplier mysqlApplier = Mockito.spy(applier);
            DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
            DataSource dataSource = Mockito.mock(DataSource.class);
            Connection conn = Mockito.mock(Connection.class);
            when(dbMetaCache.getDataSource("")).thenReturn(dataSource);
            when(dataSource.getConnection()).thenReturn(conn);
            when(conn.createStatement()).thenReturn(Mockito.mock(Statement.class));

            TableInfo tableInfo = Mockito.mock(TableInfo.class);
            when(tableInfo.getSchema()).thenReturn("schema1");
            when(tableInfo.getName()).thenReturn("table1");
            when(dbMetaCache.getTableInfo("schema1", "table1")).thenReturn(tableInfo);

            mysqlApplier.dbMetaCache = dbMetaCache;
            DmlApplyHelper.setDbMetaCache(dbMetaCache);

            // 创建RowQueue
            RowQueue rowQueue = Mockito.mock(RowQueue.class);
            LinkedList<DefaultRowChange> allRowChanges = new LinkedList<>();
            DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
            when(rowChange.getAction()).thenReturn(DBMSAction.INSERT);
            when(rowChange.getSchema()).thenReturn("schema1");
            when(rowChange.getTable()).thenReturn("table1");
            allRowChanges.add(rowChange);
            when(rowQueue.getAllRowChanges()).thenReturn(allRowChanges);

            // 测试executeWithExplicitTrans
            mysqlApplier.executeWithExplicitTrans(rowQueue);

            // 验证连接操作
            Mockito.verify(conn).setAutoCommit(false);
            Mockito.verify(conn).commit();
        }
    }

    @Test
    public void testExecuteWithoutExplicitTrans() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {

            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            // 使用BaseTest中的mockConfig方法来模拟配置
            mockConfig(RPL_SPLIT_APPLY_BATCH_MERGE_ENABLED, "false");

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 模拟MysqlApplier中的dbMetaCache和dataSource
            SplitTransactionApplier mysqlApplier = Mockito.spy(applier);
            DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
            DataSource dataSource = Mockito.mock(DataSource.class);
            Connection conn = Mockito.mock(Connection.class);
            when(dbMetaCache.getDataSource("")).thenReturn(dataSource);
            when(dataSource.getConnection()).thenReturn(conn);
            when(conn.createStatement()).thenReturn(Mockito.mock(Statement.class));

            TableInfo tableInfo = Mockito.mock(TableInfo.class);
            when(tableInfo.getSchema()).thenReturn("schema1");
            when(tableInfo.getName()).thenReturn("table1");
            when(dbMetaCache.getTableInfo("schema1", "table1")).thenReturn(tableInfo);

            mysqlApplier.dbMetaCache = dbMetaCache;
            DmlApplyHelper.setDbMetaCache(dbMetaCache);

            // 创建RowQueue
            RowQueue rowQueue = Mockito.mock(RowQueue.class);
            LinkedList<DefaultRowChange> allRowChanges = new LinkedList<>();
            DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
            when(rowChange.getAction()).thenReturn(DBMSAction.INSERT);
            when(rowChange.getSchema()).thenReturn("schema1");
            when(rowChange.getTable()).thenReturn("table1");
            allRowChanges.add(rowChange);
            when(rowQueue.getAllRowChanges()).thenReturn(allRowChanges);

            // 测试executeWithoutExplicitTrans
            mysqlApplier.executeWithoutExplicitTrans(rowQueue);

            // 验证连接操作
            Mockito.verify(conn, Mockito.never()).setAutoCommit(anyBoolean());
        }
    }

    @Test
    public void testExecuteDmlInBatchMergeMode() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {

            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 模拟MysqlApplier中的dbMetaCache
            SplitTransactionApplier mysqlApplier = Mockito.spy(applier);
            DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
            TableInfo tableInfo = Mockito.mock(TableInfo.class);
            when(tableInfo.getSchema()).thenReturn("schema1");
            when(tableInfo.getName()).thenReturn("table1");
            when(dbMetaCache.getTableInfo("schema1", "table1")).thenReturn(tableInfo);
            mysqlApplier.dbMetaCache = dbMetaCache;
            when(applierConfig.getMergeBatchSize()).thenReturn(100);

            Connection conn = Mockito.mock(Connection.class);
            Statement statement = Mockito.mock(Statement.class);
            when(conn.createStatement()).thenReturn(statement);

            // 创建RowBatch
            RowBatch rowBatch = Mockito.mock(RowBatch.class);
            DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
            when(rowChange.getSchema()).thenReturn("schema1");
            when(rowChange.getTable()).thenReturn("table1");
            when(rowChange.getAction()).thenReturn(DBMSAction.INSERT);
            when(rowChange.getColumnSet()).thenReturn(Mockito.mock(DBMSColumnSet.class));

            List<DefaultRowChange> rowChanges = new ArrayList<>();
            rowChanges.add(rowChange);
            when(rowBatch.getRowChanges()).thenReturn(rowChanges);

            // 测试executeDmlInBatchMergeMode
            mysqlApplier.executeDmlInBatchMergeMode(rowBatch, conn);
        }
    }

    @Test
    public void testParallelExecSqlContexts() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {

            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 模拟MysqlApplier中的executorService
            SplitTransactionApplier mysqlApplier = Mockito.spy(applier);
            Collection<RowQueue> rowChanges = new ArrayList<>();
            RowQueue rowQueue = Mockito.mock(RowQueue.class);
            rowChanges.add(rowQueue);

            DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
            DataSource dataSource = Mockito.mock(DataSource.class);
            Connection conn = Mockito.mock(Connection.class);
            when(dataSource.getConnection()).thenReturn(conn);
            when(dbMetaCache.getDataSource(any())).thenReturn(dataSource);
            mysqlApplier.dbMetaCache = dbMetaCache;
            mysqlApplier.executorService = Executors.newSingleThreadExecutor();

            // 测试parallelExecSqlContexts
            mysqlApplier.parallelExecSqlContexts(rowChanges, true);
            // 验证buildTask被调用
            Mockito.verify(mysqlApplier).buildTask(any(RowQueue.class), Mockito.eq(true));
        }
    }

    @Test
    public void testBuildTask() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {

            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 模拟MysqlApplier中的dbMetaCache
            SplitTransactionApplier mysqlApplier = Mockito.spy(applier);
            RowQueue rowQueue = Mockito.mock(RowQueue.class);
            when(rowQueue.getAllRowChanges()).thenReturn(new LinkedList<>());

            // 测试buildTask
            Callable<Void> task = mysqlApplier.buildTask(rowQueue, true);
            Assert.assertNotNull(task);
        }
    }

    @Test
    public void testExecuteDMLInBatchMergeMode() throws Exception {
        try (MockedStatic<TaskContext> mockedTaskContext = Mockito.mockStatic(TaskContext.class)) {

            TaskContext mockTaskContext = Mockito.mock(TaskContext.class);
            when(mockTaskContext.getStateMachineId()).thenReturn(1L);
            when(mockTaskContext.getTaskId()).thenReturn(123L);
            mockedTaskContext.when(TaskContext::getInstance).thenReturn(mockTaskContext);

            applier = new SplitTransactionApplier(applierConfig, hostInfo, srcHostInfo);

            // 模拟MysqlApplier中的dbMetaCache
            SplitTransactionApplier mysqlApplier = Mockito.spy(applier);
            DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
            DataSource dataSource = Mockito.mock(DataSource.class);
            Connection conn = Mockito.mock(Connection.class);
            when(dbMetaCache.getDataSource("")).thenReturn(dataSource);
            when(dataSource.getConnection()).thenReturn(conn);
            mysqlApplier.dbMetaCache = dbMetaCache;

            RowQueue rowQueue = Mockito.mock(RowQueue.class);
            when(rowQueue.getAllRowChanges()).thenReturn(new LinkedList<>());

            // 使用BaseTest中的mockConfig方法来模拟配置
            mockConfig(RPL_SPLIT_APPLY_BATCH_MERGE_ENABLED, "true");

            // 测试executeDMLInBatchMergeMode
            mysqlApplier.executeDMLInBatchMergeMode(rowQueue, conn, true);
        }
    }
}
