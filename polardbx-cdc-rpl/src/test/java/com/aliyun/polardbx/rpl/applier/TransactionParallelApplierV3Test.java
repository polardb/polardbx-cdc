/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowData;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.RepoUnit;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.ConflictStrategy;
import com.aliyun.polardbx.rpl.taskmeta.ConflictType;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.PersistConfig;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.lang3.tuple.Pair;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TransactionParallelApplierV3 单元测试
 * <p>
 * 核心策略：不使用 MockedStatic<DmlApplyHelper>（线程局部，不适用于 ExecutorService 子线程），
 * 而是通过 DmlApplyHelper.setDbMetaCache 设置静态字段 + mock Connection/Statement 链，
 * 使 DmlApplyHelper.executeDML 在子线程中也能正常执行。
 */
public class TransactionParallelApplierV3Test {

    private MockedStatic<DynamicApplicationConfig> mockedAppConfig;
    private ApplierConfig applierConfig;
    private HostInfo hostInfo;
    private HostInfo srcHostInfo;
    private DbMetaCache dbMetaCache;
    private DataSource dataSource;
    private Connection connection;
    private Statement statement;
    private PreparedStatement preparedStatement;

    @Before
    public void setUp() throws Exception {
        mockedAppConfig = mockStatic(DynamicApplicationConfig.class, CALLS_REAL_METHODS);
        mockConfig(ConfigKeys.RPL_ROCKSDB_DESERIALIZE_PARALLELISM, "4");
        mockConfig(ConfigKeys.IS_LAB_ENV, "false");
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "true");
        mockConfig(ConfigKeys.RPL_BATCH_ENABLED, "true");
        mockConfig(ConfigKeys.RPL_COMPACTION_LOG_ENABLED, "false");
        mockConfig(ConfigKeys.RPL_PERSIST_ENABLED, "false");

        applierConfig = mock(ApplierConfig.class);
        when(applierConfig.getLogCommitLevel()).thenReturn(0);
        when(applierConfig.getTransactionEventBatchSize()).thenReturn(1000);
        when(applierConfig.getDmlBatchSize()).thenReturn(1000);
        when(applierConfig.getConflictStrategy()).thenReturn(ConflictStrategy.OVERWRITE);

        hostInfo = mock(HostInfo.class);
        srcHostInfo = mock(HostInfo.class);

        dbMetaCache = mock(DbMetaCache.class);
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        preparedStatement = mock(PreparedStatement.class);

