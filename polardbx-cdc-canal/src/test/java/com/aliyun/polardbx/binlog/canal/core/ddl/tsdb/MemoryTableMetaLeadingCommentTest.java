/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;

import static org.mockito.Mockito.mock;

public class MemoryTableMetaLeadingCommentTest {

    @Test
    public void apply_StripsAuditCommentBeforeTddlHintAndUpdatesSchema() {
        Logger logger = mock(Logger.class);
        MemoryTableMeta tableMeta = new MemoryTableMeta(logger, false);
        String ddl = "/* audit trace */ /*+TDDL:cmd_extra()*/ CREATE TABLE t_comment (id BIGINT)";

        try (MockedStatic<SpringContextHolder> springContext = Mockito.mockStatic(SpringContextHolder.class);
            MockedStatic<DynamicApplicationConfig> config = Mockito.mockStatic(DynamicApplicationConfig.class)) {
            springContext.when(SpringContextHolder::isInitialize).thenReturn(true);
            config.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_DDL_STRIP_LEADING_COMMENTS))
                .thenReturn(true);

            Assert.assertTrue(tableMeta.apply(null, "dst", ddl, null));
            TableMeta created = tableMeta.find("dst", "t_comment");
            Assert.assertNotNull(created);
            Assert.assertEquals("bigint", created.getFieldMetaByName("id").getColumnType());
            springContext.verify(SpringContextHolder::isInitialize, Mockito.atLeastOnce());
            config.verify(
                () -> DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_DDL_STRIP_LEADING_COMMENTS));
        }
    }
}
