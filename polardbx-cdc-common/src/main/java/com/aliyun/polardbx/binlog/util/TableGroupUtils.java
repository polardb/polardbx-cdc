/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.util;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlUnique;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlTableIndex;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static com.alibaba.polardbx.druid.sql.SQLUtils.normalize;
import static com.alibaba.polardbx.druid.sql.SQLUtils.normalizeNoTrim;
import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-12-19 18:00
 **/
@Slf4j
public class TableGroupUtils {

    public static TableGroupConfig getAllTableGroupConfig(Connection connection,
                                                          Function<String, Boolean> databaseFilter,
                                                          Function<String, Boolean> tableFilter,
                                                          Function<String, Boolean> tableGroupFilter)
        throws SQLException {
        TableGroupConfig tableGroupConfig = new TableGroupConfig();
        List<String> databases = showDatabases(connection);

        databases.stream()
            .filter(d -> isAutoModeDb(d, connection) && (databaseFilter == null || databaseFilter.apply(d)))
            .forEach(d -> tableGroupConfig.getAllTableGroups()
                .put(d.toLowerCase(),
                    getTableGroupConfigBySchema(d.toLowerCase(), connection, tableFilter, tableGroupFilter)));
        return tableGroupConfig;
    }

    @SneakyThrows
    public static TableGroupItem getTableGroupConfigBySchema(String dbName, Connection connection,
                                                             Function<String, Boolean> tableFilter,
                                                             Function<String, Boolean> tableGroupFilter) {
        TableGroupItem tableGroupItem = new TableGroupItem(dbName);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("use `" + escape(dbName) + "`");
            try (ResultSet resultSet = stmt.executeQuery("show full tablegroup")) {
                while (resultSet.next()) {
                    String tgName = resultSet.getString("TABLE_GROUP_NAME");
                    String tables = resultSet.getString("TABLES");
                    boolean isManual = resultSet.getInt("IS_MANUAL_CREATE") == 1;

                    if (tableGroupFilter != null && !tableGroupFilter.apply(tgName)) {
                        log.warn("table group is skipped " + tgName);
                        continue;
                    }

                    Set<String> tableSet = Sets.newTreeSet(Lists.newArrayList(
                        StringUtils.split(tables.toLowerCase(), ",")));
                    if (tableFilter != null) {
                        tableSet.removeIf(t -> {
                            String key = t;
                            if (StringUtils.contains(t, ".")) {
                                key = StringUtils.split(t, ".")[0];
                            }
                            return !tableFilter.apply(dbName + "." + key);
                        });
                    }

                    tableGroupItem.getAllTableGroups().put(tgName, tableSet);
                    if (isManual) {
                        tableGroupItem.getAllManualTableGroups().put(tgName, tableSet);
                    } else {
                        tableGroupItem.getAllImplicitTableGroups().put(tgName, tableSet);
                    }

                    for (String tableName : tableSet) {
                        String key = tableName;
                        if (StringUtils.contains(tableName, ".")) {
                            key = StringUtils.split(tableName, ".")[0];
                        }

                        Map<String, String> map =
                            tableGroupItem.getAllTableGroupsByTable().computeIfAbsent(key, k -> new HashMap<>());
                        map.put(tableName, tgName);
                        if (isManual) {
                            Map<String, String> mapManual = tableGroupItem.getAllManualTableGroupsByTable()
                                .computeIfAbsent(key, k -> new HashMap<>());
                            mapManual.put(tableName, tgName);
                        } else {
                            Map<String, String> mapImplicit = tableGroupItem.getAllImplicitTableGroupsByTable()
                                .computeIfAbsent(key, k -> new HashMap<>());
                            mapImplicit.put(tableName, tgName);
                        }
                    }
                }
            }
        }