        when(dbMetaCache.getBuiltInDefaultDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(dbMetaCache.getMaxPoolSize()).thenReturn(2);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeUpdate(anyString())).thenReturn(1);
        when(statement.execute(anyString())).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // 设置 DmlApplyHelper 的静态 dbMetaCache，使其在子线程中也可用
        DmlApplyHelper.setDbMetaCache(dbMetaCache);
    }

    @After
    public void tearDown() {
        DmlApplyHelper.setDbMetaCache(null);
        if (mockedAppConfig != null) {
            mockedAppConfig.close();
        }
    }

    private void mockConfig(String key, String value) {
        mockedAppConfig.when(() -> DynamicApplicationConfig.getValue(key)).thenReturn(value);
        Assert.assertEquals(value, DynamicApplicationConfig.getString(key));
    }

    // ========= tranApply 基础路径测试 =========

    @Test
    public void testRejectDirectOverwrite() {
        when(applierConfig.getConflictStrategy()).thenReturn(ConflictStrategy.DIRECT_OVERWRITE);

        try {
            new TransactionParallelApplierV3(applierConfig, hostInfo, srcHostInfo);
            Assert.fail("Expected DIRECT_OVERWRITE to be rejected");
        } catch (PolardbxException e) {
            Assert.assertTrue(e.getMessage().contains("does not support DIRECT_OVERWRITE"));
        }
    }

    @Test
    public void testTranApplyEmptyList() throws Exception {
        TransactionParallelApplierV3 applier = createApplier();
        applier.tranApply(new ArrayList<>());
    }

    @Test
    public void testTranApplyDdlOnly() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        doNothing().when(spy).ddlApply(any());

        DefaultQueryLog queryLog = mock(DefaultQueryLog.class);
        Transaction tx = createMockDdlTransaction(queryLog);

        spy.tranApply(Collections.singletonList(tx));
        verify(spy, times(1)).ddlApply(queryLog);
    }

    @Test
    public void testTranApplyMultipleDdls() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        doNothing().when(spy).ddlApply(any());

        DefaultQueryLog ddl1 = mock(DefaultQueryLog.class);
        Transaction ddlTx1 = createMockDdlTransaction(ddl1);
        DefaultQueryLog ddl2 = mock(DefaultQueryLog.class);
        Transaction ddlTx2 = createMockDdlTransaction(ddl2);

        spy.tranApply(Arrays.asList(ddlTx1, ddlTx2));
        verify(spy, times(1)).ddlApply(ddl1);
        verify(spy, times(1)).ddlApply(ddl2);
    }

    @Test
    public void testTranApplyMixedDdlAndDml() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        doNothing().when(spy).ddlApply(any());
        setupDefaultTableInfo();

        Transaction dmlTx1 = createRealDmlTransaction(
            createMockRowChange("db1", "t1", DBMSAction.INSERT));

        DefaultQueryLog ddlEvent = mock(DefaultQueryLog.class);
        Transaction ddlTx = createMockDdlTransaction(ddlEvent);

        Transaction dmlTx2 = createRealDmlTransaction(
            createMockRowChange("db1", "t1", DBMSAction.INSERT));

        spy.tranApply(Arrays.asList(dmlTx1, ddlTx, dmlTx2));
        verify(spy, times(1)).ddlApply(ddlEvent);
    }

    // ========= applyDmlBatch: size==1 路径 =========

    @Test
    public void testTranApplySingleDmlEntersCompactionPath() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        // 开关在构造时已读取为 true；运行中修改配置不应影响当前实例。
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        setupDefaultTableInfo();
        clearInvocations(dbMetaCache);

        Transaction dmlTx = createRealDmlTransaction(
            createMockRowChange("db1", "t1", DBMSAction.INSERT));

        spy.tranApply(Collections.singletonList(dmlTx));
        verify(dbMetaCache, times(1)).getMaxPoolSize();
    }

    @Test
    public void testTranApplySingleTransactionCompactsIntraTxHotKey() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange firstUpdate = createMockUpdateWithSamePK("db1", "t1", "pk1");
        DefaultRowChange lastUpdate = createMockUpdateWithSamePK("db1", "t1", "pk1");
        when(lastUpdate.isForceAllColumns()).thenReturn(true);
        Transaction transaction = new Transaction(null, null);
        transaction.appendRowChange(firstUpdate);
        transaction.appendRowChange(lastUpdate);
        transaction.setFinished(true);

        spy.tranApply(Collections.singletonList(transaction));

        verify(lastUpdate, times(1)).setForceAllColumns(true);
    }

    @Test
    public void testTranApplySingleDmlSkipsCompactionWhenDisabled() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        TransactionParallelApplierV3 spy = createSpy();
        setupDefaultTableInfo();
        clearInvocations(dbMetaCache);

        Transaction dmlTx = createRealDmlTransaction(
            createMockRowChange("db1", "t1", DBMSAction.INSERT));

        spy.tranApply(Collections.singletonList(dmlTx));
        verify(dbMetaCache, never()).getMaxPoolSize();
    }

    @Test
    public void testInterruptUpdateMissThrowsPolardbxException() throws Exception {
        DefaultRowChange rowChange = createMockRowChange("db1", "t1", DBMSAction.UPDATE);

        try {
            DmlApplyHelper.handleDupException(connection, rowChange, ConflictStrategy.INTERRUPT,
                ConflictType.UPDATE_MISSED, null);
            Assert.fail("Expected UPDATE_MISSED to interrupt write");
        } catch (PolardbxException e) {
            Assert.assertTrue(e.getMessage().contains("UPDATE_MISSED"));
        }
    }

    @Test
    public void testCompactedUpdateMissInterrupts() throws Exception {
        assertCompactedUpdateMissStrategy(ConflictStrategy.INTERRUPT);
    }

    @Test
    public void testCompactedUpdateMissIsIgnored() throws Exception {
        assertCompactedUpdateMissStrategy(ConflictStrategy.IGNORE);
    }

    @Test
    public void testCompactedUpdateMissOverwritesWithCombinedFinalState() throws Exception {
        assertCompactedUpdateMissStrategy(ConflictStrategy.OVERWRITE);
    }

    @Test
    public void testBatchUpdateMissInterruptsAndRollsBack() throws Exception {
        assertBatchUpdateMissStrategy(ConflictStrategy.INTERRUPT);
    }

    @Test
    public void testBatchUpdateMissIsIgnored() throws Exception {
        assertBatchUpdateMissStrategy(ConflictStrategy.IGNORE);
    }

    @Test
    public void testBatchUpdateMissOverwritesMissingRow() throws Exception {
        assertBatchUpdateMissStrategy(ConflictStrategy.OVERWRITE);
    }

    // ========= applyDmlBatch: size>1 路径 (DAG parallel) =========

    @Test
    public void testTranApplyMultipleDmlParallel() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        Transaction tx1 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "v1"));
        Transaction tx2 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "v2"));

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    @Test
    public void testTableWithoutPkOrUkFallsBackToSerialApply() throws Exception {
        mockConfig(ConfigKeys.RPL_COLS_UPDATE_MODE, "ONUPDATE");

        JdbcDataSource targetDataSource = new JdbcDataSource();
        targetDataSource.setURL("jdbc:h2:mem:keyless_serial;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        try (Connection conn = targetDataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute("CREATE SCHEMA db1");
            stmt.execute("CREATE TABLE db1.t1 (name VARCHAR(32), content VARCHAR(32))");
            stmt.execute("INSERT INTO db1.t1 VALUES ('n0', 'c0')");
        }

        registerTableInfo(realKeylessTableInfo());
        when(dbMetaCache.getBuiltInDefaultDataSource()).thenReturn(targetDataSource);

        DefaultColumnSet columns = realKeylessColumnSet();
        DefaultRowChange first = realUpdate(columns,
            new Serializable[] {"n0", "c0"}, 1, "n1");
        DefaultRowChange second = realUpdate(columns,
            new Serializable[] {"n1", "c0"}, 2, "c1");

        TransactionParallelApplierV3 spy = createSpy();
        spy.executorService.shutdownNow();
        ExecutorService dagExecutor = mock(ExecutorService.class);
        spy.executorService = dagExecutor;
        spy.tranApply(Arrays.asList(createRealDmlTransaction(first), createRealDmlTransaction(second)));

        verifyNoInteractions(dagExecutor);
        try (Connection conn = targetDataSource.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT name, content FROM db1.t1")) {
            Assert.assertTrue(rs.next());
            Assert.assertEquals("n1", rs.getString("name"));
            Assert.assertEquals("c1", rs.getString("content"));
            Assert.assertFalse(rs.next());
        }
    }

    @Test
    public void testUnsupportedKeyMetadataFallsBackToSerialApply() throws Exception {
        mockConfig(ConfigKeys.RPL_COLS_UPDATE_MODE, "ONUPDATE");

        JdbcDataSource targetDataSource = new JdbcDataSource();
        targetDataSource.setURL(
            "jdbc:h2:mem:unsupported_uk_serial;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        try (Connection conn = targetDataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute("CREATE SCHEMA db1");
            stmt.execute("CREATE TABLE db1.t1 (id BIGINT PRIMARY KEY, uk_col VARCHAR(32) UNIQUE, "
                + "name VARCHAR(32), content VARCHAR(32))");
            stmt.execute("INSERT INTO db1.t1 VALUES (1, 'A', 'n0', 'c0')");
        }

        TableInfo tableInfo = realCompactionTableInfo();
        tableInfo.setParallelApplyKeyUnsupported(true);
        tableInfo.setParallelApplyKeyIncompatibleReason(
            "unique index uk_name uses prefix column uk_col(16)");
        registerTableInfo(tableInfo);
        when(dbMetaCache.getBuiltInDefaultDataSource()).thenReturn(targetDataSource);

        DefaultColumnSet columns = realCompactionColumnSet();
        DefaultRowChange first = realUpdate(columns,
            new Serializable[] {1L, "A", "n0", "c0"}, 3, "n1");
        DefaultRowChange second = realUpdate(columns,
            new Serializable[] {1L, "A", "n1", "c0"}, 4, "c1");

        TransactionParallelApplierV3 spy = createSpy();
        spy.executorService.shutdownNow();
        ExecutorService dagExecutor = mock(ExecutorService.class);
        spy.executorService = dagExecutor;
        spy.tranApply(Arrays.asList(createRealDmlTransaction(first), createRealDmlTransaction(second)));

        verifyNoInteractions(dagExecutor);
        try (Connection conn = targetDataSource.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT uk_col, name, content FROM db1.t1 WHERE id=1")) {
            Assert.assertTrue(rs.next());
            Assert.assertEquals("A", rs.getString("uk_col"));
            Assert.assertEquals("n1", rs.getString("name"));
            Assert.assertEquals("c1", rs.getString("content"));
            Assert.assertFalse(rs.next());
        }
    }

    @Test
    public void testTranApplyMultipleDmlWithDependency() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        Transaction tx1 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "sameKey"));
        Transaction tx2 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "sameKey"));

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    @Test
    public void testTranApplyUpdateExtractsBeforeAndAfterKeys() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange updateEvent = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(updateEvent.getRowValue(1, 0)).thenReturn("oldVal");
        when(updateEvent.getChangeValue(1, 0)).thenReturn("newVal");
        Transaction tx1 = createRealDmlTransaction(updateEvent);

        Transaction tx2 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "otherVal"));

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    @Test
    public void testTranApplyWithUkGroups() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkAndUkTableInfo();

        DefaultRowChange updateEvent = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(updateEvent.getRowValue(1, 0)).thenReturn("pk1");
        when(updateEvent.getChangeValue(1, 0)).thenReturn("pk1");
        when(updateEvent.getRowValue(1, 1)).thenReturn("ukBefore1");
        when(updateEvent.getRowValue(1, 2)).thenReturn("ukBefore2");
        when(updateEvent.getChangeValue(1, 1)).thenReturn("ukAfter1");
        when(updateEvent.getChangeValue(1, 2)).thenReturn("ukAfter2");
        Transaction tx1 = createRealDmlTransaction(updateEvent);

        DefaultRowChange dml2 = createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "other");
        when(dml2.getRowValue(1, 1)).thenReturn("ukOther1");
        when(dml2.getRowValue(1, 2)).thenReturn("ukOther2");
        Transaction tx2 = createRealDmlTransaction(dml2);

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    @Test
    public void testTranApplyWithUkNullValues() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkAndUkTableInfoSingleColumn();

        DefaultRowChange dml1 = createMockRowChange("db1", "t1", DBMSAction.INSERT);
        when(dml1.getRowValue(1, 0)).thenReturn("pk1");
        when(dml1.getRowValue(1, 1)).thenReturn(null);
        Transaction tx1 = createRealDmlTransaction(dml1);

        DefaultRowChange dml2 = createMockRowChange("db1", "t1", DBMSAction.INSERT);
        when(dml2.getRowValue(1, 0)).thenReturn("pk2");
        when(dml2.getRowValue(1, 1)).thenReturn(null);
        Transaction tx2 = createRealDmlTransaction(dml2);

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    @Test
    public void testTranApplyWithUkNullInUpdate() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkAndUkTableInfoSingleColumn();

        DefaultRowChange updateEvent = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(updateEvent.getRowValue(1, 0)).thenReturn("pk1");
        when(updateEvent.getChangeValue(1, 0)).thenReturn("pk1");
        when(updateEvent.getRowValue(1, 1)).thenReturn(null);
        when(updateEvent.getChangeValue(1, 1)).thenReturn(null);
        Transaction tx1 = createRealDmlTransaction(updateEvent);

        DefaultRowChange dml2 = createMockRowChange("db1", "t1", DBMSAction.INSERT);
        when(dml2.getRowValue(1, 0)).thenReturn("pk2");
        when(dml2.getRowValue(1, 1)).thenReturn(null);
        Transaction tx2 = createRealDmlTransaction(dml2);

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    @Test
    public void testTranApplyWithZeroEventTransaction() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupDefaultTableInfo();

        // 单个零事件事务走 dmlBatch size==1 → super.tranApply 路径
        Transaction emptyTx = new Transaction(null, null);
        emptyTx.setFinished(true);

        spy.tranApply(Collections.singletonList(emptyTx));
    }

    @Test
    public void testTranApplyOnlyZeroEventDmlBatch() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupDefaultTableInfo();

        // 两个零事件事务走 DAG 路径 (size==2)
        Transaction emptyTx1 = new Transaction(null, null);
        emptyTx1.setFinished(true);
        Transaction emptyTx2 = new Transaction(null, null);
        emptyTx2.setFinished(true);

        spy.tranApply(Arrays.asList(emptyTx1, emptyTx2));
    }

    // ========= 错误处理 =========

    @Test
    public void testTranApplyParallelWithError() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        // 让连接抛异常，触发 parallelApply 中的 hasError 分支
        when(dataSource.getConnection()).thenThrow(new RuntimeException("connection error"));

        Transaction tx1 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "v1"));
        Transaction tx2 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "v2"));

        try {
            spy.tranApply(Arrays.asList(tx1, tx2));
            Assert.fail("Expected exception");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof PolardbxException || e instanceof RuntimeException);
        }
    }

    // ========= DDL 屏障测试 =========

    @Test
    public void testTranApplyDdlBarrier() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        doNothing().when(spy).ddlApply(any());
        setupPkTableInfo();

        Transaction tx1 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "a"));
        Transaction tx2 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "b"));

        DefaultQueryLog ddl = mock(DefaultQueryLog.class);
        Transaction ddlTx = createMockDdlTransaction(ddl);

        Transaction tx3 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "c"));
        Transaction tx4 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "d"));

        spy.tranApply(Arrays.asList(tx1, tx2, ddlTx, tx3, tx4));
        verify(spy, times(1)).ddlApply(ddl);
    }

    // ========= extractAffectedKeys 不同场景 =========

    @Test
    public void testTranApplyEmptyPkColumns() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();

        TableInfo tableInfo = createBaseTableInfo();
        when(tableInfo.getPks()).thenReturn(Collections.emptyList());
        when(tableInfo.getPkColumnsIndex(any())).thenReturn(Collections.emptyList());
        when(tableInfo.getUkGroups()).thenReturn(Collections.emptyList());
        when(tableInfo.getUkGroupColumnsIndex(any())).thenReturn(Collections.emptyList());
        registerTableInfo(tableInfo);

        Transaction tx1 = createRealDmlTransaction(
            createMockRowChange("db1", "t1", DBMSAction.INSERT));
        Transaction tx2 = createRealDmlTransaction(
            createMockRowChange("db1", "t1", DBMSAction.INSERT));

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    @Test
    public void testTranApplyDeleteAction() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        Transaction tx1 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.DELETE, "pk1"));
        Transaction tx2 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.DELETE, "pk2"));

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    @Test
    public void testTranApplyDeleteWithUkNonNull() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkAndUkTableInfoSingleColumn();

        DefaultRowChange dml1 = createMockRowChange("db1", "t1", DBMSAction.DELETE);
        when(dml1.getRowValue(1, 0)).thenReturn("pk1");
        when(dml1.getRowValue(1, 1)).thenReturn("ukVal");
        Transaction tx1 = createRealDmlTransaction(dml1);

        DefaultRowChange dml2 = createMockRowChange("db1", "t1", DBMSAction.DELETE);
        when(dml2.getRowValue(1, 0)).thenReturn("pk2");
        when(dml2.getRowValue(1, 1)).thenReturn("ukVal2");
        Transaction tx2 = createRealDmlTransaction(dml2);

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    // ========= 通过反射测试私有方法 extractAffectedKeys =========

    @Test
    public void testExtractAffectedKeysDirectly() throws Exception {
        TransactionParallelApplierV3 applier = createApplier();
        setupPkAndUkTableInfo();

        // 多行事件的 UPDATE
        DefaultRowChange updateEvent = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(updateEvent.getRowValue(1, 0)).thenReturn("pk1");
        when(updateEvent.getChangeValue(1, 0)).thenReturn("pk2");
        when(updateEvent.getRowValue(1, 1)).thenReturn("uk1");
        when(updateEvent.getRowValue(1, 2)).thenReturn("uk2");
        when(updateEvent.getChangeValue(1, 1)).thenReturn("uk3");
        when(updateEvent.getChangeValue(1, 2)).thenReturn("uk4");

        Transaction tx = new Transaction(null, null);
        tx.appendRowChange(updateEvent);
        tx.setFinished(true);

        Method extractMethod = TransactionParallelApplierV3.class.getDeclaredMethod(
            "extractAffectedKeys", Transaction.class, Set.class);
        extractMethod.setAccessible(true);

        Set<Object> keys = new HashSet<>();
        extractMethod.invoke(applier, tx, keys);

        // PK before + PK after + UK before + UK after = 4 keys
        Assert.assertTrue(keys.size() >= 4);
    }

    @Test
    public void testCharacterUkDagNormalizationDoesNotMutateRowImages() throws Exception {
        TransactionParallelApplierV3 applier = createApplier();
        try {
            TableInfo tableInfo = Mockito.spy(realCompactionTableInfo());
            registerTableInfo(tableInfo);
            DefaultColumnSet columns = realCompactionColumnSet();

            DefaultRowChange releaseUk = new DefaultRowChange(DBMSAction.DELETE, "db1", "t1", columns);
            releaseUk.addRowData(new DefaultRowData(new Serializable[] {1L, "Alice ", "n1", "c1"}));
            DefaultRowChange acquireUk = new DefaultRowChange(DBMSAction.INSERT, "db1", "t1", columns);
            acquireUk.addRowData(new DefaultRowData(new Serializable[] {2L, "alice", "n2", "c2"}));

            Method buildGraph = TransactionParallelApplierV3.class.getDeclaredMethod(
                "buildDependencyGraph", List.class, boolean.class);
            buildGraph.setAccessible(true);
            List<?> graph = (List<?>) buildGraph.invoke(applier,
                Arrays.asList(createRealDmlTransaction(releaseUk), createRealDmlTransaction(acquireUk)), false);

            Field originalIndegree = graph.get(1).getClass().getDeclaredField("originalIndegree");
            originalIndegree.setAccessible(true);
            Assert.assertEquals("normalization must add the UK release/acquire dependency",
                1, originalIndegree.getInt(graph.get(1)));

            // Normalization is a derived DAG key only; actual events remain available to SQL with exact values.
            Assert.assertEquals("Alice ", releaseUk.getRowValue(1, "uk_col"));
            Assert.assertEquals("alice", acquireUk.getRowValue(1, "uk_col"));
            verify(tableInfo, times(1)).getColumns();
        } finally {
            applier.executorService.shutdownNow();
        }
    }

    @Test
    public void testCharacterDagNormalizationDoesNotMergeCompactionIdentity() throws Exception {
        TransactionParallelApplierV3 applier = createApplier();
        try {
            TableInfo tableInfo = new TableInfo("db1", "t1");
            tableInfo.setEngine("InnoDB");
            tableInfo.setPks(Collections.singletonList("id"));
            tableInfo.setColumns(Arrays.asList(
                new ColumnInfo("id", Types.VARCHAR, null, false, false, "VARCHAR", 32),
                new ColumnInfo("payload", Types.VARCHAR, null, true, false, "VARCHAR", 32)));
            registerTableInfo(tableInfo);

            DefaultColumnSet columns = new DefaultColumnSet(Arrays.asList(
                new DefaultColumn("id", 1, Types.VARCHAR, false, false, true),
                new DefaultColumn("payload", 2, Types.VARCHAR, false, true, false)));
            DefaultRowChange upper = new DefaultRowChange(DBMSAction.INSERT, "db1", "t1", columns);
            upper.addRowData(new DefaultRowData(new Serializable[] {"A", "upper"}));
            DefaultRowChange lower = new DefaultRowChange(DBMSAction.INSERT, "db1", "t1", columns);
            lower.addRowData(new DefaultRowData(new Serializable[] {"a", "lower"}));

            Method buildGraph = TransactionParallelApplierV3.class.getDeclaredMethod(
                "buildDependencyGraph", List.class, boolean.class);
            buildGraph.setAccessible(true);
            List<?> graph = (List<?>) buildGraph.invoke(applier,
                Arrays.asList(createRealDmlTransaction(upper), createRealDmlTransaction(lower)), false);

            Field originalIndegree = graph.get(1).getClass().getDeclaredField("originalIndegree");
            originalIndegree.setAccessible(true);
            Assert.assertEquals(1, originalIndegree.getInt(graph.get(1)));

            Field cachedPkKeys = graph.get(0).getClass().getDeclaredField("cachedPkKeys");
            cachedPkKeys.setAccessible(true);
            Set<?> upperCompactionKeys = (Set<?>) cachedPkKeys.get(graph.get(0));
            Set<?> lowerCompactionKeys = (Set<?>) cachedPkKeys.get(graph.get(1));
            Assert.assertTrue("compaction identity must continue to distinguish exact source values",
                Collections.disjoint(upperCompactionKeys, lowerCompactionKeys));
            Assert.assertEquals("A", upper.getRowValue(1, "id"));
            Assert.assertEquals("a", lower.getRowValue(1, "id"));
        } finally {
            applier.executorService.shutdownNow();
        }
    }

    // ========= TableKey equals/hashCode/toString =========

    @Test
    public void testTableKeyEqualsHashCodeToString() throws Exception {
        Class<?> tableKeyClass = getInnerClass("TableKey");
        Constructor<?> ctor = tableKeyClass.getDeclaredConstructor(String.class, String.class, Map.class);
        ctor.setAccessible(true);

        Map<String, Serializable> keyValues1 = new HashMap<>();
        keyValues1.put("id", "1");
        Object key1 = ctor.newInstance("db1", "t1", keyValues1);

        Map<String, Serializable> keyValues2 = new HashMap<>();
        keyValues2.put("id", "1");
        Object key2 = ctor.newInstance("db1", "t1", keyValues2);

        Map<String, Serializable> keyValues3 = new HashMap<>();
        keyValues3.put("id", "2");
        Object key3 = ctor.newInstance("db1", "t1", keyValues3);

        Assert.assertEquals(key1, key2);
        Assert.assertNotEquals(key1, key3);
        Assert.assertNotEquals(key1, null);
        Assert.assertNotEquals(key1, "notATableKey");
        Assert.assertEquals(key1, key1);
        Assert.assertEquals(key1.hashCode(), key2.hashCode());

        String str = key1.toString();
        Assert.assertTrue(str.contains("db1"));
        Assert.assertTrue(str.contains("t1"));
    }

    @Test
    public void testTableKeyUsesDeepValueSemanticsForBinaryIdentity() throws Exception {
        Class<?> tableKeyClass = getInnerClass("TableKey");
        Constructor<?> ctor = tableKeyClass.getDeclaredConstructor(String.class, String.class, Map.class);
        ctor.setAccessible(true);

        Map<String, Serializable> firstValues = new HashMap<>();
        firstValues.put("id", new byte[] {1, 2, 3});
        Object first = ctor.newInstance("db1", "t1", firstValues);

        Map<String, Serializable> secondValues = new HashMap<>();
        secondValues.put("id", new byte[] {1, 2, 3});
        Object second = ctor.newInstance("db1", "t1", secondValues);

        Map<String, Serializable> differentValues = new HashMap<>();
        differentValues.put("id", new byte[] {1, 2, 4});
        Object different = ctor.newInstance("db1", "t1", differentValues);

        Assert.assertEquals(first, second);
        Assert.assertEquals(first.hashCode(), second.hashCode());
        Assert.assertNotEquals(first, different);

        firstValues.put("id", new byte[] {9});
        Assert.assertEquals("TableKey must snapshot mutable input maps", first, second);
    }

    @Test
    public void testCompactionLogSwitch() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        Logger logger = (Logger) LoggerFactory.getLogger(TransactionParallelApplierV3.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Transaction first = createRealDmlTransaction(
                createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "first"));
            Transaction second = createRealDmlTransaction(
                createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "second"));
            spy.tranApply(Arrays.asList(first, second));

            Assert.assertFalse(appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.startsWith("[Graph]") || message.startsWith("[Exec]")
                    || message.startsWith("[Compact] window")));

            appender.list.clear();
            mockConfig(ConfigKeys.RPL_COMPACTION_LOG_ENABLED, "true");
            DefaultRowChange firstUpdate = createMockUpdateWithSamePK("db1", "t1", "pk1");
            configureUpdateColumns(firstUpdate, "pk1", "v1");
            DefaultRowChange lastUpdate = createMockUpdateWithSamePK("db1", "t1", "pk1");
            configureUpdateColumns(lastUpdate, "pk1", "v2");
            spy.tranApply(Arrays.asList(createRealDmlTransaction(firstUpdate),
                createRealDmlTransaction(lastUpdate)));

            List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.toList());
            Assert.assertTrue(messages.stream().anyMatch(message -> message.startsWith("[Graph] Build DAG")));
            Assert.assertTrue(messages.stream().anyMatch(message -> message.startsWith("[Graph] TotalTx")));
            Assert.assertTrue(messages.stream().anyMatch(message -> message.startsWith("[Graph] Top Hot Keys")));
            Assert.assertTrue(messages.stream().anyMatch(message -> message.startsWith("[Exec] All TX done")));
            Assert.assertTrue(messages.stream().anyMatch(message -> message.startsWith("[Compact] window")));
            verify(lastUpdate).setForceAllColumns(true);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void testTableKeyDifferentSchemaTable() throws Exception {
        Class<?> tableKeyClass = getInnerClass("TableKey");
        Constructor<?> ctor = tableKeyClass.getDeclaredConstructor(String.class, String.class, Map.class);
        ctor.setAccessible(true);

        Map<String, Serializable> keyValues = new HashMap<>();
        keyValues.put("id", "1");

        Object key1 = ctor.newInstance("db1", "t1", keyValues);
        Object key2 = ctor.newInstance("db2", "t1", keyValues);
        Object key3 = ctor.newInstance("db1", "t2", keyValues);

        Assert.assertNotEquals(key1, key2);
        Assert.assertNotEquals(key1, key3);
    }

    // ========= 多表场景 =========

    @Test
    public void testTranApplyMultipleTablesNoDependency() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();

        // 设置两个表的 TableInfo
        TableInfo tableInfo1 = createBaseTableInfo();
        when(tableInfo1.getPks()).thenReturn(Collections.singletonList("id"));
        when(tableInfo1.getPkColumnsIndex(any())).thenReturn(Collections.singletonList(0));
        when(tableInfo1.getUkGroups()).thenReturn(Collections.emptyList());
        when(tableInfo1.getUkGroupColumnsIndex(any())).thenReturn(Collections.emptyList());

        TableInfo tableInfo2 = mock(TableInfo.class);
        when(tableInfo2.getSchema()).thenReturn("db1");
        when(tableInfo2.getName()).thenReturn("t2");
        when(tableInfo2.getEngine()).thenReturn("InnoDB");
        when(tableInfo2.getWithTypeKeyList()).thenReturn(Collections.emptyList());
        when(tableInfo2.getPks()).thenReturn(Collections.singletonList("id"));
        when(tableInfo2.getPkColumnsIndex(any())).thenReturn(Collections.singletonList(0));
        when(tableInfo2.getUkGroups()).thenReturn(Collections.emptyList());
        when(tableInfo2.getUkGroupColumnsIndex(any())).thenReturn(Collections.emptyList());

        when(dbMetaCache.getTableInfo("db1", "t1")).thenReturn(tableInfo1);
        when(dbMetaCache.getTableInfo("db1.t1")).thenReturn(tableInfo1);
        when(dbMetaCache.getTableInfo("db1", "t2")).thenReturn(tableInfo2);
        when(dbMetaCache.getTableInfo("db1.t2")).thenReturn(tableInfo2);

        Transaction tx1 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "a"));
        Transaction tx2 = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t2", DBMSAction.INSERT, "b"));

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    // ========= mergeConsecutiveUpdates 合并测试 =========

    @Test
    public void testMergeConsecutiveUpdates_SameKeySameTable() throws Exception {
        // 同一 PK 连续 3 次 UPDATE，应合并为 1 次
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange upd1 = createMockUpdateWithSamePK("db1", "t1", "pk1");
        configureUpdateColumns(upd1, "pk1", "v1");
        DefaultRowChange upd2 = createMockUpdateWithSamePK("db1", "t1", "pk1");
        configureUpdateColumns(upd2, "pk1", "v2");
        DefaultRowChange upd3 = createMockUpdateWithSamePK("db1", "t1", "pk1");
        configureUpdateColumns(upd3, "pk1", "v3");

        Transaction tx1 = createRealDmlTransaction(upd1);
        Transaction tx2 = createRealDmlTransaction(upd2);
        Transaction tx3 = createRealDmlTransaction(upd3);

        // 增加一个不同 key 的事务使 batch > 1
        Transaction txOther = createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "otherKey"));

        // 4 个事务 → 合并后应为 2 个 (pk1 最后一次 + otherKey)
        spy.tranApply(Arrays.asList(tx1, tx2, tx3, txOther));

        // 合并后仅 2 次事务应用（每次应用恰好取 1 个连接）：pk1 合并链 1 次 + otherKey 1 次
        verify(dataSource, times(2)).getConnection();
        // supersede 幸存者是最后一次 UPDATE（upd3），且仅它被标记全列更新
        verify(upd3, times(1)).setForceAllColumns(true);
        verify(upd1, never()).setForceAllColumns(true);
        verify(upd2, never()).setForceAllColumns(true);
        // 只执行 1 条 UPDATE SQL，且参数为 upd3 的终值 v3（v1/v2 被合并消除）
        verify(preparedStatement, times(1)).executeUpdate();
        verify(preparedStatement).setObject(1, "v3");
        verify(preparedStatement, never()).setObject(1, "v1");
        verify(preparedStatement, never()).setObject(1, "v2");
        // otherKey 的 INSERT 正常执行（无参 SQL 走 Statement 路径）
        verify(statement, times(1)).executeUpdate(contains("INSERT"));
    }

    @Test
    public void testMergeConsecutiveUpdates_DifferentKeyNoMerge() throws Exception {
        // 不同 PK 的 UPDATE 不应合并
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange upd1 = createMockUpdateWithSamePK("db1", "t1", "pk1");
        configureUpdateColumns(upd1, "pk1", "v1");
        DefaultRowChange upd2 = createMockUpdateWithSamePK("db1", "t1", "pk2");
        configureUpdateColumns(upd2, "pk2", "v2");

        Transaction tx1 = createRealDmlTransaction(upd1);
        Transaction tx2 = createRealDmlTransaction(upd2);

        spy.tranApply(Arrays.asList(tx1, tx2));

        // 不同 PK 不合并：两个事务各自独立应用
        verify(dataSource, times(2)).getConnection();
        // 未发生任何 supersede，幸存者标记不应被调用
        verify(upd1, never()).setForceAllColumns(true);
        verify(upd2, never()).setForceAllColumns(true);
        // 两条 UPDATE 都实际执行，且各自参数保留（内容断言证明两条都没被吞掉）
        verify(preparedStatement, times(2)).executeUpdate();
        verify(preparedStatement).setObject(1, "v1");
        verify(preparedStatement).setObject(1, "v2");
    }

    @Test
    public void testMergeConsecutiveUpdates_PkChangedNoMerge() throws Exception {
        // PK 变更的 UPDATE 不参与合并
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange upd1 = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(upd1.getRowValue(1, 0)).thenReturn("oldPk");
        when(upd1.getChangeValue(1, 0)).thenReturn("newPk"); // PK changed

        DefaultRowChange upd2 = createMockUpdateWithSamePK("db1", "t1", "oldPk");

        Transaction tx1 = createRealDmlTransaction(upd1);
        Transaction tx2 = createRealDmlTransaction(upd2);

        spy.tranApply(Arrays.asList(tx1, tx2));
    }

    @Test
    public void testMergeConsecutiveUpdates_UkChangedBreaksChain() throws Exception {
        // 同一 PK 的 UK 连续迁移可能承担唯一键让位语义，不能 supersede 中间 UPDATE
        TransactionParallelApplierV3 spy = createSpy();
        setupPkAndUkTableInfoSingleColumn();

        DefaultRowChange upd1 = createMockUpdateWithSamePK("db1", "t1", "pk1");
        when(upd1.getColumnIndex("uk_col")).thenReturn(1);
        when(upd1.getRowValue(1, 1)).thenReturn("a");
        when(upd1.getChangeValue(1, 1)).thenReturn("b");

        DefaultRowChange upd2 = createMockUpdateWithSamePK("db1", "t1", "pk1");
        when(upd2.getColumnIndex("uk_col")).thenReturn(1);
        when(upd2.getRowValue(1, 1)).thenReturn("b");
        when(upd2.getChangeValue(1, 1)).thenReturn("c");

        spy.tranApply(Arrays.asList(createRealDmlTransaction(upd1), createRealDmlTransaction(upd2)));

        verify(upd1, never()).setForceAllColumns(true);
        verify(upd2, never()).setForceAllColumns(true);
    }

    @Test
    public void testPkChangeBarrierFinalizesSupersededUpdateSegment() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange firstUpdate = createMockUpdateWithSamePK("db1", "t1", "pk1");
        DefaultRowChange survivor = createMockUpdateWithSamePK("db1", "t1", "pk1");
        DefaultRowChange barrier = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(barrier.getRowValue(1, 0)).thenReturn("pk1");
        when(barrier.getChangeValue(1, 0)).thenReturn("pk2");

        spy.tranApply(Arrays.asList(
            createRealDmlTransaction(firstUpdate),
            createRealDmlTransaction(survivor),
            createRealDmlTransaction(barrier)));

        verify(firstUpdate, never()).setForceAllColumns(true);
        verify(survivor, times(1)).setForceAllColumns(true);
        verify(barrier, never()).setForceAllColumns(true);
    }

    @Test
    public void testPkChangeBarrierFinalizesSupersededUpdateSegmentWhenBatchDisabled() throws Exception {
        mockConfig(ConfigKeys.RPL_BATCH_ENABLED, "false");
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange firstUpdate = createMockUpdateWithSamePK("db1", "t1", "pk1");
        DefaultRowChange survivor = createMockUpdateWithSamePK("db1", "t1", "pk1");
        DefaultRowChange barrier = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(barrier.getRowValue(1, 0)).thenReturn("pk1");
        when(barrier.getChangeValue(1, 0)).thenReturn("pk2");

        spy.tranApply(Arrays.asList(
            createRealDmlTransaction(firstUpdate),
            createRealDmlTransaction(survivor),
            createRealDmlTransaction(barrier)));

        verify(firstUpdate, never()).setForceAllColumns(true);
        verify(survivor, times(1)).setForceAllColumns(true);
        verify(barrier, never()).setForceAllColumns(true);
    }

    @Test
    public void testUkChangeBarrierPreservesCompactedFinalStateWhenBatchEnabled() throws Exception {
        assertUkChangeBarrierPreservesCompactedFinalState(true);
    }

    @Test
    public void testUkChangeBarrierPreservesCompactedFinalStateWhenBatchDisabled() throws Exception {
        assertUkChangeBarrierPreservesCompactedFinalState(false);
    }

    @Test
    public void testPersistedUkChangeBarrierPreservesCompactedFinalState() throws Exception {
        assertUkChangeBarrierPreservesCompactedFinalState(false, true);
    }

    @Test
    public void testBinaryPkCompactionKeepsLastUpdate() throws Exception {
        mockConfig(ConfigKeys.RPL_BATCH_ENABLED, "true");
        mockConfig(ConfigKeys.RPL_COLS_UPDATE_MODE, "ONUPDATE");

        JdbcDataSource targetDataSource = new JdbcDataSource();
        targetDataSource.setURL("jdbc:h2:mem:binary_pk_compaction;MODE=MySQL;DB_CLOSE_DELAY=-1;"
            + "DATABASE_TO_LOWER=TRUE");
        try (Connection conn = targetDataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute("CREATE SCHEMA db1");
            stmt.execute("CREATE TABLE db1.t1 (id VARBINARY(8) PRIMARY KEY, payload VARCHAR(32))");
            stmt.execute("INSERT INTO db1.t1 VALUES (X'0102', 'p0')");
        }

        registerTableInfo(realBinaryPkTableInfo());
        when(dbMetaCache.getBuiltInDefaultDataSource()).thenReturn(targetDataSource);

        DefaultColumnSet columns = realBinaryPkColumnSet();
        DefaultRowChange first = realUpdate(columns,
            new Serializable[] {new byte[] {1, 2}, "p0"}, 2, "p1");
        DefaultRowChange survivor = realUpdate(columns,
            new Serializable[] {new byte[] {1, 2}, "p1"}, 2, "p2");
        Transaction transaction = new Transaction(null, null);
        transaction.appendRowChange(first);
        transaction.appendRowChange(survivor);
        transaction.setFinished(true);

        TransactionParallelApplierV3 spy = createSpy();
        spy.executorService.shutdownNow();
        spy.executorService = MoreExecutors.newDirectExecutorService();
        spy.tranApply(Collections.singletonList(transaction));

        try (Connection conn = targetDataSource.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT payload FROM db1.t1 WHERE id=X'0102'")) {
            Assert.assertTrue(rs.next());
            Assert.assertEquals("p2", rs.getString("payload"));
            Assert.assertFalse(rs.next());
        }
        Assert.assertTrue("binary PK events must form one compaction chain", survivor.isForceAllColumns());
    }

    @Test
    public void testUkChangeBarrierFinalizesSupersededUpdateSegment() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkAndUkTableInfoSingleColumn();

        DefaultRowChange firstUpdate = createMockUpdateWithSamePK("db1", "t1", "pk1");
        configureUkValues(firstUpdate, "uk1", "uk1");
        DefaultRowChange survivor = createMockUpdateWithSamePK("db1", "t1", "pk1");
        configureUkValues(survivor, "uk1", "uk1");
        DefaultRowChange barrier = createMockUpdateWithSamePK("db1", "t1", "pk1");
        configureUkValues(barrier, "uk1", "uk2");

        spy.tranApply(Arrays.asList(
            createRealDmlTransaction(firstUpdate),
            createRealDmlTransaction(survivor),
            createRealDmlTransaction(barrier)));

        verify(firstUpdate, never()).setForceAllColumns(true);
        verify(survivor, times(1)).setForceAllColumns(true);
        verify(barrier, never()).setForceAllColumns(true);
    }

    @Test
    public void testPkChangeBarrierFinalizesSupersededInsertSegment() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange insert = createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "pk1");
        DefaultRowChange firstUpdate = createMockUpdateWithSamePK("db1", "t1", "pk1");
        DefaultRowChange survivor = createMockUpdateWithSamePK("db1", "t1", "pk1");
        DefaultRowChange barrier = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(barrier.getRowValue(1, 0)).thenReturn("pk1");
        when(barrier.getChangeValue(1, 0)).thenReturn("pk2");

        spy.tranApply(Arrays.asList(
            createRealDmlTransaction(insert),
            createRealDmlTransaction(firstUpdate),
            createRealDmlTransaction(survivor),
            createRealDmlTransaction(barrier)));

        verify(firstUpdate, never()).setForceAllColumns(true);
        verify(survivor, times(1)).setForceAllColumns(true);
        verify(barrier, never()).setForceAllColumns(true);
    }

    @Test
    public void testPartitionKeyChangeBarrierFinalizesSupersededUpdateSegment() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkAndPartitionKeyTableInfo();

        DefaultRowChange firstUpdate = createMockUpdateWithIdentity("pk1", "p1");
        DefaultRowChange survivor = createMockUpdateWithIdentity("pk1", "p1");
        DefaultRowChange barrier = createMockUpdateWithIdentity("pk1", "p1");
        when(barrier.getChangeValue(1, 1)).thenReturn("p2");

        spy.tranApply(Arrays.asList(
            createRealDmlTransaction(firstUpdate),
            createRealDmlTransaction(survivor),
            createRealDmlTransaction(barrier)));

        verify(firstUpdate, never()).setForceAllColumns(true);
        verify(survivor, times(1)).setForceAllColumns(true);
        verify(barrier, never()).setForceAllColumns(true);
    }

    @Test
    public void testPkChangeBarrierSeparatesBeforeAndAfterIdentitySegments() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange oldFirst = createMockUpdateWithSamePK("db1", "t1", "pk1");
        DefaultRowChange oldSurvivor = createMockUpdateWithSamePK("db1", "t1", "pk1");
        DefaultRowChange barrier = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(barrier.getRowValue(1, 0)).thenReturn("pk1");
        when(barrier.getChangeValue(1, 0)).thenReturn("pk2");
        DefaultRowChange newFirst = createMockUpdateWithSamePK("db1", "t1", "pk2");
        DefaultRowChange newSurvivor = createMockUpdateWithSamePK("db1", "t1", "pk2");

        spy.tranApply(Arrays.asList(
            createRealDmlTransaction(oldFirst),
            createRealDmlTransaction(oldSurvivor),
            createRealDmlTransaction(barrier),
            createRealDmlTransaction(newFirst),
            createRealDmlTransaction(newSurvivor)));

        verify(oldFirst, never()).setForceAllColumns(true);
        verify(oldSurvivor, times(1)).setForceAllColumns(true);
        verify(barrier, never()).setForceAllColumns(true);
        verify(newFirst, never()).setForceAllColumns(true);
        verify(newSurvivor, times(1)).setForceAllColumns(true);
    }

    @Test
    public void testPkChangeBarrierWithoutSupersedeDoesNotForceAllColumns() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange update = createMockUpdateWithSamePK("db1", "t1", "pk1");
        DefaultRowChange barrier = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(barrier.getRowValue(1, 0)).thenReturn("pk1");
        when(barrier.getChangeValue(1, 0)).thenReturn("pk2");

        spy.tranApply(Arrays.asList(
            createRealDmlTransaction(update),
            createRealDmlTransaction(barrier)));

        verify(update, never()).setForceAllColumns(true);
        verify(barrier, never()).setForceAllColumns(true);
    }

    @Test
    public void testBatchUpdate_UkChangedExecutesRowByRow() throws Exception {
        TransactionParallelApplierV3 spy = createSpy();
        setupPkAndUkTableInfoSingleColumn();

        DefaultRowChange upd1 = createMockUpdateWithSamePK("db1", "t1", "pk1");
        when(upd1.getColumnIndex("uk_col")).thenReturn(1);
        when(upd1.getRowValue(1, 1)).thenReturn("a");
        when(upd1.getChangeValue(1, 1)).thenReturn("b");
        configureUpdateColumns(upd1, "pk1", "b");

        DefaultRowChange upd2 = createMockUpdateWithSamePK("db1", "t1", "pk2");
        when(upd2.getColumnIndex("uk_col")).thenReturn(1);
        when(upd2.getRowValue(1, 1)).thenReturn("c");
        when(upd2.getChangeValue(1, 1)).thenReturn("a");
        configureUpdateColumns(upd2, "pk2", "a");

        Transaction tx = new Transaction(null, null);
        tx.appendRowChange(upd1);
        tx.appendRowChange(upd2);
        tx.setFinished(true);

        spy.tranApply(Collections.singletonList(tx));

        verify(preparedStatement, times(2)).executeUpdate();
    }

    @Test
    public void testMergeConsecutiveUpdates_DeleteBreaksChain() throws Exception {
        // DELETE 应打断合并链
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        DefaultRowChange upd1 = createMockUpdateWithSamePK("db1", "t1", "pk1");
        configureUpdateColumns(upd1, "pk1", "v1");
        DefaultRowChange del = createMockRowChangeWithPK("db1", "t1", DBMSAction.DELETE, "pk1");
        DefaultRowChange upd2 = createMockUpdateWithSamePK("db1", "t1", "pk1");
        configureUpdateColumns(upd2, "pk1", "v2");

        Transaction tx1 = createRealDmlTransaction(upd1);
        Transaction tx2 = createRealDmlTransaction(del);
        Transaction tx3 = createRealDmlTransaction(upd2);

        // DELETE 打断链，不应合并 tx1 和 tx3
        spy.tranApply(Arrays.asList(tx1, tx2, tx3));

        // tx1 与 tx3 未按 UPDATE 链合并：upd2 不是 supersede 幸存者，不应被标记全列更新
        verify(upd1, never()).setForceAllColumns(true);
        verify(upd2, never()).setForceAllColumns(true);
        // DELETE 正常执行（无参 SQL 走 Statement 路径）
        verify(statement, times(1)).executeUpdate(contains("DELETE"));
        // DELETE 之后的 upd2 独立执行且带自身参数 v2
        verify(preparedStatement, times(1)).executeUpdate();
        verify(preparedStatement).setObject(1, "v2");
        // upd1 被 UPDATE+DELETE 折叠消除（行随后即被删除，终态等价），不执行
        verify(preparedStatement, never()).setObject(1, "v1");
        // 三个事务位于同一 compaction window，合并为 1 次事务应用 [DELETE, upd2]
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    public void testMergeConsecutiveUpdates_HotSpotPattern() throws Exception {
        // 模拟文档中的热点场景：同一 PK 大量连续 UPDATE
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        List<Transaction> txList = new ArrayList<>();
        List<DefaultRowChange> updates = new ArrayList<>();
        // 20 个相同 PK 的 UPDATE
        for (int i = 0; i < 20; i++) {
            DefaultRowChange upd = createMockUpdateWithSamePK("db1", "t1", "hotKey");
            configureUpdateColumns(upd, "hotKey", "v" + i);
            updates.add(upd);
            txList.add(createRealDmlTransaction(upd));
        }
        // 加一个不同 key 的事务使 batch > 1 after merge
        txList.add(createRealDmlTransaction(
            createMockRowChangeWithPK("db1", "t1", DBMSAction.INSERT, "coldKey")));

        // 21 个事务 → 合并后应为 2 个
        spy.tranApply(txList);

        // 21 个事务合并为 2 次事务应用：hotKey 合并链 1 次 + coldKey 1 次
        verify(dataSource, times(2)).getConnection();
        // 20 次 UPDATE 合并为 1 条，仅执行最后一次的终值 v19
        verify(preparedStatement, times(1)).executeUpdate();
        verify(preparedStatement).setObject(1, "v19");
        // supersede 幸存者是最后一次 UPDATE，前 19 次均被消除
        verify(updates.get(19), times(1)).setForceAllColumns(true);
        for (int i = 0; i < 19; i++) {
            verify(updates.get(i), never()).setForceAllColumns(true);
            verify(preparedStatement, never()).setObject(1, "v" + i);
        }
        // coldKey 的 INSERT 正常执行
        verify(statement, times(1)).executeUpdate(contains("INSERT"));
    }

    @Test
    public void testMergeConsecutiveUpdates_MixedMultipleHotKeys() throws Exception {
        // 多个热点 key 交织
        TransactionParallelApplierV3 spy = createSpy();
        setupPkTableInfo();

        List<Transaction> txList = new ArrayList<>();
        List<DefaultRowChange> hotAUpdates = new ArrayList<>();
        List<DefaultRowChange> hotBUpdates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            DefaultRowChange updA = createMockUpdateWithSamePK("db1", "t1", "hotA");
            configureUpdateColumns(updA, "hotA", "a" + i);
            hotAUpdates.add(updA);
            txList.add(createRealDmlTransaction(updA));

            DefaultRowChange updB = createMockUpdateWithSamePK("db1", "t1", "hotB");
            configureUpdateColumns(updB, "hotB", "b" + i);
            hotBUpdates.add(updB);
            txList.add(createRealDmlTransaction(updB));
        }

        // 20 个事务（10 for hotA, 10 for hotB）→ 合并后应为 2 个
        spy.tranApply(txList);

        // 20 个事务合并为 2 次事务应用：hotA、hotB 各一条合并链
        verify(dataSource, times(2)).getConnection();
        // 每条热点链各合并为 1 条 UPDATE，且都是各自链的终值
        verify(preparedStatement, times(2)).executeUpdate();
        verify(preparedStatement).setObject(1, "a9");
        verify(preparedStatement).setObject(1, "b9");
        // 幸存者为各链最后一次 UPDATE，中间 UPDATE 全部被消除
        verify(hotAUpdates.get(9), times(1)).setForceAllColumns(true);
        verify(hotBUpdates.get(9), times(1)).setForceAllColumns(true);
        for (int i = 0; i < 9; i++) {
            verify(hotAUpdates.get(i), never()).setForceAllColumns(true);
            verify(hotBUpdates.get(i), never()).setForceAllColumns(true);
            verify(preparedStatement, never()).setObject(1, "a" + i);
            verify(preparedStatement, never()).setObject(1, "b" + i);
        }
    }

    @Test
    public void testFlushCaseWhenUpdate_DeduplicatesByIdentityAndKeepsLastValue() throws Exception {
        TransactionParallelApplierV3 applier = createApplier();
        TableInfo tableInfo = createBaseTableInfo();
        DBMSColumn id = mockColumn("id", 0);
        DBMSColumn payload = mockColumn("payload", 1);
        DBMSColumn extra = mockColumn("extra", 2);
        List<DBMSColumn> columns = Arrays.asList(id, payload, extra);

        DefaultRowChange superseded = mockBatchUpdateRow(1L, "old", "x");
        DefaultRowChange survivor = mockBatchUpdateRow(1L, "new", "y");
        DefaultRowChange nullIdentity = mockBatchUpdateRow(null, "null-id", "z");
        when(preparedStatement.executeUpdate()).thenReturn(2);

        Method method = TransactionParallelApplierV3.class.getDeclaredMethod("flushCaseWhenUpdate",
            Connection.class, TableInfo.class, List.class, List.class, Set.class, List.class, String.class);
        method.setAccessible(true);
        method.invoke(applier, connection, tableInfo,
            Arrays.asList(superseded, survivor, nullIdentity), Collections.singletonList("id"),
            Collections.singleton("id"), columns, "db1.t1");

        verify(connection).prepareStatement(argThat(sql ->
            sql.startsWith("UPDATE `db1`.`t1` SET")
                && sql.contains("`payload` = CASE")
                && sql.contains("`extra` = CASE")
                && sql.contains("`id` IS NULL")
                && sql.contains(" OR ")));
        verify(preparedStatement, never()).setObject(anyInt(), eq("old"));
        verify(preparedStatement).setObject(1, 1L);
        verify(preparedStatement).setObject(2, "new");
        verify(preparedStatement).setObject(3, "null-id");
        verify(preparedStatement).setObject(4, 1L);
        verify(preparedStatement).setObject(5, "y");
        verify(preparedStatement).setObject(6, "z");
        verify(preparedStatement).setObject(7, 1L);
        verify(preparedStatement).executeUpdate();
    }

    @Test
    public void testFlushSingleRowUpdate_ChangesIdentityAndPreservesBeforeImageInWhere() throws Exception {
        TransactionParallelApplierV3 applier = createApplier();
        TableInfo tableInfo = createBaseTableInfo();
        DBMSColumn id = mockColumn("id", 0);
        DBMSColumn payload = mockColumn("payload", 1);
        DefaultRowChange rowChange = mock(DefaultRowChange.class);
        when(rowChange.getColumnIndex("id")).thenReturn(0);
        when(rowChange.getRowValue(1, 0)).thenReturn("old-id");
        when(rowChange.getChangeValue(1, 0)).thenReturn("new-id");
        when(rowChange.getRowValue(1, "id")).thenReturn("old-id");
        when(rowChange.getChangeValue(1, "id")).thenReturn("new-id");
        when(rowChange.getChangeValue(1, "payload")).thenReturn("value");

        Method method = TransactionParallelApplierV3.class.getDeclaredMethod("flushSingleRowUpdate",
            Connection.class, TableInfo.class, DefaultRowChange.class, List.class, Set.class, List.class, String.class);
        method.setAccessible(true);
        method.invoke(applier, connection, tableInfo, rowChange, Collections.singletonList("id"),
            Collections.singleton("id"), Arrays.asList(id, payload), "db1.t1");

        verify(connection).prepareStatement(
            "UPDATE `db1`.`t1` SET `id`=?, `payload`=? WHERE `id`=?");
        verify(preparedStatement).setObject(1, "new-id");
        verify(preparedStatement).setObject(2, "value");
        verify(preparedStatement).setObject(3, "old-id");
        verify(preparedStatement).executeUpdate();
    }

    @Test
    public void testFlushBatch_MergesInsertAndDeleteRowsIntoOneStatementEach() throws Exception {
        TransactionParallelApplierV3 applier = createApplier();
        TableInfo tableInfo = realPkTableInfo();
        when(dbMetaCache.getTableInfo("db1", "t1")).thenReturn(tableInfo);
        DefaultRowChange firstInsert = realSingleRowChange(DBMSAction.INSERT, 1L);
        DefaultRowChange secondInsert = realSingleRowChange(DBMSAction.INSERT, 2L);

        Method method = TransactionParallelApplierV3.class.getDeclaredMethod("flushBatch",
            Connection.class, List.class, DBMSAction.class, int.class);
        method.setAccessible(true);
        method.invoke(applier, connection, Arrays.asList(firstInsert, secondInsert), DBMSAction.INSERT,
            RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);

        verify(connection).prepareStatement("INSERT INTO `db1`.`t1`(`id`) VALUES (?),(?)");
        verify(preparedStatement).setObject(1, 1L);
        verify(preparedStatement).setObject(2, 2L);
        verify(preparedStatement).executeUpdate();

        clearInvocations(connection, preparedStatement);
        DefaultRowChange firstDelete = realSingleRowChange(DBMSAction.DELETE, 3L);
        DefaultRowChange secondDelete = realSingleRowChange(DBMSAction.DELETE, 4L);
        method.invoke(applier, connection, Arrays.asList(firstDelete, secondDelete), DBMSAction.DELETE,
            RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);

        verify(connection).prepareStatement(contains("DELETE FROM `db1`.`t1` WHERE"));
        verify(preparedStatement).setObject(1, 3L);
        verify(preparedStatement).setObject(2, 4L);
        verify(preparedStatement).executeUpdate();
    }

    @Test
    public void testFlushPendingBatch_NonDuplicateSqlFailureDoesNotRetryRowByRow() throws Exception {
        TransactionParallelApplierV3 applier = createApplier();
        when(dbMetaCache.getTableInfo("db1", "t1")).thenReturn(realPkTableInfo());
        SQLException deadlock = new SQLException("deadlock found");
        when(preparedStatement.executeUpdate()).thenThrow(deadlock);
        List<DefaultRowChange> batch = Arrays.asList(
            realSingleRowChange(DBMSAction.INSERT, 1L), realSingleRowChange(DBMSAction.INSERT, 2L));

        Method method = TransactionParallelApplierV3.class.getDeclaredMethod("flushPendingBatch",
            Connection.class, List.class, DBMSAction.class, int.class);
        method.setAccessible(true);
        try {
            method.invoke(applier, connection, batch, DBMSAction.INSERT,
                RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);
            Assert.fail("non-duplicate SQL failure must abort the batch");
        } catch (InvocationTargetException e) {
            Assert.assertSame(deadlock, e.getCause());
        }
        verify(preparedStatement, times(1)).executeUpdate();
    }

    // ========= Externalized INSERT conflict fallback tests =========

    @Test
    public void testExternalizedInsertSuccessCommitsWithoutReplay() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        registerTableInfo(externalizedTableInfo(true, false));

        TransactionParallelApplierV3 spy = createSpy();
        try {
            spy.tranApply(Collections.singletonList(
                createRealDmlTransaction(realExternalizedInsert(1L, "payload"))));
        } finally {
            spy.executorService.shutdownNow();
        }

        verify(connection).setAutoCommit(false);
        verify(connection).prepareStatement(contains("INSERT INTO `db1`.`t1`"));
        verify(connection, never()).prepareStatement(contains("REPLACE INTO"));
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(dataSource, times(1)).getConnection();
        verify(spy).updateMetrics(any());
    }

    @Test
    public void testExternalizedTableWithoutInsertKeepsSingleEventPath() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        registerTableInfo(externalizedTableInfo(true, false));
        DefaultRowChange delete = realExternalizedRowChange(DBMSAction.DELETE, 1L, "payload");

        TransactionParallelApplierV3 spy = createSpy();
        try {
            spy.tranApply(Collections.singletonList(createRealDmlTransaction(delete)));
        } finally {
            spy.executorService.shutdownNow();
        }

        verify(connection).prepareStatement(contains("DELETE FROM `db1`.`t1`"));
        verify(connection).commit();
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    public void testExternalizedTableWithoutInsertKeepsBatchPath() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        registerTableInfo(externalizedTableInfo(true, false));
        Transaction transaction = new Transaction(null, null);
        transaction.appendRowChange(realExternalizedRowChange(DBMSAction.DELETE, 1L, "first"));
        transaction.appendRowChange(realExternalizedRowChange(DBMSAction.DELETE, 2L, "second"));
        transaction.setFinished(true);

        TransactionParallelApplierV3 spy = createSpy();
        try {
            spy.tranApply(Collections.singletonList(transaction));
        } finally {
            spy.executorService.shutdownNow();
        }

        verify(connection).prepareStatement(contains("DELETE FROM `db1`.`t1` WHERE"));
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(spy).updateMetrics(any());
    }

    @Test
    public void testExternalizedSingleInsertDuplicateRollsBackAndClosesBeforeOverwriteReplay() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        TableInfo tableInfo = externalizedTableInfo(true, false);
        registerTableInfo(tableInfo);
        DefaultRowChange insert = realExternalizedInsert(1L, "new-payload");

        Connection normalConnection = mock(Connection.class);
        PreparedStatement normalStatement = mock(PreparedStatement.class);
        when(normalConnection.prepareStatement(anyString())).thenReturn(normalStatement);
        when(normalStatement.executeUpdate()).thenThrow(duplicateKeyException());

        Connection replayConnection = mock(Connection.class);
        PreparedStatement replayStatement = mock(PreparedStatement.class);
        when(replayConnection.prepareStatement(anyString())).thenReturn(replayStatement);
        when(replayStatement.executeUpdate()).thenReturn(1);
        useConnectionsInRollbackThenReplayOrder(normalConnection, replayConnection);

        TransactionParallelApplierV3 spy = createSpy();
        try {
            spy.tranApply(Collections.singletonList(createRealDmlTransaction(insert)));
        } finally {
            spy.executorService.shutdownNow();
        }

        verify(normalConnection).setAutoCommit(false);
        verify(normalConnection).prepareStatement(contains("INSERT INTO `db1`.`t1`"));
        verify(normalConnection).rollback();
        verify(normalConnection, never()).commit();
        verify(replayConnection).setAutoCommit(false);
        verify(replayConnection).prepareStatement(contains("REPLACE INTO `db1`.`t1`"));
        verify(replayConnection).commit();
        verify(replayConnection, never()).rollback();
        verify(spy).updateMetrics(any());
        verify(dataSource, times(2)).getConnection();
    }

    @Test
    public void testExternalizedBatchDuplicateRollsBackWholeBatchAndReplaysRowsIndividually() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        registerTableInfo(externalizedTableInfo(true, false));
        Transaction transaction = new Transaction(null, null);
        transaction.appendRowChange(realExternalizedInsert(1L, "first"));
        transaction.appendRowChange(realExternalizedInsert(2L, "second"));
        transaction.setFinished(true);

        Connection normalConnection = mock(Connection.class);
        PreparedStatement batchStatement = mock(PreparedStatement.class);
        when(normalConnection.prepareStatement(anyString())).thenReturn(batchStatement);
        when(batchStatement.executeUpdate()).thenThrow(duplicateKeyException());

        Connection replayConnection = mock(Connection.class);
        PreparedStatement replayStatement = mock(PreparedStatement.class);
        when(replayConnection.prepareStatement(anyString())).thenReturn(replayStatement);
        when(replayStatement.executeUpdate()).thenReturn(1);
        useConnectionsInRollbackThenReplayOrder(normalConnection, replayConnection);

        TransactionParallelApplierV3 spy = createSpy();
        try {
            spy.tranApply(Collections.singletonList(transaction));
        } finally {
            spy.executorService.shutdownNow();
        }

        verify(normalConnection).prepareStatement(
            contains("INSERT INTO `db1`.`t1`(`id`,`payload`) VALUES (?,?),(?,?)"));
        verify(normalConnection, times(1)).prepareStatement(anyString());
        verify(normalConnection).rollback();
        verify(replayConnection, times(2)).prepareStatement(contains("REPLACE INTO `db1`.`t1`"));
        verify(replayStatement, times(2)).executeUpdate();
        verify(replayConnection).commit();
        verify(spy).updateMetrics(any());
    }

    @Test
    public void testExternalizedInsertDuplicateUsesInsertIgnoreForIgnoreStrategy() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        when(applierConfig.getConflictStrategy()).thenReturn(ConflictStrategy.IGNORE);
        registerTableInfo(externalizedTableInfo(true, false));

        Connection normalConnection = duplicateInsertConnection();
        Connection replayConnection = successfulInsertConnection();
        useConnectionsInRollbackThenReplayOrder(normalConnection, replayConnection);

        TransactionParallelApplierV3 spy = createSpy();
        try {
            spy.tranApply(Collections.singletonList(
                createRealDmlTransaction(realExternalizedInsert(1L, "payload"))));
        } finally {
            spy.executorService.shutdownNow();
        }

        verify(replayConnection).prepareStatement(contains("INSERT IGNORE INTO `db1`.`t1`"));
        verify(replayConnection).commit();
        verify(spy).updateMetrics(any());
    }

    @Test
    public void testExternalizedInsertNonDuplicateFailureRollsBackWithoutReplay() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        registerTableInfo(externalizedTableInfo(true, false));
        SQLException networkError = new SQLException("network failure");
        when(preparedStatement.executeUpdate()).thenThrow(networkError);

        TransactionParallelApplierV3 spy = createSpy();
        SQLException error = null;
        try {
            spy.tranApply(Collections.singletonList(
                createRealDmlTransaction(realExternalizedInsert(1L, "payload"))));
            Assert.fail("non-duplicate failure must abort without replay");
        } catch (SQLException e) {
            error = e;
        } finally {
            spy.executorService.shutdownNow();
        }

        Assert.assertSame(networkError, error);
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(dataSource, times(1)).getConnection();
        verify(spy, never()).updateMetrics(any());
    }

    @Test
    public void testExternalizedInsertRejectsMultiRowChangeBeforeExecutingSql() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        registerTableInfo(externalizedTableInfo(true, false));
        DefaultRowChange insert = realExternalizedInsert(1L, "first");
        insert.addRowData(new DefaultRowData(new Serializable[] {2L, "second"}));

        TransactionParallelApplierV3 spy = createSpy();
        PolardbxException error = null;
        try {
            spy.tranApply(Collections.singletonList(createRealDmlTransaction(insert)));
            Assert.fail("multi-row row change must be rejected before SQL execution");
        } catch (PolardbxException e) {
            error = e;
        } finally {
            spy.executorService.shutdownNow();
        }

        Assert.assertNotNull(error);
        Assert.assertTrue(error.getMessage().contains("more than 1 row"));
        verify(connection).rollback();
        verify(connection, never()).prepareStatement(anyString());
        verify(dataSource, times(1)).getConnection();
        verify(spy, never()).updateMetrics(any());
    }

    @Test
    public void testExternalizedInsertDuplicateInterruptReplayRollsBackAndPropagates() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        when(applierConfig.getConflictStrategy()).thenReturn(ConflictStrategy.INTERRUPT);
        registerTableInfo(externalizedTableInfo(true, false));

        Connection normalConnection = duplicateInsertConnection();
        Connection replayConnection = duplicateInsertConnection();
        useConnectionsInRollbackThenReplayOrder(normalConnection, replayConnection);

        TransactionParallelApplierV3 spy = createSpy();
        SQLException error = null;
        try {
            spy.tranApply(Collections.singletonList(
                createRealDmlTransaction(realExternalizedInsert(1L, "payload"))));
            Assert.fail("INTERRUPT replay must propagate the duplicate-key error");
        } catch (SQLException e) {
            error = e;
        } finally {
            spy.executorService.shutdownNow();
        }

        Assert.assertNotNull(error);
        Assert.assertTrue(error.getMessage().contains("Duplicate entry"));
        verify(replayConnection).prepareStatement(contains("INSERT INTO `db1`.`t1`"));
        verify(replayConnection).rollback();
        verify(replayConnection, never()).commit();
        verify(spy, never()).updateMetrics(any());
    }

    @Test
    public void testExternalizedInsertDuplicateUsesInsertIgnoreForLabGeneratedUk() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        mockConfig(ConfigKeys.IS_LAB_ENV, "true");
        registerTableInfo(externalizedTableInfo(true, true));

        Connection normalConnection = duplicateInsertConnection();
        Connection replayConnection = successfulInsertConnection();
        useConnectionsInRollbackThenReplayOrder(normalConnection, replayConnection);

        TransactionParallelApplierV3 spy = createSpy();
        try {
            spy.tranApply(Collections.singletonList(
                createRealDmlTransaction(realExternalizedInsert(1L, "payload"))));
        } finally {
            spy.executorService.shutdownNow();
        }

        verify(replayConnection).prepareStatement(contains("INSERT IGNORE INTO `db1`.`t1`"));
        verify(replayConnection, never()).prepareStatement(contains("REPLACE INTO"));
        verify(replayConnection).commit();
    }

    @Test
    public void testExternalizedInsertInSerialFallbackStillUsesFreshReplayTransaction() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        registerTableInfo(externalizedTableInfo(false, false));

        Connection normalConnection = duplicateInsertConnection();
        Connection replayConnection = successfulInsertConnection();
        useConnectionsInRollbackThenReplayOrder(normalConnection, replayConnection);

        TransactionParallelApplierV3 spy = createSpy();
        try {
            spy.tranApply(Collections.singletonList(
                createRealDmlTransaction(realExternalizedInsert(1L, "payload"))));
        } finally {
            spy.executorService.shutdownNow();
        }

        verify(normalConnection).rollback();
        verify(replayConnection).prepareStatement(contains("REPLACE INTO `db1`.`t1`"));
        verify(replayConnection).commit();
        verify(dataSource, times(2)).getConnection();
    }

    @Test
    public void testExternalizedInsertDoesNotReplayWhenInitialRollbackFails() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        registerTableInfo(externalizedTableInfo(true, false));
        Connection normalConnection = duplicateInsertConnection();
        SQLException rollbackError = new SQLException("rollback failed");
        Mockito.doThrow(rollbackError).when(normalConnection).rollback();
        when(dataSource.getConnection()).thenReturn(normalConnection);

        TransactionParallelApplierV3 spy = createSpy();
        PolardbxException error = null;
        try {
            spy.tranApply(Collections.singletonList(
                createRealDmlTransaction(realExternalizedInsert(1L, "payload"))));
            Assert.fail("rollback failure must prevent replay");
        } catch (PolardbxException e) {
            error = e;
        } finally {
            spy.executorService.shutdownNow();
        }

        Assert.assertNotNull(error);
        Assert.assertTrue(error.getMessage().contains("target transaction rollback failed"));
        Assert.assertEquals("ExternalizedInsertConflictException", error.getCause().getClass().getSimpleName());
        Assert.assertArrayEquals(new Throwable[] {rollbackError}, error.getCause().getSuppressed());
        verify(dataSource, times(1)).getConnection();
        verify(normalConnection, never()).commit();
        verify(spy, never()).updateMetrics(any());
    }

    @Test
    public void testExternalizedInsertReplayFailureRollsBackReplayTransaction() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        registerTableInfo(externalizedTableInfo(true, false));
        Connection normalConnection = duplicateInsertConnection();
        Connection replayConnection = mock(Connection.class);
        PreparedStatement replayStatement = mock(PreparedStatement.class);
        SQLException replayError = new SQLException("lock wait timeout");
        when(replayConnection.prepareStatement(anyString())).thenReturn(replayStatement);
        when(replayStatement.executeUpdate()).thenThrow(replayError);
        useConnectionsInRollbackThenReplayOrder(normalConnection, replayConnection);

        TransactionParallelApplierV3 spy = createSpy();
        SQLException error = null;
        try {
            spy.tranApply(Collections.singletonList(
                createRealDmlTransaction(realExternalizedInsert(1L, "payload"))));
            Assert.fail("replay SQL failure must be propagated");
        } catch (SQLException e) {
            error = e;
        } finally {
            spy.executorService.shutdownNow();
        }

        Assert.assertSame(replayError, error);
        verify(normalConnection).rollback();
        verify(replayConnection).rollback();
        verify(replayConnection, never()).commit();
        verify(spy, never()).updateMetrics(any());
    }

    @Test
    public void testExternalizedInsertConflictReplaysMixedEventsInOriginalOrder() throws Exception {
        mockConfig(ConfigKeys.RPL_COMPACTION_ENABLED, "false");
        mockConfig(ConfigKeys.RPL_BATCH_ENABLED, "false");
        registerTableInfo(externalizedTableInfo(true, false));
        TableInfo ordinaryTable = ordinaryPkTableInfo("t2");
        when(dbMetaCache.getTableInfo("db1", "t2")).thenReturn(ordinaryTable);
        when(dbMetaCache.getTableInfo("db1.t2")).thenReturn(ordinaryTable);
        Transaction transaction = new Transaction(null, null);
        transaction.appendRowChange(realInsert("db1", "t2", 2L));
        transaction.appendRowChange(realExternalizedInsert(1L, "payload"));
        transaction.setFinished(true);

        Connection normalConnection = mock(Connection.class);
        PreparedStatement normalExternalStatement = mock(PreparedStatement.class);
        PreparedStatement normalOrdinaryStatement = mock(PreparedStatement.class);
        when(normalConnection.prepareStatement(contains("`t1`"))).thenReturn(normalExternalStatement);
        when(normalConnection.prepareStatement(contains("`t2`"))).thenReturn(normalOrdinaryStatement);
        when(normalExternalStatement.executeUpdate()).thenThrow(duplicateKeyException());
        when(normalOrdinaryStatement.executeUpdate()).thenReturn(1);

        Connection replayConnection = mock(Connection.class);
        PreparedStatement replayExternalStatement = mock(PreparedStatement.class);
        PreparedStatement replayOrdinaryStatement = mock(PreparedStatement.class);
        when(replayConnection.prepareStatement(contains("`t1`"))).thenReturn(replayExternalStatement);
        when(replayConnection.prepareStatement(contains("`t2`"))).thenReturn(replayOrdinaryStatement);
        when(replayExternalStatement.executeUpdate()).thenReturn(1);
        when(replayOrdinaryStatement.executeUpdate()).thenReturn(1);
        useConnectionsInRollbackThenReplayOrder(normalConnection, replayConnection);

        TransactionParallelApplierV3 spy = createSpy();
        try {
            spy.tranApply(Collections.singletonList(transaction));
        } finally {
            spy.executorService.shutdownNow();
        }

        InOrder replayOrder = Mockito.inOrder(replayConnection);
        replayOrder.verify(replayConnection).prepareStatement(contains("INSERT INTO `db1`.`t2`"));
        replayOrder.verify(replayConnection).prepareStatement(contains("REPLACE INTO `db1`.`t1`"));
        verify(replayConnection).commit();
        verify(spy).updateMetrics(any());
    }

    @Test
    public void testOrdinaryBatchDuplicateKeepsSameTransactionOneByOneFallback() throws Exception {
        TransactionParallelApplierV3 applier = createApplier();
        registerTableInfo(realPkTableInfo());
        PreparedStatement batchStatement = mock(PreparedStatement.class);
        PreparedStatement insertStatement = mock(PreparedStatement.class);
        PreparedStatement replaceStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement("INSERT INTO `db1`.`t1`(`id`) VALUES (?)"))
            .thenReturn(insertStatement);
        when(connection.prepareStatement("REPLACE INTO `db1`.`t1`(`id`) VALUES (?)"))
            .thenReturn(replaceStatement);
        when(connection.prepareStatement("INSERT INTO `db1`.`t1`(`id`) VALUES (?),(?)"))
            .thenReturn(batchStatement);
        when(batchStatement.executeUpdate()).thenThrow(duplicateKeyException());
        when(insertStatement.executeUpdate()).thenThrow(duplicateKeyException());
        when(replaceStatement.executeUpdate()).thenReturn(1);
        List<DefaultRowChange> batch = Arrays.asList(
            realSingleRowChange(DBMSAction.INSERT, 1L), realSingleRowChange(DBMSAction.INSERT, 2L));

        Method method = TransactionParallelApplierV3.class.getDeclaredMethod("flushPendingBatch",
            Connection.class, List.class, DBMSAction.class, int.class);
        method.setAccessible(true);
        try {
            method.invoke(applier, connection, batch, DBMSAction.INSERT,
                RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);
        } finally {
            applier.executorService.shutdownNow();
        }

        verify(connection).prepareStatement("INSERT INTO `db1`.`t1`(`id`) VALUES (?),(?)");
        verify(connection, times(2)).prepareStatement("INSERT INTO `db1`.`t1`(`id`) VALUES (?)");
        verify(connection, times(2)).prepareStatement("REPLACE INTO `db1`.`t1`(`id`) VALUES (?)");
        verify(connection, never()).rollback();
    }

    // ========= Helper Methods =========

    private TransactionParallelApplierV3 createApplier() {
        TransactionParallelApplierV3 applier = new TransactionParallelApplierV3(applierConfig, hostInfo, srcHostInfo);
        applier.dbMetaCache = dbMetaCache;
        applier.executorService = Executors.newFixedThreadPool(2);
        return applier;
    }

    private TransactionParallelApplierV3 createSpy() {
        TransactionParallelApplierV3 spy = Mockito.spy(createApplier());
        // DynamicApplicationConfig 的静态 mock 只对当前线程生效，V3 会在 executor
        // 线程调用 updateMetrics。这里屏蔽与被测 apply/compaction 路径无关的指标更新，
        // 避免测试线程模型导致配置 mock 失效。
        doNothing().when(spy).updateMetrics(any());
        return spy;
    }

    /**
     * 创建一个基础 TableInfo mock，包含 DmlApplyHelper 所需的所有字段
     */
    private TableInfo createBaseTableInfo() {
        TableInfo tableInfo = mock(TableInfo.class);
        when(tableInfo.getSchema()).thenReturn("db1");
        when(tableInfo.getName()).thenReturn("t1");
        when(tableInfo.getEngine()).thenReturn("InnoDB");
        // DML generation requires typed identity columns, not just the DAG's key-name list.
        when(tableInfo.getWithTypeKeyList()).thenAnswer(invocation -> tableInfo.getKeyList().stream()
            .map(name -> new ColumnInfo(name, Types.BIGINT, null, false, false, "BIGINT", 20))
            .collect(java.util.stream.Collectors.toList()));
        return tableInfo;
    }

    private void registerTableInfo(TableInfo tableInfo) throws Exception {
        when(dbMetaCache.getTableInfo("db1", "t1")).thenReturn(tableInfo);
        when(dbMetaCache.getTableInfo("db1.t1")).thenReturn(tableInfo);
    }

    private void setupDefaultTableInfo() throws Exception {
        TableInfo tableInfo = createBaseTableInfo();
        when(tableInfo.getPks()).thenReturn(Collections.singletonList("id"));
        when(tableInfo.getKeyList()).thenReturn(Collections.singletonList("id"));
        when(tableInfo.getPkColumnsIndex(any())).thenReturn(Collections.singletonList(0));
        when(tableInfo.getUkGroups()).thenReturn(Collections.emptyList());
        when(tableInfo.getUkGroupColumnsIndex(any())).thenReturn(Collections.emptyList());
        registerTableInfo(tableInfo);
    }

    private void setupPkTableInfo() throws Exception {
        setupDefaultTableInfo();
    }

    private void setupPkAndUkTableInfo() throws Exception {
        TableInfo tableInfo = createBaseTableInfo();
        when(tableInfo.getPks()).thenReturn(Collections.singletonList("id"));
        when(tableInfo.getKeyList()).thenReturn(Collections.singletonList("id"));
        when(tableInfo.getPkColumnsIndex(any())).thenReturn(Collections.singletonList(0));
        List<List<String>> ukGroups = new ArrayList<>();
        ukGroups.add(Arrays.asList("uk_col1", "uk_col2"));
        when(tableInfo.getUkGroups()).thenReturn(ukGroups);
        List<List<Integer>> ukGroupColumnsIndex = new ArrayList<>();
        ukGroupColumnsIndex.add(Arrays.asList(1, 2));
        when(tableInfo.getUkGroupColumnsIndex(any())).thenReturn(ukGroupColumnsIndex);
        registerTableInfo(tableInfo);
    }

    private void setupPkAndUkTableInfoSingleColumn() throws Exception {
        TableInfo tableInfo = createBaseTableInfo();
        when(tableInfo.getPks()).thenReturn(Collections.singletonList("id"));
        when(tableInfo.getKeyList()).thenReturn(Collections.singletonList("id"));
        when(tableInfo.getPkColumnsIndex(any())).thenReturn(Collections.singletonList(0));
        List<List<String>> ukGroups = new ArrayList<>();
        ukGroups.add(Collections.singletonList("uk_col"));
        when(tableInfo.getUkGroups()).thenReturn(ukGroups);
        List<List<Integer>> ukGroupColumnsIndex = new ArrayList<>();
        ukGroupColumnsIndex.add(Collections.singletonList(1));
        when(tableInfo.getUkGroupColumnsIndex(any())).thenReturn(ukGroupColumnsIndex);
        registerTableInfo(tableInfo);
    }

    private void setupPkAndPartitionKeyTableInfo() throws Exception {
        TableInfo tableInfo = createBaseTableInfo();
        when(tableInfo.getPks()).thenReturn(Collections.singletonList("id"));
        when(tableInfo.getKeyList()).thenReturn(Arrays.asList("id", "part_col"));
        when(tableInfo.getPkColumnsIndex(any())).thenReturn(Collections.singletonList(0));
        when(tableInfo.getUkGroups()).thenReturn(Collections.emptyList());
        when(tableInfo.getUkGroupColumnsIndex(any())).thenReturn(Collections.emptyList());
        registerTableInfo(tableInfo);
    }

    private Transaction createMockDdlTransaction(DefaultQueryLog queryLog) {
        Transaction tx = mock(Transaction.class);
        when(tx.getEventCount()).thenReturn(1L);
        when(tx.peekFirst()).thenReturn(queryLog);
        when(tx.peekLast()).thenReturn(queryLog);
        return tx;
    }

    private DefaultRowChange createMockRowChange(String schema, String table, DBMSAction action) {
        DefaultRowChange rc = mock(DefaultRowChange.class);
        when(rc.getSchema()).thenReturn(schema);
        when(rc.getTable()).thenReturn(table);
        when(rc.getAction()).thenReturn(action);
        when(rc.getRowSize()).thenReturn(1);
        when(rc.getColumns()).thenReturn(Collections.emptyList());
        when(rc.getColumnSet()).thenReturn(new DefaultColumnSet(Collections.emptyList()));
        // UPDATE SQL 的列裁剪依赖动态配置；这些用例关注 DAG/compaction，而非列裁剪，
        // 使用 force-all 模式使 executor 线程不再读取线程局部的静态配置 mock。
        when(rc.isForceAllColumns()).thenReturn(action == DBMSAction.UPDATE);
        return rc;
    }

    private DefaultRowChange createMockRowChangeWithPK(String schema, String table, DBMSAction action,
                                                       String pkValue) {
        DefaultRowChange rc = createMockRowChange(schema, table, action);
        when(rc.getRowValue(1, 0)).thenReturn(pkValue);
        return rc;
    }

    private DefaultRowChange createMockUpdateWithSamePK(String schema, String table, String pkValue) {
        DefaultRowChange rc = createMockRowChange(schema, table, DBMSAction.UPDATE);
        when(rc.getRowValue(1, 0)).thenReturn(pkValue);
        when(rc.getChangeValue(1, 0)).thenReturn(pkValue); // PK 不变
        return rc;
    }

    private DefaultRowChange createMockUpdateWithIdentity(String pkValue, String partitionValue) {
        DefaultRowChange rc = createMockRowChange("db1", "t1", DBMSAction.UPDATE);
        when(rc.getColumnIndex("id")).thenReturn(0);
        when(rc.getColumnIndex("part_col")).thenReturn(1);
        when(rc.getRowValue(1, 0)).thenReturn(pkValue);
        when(rc.getChangeValue(1, 0)).thenReturn(pkValue);
        when(rc.getRowValue(1, 1)).thenReturn(partitionValue);
        when(rc.getChangeValue(1, 1)).thenReturn(partitionValue);
        return rc;
    }

    private void configureUkValues(DefaultRowChange rc, Serializable beforeValue, Serializable afterValue) {
        when(rc.getColumnIndex("uk_col")).thenReturn(1);
        when(rc.getRowValue(1, 1)).thenReturn(beforeValue);
        when(rc.getChangeValue(1, 1)).thenReturn(afterValue);
    }

    private void configureUpdateColumns(DefaultRowChange rc, String pkValue, String ukAfterValue) {
        DBMSColumn idColumn = mock(DBMSColumn.class);
        when(idColumn.getName()).thenReturn("id");
        when(idColumn.getColumnIndex()).thenReturn(0);
        DBMSColumn ukColumn = mock(DBMSColumn.class);
        when(ukColumn.getName()).thenReturn("uk_col");
        when(ukColumn.getColumnIndex()).thenReturn(1);
        List<DBMSColumn> columns = Arrays.asList(idColumn, ukColumn);
        DefaultColumnSet columnSet = new DefaultColumnSet(columns);
        doReturn(columns).when(rc).getColumns();
        when(rc.getColumnSet()).thenReturn(columnSet);
        when(rc.getRowValue(1, "id")).thenReturn(pkValue);
        when(rc.getChangeValue(1, "uk_col")).thenReturn(ukAfterValue);
    }

    private DBMSColumn mockColumn(String name, int index) {
        DBMSColumn column = mock(DBMSColumn.class);
        when(column.getName()).thenReturn(name);
        when(column.getColumnIndex()).thenReturn(index);
        return column;
    }

    private DefaultRowChange mockBatchUpdateRow(Serializable id, String payload, String extra) {
        DefaultRowChange rowChange = mock(DefaultRowChange.class);
        when(rowChange.getRowValue(1, "id")).thenReturn(id);
        when(rowChange.getChangeValue(1, "payload")).thenReturn(payload);
        when(rowChange.getChangeValue(1, "extra")).thenReturn(extra);
        return rowChange;
    }

    private TableInfo realPkTableInfo() {
        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setColumns(Collections.singletonList(
            new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20)));
        return tableInfo;
    }

    private DefaultRowChange realSingleRowChange(DBMSAction action, Serializable id) {
        DefaultColumn column = new DefaultColumn("id", 1, Types.BIGINT, true, false, true);
        DefaultRowChange rowChange = new DefaultRowChange(action, "db1", "t1",
            new DefaultColumnSet(Collections.singletonList(column)));
        rowChange.addRowData(new DefaultRowData(new Serializable[] {id}));
        return rowChange;
    }

    private TableInfo externalizedTableInfo(boolean withPrimaryKey, boolean generatedUk) {
        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setEngine("InnoDB");
        if (withPrimaryKey) {
            tableInfo.setPks(Collections.singletonList("id"));
        }
        ColumnInfo id = new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20);
        ColumnInfo payload = new ColumnInfo("payload", Types.VARCHAR, null, true, false, "VARCHAR", 64);
        payload.setExternalized(true);
        tableInfo.setColumns(Arrays.asList(id, payload));
        tableInfo.setHasGeneratedUk(generatedUk);
        return tableInfo;
    }

    private TableInfo ordinaryPkTableInfo(String tableName) {
        TableInfo tableInfo = new TableInfo("db1", tableName);
        tableInfo.setEngine("InnoDB");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setColumns(Collections.singletonList(
            new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20)));
        return tableInfo;
    }

    private DefaultRowChange realExternalizedInsert(Serializable id, Serializable payload) {
        return realExternalizedRowChange(DBMSAction.INSERT, id, payload);
    }

    private DefaultRowChange realExternalizedRowChange(DBMSAction action, Serializable id, Serializable payload) {
        DefaultColumn idColumn = new DefaultColumn("id", 1, Types.BIGINT, true, false, true);
        DefaultColumn payloadColumn = new DefaultColumn("payload", 2, Types.VARCHAR, false, true, false);
        DefaultRowChange rowChange = new DefaultRowChange(action, "db1", "t1",
            new DefaultColumnSet(Arrays.asList(idColumn, payloadColumn), Collections.singleton("payload")));
        rowChange.addRowData(new DefaultRowData(new Serializable[] {id, payload}));
        return rowChange;
    }

    private DefaultRowChange realInsert(String schema, String table, Serializable id) {
        DefaultColumn idColumn = new DefaultColumn("id", 1, Types.BIGINT, true, false, true);
        DefaultRowChange rowChange = new DefaultRowChange(DBMSAction.INSERT, schema, table,
            new DefaultColumnSet(Collections.singletonList(idColumn)));
        rowChange.addRowData(new DefaultRowData(new Serializable[] {id}));
        return rowChange;
    }

    private SQLException duplicateKeyException() {
        return new SQLException("Duplicate entry '1' for key 'PRIMARY'");
    }

    private Connection duplicateInsertConnection() throws Exception {
        Connection duplicateConnection = mock(Connection.class);
        PreparedStatement duplicateStatement = mock(PreparedStatement.class);
        when(duplicateConnection.prepareStatement(anyString())).thenReturn(duplicateStatement);
        when(duplicateStatement.executeUpdate()).thenThrow(duplicateKeyException());
        return duplicateConnection;
    }

    private Connection successfulInsertConnection() throws Exception {
        Connection successfulConnection = mock(Connection.class);
        PreparedStatement successfulStatement = mock(PreparedStatement.class);
        when(successfulConnection.prepareStatement(anyString())).thenReturn(successfulStatement);
        when(successfulStatement.executeUpdate()).thenReturn(1);
        return successfulConnection;
    }

    private void useConnectionsInRollbackThenReplayOrder(Connection normalConnection,
                                                         Connection replayConnection) throws Exception {
        AtomicInteger connectionCount = new AtomicInteger();
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            int current = connectionCount.getAndIncrement();
            if (current == 0) {
                return normalConnection;
            }
            if (current == 1) {
                verify(normalConnection).rollback();
                verify(normalConnection).close();
                return replayConnection;
            }
            throw new AssertionError("unexpected third target connection");
        });
    }

    private void assertCompactedUpdateMissStrategy(ConflictStrategy strategy) throws Exception {
        mockConfig(ConfigKeys.RPL_BATCH_ENABLED, "false");
        mockConfig(ConfigKeys.RPL_COLS_UPDATE_MODE, "ONUPDATE");
        when(applierConfig.getConflictStrategy()).thenReturn(strategy);
        when(applierConfig.isInsertOnUpdateMiss()).thenReturn(true);

        JdbcDataSource targetDataSource = new JdbcDataSource();
        targetDataSource.setURL("jdbc:h2:mem:compaction_conflict_" + strategy
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        try (Connection conn = targetDataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute("CREATE SCHEMA db1");
            stmt.execute("CREATE TABLE db1.t1 (id BIGINT PRIMARY KEY, name VARCHAR(32), content VARCHAR(32))");
        }

        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setEngine("InnoDB");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setColumns(Arrays.asList(
            new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20),
            new ColumnInfo("name", Types.VARCHAR, null, true, false, "VARCHAR", 32),
            new ColumnInfo("content", Types.VARCHAR, null, true, false, "VARCHAR", 32)));
        registerTableInfo(tableInfo);
        when(dbMetaCache.getBuiltInDefaultDataSource()).thenReturn(targetDataSource);

        DefaultColumnSet columns = new DefaultColumnSet(Arrays.asList(
            new DefaultColumn("id", 1, Types.BIGINT, true, false, true),
            new DefaultColumn("name", 2, Types.VARCHAR, false, true, false),
            new DefaultColumn("content", 3, Types.VARCHAR, false, true, false)));
        DefaultRowChange first = realUpdate(columns,
            new Serializable[] {1L, "n0", "c0"}, 2, "n1");
        DefaultRowChange survivor = realUpdate(columns,
            new Serializable[] {1L, "n1", "c0"}, 3, "c1");
        Transaction transaction = new Transaction(null, null);
        transaction.appendRowChange(first);
        transaction.appendRowChange(survivor);
        transaction.setFinished(true);

        TransactionParallelApplierV3 spy = createSpy();
        spy.executorService.shutdownNow();
        spy.executorService = MoreExecutors.newDirectExecutorService();

        Exception applyError = null;
        try {
            spy.tranApply(Collections.singletonList(transaction));
        } catch (Exception e) {
            applyError = e;
        }

        if (strategy == ConflictStrategy.INTERRUPT) {
            Assert.assertNotNull("INTERRUPT must report compacted UPDATE miss", applyError);
            Assert.assertTrue(applyError.toString().contains("UPDATE_MISSED"));
        } else if (applyError != null) {
            throw applyError;
        }

        try (Connection conn = targetDataSource.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, name, content FROM db1.t1")) {
            if (strategy == ConflictStrategy.OVERWRITE) {
                Assert.assertTrue(rs.next());
                Assert.assertEquals(1L, rs.getLong("id"));
                Assert.assertEquals("n1", rs.getString("name"));
                Assert.assertEquals("c1", rs.getString("content"));
                Assert.assertFalse(rs.next());
            } else {
                Assert.assertFalse(rs.next());
            }
        }
        Assert.assertTrue("compacted survivor must carry the combined after-image", survivor.isForceAllColumns());
    }

    private void assertBatchUpdateMissStrategy(ConflictStrategy strategy) throws Exception {
        mockConfig(ConfigKeys.RPL_BATCH_ENABLED, "true");
        mockConfig(ConfigKeys.RPL_COLS_UPDATE_MODE, "ONUPDATE");
        when(applierConfig.getConflictStrategy()).thenReturn(strategy);
        when(applierConfig.isInsertOnUpdateMiss()).thenReturn(true);

        JdbcDataSource targetDataSource = new JdbcDataSource();
        targetDataSource.setURL("jdbc:h2:mem:batch_conflict_" + strategy
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        try (Connection conn = targetDataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute("CREATE SCHEMA db1");
            stmt.execute("CREATE TABLE db1.t1 (id BIGINT PRIMARY KEY, name VARCHAR(32))");
            stmt.execute("INSERT INTO db1.t1 VALUES (1, 'n0')");
        }

        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setEngine("InnoDB");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setColumns(Arrays.asList(
            new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20),
            new ColumnInfo("name", Types.VARCHAR, null, true, false, "VARCHAR", 32)));
        registerTableInfo(tableInfo);
        when(dbMetaCache.getBuiltInDefaultDataSource()).thenReturn(targetDataSource);

        DefaultColumnSet columns = new DefaultColumnSet(Arrays.asList(
            new DefaultColumn("id", 1, Types.BIGINT, true, false, true),
            new DefaultColumn("name", 2, Types.VARCHAR, false, true, false)));
        DefaultRowChange existing = realUpdate(columns,
            new Serializable[] {1L, "n0"}, 2, "n1");
        DefaultRowChange missing = realUpdate(columns,
            new Serializable[] {2L, "m0"}, 2, "m1");
        Transaction transaction = new Transaction(null, null);
        transaction.appendRowChange(existing);
        transaction.appendRowChange(missing);
        transaction.setFinished(true);

        TransactionParallelApplierV3 spy = createSpy();
        spy.executorService.shutdownNow();
        spy.executorService = MoreExecutors.newDirectExecutorService();

        Exception applyError = null;
        try {
            spy.tranApply(Collections.singletonList(transaction));
        } catch (Exception e) {
            applyError = e;
        }

        if (strategy == ConflictStrategy.INTERRUPT) {
            Assert.assertNotNull("INTERRUPT must report batch UPDATE miss", applyError);
            Assert.assertTrue(applyError.toString().contains("UPDATE_MISSED"));
        } else if (applyError != null) {
            throw applyError;
        }

        try (Connection conn = targetDataSource.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, name FROM db1.t1 ORDER BY id")) {
            Assert.assertTrue(rs.next());
            Assert.assertEquals(1L, rs.getLong("id"));
            Assert.assertEquals(strategy == ConflictStrategy.INTERRUPT ? "n0" : "n1", rs.getString("name"));
            if (strategy == ConflictStrategy.OVERWRITE) {
                Assert.assertTrue(rs.next());
                Assert.assertEquals(2L, rs.getLong("id"));
                Assert.assertEquals("m1", rs.getString("name"));
            }
            Assert.assertFalse(rs.next());
        }
    }

    private void assertUkChangeBarrierPreservesCompactedFinalState(boolean batchEnabled) throws Exception {
        assertUkChangeBarrierPreservesCompactedFinalState(batchEnabled, false);
    }

    private void assertUkChangeBarrierPreservesCompactedFinalState(boolean batchEnabled,
                                                                   boolean persisted) throws Exception {
        mockConfig(ConfigKeys.RPL_BATCH_ENABLED, Boolean.toString(batchEnabled));
        mockConfig(ConfigKeys.RPL_COLS_UPDATE_MODE, "ONUPDATE");

        JdbcDataSource targetDataSource = new JdbcDataSource();
        targetDataSource.setURL("jdbc:h2:mem:compaction_barrier_" + batchEnabled
            + "_" + persisted + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        try (Connection conn = targetDataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute("CREATE SCHEMA db1");
            stmt.execute("CREATE TABLE db1.t1 (id BIGINT PRIMARY KEY, uk_col VARCHAR(32) UNIQUE, "
                + "name VARCHAR(32), content VARCHAR(32))");
            stmt.execute("INSERT INTO db1.t1 VALUES (1, 'A', 'n0', 'c0')");
        }

        TableInfo tableInfo = realCompactionTableInfo();
        registerTableInfo(tableInfo);
        when(dbMetaCache.getBuiltInDefaultDataSource()).thenReturn(targetDataSource);

        DefaultColumnSet columns = realCompactionColumnSet();
        DefaultRowChange firstUpdate = realUpdate(columns,
            new Serializable[] {1L, "A", "n0", "c0"}, 3, "n1");
        DefaultRowChange survivor = realUpdate(columns,
            new Serializable[] {1L, "A", "n1", "c0"}, 4, "c1");
        DefaultRowChange barrier = realUpdate(columns,
            new Serializable[] {1L, "A", "n1", "c1"}, 2, "B");

        TransactionParallelApplierV3 spy = createSpy();
        spy.executorService.shutdownNow();
        spy.executorService = MoreExecutors.newDirectExecutorService();
        List<Transaction> transactions;
        if (persisted) {
            transactions = Arrays.asList(
                createPersistedDmlTransaction(firstUpdate),
                createPersistedDmlTransaction(survivor),
                createPersistedDmlTransaction(barrier));
        } else {
            transactions = Arrays.asList(
                createRealDmlTransaction(firstUpdate),
                createRealDmlTransaction(survivor),
                createRealDmlTransaction(barrier));
        }
        spy.tranApply(transactions);

        try (Connection conn = targetDataSource.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, uk_col, name, content FROM db1.t1")) {
            Assert.assertTrue(rs.next());
            Assert.assertEquals(1L, rs.getLong("id"));
            Assert.assertEquals("B", rs.getString("uk_col"));
            Assert.assertEquals("n1", rs.getString("name"));
            Assert.assertEquals("c1", rs.getString("content"));
            Assert.assertFalse(rs.next());
        }
        if (!persisted) {
            Assert.assertTrue("supersede survivor must carry the combined after-image", survivor.isForceAllColumns());
            Assert.assertFalse("barrier UPDATE must remain independent", barrier.isForceAllColumns());
        }
    }

    private TableInfo realKeylessTableInfo() {
        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setEngine("InnoDB");
        tableInfo.setColumns(Arrays.asList(
            new ColumnInfo("name", Types.VARCHAR, null, true, false, "VARCHAR", 32),
            new ColumnInfo("content", Types.VARCHAR, null, true, false, "VARCHAR", 32)));
        return tableInfo;
    }

    private DefaultColumnSet realKeylessColumnSet() {
        return new DefaultColumnSet(Arrays.asList(
            new DefaultColumn("name", 1, Types.VARCHAR, false, true, false),
            new DefaultColumn("content", 2, Types.VARCHAR, false, true, false)));
    }

    private TableInfo realBinaryPkTableInfo() {
        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setEngine("InnoDB");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setColumns(Arrays.asList(
            new ColumnInfo("id", Types.VARBINARY, null, false, false, "VARBINARY", 8),
            new ColumnInfo("payload", Types.VARCHAR, null, true, false, "VARCHAR", 32)));
        return tableInfo;
    }

    private DefaultColumnSet realBinaryPkColumnSet() {
        return new DefaultColumnSet(Arrays.asList(
            new DefaultColumn("id", 1, Types.VARBINARY, true, false, true),
            new DefaultColumn("payload", 2, Types.VARCHAR, false, true, false)));
    }

    private TableInfo realCompactionTableInfo() {
        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setEngine("InnoDB");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setUks(Collections.singletonList("uk_col"));
        tableInfo.setUkGroups(Collections.singletonList(Collections.singletonList("uk_col")));
        tableInfo.setColumns(Arrays.asList(
            new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20),
            new ColumnInfo("uk_col", Types.VARCHAR, null, false, false, "VARCHAR", 32),
            new ColumnInfo("name", Types.VARCHAR, null, true, false, "VARCHAR", 32),
            new ColumnInfo("content", Types.VARCHAR, null, true, false, "VARCHAR", 32)));
        return tableInfo;
    }

    private DefaultColumnSet realCompactionColumnSet() {
        List<DBMSColumn> columns = Arrays.asList(
            new DefaultColumn("id", 1, Types.BIGINT, true, false, true),
            new DefaultColumn("uk_col", 2, Types.VARCHAR, false, false, false, true),
            new DefaultColumn("name", 3, Types.VARCHAR, false, true, false),
            new DefaultColumn("content", 4, Types.VARCHAR, false, true, false));
        return new DefaultColumnSet(columns);
    }

    private DefaultRowChange realUpdate(DefaultColumnSet columns, Serializable[] beforeValues,
                                        int changedColumnIndex, Serializable afterValue) {
        DefaultRowChange rowChange = new DefaultRowChange(DBMSAction.UPDATE, "db1", "t1", columns);
        rowChange.addRowData(new DefaultRowData(beforeValues));
        rowChange.setChangeValue(1, changedColumnIndex, afterValue);
        return rowChange;
    }

    private Transaction createRealDmlTransaction(DefaultRowChange event) {
        Transaction tx = new Transaction(null, null);
        tx.appendRowChange(event);
        tx.setFinished(true);
        return tx;
    }

    private Transaction createPersistedDmlTransaction(DefaultRowChange event) throws Exception {
        RepoUnit repoUnit = mock(RepoUnit.class);
        Map<String, byte[]> persistedEvents = new HashMap<>();
        Mockito.doAnswer(invocation -> {
            byte[] key = invocation.getArgument(0);
            byte[] value = invocation.getArgument(1);
            persistedEvents.put(new String(key, StandardCharsets.UTF_8), value);
            return null;
        }).when(repoUnit).put(any(byte[].class), any(byte[].class));
        when(repoUnit.get(any(byte[].class))).thenAnswer(invocation ->
            persistedEvents.get(new String(invocation.getArgument(0), StandardCharsets.UTF_8)));
        when(repoUnit.getRange(any(byte[].class), any(byte[].class), anyInt())).thenAnswer(invocation -> {
            String begin = new String(invocation.getArgument(0), StandardCharsets.UTF_8);
            String end = new String(invocation.getArgument(1), StandardCharsets.UTF_8);
            int count = invocation.getArgument(2);
            List<Pair<byte[], byte[]>> result = new ArrayList<>();
            persistedEvents.entrySet().stream()
                .filter(entry -> entry.getKey().compareTo(begin) >= 0 && entry.getKey().compareTo(end) < 0)
                .sorted(Map.Entry.comparingByKey())
                .limit(count)
                .forEach(entry -> result.add(Pair.of(
                    entry.getKey().getBytes(StandardCharsets.UTF_8), entry.getValue())));
            return result;
        });

        PersistConfig persistConfig = new PersistConfig();
        persistConfig.setSupportPersist(true);
        persistConfig.setForcePersist(true);
        persistConfig.setTransPersistRangeMaxItemSize(100);
        persistConfig.setTransPersistRangeMaxByteSize(1024 * 1024);

        Transaction tx = new Transaction(repoUnit, persistConfig);
        tx.appendRowChange(event);
        tx.setFinished(true);
        Assert.assertTrue(tx.isPersisted());
        return tx;
    }

    private Class<?> getInnerClass(String simpleName) throws ClassNotFoundException {
        for (Class<?> clz : TransactionParallelApplierV3.class.getDeclaredClasses()) {
            if (clz.getSimpleName().equals(simpleName)) {
                return clz;
            }
        }
        throw new ClassNotFoundException("Inner class not found: " + simpleName);
    }
}
