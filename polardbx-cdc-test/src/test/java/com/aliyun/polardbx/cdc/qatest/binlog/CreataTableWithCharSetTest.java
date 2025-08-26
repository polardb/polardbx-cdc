/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

public class CreataTableWithCharSetTest extends RplBaseTestCase {
    @Test
    public void testCreateTableWithCharset() throws Exception {
        String sql = "create table if not exists `zm_test_charset` (\n"
            + "        `id` int NOT NULL AUTO_INCREMENT,\n"
            + "        `v` int,\n"
            + "        primary key (`id`)\n"
            + ") engine = 'innodb' default character set = 'utf8mb4' default collate = 'utf8mb4_general_ci'";
        try (Connection c = getPolardbxConnection(PropertiesUtil.polardbXDBName1(usingNewPartDb()))) {
            c.createStatement().execute(sql);
            c.createStatement().execute("insert into zm_test_charset(`v`) values (1)");
        }
        sendTokenAndWait(CheckParameter.builder().build());
        try (Connection c = getCdcSyncDbConnection(PropertiesUtil.polardbXDBName1(usingNewPartDb()))) {
            ResultSet rs = c.createStatement().executeQuery("select * from zm_test_charset where id = 1");
            Assert.assertTrue(rs.next());
            int v = rs.getInt("v");
            Assert.assertEquals(v, 1);
        }
    }
}
