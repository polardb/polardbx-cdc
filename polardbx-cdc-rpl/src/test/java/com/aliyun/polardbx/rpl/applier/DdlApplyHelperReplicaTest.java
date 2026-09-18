/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Timestamp;

public class DdlApplyHelperReplicaTest {

    @Test
    public void onlyAddColumn_AcceptsEveryAddColumnAndRejectsOtherStatements() {
        Assert.assertFalse(DdlApplyHelper.isOnlyAddColumn(statement("CREATE TABLE t(id INT)")));
        Assert.assertFalse(DdlApplyHelper.isOnlyAddColumn(new SQLAlterTableStatement()));
        Assert.assertTrue(DdlApplyHelper.isOnlyAddColumn(statement(
            "ALTER TABLE t ADD COLUMN c1 INT, ADD COLUMN c2 VARCHAR(20)")));
        Assert.assertFalse(DdlApplyHelper.isOnlyAddColumn(statement("ALTER TABLE t DROP COLUMN c1")));
        Assert.assertFalse(DdlApplyHelper.isOnlyAddColumn(statement(
            "ALTER TABLE t ADD COLUMN c1 INT, DROP COLUMN c2")));
    }

    @Test
    public void ddlSqlContext_StripsAuditCommentBeforeTddlHintWhenConfigured() {
        String sql = "/* audit comment */ /*+TDDL:CMD_EXTRA(ENABLE_ASYNC_DDL=false)*/ "
            + "ALTER TABLE t ADD COLUMN c1 INT";
        DefaultQueryLog queryLog = new DefaultQueryLog("dst", sql, new Timestamp(1), 0, 0);

        try (MockedStatic<DynamicApplicationConfig> config = Mockito.mockStatic(DynamicApplicationConfig.class)) {
            config.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_DDL_STRIP_LEADING_COMMENTS))
                .thenReturn(true);
            Assert.assertTrue(DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_DDL_STRIP_LEADING_COMMENTS));

            SqlContext context = DdlApplyHelper.getDdlSqlContext(queryLog, "token-1", "tso-1");

            Assert.assertNotNull(context);
            Assert.assertFalse(context.getSql().contains("audit comment"));
            Assert.assertTrue(context.getSql().startsWith("/*DDL_SUBMIT_TOKEN=token-1*/"));
            Assert.assertEquals("dst", context.getDstSchema());
            Assert.assertEquals("t", context.getDstTable());
        }
    }

    @Test
    public void dropDatabase_DetectsCommentsAndCaseButNotDropTable() {
        Assert.assertTrue(DdlApplyHelper.isDropDatabase("/*hint*/ DROP DATABASE IF EXISTS dst"));
        Assert.assertTrue(DdlApplyHelper.isDropDatabase("drop database dst"));
        Assert.assertFalse(DdlApplyHelper.isDropDatabase("DROP TABLE dst.t"));
    }

    private static SQLStatement statement(String sql) {
        SQLStatement statement = SQLUtils.parseSQLStatement(sql);
        Assert.assertNotNull("DDL must be parseable for this test", statement);
        return statement;
    }
}
