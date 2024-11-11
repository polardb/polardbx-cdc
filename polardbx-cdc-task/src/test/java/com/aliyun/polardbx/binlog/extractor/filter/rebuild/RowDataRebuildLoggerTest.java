/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 *
 */
public class RowDataRebuildLoggerTest extends BaseTestWithGmsTables {

    @Test
    public void testEnum() {
        setConfig(ConfigKeys.TASK_EXTRACT_REBUILD_DATA_LOG, "true");
        Field field =
            MakeFieldFactory.makeField("enum('x-small','small','medium','large','x-large')", "x-large", "utf8", false,
                false);
        RowDataRebuildLogger logger = new RowDataRebuildLogger();
        Serializable s =
            logger.doDecode(field.getMysqlType().getType(), field.doGetTableMeta(), field.encode(), "utf8", false);
        Assert.assertEquals(5, s);
    }

    @Test
    public void testDecimal() {
        setConfig(ConfigKeys.TASK_EXTRACT_REBUILD_DATA_LOG, "true");
        float b = 23.1415f;
        Field field =
            MakeFieldFactory.makeField("decimal(10,3)", b + "", "utf8", false,
                false);
        RowDataRebuildLogger logger = new RowDataRebuildLogger();
        Serializable s =
            logger.doDecode(field.getMysqlType().getType(), field.doGetTableMeta(), field.encode(), "utf8", false);
        Assert.assertEquals(new BigDecimal("23.141"), s);
    }

    @Test
    public void testVarchar() {
        setConfig(ConfigKeys.TASK_EXTRACT_REBUILD_DATA_LOG, "true");
        String str = "abcdefgh";
        Field field =
            MakeFieldFactory.makeField("varchar(5)", str, "utf8", false,
                false);
        RowDataRebuildLogger logger = new RowDataRebuildLogger();
        Serializable s =
            logger.doDecode(field.getMysqlType().getType(), field.doGetTableMeta(), field.encode(), "utf8", false);
        Assert.assertEquals(str, s);
    }
}
