/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DdlResult;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DruidDdlParser;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class DruidDdlParserTest {
    @Test
    public void testForeignKey() {
        String sql1 = "ALTER TABLE `res_catalog`\n"
            + "  COMMENT = ''";
        String sql2 = "ALTER TABLE res_catalog";
        String sql3 = "ALTER TABLE `device` ADD CONSTRAINT FOREIGN KEY (`b`) REFERENCES `user2` (`a`)";
        DdlResult result1 = DruidDdlParser.parse(sql1, "abc");
        DdlResult result2 = DruidDdlParser.parse(sql2, "abc");
        DdlResult result3 = DruidDdlParser.parse(sql3, "abc");
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3.getType());
    }

    @Test
    public void testDropTableIfExists() {
        // see https://aone.alibaba-inc.com/v2/project/860366/bug/55137024
        String sql = "DROP TABLE IF EXISTS d1.d2.rename_target_auto";
        DdlResult ddlResult = DruidDdlParser.parse(sql, "abc");
        Assert.assertEquals("d1", ddlResult.getSchemaName());
        Assert.assertEquals("rename_target_auto", ddlResult.getTableName());
    }

    @Test
    public void testCall() {
        String sql = "CALL __polardbx_inner_procedure__.trigger_sync_point_trx()";
        DdlResult ddlResult = DruidDdlParser.parse(sql, "abc");
    }
}
