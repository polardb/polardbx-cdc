/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.metadata;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by ziyang.lb
 **/
public class TableTopologyManager {

    private static final String CACHE_KEY = "CACHE_KEY";
    private static final LoadingCache<String, TableTopology> cache = CacheBuilder.newBuilder()
        .build(new CacheLoader<String, TableTopology>() {

            @Override
            public TableTopology load(String key) throws Exception {
                return buildCache();
            }
        });

    /**
     * 通过物理库名获取逻辑库名
     */
    public static String getLogicalDbName(String physicalDbName) {
        String result;
        int count = 0;
        while (true) {
            result = cache.getUnchecked(CACHE_KEY).getPhysicalDatabasesMap().get(physicalDbName);
            if (StringUtils.isBlank(result) && count < 1) {
                reloadCache();
                count++;
            } else {
                break;
            }
        }
        if (StringUtils.isBlank(result)) {
            throw new PolardbxException("logical db name is not found for physical key " + physicalDbName);
        }
        return result;
    }

    /**
     * 通过物理表名获取逻辑表名
     *
     * @param physicalTableName 物理表名（全限定名称，即："逻辑库名.物理表名"的形式）
     */
    public static String getLogicalTableName(String physicalTableName) {
        String result;
        int count = 0;
        while (true) {
            result = cache.getUnchecked(CACHE_KEY).getPhysicalTablesMap().get(physicalTableName);
            if (StringUtils.isBlank(result) && count < 1) {
                reloadCache();
                count++;
            } else {
                break;
            }
        }
        if (StringUtils.isBlank(result)) {
            throw new PolardbxException("logical table name is not found for physical key " + physicalTableName);
        }
        return result;
    }

    public static Set<String> getPhyDbNames(String logicDatabaseName) {
        return cache.getUnchecked(CACHE_KEY).getLogicalDatabasesMap().get(logicDatabaseName);
    }

    public static Set<String> getPhyTableNames(String phyDatabaseName) {
        return cache.getUnchecked(CACHE_KEY).getPhysicalDatabaseTableMap().get(phyDatabaseName);
    }

    protected static TableTopology buildCache() {
        TableTopology topology = new TableTopology();
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        JdbcTemplate polarxJdbcTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");

        // 获取大小写配置
        // 该参数的介绍参见：https://dev.mysql.com/doc/refman/5.7/en/server-system-variables.html#sysvar_lower_case_table_names
        String lowerCaseFlag = polarxJdbcTemplate.queryForObject("select @@lower_case_table_names", String.class);

        // 获取DB配置
        List<DBEntry> dbGroups =
            metaJdbcTemplate.query("select phy_db_name,db_name,group_name from `db_group_info`", (i, j) -> {
                DBEntry dbEntry = new DBEntry();
                dbEntry.setLogicalDbName(translate(i.getString("db_name"), lowerCaseFlag));
                dbEntry.setPhysicalDbName(translate(i.getString("phy_db_name"), lowerCaseFlag));
                dbEntry.setGroupName(translate(i.getString("group_name"), lowerCaseFlag));
                return dbEntry;
            });
        if (!CollectionUtils.isEmpty(dbGroups)) {
            // 构造DB Map
            dbGroups.stream()
                .filter(d -> !("polardbx".equals(d.logicalDbName) || "information_schema".equals(d.logicalDbName)))
                .collect(Collectors.groupingBy(DBEntry::getLogicalDbName))
                .forEach((key, value) -> topology.getLogicalDatabasesMap()
                    .put(key, value.stream().map(DBEntry::getPhysicalDbName).collect(Collectors.toSet())));
            Map<String, String> groupPhyDb = Maps.newHashMapWithExpectedSize(dbGroups.size());
            dbGroups.forEach(i -> {
                topology.getPhysicalDatabasesMap().put(i.physicalDbName, i.logicalDbName);
                groupPhyDb.put(i.groupName, i.physicalDbName);
            });

            // 构造Table Map
            topology.getLogicalDatabasesMap().keySet().forEach(d -> {
                List<String> tables = polarxJdbcTemplate.query("show tables from `" + d + "`",
                    (i, j) -> translate(i.getString(1), lowerCaseFlag));

                if (!CollectionUtils.isEmpty(tables)) {
                    tables.parallelStream().forEach(t -> {
                        String logicalTableFullName = d + "." + t;
                        List<TableEntry> tableDetails = polarxJdbcTemplate.query(
                            "show topology from " + logicalTableFullName, (i, j) -> {
                                TableEntry tableEntry = new TableEntry();
                                tableEntry.setGroupName(translate(i.getString("GROUP_NAME"), lowerCaseFlag));
                                tableEntry.setTableName(translate(i.getString("TABLE_NAME"), lowerCaseFlag));
                                return tableEntry;
                            });
                        List<String> physicalTables = tableDetails.stream().map(TableEntry::getTableName).collect(
                            Collectors.toList());
                        if (!CollectionUtils.isEmpty(physicalTables)) {
                            final Map<String, Set<String>> groupTables = tableDetails.stream().collect(
                                Collectors.groupingBy(TableEntry::getGroupName,
                                    Collectors.mapping(TableEntry::getTableName, Collectors.toSet())));
                            groupTables.forEach((k, v) -> {
                                final String phyDbName = groupPhyDb.get(k);
                                if (topology.getPhysicalDatabaseTableMap().get(phyDbName) == null) {
                                    topology.getPhysicalDatabaseTableMap().put(phyDbName, Sets.newHashSet());
                                }
                                topology.getPhysicalDatabaseTableMap().get(phyDbName).addAll(v);
                            });
                            topology.getLogicalTablesMap().put(logicalTableFullName, new HashSet<>(physicalTables));
                            physicalTables.forEach(p -> {
                                topology.getPhysicalTablesMap().put(d + "." + p, t);
                            });
                        }
                    });
                }
            });
        }

        return topology;
    }

    private static void reloadCache() {
        cache.invalidate(CACHE_KEY);
        cache.getUnchecked(CACHE_KEY);
    }

    private static String translate(String input, String flag) {
        if ("0".equals(flag) || "2".equals(flag)) {
            return input;
        } else {
            return input.toLowerCase();
        }
    }

    static class DBEntry {

        private String logicalDbName;
        private String physicalDbName;
        private String groupName;

        public String getLogicalDbName() {
            return logicalDbName;
        }

        public void setLogicalDbName(String logicalDbName) {
            this.logicalDbName = logicalDbName;
        }

        public String getPhysicalDbName() {
            return physicalDbName;
        }

        public void setPhysicalDbName(String physicalDbName) {
            this.physicalDbName = physicalDbName;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }
    }

    static class TableEntry {

        private String groupName;
        private String tableName;

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }
}