        return tableGroupItem;
    }

    public static List<String> showDatabases(Connection conn) throws SQLException {
        List<String> databases = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("show databases")) {
                while (rs.next()) {
                    String database = rs.getString(1);
                    if (!StringUtils.equalsAnyIgnoreCase(database, "polardbx", "information_schema", "__cdc__")) {
                        databases.add(database.toLowerCase());
                    }
                }
            }
        }
        return databases;
    }

    @SneakyThrows
    public static boolean isAutoModeDb(String dbName, Connection connection) {
        String showCreateSql = "show full create database `" + escape(dbName) + "`";
        String createSql = null;
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet resultSet = stmt.executeQuery(showCreateSql)) {
                if (resultSet.next()) {
                    createSql = resultSet.getString(2);
                }
            }
        }
        return checkAutoMode(createSql);
    }

    private static boolean checkAutoMode(String createSql) {
        SQLCreateDatabaseStatement statement = SQLUtils.parseSQLStatement(createSql);
        SQLCharExpr sqlCharExpr = (SQLCharExpr) statement.getPartitionMode();
        if (sqlCharExpr != null) {
            String mode = normalize(sqlCharExpr.getText());
            return org.apache.commons.lang.StringUtils.equalsIgnoreCase("auto", mode);
        }
        return false;
    }

    public static Pair<String, Map<String, String>> parseImplicitTableGroups(String createSql) {
        Map<String, String> result = new HashMap<>();

        MySqlCreateTableStatement createTableStatement = SQLUtils.parseSQLStatement(createSql);
        String tableName = normalizeNoTrim(createTableStatement.getTableName()).toLowerCase();

        // parse implicit tableGroup from main table
        if (createTableStatement.isWithImplicitTablegroup()) {
            result.put(tableName, normalize(createTableStatement.getTableGroup().getSimpleName()).toLowerCase());
        }

        // parse implicit tableGroup from index
        for (SQLTableElement element : createTableStatement.getTableElementList()) {
            if (element instanceof MySqlTableIndex) {
                MySqlTableIndex tableIndex = (MySqlTableIndex) element;
                if (tableIndex.isWithImplicitTablegroup()) {
                    String key = normalize(tableIndex.getName().getSimpleName()).toLowerCase();
                    String value = normalize(tableIndex.getTableGroup().getSimpleName()).toLowerCase();
                    result.put(tableName + "." + key, value);
                }
            } else if (element instanceof MySqlKey) {
                if (!(element instanceof MySqlPrimaryKey)) {
                    if (element instanceof MySqlUnique) {
                        MySqlUnique mySqlUnique = (MySqlUnique) element;
                        if (mySqlUnique.isWithImplicitTablegroup()) {
                            String key = normalize(mySqlUnique.getIndexDefinition()
                                .getName().getSimpleName()).toLowerCase();
                            String value = normalize(mySqlUnique.getTableGroup()
                                .getSimpleName()).toLowerCase();
                            result.put(tableName + "." + key, value);
                        }
                    } else {
                        MySqlKey mySqlKey = (MySqlKey) element;
                        if (mySqlKey.getIndexDefinition().isWithImplicitTablegroup()) {
                            String key = normalize(mySqlKey.getIndexDefinition().getName().
                                getSimpleName()).toLowerCase();
                            String value = normalize(mySqlKey.getIndexDefinition().getTableGroup()
                                .getSimpleName()).toLowerCase();
                            result.put(tableName + "." + key, value);
                        }
                    }
                }
            }
        }

        return Pair.of(tableName, result);
    }

    @Data
    public static class TableGroupConfig {
        Map<String, TableGroupItem> allTableGroups = new HashMap<>();

        public String diff(TableGroupConfig input) {
            MapDifference<String, TableGroupItem> rootDiff =
                Maps.difference(this.allTableGroups, input.getAllTableGroups());

            Map<String, Object> diffMap = new HashMap<>();
            diffMap.put("onlyOnLeft", rootDiff.entriesOnlyOnLeft());
            diffMap.put("onlyOnRight", rootDiff.entriesOnlyOnRight());

            Map<String, Object> diffDetail = new HashMap<>();
            rootDiff.entriesDiffering().forEach((k, v) -> {
                Map<String, Map<?, ?>> detail = v.leftValue().diff(v.rightValue());
                diffDetail.put(k, detail);
            });
            diffMap.put("differing", diffDetail);
            return JSONObject.toJSONString(diffMap, true);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TableGroupConfig that = (TableGroupConfig) o;
            return Objects.equals(allTableGroups, that.allTableGroups);
        }

        @Override
        public int hashCode() {
            return Objects.hash(allTableGroups);
        }

        @Override
        public String toString() {
            return "TableGroupConfig{" +
                "allTableGroups=" + allTableGroups +
                '}';
        }
    }

    @Data
    public static class TableGroupItem {
        String schemaName;

        public TableGroupItem(String schemaName) {
            this.schemaName = schemaName;
        }

        Map<String, Set<String>> allTableGroups = new HashMap<>();
        Map<String, Set<String>> allImplicitTableGroups = new HashMap<>();
        Map<String, Set<String>> allManualTableGroups = new HashMap<>();
        Map<String, Map<String, String>> allTableGroupsByTable = new HashMap<>();
        Map<String, Map<String, String>> allImplicitTableGroupsByTable = new HashMap<>();
        Map<String, Map<String, String>> allManualTableGroupsByTable = new HashMap<>();

        public Map<String, Map<?, ?>> diff(TableGroupItem input) {
            Map<String, Map<?, ?>> result = new HashMap<>();

            MapDifference<String, Set<String>> allTableGroupsDiff =
                Maps.difference(this.allTableGroups, input.allTableGroups);
            result.put("allTableGroupsDiff", toPlainMap(allTableGroupsDiff));

            MapDifference<String, Set<String>> allImplicitTableGroupsDiff =
                Maps.difference(this.allImplicitTableGroups, input.allImplicitTableGroups);
            result.put("allImplicitTableGroupsDiff", toPlainMap(allImplicitTableGroupsDiff));

            MapDifference<String, Set<String>> allManualTableGroupsDiff =
                Maps.difference(this.allManualTableGroups, input.allManualTableGroups);
            result.put("allManualTableGroupsDiff", toPlainMap(allManualTableGroupsDiff));

            MapDifference<String, Map<String, String>> allTableGroupsByTableDiff =
                Maps.difference(this.allTableGroupsByTable, input.allTableGroupsByTable);
            result.put("allTableGroupsByTableDiff", toPlainMap(allTableGroupsByTableDiff));

            MapDifference<String, Map<String, String>> allImplicitTableGroupsByTableDiff =
                Maps.difference(this.allImplicitTableGroupsByTable, input.allImplicitTableGroupsByTable);
            result.put("allImplicitTableGroupsByTableDiff", toPlainMap(allImplicitTableGroupsByTableDiff));

            MapDifference<String, Map<String, String>> allManualTableGroupsByTableDiff =
                Maps.difference(this.allManualTableGroupsByTable, input.allManualTableGroupsByTable);
            result.put("allManualTableGroupsByTableDiff", toPlainMap(allManualTableGroupsByTableDiff));

            return result;
        }

        private Map<String, Object> toPlainMap(MapDifference<?, ?> difference) {
            Map<String, Object> result = new HashMap<>();
            result.put("onlyOnLeft", difference.entriesOnlyOnLeft());
            result.put("onlyOnRight", difference.entriesOnlyOnRight());
            result.put("differing", difference.toString());
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TableGroupItem that = (TableGroupItem) o;
            return Objects.equals(schemaName, that.schemaName) && Objects.equals(allTableGroups,
                that.allTableGroups) && Objects.equals(allImplicitTableGroups, that.allImplicitTableGroups)
                && Objects.equals(allManualTableGroups, that.allManualTableGroups) && Objects.equals(
                allTableGroupsByTable, that.allTableGroupsByTable) && Objects.equals(allImplicitTableGroupsByTable,
                that.allImplicitTableGroupsByTable) && Objects.equals(allManualTableGroupsByTable,
                that.allManualTableGroupsByTable);
        }

        @Override
        public int hashCode() {
            return Objects.hash(schemaName, allTableGroups, allImplicitTableGroups, allManualTableGroups,
                allTableGroupsByTable,
                allImplicitTableGroupsByTable, allManualTableGroupsByTable);
        }

        @Override
        public String toString() {
            return "TableGroupItem{" +
                "schemaName='" + schemaName + '\'' +
                ", allTableGroups=" + allTableGroups +
                ", allImplicitTableGroups=" + allImplicitTableGroups +
                ", allManualTableGroups=" + allManualTableGroups +
                ", allTableGroupsByTable=" + allTableGroupsByTable +
                ", allImplicitTableGroupsByTable=" + allImplicitTableGroupsByTable +
                ", allManualTableGroupsByTable=" + allManualTableGroupsByTable +
                '}';
        }
    }
}
