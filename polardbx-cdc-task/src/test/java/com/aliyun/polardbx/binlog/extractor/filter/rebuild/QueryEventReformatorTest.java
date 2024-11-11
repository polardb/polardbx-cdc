/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat.QueryEventReformator;
import com.aliyun.polardbx.binlog.format.utils.SqlModeUtil;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class QueryEventReformatorTest extends BaseTestWithGmsTables {

    @Test
    public void ignoreTruncateTableTest() {
        QueryEventReformator reformator = Mockito.mock(QueryEventReformator.class);
        QueryLogEvent queryLogEvent = Mockito.mock(QueryLogEvent.class);
        Mockito.when(queryLogEvent.getQuery()).thenReturn("truncate table test");
        Mockito.when(queryLogEvent.getDbName()).thenReturn("test_db");
        Mockito.when(reformator.accept(queryLogEvent)).thenCallRealMethod();
        Assert.assertFalse(reformator.accept(queryLogEvent));
    }

    @Test
    public void ignoreTruncateTableExceptionTest() {
        QueryEventReformator reformator = Mockito.mock(QueryEventReformator.class);
        QueryLogEvent queryLogEvent = Mockito.mock(QueryLogEvent.class);
        Mockito.when(queryLogEvent.getQuery()).thenReturn("this is not a ddl st");
        Mockito.when(queryLogEvent.getDbName()).thenReturn("test_db");
        Mockito.when(reformator.accept(queryLogEvent)).thenCallRealMethod();
        setConfig(ConfigKeys.META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_FILTER_IGNORE_PARSE_ERROR, "false");
        Exception ex = null;
        try {
            reformator.accept(queryLogEvent);
        } catch (Exception e) {
            ex = e;
        }
        Assert.assertNotNull(ex);
        setConfig(ConfigKeys.META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_FILTER_IGNORE_PARSE_ERROR, "true");
        Assert.assertTrue(reformator.accept(queryLogEvent));
    }

    @Test
    public void testCloneAndProcessBeforeApplyWithCreateTable() {
        QueryLogEvent queryLogEvent = Mockito.mock(QueryLogEvent.class);
        Mockito.when(queryLogEvent.getQuery()).thenReturn("CREATE TABLE tt (\n"
            + "\tmm real(4,2),\n"
            + "\tm1 double,\n"
            + "\tm2 float,\n"
            + "\t_drds_implicit_id_ bigint AUTO_INCREMENT,\n"
            + "\tPRIMARY KEY (_drds_implicit_id_)\n"
            + ") DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci");
        Mockito.when(queryLogEvent.getSqlMode()).thenReturn(SqlModeUtil.MODE_REAL_AS_FLOAT);
        QueryEventReformator reformator = new QueryEventReformator(null);
        String expect = "CREATE TABLE tt (\n"
            + "\tmm float(4, 2),\n"
            + "\tm1 double,\n"
            + "\tm2 float,\n"
            + "\t_drds_implicit_id_ bigint AUTO_INCREMENT,\n"
            + "\tPRIMARY KEY (_drds_implicit_id_)\n"
            + ") DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci";
        Assert.assertEquals(expect, reformator.processQueryDDL(queryLogEvent));
    }


    @Test
    public void testCloneAndProcessBeforeApplyWithAlterTableAdd() {
        QueryLogEvent queryLogEvent = Mockito.mock(QueryLogEvent.class);
        Mockito.when(queryLogEvent.getQuery()).thenReturn("alter table tt add column m22 real(5,2)");
        Mockito.when(queryLogEvent.getSqlMode()).thenReturn(SqlModeUtil.MODE_REAL_AS_FLOAT);
        QueryEventReformator reformator = new QueryEventReformator(null);
        String expect = "ALTER TABLE tt\n"
            + "\tADD COLUMN m22 float(5, 2)";
        Assert.assertEquals(expect, reformator.processQueryDDL(queryLogEvent));
    }

    @Test
    public void testCloneAndProcessBeforeApplyWithAlterTableModify() {
        QueryLogEvent queryLogEvent = Mockito.mock(QueryLogEvent.class);
        Mockito.when(queryLogEvent.getQuery()).thenReturn("alter table tt modify column md2 real(20,2);");
        Mockito.when(queryLogEvent.getSqlMode()).thenReturn(SqlModeUtil.MODE_REAL_AS_FLOAT);
        QueryEventReformator reformator = new QueryEventReformator(null);
        String expect = "ALTER TABLE tt\n"
            + "\tMODIFY COLUMN md2 float(20, 2);";
        Assert.assertEquals(expect, reformator.processQueryDDL(queryLogEvent));
    }

    @Test
    public void testCloneAndProcessBeforeApplyWithAlterTableChange() {
        QueryLogEvent queryLogEvent = Mockito.mock(QueryLogEvent.class);
        Mockito.when(queryLogEvent.getQuery()).thenReturn("alter table tt change column md1 md11 real(10,2);");
        Mockito.when(queryLogEvent.getSqlMode()).thenReturn(SqlModeUtil.MODE_REAL_AS_FLOAT);
        QueryEventReformator reformator = new QueryEventReformator(null);
        String expect = "ALTER TABLE tt\n"
            + "\tCHANGE COLUMN md1 md11 float(10, 2);";
        Assert.assertEquals(expect, reformator.processQueryDDL(queryLogEvent));
    }
}
