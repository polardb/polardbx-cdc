/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.com.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.format.utils.SqlModeUtil;
import org.junit.Assert;
import org.junit.Test;

public class SqlModeUtilTest {

    @Test
    public void getStrictModeTest1() {
        int sqlMode = 4194304;
        String sqlModeString = SqlModeUtil.convertSqlMode(sqlMode);
        Assert.assertEquals("STRICT_ALL_TABLES",
            sqlModeString);
    }

    @Test
    public void getStrictModeTest2() {
        int sqlMode = 1075838976;
        String sqlModeString = SqlModeUtil.convertSqlMode(sqlMode);
        Assert.assertEquals("NO_ENGINE_SUBSTITUTION,STRICT_TRANS_TABLES",
            sqlModeString);
    }
}
