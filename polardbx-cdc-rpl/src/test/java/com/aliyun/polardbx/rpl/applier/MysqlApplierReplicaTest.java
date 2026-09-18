/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.ApplierType;
import com.aliyun.polardbx.rpl.taskmeta.ConflictStrategy;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Timestamp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class MysqlApplierReplicaTest {

    private ApplierConfig config;
    private HostInfo hostInfo;
    private DbMetaCache dbMetaCache;
    private MysqlApplier applier;

    @Before
    public void setUp() {
        config = mock(ApplierConfig.class);
        hostInfo = mock(HostInfo.class);
        dbMetaCache = mock(DbMetaCache.class);
        when(config.getConflictStrategy()).thenReturn(ConflictStrategy.OVERWRITE);
        when(config.isEnableDdl()).thenReturn(true);
        applier = new MysqlApplier(config, hostInfo, mock(HostInfo.class));
        applier.dbMetaCache = dbMetaCache;
    }

    @Test
    public void init_RejectsSkipMismatchedColumnsForDirectUpdateApplier() throws Exception {
        when(config.isSkipMismatchedColumns()).thenReturn(true);
        when(config.getApplierType()).thenReturn(ApplierType.TRANSACTION);

        try {
            applier.init();
            Assert.fail("direct-update applier must reject missing-column mode");
        } catch (PolardbxException e) {
            Assert.assertTrue(e.getMessage().contains("MERGE and FULL_COPY"));
        }
    }

    @Test
    public void schemaRefreshOnly_RefreshesTableAndRemovesDroppedDatabaseWithoutExecutingDdl() throws Exception {
        DefaultQueryLog alter = query("dst", "ALTER TABLE t ADD COLUMN c1 INT");
        alter.setSchemaRefreshOnly(true);
        applier.ddlApply(alter);
        verify(dbMetaCache).refreshTableInfo("dst", "t");

        clearInvocations(dbMetaCache);
        DefaultQueryLog dropDatabase = query("dst", "DROP DATABASE dst");
        dropDatabase.setSchemaRefreshOnly(true);
        applier.ddlApply(dropDatabase);
        verify(dbMetaCache).removeDataSource("dst");
    }

    @Test
    public void addColumnWhitelist_RejectsDropColumnBeforeAnyDatabaseAction() throws Exception {
        when(config.isDdlOnlyAddColumn()).thenReturn(true);

        applier.ddlApply(query("dst", "ALTER TABLE t DROP COLUMN old_col"));

        verifyNoInteractions(dbMetaCache);
    }

    @Test
    public void addColumnWhitelist_AllowsAddColumnToReachDdlPreparation() throws Exception {
        when(config.isDdlOnlyAddColumn()).thenReturn(true);
        DefaultQueryLog queryLog = query("dst", "ALTER TABLE t ADD COLUMN c1 INT");

        try (MockedStatic<DdlApplyHelper> ddl = Mockito.mockStatic(DdlApplyHelper.class)) {
            ddl.when(() -> DdlApplyHelper.getOriginSql(anyString())).thenReturn("");
            ddl.when(() -> DdlApplyHelper.isOnlyAddColumn(any())).thenReturn(true);
            ddl.when(() -> DdlApplyHelper.getTso(queryLog.getQuery(), queryLog.getTimestamp(), queryLog.getPosition()))
                .thenReturn("tso-1");
            ddl.when(() -> DdlApplyHelper.getDdlSqlContext(eq(queryLog), anyString(), eq("tso-1")))
                .thenReturn(null);

            applier.ddlApply(queryLog);

            ddl.verify(() -> DdlApplyHelper.isOnlyAddColumn(any()));
            ddl.verify(() -> DdlApplyHelper.getDdlSqlContext(eq(queryLog), anyString(), eq("tso-1")));
        }
        verifyNoInteractions(dbMetaCache);
    }

    private static DefaultQueryLog query(String schema, String sql) {
        return new DefaultQueryLog(schema, sql, new Timestamp(1), 0, 0);
    }
}
