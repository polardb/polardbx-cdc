/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta.mapping;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_VIRTUAL_TABLE_MAPPING_RULE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static org.junit.Assert.assertEquals;

@Slf4j
public class TableNameMapperTest extends BaseTest {

    private TableNameMapper mapper;

    @Before
    public void setUp() {
        mockConfig(META_VIRTUAL_TABLE_MAPPING_RULE,
            "b03\\.b03_user_balance_log_\\d+|b03.b03_user_baLAnce_log_xxx,"
                + "b03\\.b03_reward_group_log_\\d+|b03.b03_REWARD_group_log_xxx,"
                + "b03\\.b03_game_order_\\d+|b03.b03_game_order_xxx,"
                + "b03\\.b03_game_over_\\d+|b03_game_over_xxx,"
                + "b03\\.b03_error_test_\\d+|");
        mapper = new TableNameMapper();
        System.out.println(
            "meta_virtual_table_mapping_rule is config to " + getString(META_VIRTUAL_TABLE_MAPPING_RULE));
    }

    @Test
    public void testMatchedUserBalanceLog() {
        assertEquals(Pair.of("b03", "b03_user_balance_log_xxx"),
            mapper.mapToVirtualTableName(Pair.of("b03", "b03_user_balance_log_1752811200")));
    }

    @Test
    public void testMatchedRewardGroupLog() {
        assertEquals(Pair.of("b03", "b03_reward_group_log_xxx"),
            mapper.mapToVirtualTableName(Pair.of("b03", "b03_reward_group_log_1752811201")));
    }

    @Test
    public void testMatchedGameOrder() {
        assertEquals(Pair.of("b03", "b03_game_order_xxx"),
            mapper.mapToVirtualTableName(Pair.of("b03", "b03_game_order_1752811201")));
    }

    @Test
    public void testMatchedGameOver() {
        assertEquals(Pair.of("b03", "b03_game_over_xxx"),
            mapper.mapToVirtualTableName(Pair.of("b03", "b03_game_over_1752811201")));
    }

    @Test
    public void testErrorConfig() {
        try {
            mapper.mapToVirtualTableName(Pair.of("b03", "b03_error_test_1752811201"));
            Assert.fail("expect error, but not");
        } catch (PolardbxException e) {
            Assert.assertEquals("table name can`t be empty for virtual table : (b03,)", e.getMessage());
        }
    }

    @Test
    public void testUnmatchedTableName() {
        assertEquals(Pair.of("unknown_db", "unknown_table"),
            mapper.mapToVirtualTableName(Pair.of("unknown_db", "unknown_table")));
        assertEquals(Pair.of("b04", "b03_user_balance_log_1752811200"),
            mapper.mapToVirtualTableName(Pair.of("b04", "b03_user_balance_log_1752811200")));
        assertEquals(Pair.of("b03", "b03_user_balance_log_123avxz"),
            mapper.mapToVirtualTableName(Pair.of("b03", "b03_user_balance_log_123avxz")));
        assertEquals(Pair.of("b03", "b03_user_balance_log_avxz123"),
            mapper.mapToVirtualTableName(Pair.of("b03", "b03_user_balance_log_avxz123")));
        assertEquals(Pair.of("b03", "b03_user_balance_log_"),
            mapper.mapToVirtualTableName(Pair.of("b03", "b03_user_balance_log_")));
    }

    @Test
    public void testUnmatchedTableName2() {
        mockConfig(META_VIRTUAL_TABLE_MAPPING_RULE, "");
        mapper = new TableNameMapper();

        try {
            testMatchedGameOrder();
            throw new PolardbxException("should not match for testMatchedGameOrder");
        } catch (AssertionError ignore) {
        }

        try {
            testMatchedRewardGroupLog();
            throw new PolardbxException("should not match for testMatchedRewardGroupLog");
        } catch (AssertionError ignore) {
        }

        try {
            testMatchedUserBalanceLog();
            throw new PolardbxException("should not match for testMatchedUserBalanceLog");
        } catch (AssertionError ignore) {
        }
    }
}

