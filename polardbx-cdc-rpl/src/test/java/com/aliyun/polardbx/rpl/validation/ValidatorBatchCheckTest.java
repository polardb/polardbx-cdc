/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ValidatorBatchCheckTest extends BaseTest {

    private Validator validator;

    @Mock
    private DataImportMeta.ValidationMeta validationMeta;

    @Mock
    private DataImportMeta.ConnInfo srcConnInfo;

    @Mock
    private DataImportMeta.ConnInfo dstConnInfo;

    @Mock
    private TableInfo tableInfo;

    @Mock
    private DruidDataSource srcDataSource;

    @Mock
    private DruidDataSource dstDataSource;

    @Mock
    private DruidPooledConnection srcConnection;

    @Mock
    private DruidPooledConnection dstConnection;

    @Mock
    private PreparedStatement srcPreparedStatement;

    @Mock
    private PreparedStatement dstPreparedStatement;

    @Mock
    private ResultSet srcResultSet;

    @Mock
    private ResultSet dstResultSet;

    @Mock
    private ExecutorService srcThreadPool;

    @Mock
    private ExecutorService dstThreadPool;

    @Mock
    private Future<Pair<Integer, String>> srcFuture;

    @Mock
    private Future<Pair<Integer, String>> dstFuture;

    private Map<String, DruidDataSource> srcDsMap;
    private Map<String, DruidDataSource> dstDsMap;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Mock configuration
        mockConfig("rpl_full_valid_max_row_size_per_second", "1000");
        mockConfig("rpl_full_valid_table_parallelism", "4");
        mockConfig("rpl_full_valid_skip_collect_statistic", "false");

        // Setup validation meta
        when(validationMeta.getType()).thenReturn(ValidationTypeEnum.BACKWARD);
        when(validationMeta.getSrcLogicalConnInfo()).thenReturn(srcConnInfo);
        when(validationMeta.getDstLogicalConnInfo()).thenReturn(dstConnInfo);

        // Setup connection info
        when(srcConnInfo.getType()).thenReturn(HostType.POLARX1);

        // Setup data sources map
        srcDsMap = new HashMap<>();
        dstDsMap = new HashMap<>();
        srcDsMap.put("test_db", srcDataSource);
        dstDsMap.put("test_db", dstDataSource);

        // Setup database connections
        when(srcDataSource.getConnection()).thenReturn(srcConnection);
        when(dstDataSource.getConnection()).thenReturn(dstConnection);

        // Setup prepared statements
        when(srcConnection.prepareStatement(anyString())).thenReturn(srcPreparedStatement);
        when(dstConnection.prepareStatement(anyString())).thenReturn(dstPreparedStatement);

        // Setup result sets
        when(srcPreparedStatement.executeQuery()).thenReturn(srcResultSet);
        when(dstPreparedStatement.executeQuery()).thenReturn(dstResultSet);

        // Setup table info
        when(tableInfo.getKeyList()).thenReturn(new ArrayList<String>() {{
            add("id");
        }});

        RplService rplService = mock(RplService.class);
        RplTask rplTask = mock(RplTask.class);
        RplStateMachine rplStateMachine = mock(RplStateMachine.class);
        when(rplService.getId()).thenReturn(1L);
        when(rplTask.getId()).thenReturn(1L);
        when(rplStateMachine.getId()).thenReturn(1L);
        TaskContext.getInstance().setService(rplService);
        TaskContext.getInstance().setTask(rplTask);
        TaskContext.getInstance().setStateMachine(rplStateMachine);
        validator = new Validator(validationMeta);
        // Inject thread pools using reflection
        injectThreadPools();
    }

    private void injectThreadPools() throws Exception {
        // Use reflection to set the private thread pool fields
        Class<Validator> validatorClass = Validator.class;

        java.lang.reflect.Field srcThreadPoolField = validatorClass.getDeclaredField("srcThreadPool");
        srcThreadPoolField.setAccessible(true);
        srcThreadPoolField.set(validator, srcThreadPool);

        java.lang.reflect.Field dstThreadPoolField = validatorClass.getDeclaredField("dstThreadPool");
        dstThreadPoolField.setAccessible(true);
        dstThreadPoolField.set(validator, dstThreadPool);

        java.lang.reflect.Field srcDsField = validatorClass.getDeclaredField("srcDs");
        srcDsField.setAccessible(true);
        srcDsField.set(validator, srcDsMap);

        java.lang.reflect.Field dstDsField = validatorClass.getDeclaredField("dstDs");
        dstDsField.setAccessible(true);
        dstDsField.set(validator, dstDsMap);
    }

    @Test
    public void testBatchCheck_Success() throws Exception {
        String srcDbName = "test_db";
        String dstDbName = "test_db";
        String tableName = "test_table";

        // Mock DbMetaManager.getTableInfo
        try (MockedStatic<DbMetaManager> dbMetaManagerMockedStatic = Mockito.mockStatic(DbMetaManager.class)) {
            dbMetaManagerMockedStatic.when(
                    () -> DbMetaManager.getTableInfo(any(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(tableInfo);

            // Mock ValSQLGenerator.getBatchCheckSql
            SqlContextBuilder.SqlContext srcContext =
                new SqlContextBuilder.SqlContext("SELECT COUNT(*), CHECKSUM(*) FROM test_table", new ArrayList<>());

            SqlContextBuilder.SqlContext dstContext =
                new SqlContextBuilder.SqlContext("SELECT COUNT(*), CHECKSUM(*) FROM test_table", new ArrayList<>());

            try (
                MockedStatic<ValSQLGenerator> valSQLGeneratorMockedStatic = Mockito.mockStatic(ValSQLGenerator.class)) {
                valSQLGeneratorMockedStatic.when(
                        () -> ValSQLGenerator.getBatchCheckSql(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(srcContext)
                    .thenReturn(dstContext);

                // Mock execBatchCheck results - both return same values
                Pair<Integer, String> srcResult = new ImmutablePair<>(100, "checksum123");
                Pair<Integer, String> dstResult = new ImmutablePair<>(100, "checksum123");

                when(srcFuture.get()).thenReturn(srcResult);
                when(dstFuture.get()).thenReturn(dstResult);

                when(srcThreadPool.submit(any(java.util.concurrent.Callable.class))).thenReturn(srcFuture);
                when(dstThreadPool.submit(any(java.util.concurrent.Callable.class))).thenReturn(dstFuture);

                // Mock result set for execBatchCheck
                when(srcResultSet.next()).thenReturn(true);
                when(srcResultSet.getInt(1)).thenReturn(100);
                when(srcResultSet.getString(2)).thenReturn("checksum123");

                when(dstResultSet.next()).thenReturn(true);
                when(dstResultSet.getInt(1)).thenReturn(100);
                when(dstResultSet.getString(2)).thenReturn("checksum123");

                // Mock StatMetrics
                try (MockedStatic<StatMetrics> statMetricsMockedStatic = Mockito.mockStatic(StatMetrics.class)) {
                    StatMetrics statMetrics = mock(StatMetrics.class);
                    statMetricsMockedStatic.when(StatMetrics::getInstance).thenReturn(statMetrics);

                    // Execute the method
                    boolean result = validator.batchCheck(srcDbName, dstDbName, tableName, null, null);

                    // Verify
                    assertTrue("Batch check should return true when source and destination match", result);

                    // Verify interactions
                    verify(srcThreadPool).submit(any(java.util.concurrent.Callable.class));
                    verify(dstThreadPool).submit(any(java.util.concurrent.Callable.class));
                }
            }
        }
    }

    @Test
    public void testBatchCheck_Failure() throws Exception {
        String srcDbName = "test_db";
        String dstDbName = "test_db";
        String tableName = "test_table";

        try (MockedStatic<DbMetaManager> dbMetaManagerMockedStatic = Mockito.mockStatic(DbMetaManager.class)) {
            dbMetaManagerMockedStatic.when(
                    () -> DbMetaManager.getTableInfo(any(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(tableInfo);

            SqlContextBuilder.SqlContext srcContext =
                new SqlContextBuilder.SqlContext("SELECT COUNT(*), CHECKSUM(*) FROM test_table", new ArrayList<>());

            SqlContextBuilder.SqlContext dstContext =
                new SqlContextBuilder.SqlContext("SELECT COUNT(*), CHECKSUM(*) FROM test_table", new ArrayList<>());

            try (
                MockedStatic<ValSQLGenerator> valSQLGeneratorMockedStatic = Mockito.mockStatic(ValSQLGenerator.class)) {
                valSQLGeneratorMockedStatic.when(
                        () -> ValSQLGenerator.getBatchCheckSql(anyString(), anyString(), any(), any(), any()))
                    .thenReturn(srcContext)
                    .thenReturn(dstContext);

                // Mock execBatchCheck results - different values
                Pair<Integer, String> srcResult = new ImmutablePair<>(100, "checksum123");
                Pair<Integer, String> dstResult =
                    new ImmutablePair<>(99, "checksum456"); // Different count and checksum

                when(srcFuture.get()).thenReturn(srcResult);
                when(dstFuture.get()).thenReturn(dstResult);

                when(srcThreadPool.submit(any(java.util.concurrent.Callable.class))).thenReturn(srcFuture);
                when(dstThreadPool.submit(any(java.util.concurrent.Callable.class))).thenReturn(dstFuture);

                // Mock result set for execBatchCheck
                when(srcResultSet.next()).thenReturn(true);
                when(srcResultSet.getInt(1)).thenReturn(100);
                when(srcResultSet.getString(2)).thenReturn("checksum123");

                when(dstResultSet.next()).thenReturn(true);
                when(dstResultSet.getInt(1)).thenReturn(99);
                when(dstResultSet.getString(2)).thenReturn("checksum456");

                try (MockedStatic<StatMetrics> statMetricsMockedStatic = Mockito.mockStatic(StatMetrics.class)) {
                    StatMetrics statMetrics = mock(StatMetrics.class);
                    statMetricsMockedStatic.when(StatMetrics::getInstance).thenReturn(statMetrics);

                    // Execute the method
                    boolean result = validator.batchCheck(srcDbName, dstDbName, tableName, null, null);

                    // Verify
                    assertFalse("Batch check should return false when source and destination don't match", result);
                }
            }
        }
    }

    @Test(expected = Exception.class)
    public void testBatchCheck_Exception() throws Exception {
        String srcDbName = "test_db";
        String dstDbName = "test_db";
        String tableName = "test_table";

        try (MockedStatic<DbMetaManager> dbMetaManagerMockedStatic = Mockito.mockStatic(DbMetaManager.class)) {
            dbMetaManagerMockedStatic.when(
                    () -> DbMetaManager.getTableInfo(any(), anyString(), anyString(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("Database connection error"));

            validator.batchCheck(srcDbName, dstDbName, tableName, null, null);
        }
    }
}
