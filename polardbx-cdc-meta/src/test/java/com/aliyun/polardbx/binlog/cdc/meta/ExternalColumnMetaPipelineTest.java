/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.system.ISystemDBProvider;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExternalColumnMetaPipelineTest {

    private static MockedStatic<SpringContextHolder> mockedSpringContext;

    @BeforeClass
    public static void beforeClass() {
        mockedSpringContext = Mockito.mockStatic(SpringContextHolder.class, Mockito.CALLS_REAL_METHODS);
        ISystemDBProvider provider = mock(ISystemDBProvider.class);
        mockedSpringContext.when(() -> SpringContextHolder.getObject(ISystemDBProvider.class)).thenReturn(provider);
    }

    @AfterClass
    public static void afterClass() {
        mockedSpringContext.close();
    }

    @Test
    public void testCompareMapsEverySupportedExternalTypeToBlobRef() {
        String[] types = {
            "tinytext", "text", "mediumtext", "longtext",
            "tinyblob", "blob", "mediumblob", "longblob"};
        try (MockedStatic<DynamicApplicationConfig> config = Mockito.mockStatic(DynamicApplicationConfig.class)) {
            for (String type : types) {
                TableMeta.FieldMeta logicalField = field("Payload", type, "utf8", true);
                TableMeta logic = table("logic_db", "logic_table", logicalField);
                TableMeta.FieldMeta blobRefField = field("PAYLOAD_ADDR_", "VARCHAR ( 128 )", "utf8", false);
                TableMeta physical = table("phy_db", "phy_table", blobRefField);

                LogicTableMeta result = compare(logic, physical);
                Assert.assertFalse(result.isCompatible());
                Assert.assertTrue(result.hasExternalizedFields());
                Assert.assertEquals(1, result.getLogicFields().size());
                LogicTableMeta.FieldMetaExt mapped = result.getLogicFields().get(0);
                Assert.assertEquals(0, mapped.getLogicIndex());
                Assert.assertEquals(0, mapped.getPhyIndex());
                Assert.assertFalse(mapped.isTypeMatch());
                Assert.assertFalse(mapped.isExternalMappingUnavailable());
            }
        }
    }

    @Test
    public void testCompareFailsClosedForMissingAndInvalidBlobRefByDefault() {
        try (MockedStatic<DynamicApplicationConfig> config = Mockito.mockStatic(DynamicApplicationConfig.class)) {
            config.when(() -> DynamicApplicationConfig.getBoolean(
                ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_BLOB_REF_ERROR_FALLBACK_ENABLED)).thenReturn(false);
            TableMeta logic = table("logic_db", "logic_table", field("payload", "longblob", null, true));

            assertFailure("externalized physical BlobRef column payload_addr_ is not found",
                () -> compare(logic,
                    table("phy_db", "phy_table", field("unrelated", "bigint", null, false))));
            assertFailure("invalid externalized physical BlobRef column type varchar(127)",
                () -> compare(logic,
                    table("phy_db", "phy_table", field("payload_addr_", "varchar(127)", "utf8", false))));
        }
    }

    @Test
    public void testCompareMarksMissingAndInvalidBlobRefWhenEmergencyFallbackEnabled() {
        try (MockedStatic<DynamicApplicationConfig> config = Mockito.mockStatic(DynamicApplicationConfig.class)) {
            config.when(() -> DynamicApplicationConfig.getBoolean(
                ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_BLOB_REF_ERROR_FALLBACK_ENABLED)).thenReturn(true);
            TableMeta logic = table("logic_db", "logic_table", field("payload", "longblob", null, true));

            LogicTableMeta missing = compare(logic,
                table("phy_db", "phy_table", field("unrelated", "bigint", null, false)));
            LogicTableMeta.FieldMetaExt missingField = missing.getLogicFields().get(0);
            Assert.assertEquals(-1, missingField.getPhyIndex());
            Assert.assertTrue(missingField.isExternalMappingUnavailable());
            Assert.assertTrue(missingField.getExternalMappingError().contains("is not found"));

            LogicTableMeta wrongType = compare(logic,
                table("phy_db", "phy_table", field("payload_addr_", "varchar(127)", "utf8", false)));
            LogicTableMeta.FieldMetaExt wrongTypeField = wrongType.getLogicFields().get(0);
            Assert.assertEquals(0, wrongTypeField.getPhyIndex());
            Assert.assertTrue(wrongTypeField.isExternalMappingUnavailable());
            Assert.assertTrue(wrongTypeField.getExternalMappingError().contains("expected varchar(128)"));
        }
    }

    @Test
    public void testCompareRejectsUnsupportedLogicalTypeAndTextCharset() {
        try (MockedStatic<DynamicApplicationConfig> config = Mockito.mockStatic(DynamicApplicationConfig.class)) {
            TableMeta physical =
                table("phy_db", "phy_table", field("payload_addr_", "varchar(128)", "utf8", false));
            assertFailure("unsupported externalized logical column type",
                () -> compare(table("logic_db", "logic_table", field("payload", "json", "utf8", true)), physical));
            assertFailure("unsupported externalized TEXT charset",
                () -> compare(table("logic_db", "logic_table", field("payload", "text", "latin1", true)), physical));

            Assert.assertEquals(0, compare(
                table("logic_db", "logic_table", field("payload", "text", "utf8mb3", true)), physical)
                .getLogicFields().get(0).getPhyIndex());
            Assert.assertEquals(0, compare(
                table("logic_db", "logic_table", field("payload", "text", "utf8mb4", true)), physical)
                .getLogicFields().get(0).getPhyIndex());
        }
    }

    @Test
    public void testConsistencyCheckerUsesPhysicalBlobRefForExternalLogicalField() {
        TableMeta logic = table("logic_db", "logic_table",
            field("id", "bigint", null, false), field("payload", "longblob", "utf8", true));
        TableMeta physical = table("phy_db", "phy_table",
            field("id", "bigint", null, false), field("payload_addr_", "varchar(128)", "utf8", false));
        consistencyChecker(logic, physical, physical)
            .compareForOneLogicTable("tso", "logic_db", "logic_table", false);
    }

    @Test
    public void testConsistencyCheckerFailsClosedForMissingOrWrongBlobRef() {
        TableMeta logic = table("logic_db", "logic_table", field("payload", "longblob", "utf8", true));
        TableMeta distinctPhysical =
            table("phy_db", "phy_table", field("payload_addr_", "varchar(128)", "utf8", false));

        assertFailure("distinct physical table meta is missing",
            () -> consistencyChecker(logic, distinctPhysical, null)
                .compareForOneLogicTable("tso", "logic_db", "logic_table", false));

        TableMeta missing = table("phy_db", "phy_table", field("unrelated", "bigint", null, false));
        assertFailure("is not found",
            () -> consistencyChecker(logic, missing, distinctPhysical)
                .compareForOneLogicTable("tso", "logic_db", "logic_table", false));

        TableMeta wrong =
            table("phy_db", "phy_table", field("payload_addr_", "varbinary(128)", null, false));
        assertFailure("invalid externalized physical BlobRef column type",
            () -> consistencyChecker(logic, wrong, distinctPhysical)
                .compareForOneLogicTable("tso", "logic_db", "logic_table", false));
    }

    @Test
    public void testLogicMetaTracksExternalFieldsForAddAndReplace() {
        TableMeta.FieldMeta external = field("payload", "longblob", null, true);
        LogicTableMeta.FieldMetaExt externalExt = new LogicTableMeta.FieldMetaExt(external, 0, 0);
        LogicTableMeta meta = new LogicTableMeta();
        meta.add(externalExt);
        Assert.assertTrue(meta.hasExternalizedFields());

        LogicTableMeta.FieldMetaExt ordinary =
            new LogicTableMeta.FieldMetaExt(field("id", "bigint", null, false), 0, 0);
        meta.setLogicFields(Collections.singletonList(ordinary));
        Assert.assertFalse(meta.hasExternalizedFields());
        meta.setLogicFields(Arrays.asList(ordinary, externalExt));
        Assert.assertTrue(meta.hasExternalizedFields());
        Assert.assertTrue(externalExt.toString().contains("externalized=true"));
    }

    private static LogicTableMeta compare(TableMeta logic, TableMeta physical) {
        PolarDbXTableMetaManager manager = mock(PolarDbXTableMetaManager.class, Mockito.CALLS_REAL_METHODS);
        doReturn(physical).when(manager).findPhyTable("phy_db", "phy_table", true);
        doReturn(logic).when(manager).findLogicTableMeta("phy_db", "phy_table");
        return manager.compare("phy_db", "phy_table", physical.getFields().size());
    }

    private static ConsistencyChecker consistencyChecker(TableMeta logic, TableMeta physical,
                                                         TableMeta distinctPhysical) {
        TopologyManager topologyManager = mock(TopologyManager.class);
        PolarDbXLogicTableMeta logicTableMeta = mock(PolarDbXLogicTableMeta.class);
        PolarDbXTableMetaManager tableMetaManager = mock(PolarDbXTableMetaManager.class);
        LogicMetaTopology.LogicTableMetaTopology tableTopology =
            mock(LogicMetaTopology.LogicTableMetaTopology.class);
        LogicMetaTopology.PhyTableTopology physicalTopology = mock(LogicMetaTopology.PhyTableTopology.class);
        when(topologyManager.getTopology("logic_db", "logic_table"))
            .thenReturn(Pair.of(mock(LogicMetaTopology.LogicDbTopology.class), tableTopology));
        when(tableTopology.getPhySchemas()).thenReturn(Collections.singletonList(physicalTopology));
        when(physicalTopology.getStorageInstId()).thenReturn("dn-test");
        when(physicalTopology.getSchema()).thenReturn("phy_db");
        when(physicalTopology.getPhyTables()).thenReturn(Collections.singletonList("phy_table"));
        when(logicTableMeta.find("logic_db", "logic_table")).thenReturn(logic);
        when(logicTableMeta.findDistinctPhy("logic_db", "logic_table")).thenReturn(distinctPhysical);
        when(tableMetaManager.findPhyTable("phy_db", "phy_table", false)).thenReturn(physical);
        return new ConsistencyChecker(topologyManager, logicTableMeta, tableMetaManager, "dn-test");
    }

    private static TableMeta table(String schema, String table, TableMeta.FieldMeta... fields) {
        TableMeta result = new TableMeta(schema, table, Arrays.asList(fields));
        result.setCharset("utf8");
        return result;
    }

    private static TableMeta.FieldMeta field(String name, String type, String charset, boolean externalized) {
        TableMeta.FieldMeta result = new TableMeta.FieldMeta(name, type, true, false, null, false, charset);
        result.setExternalized(externalized);
        return result;
    }

    private static void assertFailure(String messageFragment, ThrowingRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("expected PolardbxException containing: " + messageFragment);
        } catch (PolardbxException e) {
            Assert.assertTrue("unexpected message: " + e.getMessage(), e.getMessage().contains(messageFragment));
        }
    }

    private interface ThrowingRunnable {
        void run();
    }
}
