/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.cdc.meta.MetaFilter;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 * 单元测试：ConsistencyChecker.checkLogicAndPhysicalConsistency()
 */
@RunWith(MockitoJUnitRunner.class)
public class ConsistencyCheckerTest {

    private ConsistencyChecker consistencyChecker;

    @Mock
    private TopologyManager topologyManager;

    @Mock
    private PolarDbXLogicTableMeta polarDbXLogicTableMeta;

    @Mock
    private PolarDbXTableMetaManager polarDbXTableMetaManager;

    @Before
    public void setUp() throws Exception {
        // 初始化被测对象
        this.consistencyChecker = Mockito.spy(new ConsistencyChecker(
            topologyManager,
            polarDbXLogicTableMeta,
            polarDbXTableMetaManager,
            "testStorageInstId"
        ));
    }

    /**
     * TC01: 配置关闭一致性检查
     */
    @Test
    public void test_checkLogicAndPhysicalConsistency_configDisabled() {
        DDLRecord record = mock(DDLRecord.class);

        try (MockedStatic<DynamicApplicationConfig> configMockedStatic = mockStatic(DynamicApplicationConfig.class)) {
            configMockedStatic.when(() -> DynamicApplicationConfig.getBoolean(anyString())).thenReturn(false);

            doCallRealMethod().when(consistencyChecker).checkLogicAndPhysicalConsistency("tso", record);
            consistencyChecker.checkLogicAndPhysicalConsistency("tso", record);
        }
    }

    /**
     * TC02: MetaFilter.isSupportApply 返回 false
     */
    @Test
    public void test_checkLogicAndPhysicalConsistency_isSupportApplyFalse() {
        DDLRecord record = mock(DDLRecord.class);

        try (MockedStatic<DynamicApplicationConfig> configMockedStatic = mockStatic(DynamicApplicationConfig.class);
            MockedStatic<MetaFilter> filterMockedStatic = mockStatic(MetaFilter.class)) {
            configMockedStatic.when(() -> DynamicApplicationConfig.getBoolean(anyString())).thenReturn(true);
            filterMockedStatic.when(() -> MetaFilter.isSupportApply(record)).thenReturn(false);

            doCallRealMethod().when(consistencyChecker).checkLogicAndPhysicalConsistency("tso", record);
            consistencyChecker.checkLogicAndPhysicalConsistency("tso", record);
        }
    }

    /**
     * TC03: DROP_DATABASE 类型 DDL
     */
    @Test(expected = PolardbxException.class)
    public void test_checkLogicAndPhysicalConsistency_dropDatabase() {
        DDLRecord record = mock(DDLRecord.class);
        when(record.getSqlKind()).thenReturn("DROP_DATABASE");
        when(record.getSchemaName()).thenReturn("test_schema");

        try (MockedStatic<DynamicApplicationConfig> configMockedStatic = mockStatic(DynamicApplicationConfig.class);
            MockedStatic<MetaFilter> filterMockedStatic = mockStatic(MetaFilter.class)) {
            configMockedStatic.when(() -> DynamicApplicationConfig.getBoolean(anyString())).thenReturn(true);
            filterMockedStatic.when(() -> MetaFilter.isSupportApply(record)).thenReturn(true);

            when(topologyManager.getTopology(anyString())).thenReturn(mock(LogicMetaTopology.LogicDbTopology.class));

            doCallRealMethod().when(consistencyChecker).checkLogicAndPhysicalConsistency("tso", record);
            consistencyChecker.checkLogicAndPhysicalConsistency("tso", record);
        }
    }

