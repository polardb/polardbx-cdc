/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class SQLStatementDbTypeCheckTest extends RplBaseTestCase {


    @Test
    public void dbTypeCheckTest() throws SQLException {

        Pattern pattern = Pattern.compile(PropertiesUtil.getCdcCheckDdlDbTypeBlackList(), Pattern.CASE_INSENSITIVE);

        final String sql = "select * from binlog_lab_event where event_type = "
            + LabEventType.SQL_STATMENT_DB_TYPE_NOT_MYSQL.ordinal();
        int count = 0;
        try (Connection conn = getMetaConnection()) {
            ResultSet rs = JdbcUtil.executeQuery(sql, conn);
            while (rs.next()) {
                String ddl = rs.getString("params");
                if (pattern.matcher(ddl).find()){
                    continue;
                }
                count++;
            }
        }
        Assert.assertEquals(0, count);
    }
}
