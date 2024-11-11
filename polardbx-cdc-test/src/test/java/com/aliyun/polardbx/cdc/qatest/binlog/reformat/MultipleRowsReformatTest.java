/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.reformat;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

/**
 * 触发整形测试
 * created by ziyang.lb
 **/
public class MultipleRowsReformatTest extends RplBaseTestCase {
    private static final String DB_NAME = "cdc_reformat_test";
    private static final String TABLE_NAME = "multi_rows_update";

    @BeforeClass
    public static void bootStrap() throws SQLException {
        prepareTestDatabase(DB_NAME);
    }

    public static void main(String args[]) {
        System.out.println(DDL);
    }

    private static final String DDL =
        "create table if not exists " + TABLE_NAME + "("
            + "id bigint(20) NOT NULL auto_increment,"
            + "`name` varchar(20)  COLLATE utf8mb4_bin DEFAULT NULL, "
            + "a_score_1 int DEFAULT NULL,"
            + "a_score_2 int DEFAULT 1,"
            + "age int DEFAULT 1 ,"
            + " message varchar(200)  COLLATE utf8mb4_bin , "
            + "extra text  COLLATE utf8mb4_bin , "
            + "`update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' ,"
            + "primary key(id)"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_bin";

    private static final String KEY_NAME = "atest";

    private void prepareTable() throws SQLException {
        Connection connection = getPolardbxConnection(DB_NAME);
        try {
            Statement st = connection.createStatement();
            st.executeUpdate(DDL);
            st.close();
        } finally {
            connection.close();
        }
    }

    private void prepare() throws SQLException {
        Random r = new Random();
        Connection connection = getPolardbxConnection(DB_NAME);
        try {
            Statement st = connection.createStatement();
            st.executeUpdate("truncate table multi_rows_update");

            for (int i = 0; i < r.nextInt(20) + 2; i++) {
                String sql = String
                    .format("insert into multi_rows_update(`name`, age, message, extra) values('%s', %s, '%s', '%s')",
                        KEY_NAME, r.nextInt(10) + "", RandomStringUtils.randomAlphabetic(100),
                        RandomStringUtils.randomAlphabetic(100));
                st.executeUpdate(sql);
            }
            st.close();
        } finally {
            connection.close();
        }

    }

    private void reformatUpdate() throws SQLException {
        Connection connection = getPolardbxConnection(DB_NAME);
        try {
            Statement st = connection.createStatement();
            String sql = String.format("update multi_rows_update set extra = '%s' where name = '%s'",
                RandomStringUtils.randomAlphabetic(100), KEY_NAME);

            st.executeUpdate(sql);
        } finally {
            connection.close();
        }
    }

    private void reformatDelete() throws SQLException {
        Connection connection = getPolardbxConnection(DB_NAME);
        try {
            Statement st = connection.createStatement();
            String sql = String.format("delete from multi_rows_update  where name = '%s'", KEY_NAME);
            st.executeUpdate(sql);
        } finally {
            connection.close();
        }
    }

    @Test
    public void testReformat() throws Exception {
        prepareTable();
        prepare();
        waitAndCheck(CheckParameter.builder().dbName(DB_NAME).tbName(TABLE_NAME).build());

        reformatUpdate();
        waitAndCheck(CheckParameter.builder().dbName(DB_NAME).tbName(TABLE_NAME).build());

        reformatDelete();
        waitAndCheck(CheckParameter.builder().dbName(DB_NAME).tbName(TABLE_NAME).build());
    }

}