    /**
     * TC04: DROP_TABLE 类型 DDL
     */
    @Test(expected = PolardbxException.class)
    public void test_checkLogicAndPhysicalConsistency_dropTable() {
        DDLRecord record = mock(DDLRecord.class);
        when(record.getSqlKind()).thenReturn("DROP_TABLE");
        when(record.getSchemaName()).thenReturn("test_schema");
        when(record.getTableName()).thenReturn("test_table");

        try (MockedStatic<DynamicApplicationConfig> configMockedStatic = mockStatic(DynamicApplicationConfig.class);
            MockedStatic<MetaFilter> filterMockedStatic = mockStatic(MetaFilter.class)) {
            configMockedStatic.when(() -> DynamicApplicationConfig.getBoolean(anyString())).thenReturn(true);
            filterMockedStatic.when(() -> MetaFilter.isSupportApply(record)).thenReturn(true);

            Pair pair = mock(Pair.class);
            when(pair.getRight()).thenReturn(mock(LogicMetaTopology.LogicTableMetaTopology.class));
            when(topologyManager.getTopology(anyString(), anyString())).thenReturn(pair);

            doCallRealMethod().when(consistencyChecker).checkLogicAndPhysicalConsistency("tso", record);
            consistencyChecker.checkLogicAndPhysicalConsistency("tso", record);
        }
    }

    /**
     * TC06: ALTER 类型 DDL 且非 OMC
     */
    @Test
    public void test_checkLogicAndPhysicalConsistency_alterTable() {
        DDLRecord record = mock(DDLRecord.class);
        when(record.getSqlKind()).thenReturn("ALTER");
        when(record.getDdlSql()).thenReturn("ALTER TABLE test ADD COLUMN col INT");
        when(record.getSchemaName()).thenReturn("test_schema");
        when(record.getTableName()).thenReturn("test_table");
        when(record.getExtInfo()).thenReturn(null);

        try (MockedStatic<DynamicApplicationConfig> configMockedStatic = mockStatic(DynamicApplicationConfig.class);
            MockedStatic<MetaFilter> filterMockedStatic = mockStatic(MetaFilter.class);
            MockedStatic<SQLUtils> sqlUtilsMockedStatic = mockStatic(SQLUtils.class)) {
            configMockedStatic.when(() -> DynamicApplicationConfig.getBoolean(anyString())).thenReturn(true);
            filterMockedStatic.when(() -> MetaFilter.isSupportApply(record)).thenReturn(true);
            SQLStatement stmt = mock(SQLStatement.class);
            sqlUtilsMockedStatic.when(() -> SQLUtils.parseSQLStatement(anyString())).thenReturn(stmt);

            when(stmt.toString()).thenReturn("ALTER TABLE test ADD COLUMN col INT");
            doNothing().when(consistencyChecker)
                .compareForOneLogicTable(anyString(), anyString(), anyString(), anyBoolean());

            doCallRealMethod().when(consistencyChecker).checkLogicAndPhysicalConsistency("tso", record);
            consistencyChecker.checkLogicAndPhysicalConsistency("tso", record);
        }
    }

    /**
     * TC07: DDL SQL 无法解析
     */
    @Test(expected = PolardbxException.class)
    public void test_checkLogicAndPhysicalConsistency_parseFailed() {
        DDLRecord record = mock(DDLRecord.class);
        when(record.getSqlKind()).thenReturn("ALTER");
        when(record.getDdlSql()).thenReturn("INVALID_SQL");

        try (MockedStatic<DynamicApplicationConfig> configMockedStatic = mockStatic(DynamicApplicationConfig.class);
            MockedStatic<MetaFilter> filterMockedStatic = mockStatic(MetaFilter.class);
            MockedStatic<SQLUtils> sqlUtilsMockedStatic = mockStatic(SQLUtils.class)) {
            configMockedStatic.when(() -> DynamicApplicationConfig.getBoolean(anyString())).thenReturn(true);
            filterMockedStatic.when(() -> MetaFilter.isSupportApply(record)).thenReturn(true);

            sqlUtilsMockedStatic.when(() -> SQLUtils.parseSQLStatement(anyString())).thenThrow(new RuntimeException());

            doCallRealMethod().when(consistencyChecker).checkLogicAndPhysicalConsistency("tso", record);
            consistencyChecker.checkLogicAndPhysicalConsistency("tso", record);
        }
    }
}
