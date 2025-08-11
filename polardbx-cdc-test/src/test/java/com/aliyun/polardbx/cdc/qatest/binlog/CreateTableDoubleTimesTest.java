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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * @author zm
 * 该测试的主要目的是测试第二次create table时会不会自动将collate补全成第一次的值（期望不会）
 */
@Slf4j
public class CreateTableDoubleTimesTest extends RplBaseTestCase {
    private static final String CREATE_TABLE_SQL1 = "create table `zm_charset` (\n"
        + "        `table_name` varchar(45) not null,\n"
        + "        `table_version` varchar(45) not null default '',\n"
        + "        `data_version` varchar(45) not null default '',\n"
        + "        `last_update_time` bigint,\n"
        + "        primary key (`table_name`)\n"
        + ") engine = 'innodb' default character set = 'utf8mb4' default collate = 'utf8mb4_general_ci'";

    private static final String CREATE_TABLE_SQL2 = "create table if not exists `zm_charset` (\n"
        + "        `table_name` varchar(45) not null,\n"
        + "        `table_version` varchar(45) not null default '',\n"
        + "        `data_version` varchar(45) not null default '',\n"
        + "        `last_update_time` bigint,\n"
        + "        primary key (`table_name`)\n"
        + ") engine = 'innodb' default character set = 'utf8'";

    private static final String SHOW_CREATE_TABLE = "show create table `zm_charset`";

    @Test
    @SneakyThrows
    public void testCreateTableDoubleTimes() {
        String dbName = PropertiesUtil.polardbXDBName1(usingNewPartDb());
        try (Connection c = getPolardbxConnection(dbName)) {
            c.createStatement().execute(CREATE_TABLE_SQL1);
            c.createStatement().execute(CREATE_TABLE_SQL2);
        }
        sendTokenAndWait(CheckParameter.builder().build());
        try (Connection c = getCdcSyncDbConnection(dbName)) {
            ResultSet rs = c.createStatement().executeQuery(SHOW_CREATE_TABLE);
            Assert.assertTrue(rs.next());
            String createTable = rs.getString(2);
            log.info("create table info:{}", createTable);
            Assert.assertTrue(createTable.contains("utf8mb4"));
        }
    }
}
