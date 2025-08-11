/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.replica;

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.SneakyThrows;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

public class TaskIgnoreArchiveTest extends RplBaseTestCase {
    private static final String QUERY_LAB_EVENT = "SELECT * FROM binlog_lab_event where event_type = %s";
    private static final String DB_NAME = "zm_task_filter_db";
    private static final String ARCHIVE_TABLE_NAME = "zm_archive_ignore_tb";
    private static final String CREATE_DATABASE_SQL = "create database if not exists %s mode = 'auto'";
    private static final String INSERT_SQL = "insert into %s.%s values (%d, '%s', '%s')";
    private static final String SELECT_TABLE_SQL = "select * from %s.%s order by id";
    private static final String CREATE_ARCHIVE_TABLE_SQL =
        "CREATE TABLE if not exists `%s`.`%s` ( \n"
            + "  `id` int(32) NOT NULL AUTO_INCREMENT,\n"
            + "  `value` longtext,\n"
            + "  `date_field` datetime DEFAULT CURRENT_TIMESTAMP,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB DEFAULT \n"
            + "  CHARSET = utf8mb4 \n"
            + "  TTL = TTL_DEFINITION ( \n"
            + "    TTL_EXPR = `date_field` EXPIRE AFTER 1 DAY TIMEZONE '+08:00' \n"
            + "    TTL_JOB = CRON '0 0 2 */1 * ? *' TIMEZONE '+08:00'"
            + "    TTL_ENABLE = 'ON'\n"
            + "    TTL_CLEANUP = 'ON'\n"
            + "  ) \n"
            + "PARTITION BY KEY(`id`)\n"
            + "PARTITIONS 8;";
    private static final String DROP_TABLE_SQL = "DROP TABLE IF EXISTS %s.%s";
    private static final String CLEAN_UP_SQL = "ALTER TABLE %s.%s CLEANUP EXPIRED DATA";

    @Test
    @SneakyThrows
    public void testTaskIgnoreArchive() {
        boolean archiveFilterEnabled = false;
        String[] values = new String[] {
            RandomStringUtils.randomAlphabetic(70000),
            RandomStringUtils.randomAlphabetic(70000)
        };
        try (Connection metaConnection = getMetaConnection()) {
            ResultSet rs =
                JdbcUtil.executeQuery(
                    String.format(QUERY_LAB_EVENT, LabEventType.TASK_FILTER_ARCHIVE_ENABLED.ordinal()),
                    metaConnection);
            if (rs.next()) {
                archiveFilterEnabled = true;
            }
        }
        if (archiveFilterEnabled) {
            // 创建TTL表，插入数据，进行归档清理
            try (Connection c = getPolardbxConnection()) {
                c.createStatement().execute(String.format(CREATE_DATABASE_SQL, DB_NAME));
                c.createStatement().execute(String.format(CREATE_ARCHIVE_TABLE_SQL, DB_NAME, ARCHIVE_TABLE_NAME));
                c.createStatement()
                    .execute(
                        String.format(INSERT_SQL, DB_NAME, ARCHIVE_TABLE_NAME, 1, values[0], "2020-01-28 13:56:19"));
                c.createStatement().execute(String.format(CLEAN_UP_SQL, DB_NAME, ARCHIVE_TABLE_NAME));
                c.createStatement()
                    .execute(
                        String.format(INSERT_SQL, DB_NAME, ARCHIVE_TABLE_NAME, 2, values[1], "2022-01-28 13:56:19"));
            }
            // 等待下游同步
            sendTokenAndWait(CheckParameter.builder().build());
            try (Connection c = getCdcSyncDbConnection()) {
                ResultSet rs = c.createStatement().executeQuery(String.format(SELECT_TABLE_SQL, DB_NAME,
                    ARCHIVE_TABLE_NAME));
                int recordCount = 0;
                while (rs.next()) {
                    String valueTarget = rs.getString("value");
                    Assert.assertEquals(valueTarget, values[recordCount]);
                    recordCount++;
                }
                // 由于开启archive过滤，所以删除数据的动作不会同步到目标端
                Assert.assertEquals(2, recordCount);
            }
            try (Connection c = getPolardbxConnection()) {
                c.createStatement().execute(String.format(DROP_TABLE_SQL, DB_NAME, ARCHIVE_TABLE_NAME));
            }
        }
    }

}
