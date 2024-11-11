/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.binlog.util.ConfigPropMap;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.Map;

/**
 * meta db datasource
 *
 * @author yudong
 **/
public class MetaDbDataSourceTest extends BaseTestWithGmsTables {

    @Test
    public void testWithDdlMode() throws SQLException, NoSuchFieldException, IllegalAccessException {
        MockedStatic<SQLUtils> sqlUtilsStatic = Mockito.mockStatic(SQLUtils.class);
        Mockito.when(SQLUtils.isLeaderByDdl(Mockito.any())).thenReturn(true);
        Mockito.when(SQLUtils.isLeaderBySqlQuery(Mockito.any())).thenReturn(false);

        MetaDbDataSource metaDb = new MetaDbDataSource("", false);
        Field field = ConfigPropMap.class.getDeclaredField("CONFIG_MAP");
        field.setAccessible(true);
        Map<String, String> CONFIG_MAP = (Map<String, String>) field.get(null);
        CONFIG_MAP.put(ConfigKeys.BINLOG_META_LEADER_DETECT_BY_DDL_MODE_ENABLE, "true");
        Assert.assertTrue(metaDb.isLeaderAndAvailable());
        CONFIG_MAP.put(ConfigKeys.BINLOG_META_LEADER_DETECT_BY_DDL_MODE_ENABLE, "false");
        Assert.assertFalse(metaDb.isLeaderAndAvailable());

        sqlUtilsStatic.close();
    }
}
