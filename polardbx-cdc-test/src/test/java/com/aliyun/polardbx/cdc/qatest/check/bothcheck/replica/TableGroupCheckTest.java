/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.util.TableGroupUtils;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Slf4j
public class TableGroupCheckTest extends RplBaseTestCase {

    @Test
    public void testShowCreateTableWithImplicitTg() throws SQLException {

        sendTokenAndWait(CheckParameter.builder().build());

        Set<String> filterTables = querySkipCutOverTables();
        Function<String, Boolean> tablesFilter = s -> !filterTables.contains(s);

        TableGroupUtils.showDatabases(getDruidConnection(0))
            .stream()
            .filter(d -> TableGroupUtils.isAutoModeDb(d, getDruidConnection(0)))
            .forEach(d -> {
                try {
                    List<String> tables = getTableList(d, 0);
                    TableGroupUtils.TableGroupItem tableGroupItem =
                        TableGroupUtils.getTableGroupConfigBySchema(d, getDruidConnection(0), tablesFilter, null);

                    Set<String> diffSet = new HashSet<>();
                    for (String table : tables) {
                        if (filterTables.contains(d + "." + table)) {
                            continue;
                        }

                        String createSql = JdbcUtil.executeQueryAndGetStringResult(
                            "/!+TDDL:cmd_extra(SHOW_IMPLICIT_TABLE_GROUP=true)*/show create table "
                                + "`" + escape(d) + "`.`" + escape(table) + "`", getDruidConnection(0), 2);
                        checkImplicitTgOnce(diffSet, createSql, tableGroupItem);

                        createSql = JdbcUtil.executeQueryAndGetStringResult(
                            "/!+TDDL:cmd_extra(SHOW_IMPLICIT_TABLE_GROUP=true)*/show full create table "
                                + "`" + escape(d) + "`.`" + escape(table) + "`", getDruidConnection(0), 2);
                        checkImplicitTgOnce(diffSet, createSql, tableGroupItem);
                    }

                    Assert.assertEquals(new HashSet<>(), diffSet);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });

    }

    @Test
    public void testCompareTableGroup() throws SQLException {

        sendTokenAndWait(CheckParameter.builder().build());

        Set<String> filterDbs = new HashSet<>();
        Set<String> filterTables = new HashSet<>();

        // 如下这些表在同步链路中被过滤了
        filterDbs.add("partition_hint_test");
        filterTables.add("drds_polarx2_part_qatest_app.select_with_no_rule");
        filterTables.add("drds_polarx1_part_qatest_app.select_with_no_rule");

        // 跳过cut over的表
        filterTables.addAll(querySkipCutOverTables());

        final Function<String, Boolean> databaseFilter = s -> !filterDbs.contains(s);
        final Function<String, Boolean> tableFilter = s -> !filterTables.contains(s);
        final Function<String, Boolean> tableGroupFilter =
            s -> !StringUtils.startsWith(s, "oss_") && !StringUtils.startsWith(s, "columnar_");

        TableGroupUtils.TableGroupConfig sourceTgs = TableGroupUtils.getAllTableGroupConfig(
            getDruidConnection(0), databaseFilter, tableFilter, tableGroupFilter);
        TableGroupUtils.TableGroupConfig targetTgs = TableGroupUtils.getAllTableGroupConfig(
            getDruidConnection(1), databaseFilter, tableFilter, tableGroupFilter);

        try {
            Assert.assertEquals(sourceTgs, targetTgs);
        } catch (Throwable t) {
            log.error("source table groups : " + JSONObject.toJSONString(sourceTgs, true));
            log.error("target table groups : " + JSONObject.toJSONString(targetTgs, true));
            log.error("diff between source and target groups : " + sourceTgs.diff(targetTgs));
            throw t;
        }
    }

    private Set<String> querySkipCutOverTables() throws SQLException {
        // 跳过cutover的表，会存在中间状态的表属于某个表组，这些中间状态的表，通过show create table是感知不到的
        Set<String> filterTables = new HashSet<>();
        try (Connection connection = getDruidConnection(0)) {
            try (Statement stmt = connection.createStatement()) {
                try (ResultSet rs = stmt.executeQuery(
                    "select schema_name,object_name from metadb.ddl_engine_archive where ddl_stmt like '%REPARTITION_SKIP_CUTOVER=true%'")) {
                    while (rs.next()) {
                        String schema = StringUtils.lowerCase(rs.getString("schema_name"));
                        String table = StringUtils.lowerCase(rs.getString("object_name"));
                        filterTables.add(schema + "." + table);
                    }
                }
            }
        }
        return filterTables;
    }

    private void checkImplicitTgOnce(Set<String> diffSet, String createSql,
                                     TableGroupUtils.TableGroupItem tableGroupItem) {
        Pair<String, Map<String, String>> pair = TableGroupUtils.parseImplicitTableGroups(createSql);
        Map<String, String> expect = tableGroupItem.getAllImplicitTableGroupsByTable().get(pair.getKey());
        expect = (expect == null) ? new HashMap<>() : expect;

        try {
            Assert.assertEquals(expect, pair.getValue());
        } catch (AssertionError e) {
            diffSet.add(String.format(
                "compare implicit table group error, schema name : %s, table name : %s, expect : %s , actual : %s",
                tableGroupItem.getSchemaName(), pair.getKey(), expect, pair.getValue()));
        }
    }
}
