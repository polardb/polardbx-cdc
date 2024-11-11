/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * created by ziyang.lb
 **/
public class SpecialDmlTest extends RplBaseTestCase {
    private static final String DB_NAME = "cdc_special_dml";
    private static final String CREATE_T_PK_NORMAL = "create table t_pk_normal(\n"
        + " `id` bigint auto_increment,\n"
        + " `name` varchar(100),\n"
        + " `gmt_created` datetime,\n"
        + "  primary key(id)\n"
        + ") dbpartition by hash(`name`) tbpartition by hash(`name`) tbpartitions 8";

    private static final String CREATE_T_PK_SHARDING = "create table t_pk_sharding(\n"
        + " `id` bigint auto_increment,\n"
        + " `name` varchar(100),\n"
        + " `gmt_created` datetime,\n"
        + "  primary key(id)\n"
        + ") dbpartition by hash(`id`) tbpartition by hash(`id`) tbpartitions 8";

    private static final String CREATE_T_SHARDING_KEY = "create table t_sharding_key(\n"
        + " `id` bigint auto_increment,\n"
        + " `name` varchar(100),\n"
        + " `gmt_created` datetime,\n"
        + "  primary key(id)\n"
        + ") dbpartition by hash(`name`) tbpartition by hash(`name`) tbpartitions 8";

    private static final String CREATE_T_UK_EXCHANGE_WITH_DELETE = "create table t_uk_exchange_with_delete(\n"
        + " id bigint auto_increment,\n"
        + " name varchar(50) not null,\n"
        + " gmt_created datetime not null,\n"
        + " primary key(id),\n"
        + " unique key uk_n(name)) dbpartition by hash(`id`) tbpartition by hash(`id`) tbpartitions 8";

    private static final String CREATE_T_UK_EXCHANGE_WITH_SWAP = "create table t_uk_exchange_with_swap(\n"
        + " id bigint auto_increment,\n"
        + " name varchar(50) not null,\n"
        + " gmt_created datetime not null,\n"
        + " primary key(id),\n"
        + " unique key uk_n(name)) dbpartition by hash(`id`) tbpartition by hash(`id`) tbpartitions 8";

    @BeforeClass
    public static void bootStrap() throws SQLException {
        prepareTestDatabase(DB_NAME);
    }

    @Test
    public void testUpdatePkNormal() throws SQLException {
        updatePk(CREATE_T_PK_NORMAL, "t_pk_normal");
    }

    @Test
    public void testUpdatePkSharding() throws SQLException {
        updatePk(CREATE_T_PK_SHARDING, "t_pk_sharding");
    }

    @Test
    public void testUpdateShardingKey() {
        JdbcUtil.executeUpdate(polardbxConnection, CREATE_T_SHARDING_KEY);
        int count = 1000;
        String tableName = "t_sharding_key";

        for (int i = 1; i <= count; i++) {
            String sql = String.format("insert into %s(id,name,gmt_created)values(%s,'%s',now())",
                tableName, i, UUID.randomUUID().toString());
            JdbcUtil.executeSuccess(polardbxConnection, sql);
        }
        for (int i = 1; i <= count; i++) {
            String sql = String.format("update %s set name = '%s' where id = %s",
                tableName, UUID.randomUUID().toString(), i);
            JdbcUtil.executeSuccess(polardbxConnection, sql);
        }
    }

    @Test
    public void testUKExchangeWithDelete() {
        JdbcUtil.executeUpdate(polardbxConnection, CREATE_T_UK_EXCHANGE_WITH_DELETE);
        int count = 1000;
        String tableName = "t_uk_exchange_with_delete";

        insertWithUK(tableName, "a_", count);
        insertWithUK(tableName, "b_", count);
        deleteWithUK(tableName, "a_", count);
        deleteWithUK(tableName, "b_", count);
        insertWithUK(tableName, "a_", count);
        insertWithUK(tableName, "b_", count);
    }

    @Test
    public void testUkExchangeWithSwap() {
        JdbcUtil.executeUpdate(polardbxConnection, CREATE_T_UK_EXCHANGE_WITH_SWAP);
        int count = 1000;
        String tableName = "t_uk_exchange_with_swap";

        insertWithUK(tableName, "a_", count);
        insertWithUK(tableName, "b_", count);
        updateWithUK(tableName, "a_", "c_", count);
        updateWithUK(tableName, "b_", "a_", count);
        updateWithUK(tableName, "a_", "b_", count);
    }

    private void updatePk(String createSql, String tableName) throws SQLException {
        JdbcUtil.executeUpdate(polardbxConnection, createSql);
        int count = 1000;
        for (int i = 1; i <= count; i++) {
            String sql = String.format("insert into %s(id,name,gmt_created)values(%s,'%s',now())",
                tableName, i, UUID.randomUUID().toString());
            JdbcUtil.executeSuccess(polardbxConnection, sql);
        }
        for (int i = 1; i <= count; i++) {
            String sql = String.format("update %s set id = id+10000 where id = %s", tableName, i);
            JdbcUtil.executeSuccess(polardbxConnection, sql);
        }
        for (int i = 1; i <= count; i++) {
            String sql = String.format("select id from %s where id = %s", tableName, i);
            ResultSet resultSet = JdbcUtil.executeQuery(sql, polardbxConnection);
            Assert.isTrue(!resultSet.next());

            sql = String.format("select id from %s where id = %s", tableName, i + 10000);
            resultSet = JdbcUtil.executeQuery(sql, polardbxConnection);
            Assert.isTrue(resultSet.next());
        }
    }

    private void insertWithUK(String tableName, String ukPrefix, int count) {
        for (int i = 1; i <= count; i++) {
            String sql = String.format("insert into %s(name,gmt_created)values('%s',now())", tableName, ukPrefix + i);
            JdbcUtil.executeSuccess(polardbxConnection, sql);
        }
    }

    private void deleteWithUK(String tableName, String ukPrefix, int count) {
        for (int i = 1; i <= count; i++) {
            String sql = String.format("delete from %s where name = '%s'", tableName, ukPrefix + i);
            JdbcUtil.executeSuccess(polardbxConnection, sql);
        }
    }

    private void updateWithUK(String tableName, String ukPrefixFrom, String ukPrefixTo, int count) {
        for (int i = 1; i <= count; i++) {
            String sql = String.format("delete from %s where name = '%s'", tableName, ukPrefixFrom + i);
            JdbcUtil.executeSuccess(polardbxConnection, sql);
        }
    }
}
