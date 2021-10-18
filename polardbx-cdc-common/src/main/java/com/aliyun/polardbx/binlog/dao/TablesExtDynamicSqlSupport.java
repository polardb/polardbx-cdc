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

package com.aliyun.polardbx.binlog.dao;

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

import javax.annotation.Generated;
import java.sql.JDBCType;

public final class TablesExtDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.403+08:00",
        comments = "Source Table: tables_ext")
    public static final TablesExt tablesExt = new TablesExt();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.403+08:00",
        comments = "Source field: tables_ext.id")
    public static final SqlColumn<Long> id = tablesExt.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.403+08:00",
        comments = "Source field: tables_ext.table_catalog")
    public static final SqlColumn<String> tableCatalog = tablesExt.tableCatalog;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.404+08:00",
        comments = "Source field: tables_ext.table_schema")
    public static final SqlColumn<String> tableSchema = tablesExt.tableSchema;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.404+08:00",
        comments = "Source field: tables_ext.table_name")
    public static final SqlColumn<String> tableName = tablesExt.tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.404+08:00",
        comments = "Source field: tables_ext.new_table_name")
    public static final SqlColumn<String> newTableName = tablesExt.newTableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.404+08:00",
        comments = "Source field: tables_ext.table_type")
    public static final SqlColumn<Integer> tableType = tablesExt.tableType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.404+08:00",
        comments = "Source field: tables_ext.db_partition_key")
    public static final SqlColumn<String> dbPartitionKey = tablesExt.dbPartitionKey;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.404+08:00",
        comments = "Source field: tables_ext.db_partition_policy")
    public static final SqlColumn<String> dbPartitionPolicy = tablesExt.dbPartitionPolicy;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.404+08:00",
        comments = "Source field: tables_ext.db_partition_count")
    public static final SqlColumn<Integer> dbPartitionCount = tablesExt.dbPartitionCount;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.405+08:00",
        comments = "Source field: tables_ext.db_name_pattern")
    public static final SqlColumn<String> dbNamePattern = tablesExt.dbNamePattern;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.405+08:00",
        comments = "Source field: tables_ext.db_rule")
    public static final SqlColumn<String> dbRule = tablesExt.dbRule;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.405+08:00",
        comments = "Source field: tables_ext.tb_partition_key")
    public static final SqlColumn<String> tbPartitionKey = tablesExt.tbPartitionKey;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.406+08:00",
        comments = "Source field: tables_ext.tb_partition_policy")
    public static final SqlColumn<String> tbPartitionPolicy = tablesExt.tbPartitionPolicy;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.406+08:00",
        comments = "Source field: tables_ext.tb_partition_count")
    public static final SqlColumn<Integer> tbPartitionCount = tablesExt.tbPartitionCount;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.406+08:00",
        comments = "Source field: tables_ext.tb_name_pattern")
    public static final SqlColumn<String> tbNamePattern = tablesExt.tbNamePattern;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.406+08:00",
        comments = "Source field: tables_ext.tb_rule")
    public static final SqlColumn<String> tbRule = tablesExt.tbRule;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.406+08:00",
        comments = "Source field: tables_ext.full_table_scan")
    public static final SqlColumn<Integer> fullTableScan = tablesExt.fullTableScan;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.406+08:00",
        comments = "Source field: tables_ext.broadcast")
    public static final SqlColumn<Integer> broadcast = tablesExt.broadcast;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.407+08:00",
        comments = "Source field: tables_ext.version")
    public static final SqlColumn<Long> version = tablesExt.version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.407+08:00",
        comments = "Source field: tables_ext.status")
    public static final SqlColumn<Integer> status = tablesExt.status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.407+08:00",
        comments = "Source field: tables_ext.flag")
    public static final SqlColumn<Long> flag = tablesExt.flag;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.407+08:00",
        comments = "Source field: tables_ext.db_meta_map")
    public static final SqlColumn<String> dbMetaMap = tablesExt.dbMetaMap;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.407+08:00",
        comments = "Source field: tables_ext.tb_meta_map")
    public static final SqlColumn<String> tbMetaMap = tablesExt.tbMetaMap;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.407+08:00",
        comments = "Source field: tables_ext.ext_partitions")
    public static final SqlColumn<String> extPartitions = tablesExt.extPartitions;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.403+08:00",
        comments = "Source Table: tables_ext")
    public static final class TablesExt extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<String> tableCatalog = column("table_catalog", JDBCType.VARCHAR);

        public final SqlColumn<String> tableSchema = column("table_schema", JDBCType.VARCHAR);

        public final SqlColumn<String> tableName = column("table_name", JDBCType.VARCHAR);

        public final SqlColumn<String> newTableName = column("new_table_name", JDBCType.VARCHAR);

        public final SqlColumn<Integer> tableType = column("table_type", JDBCType.INTEGER);

        public final SqlColumn<String> dbPartitionKey = column("db_partition_key", JDBCType.VARCHAR);

        public final SqlColumn<String> dbPartitionPolicy = column("db_partition_policy", JDBCType.VARCHAR);

        public final SqlColumn<Integer> dbPartitionCount = column("db_partition_count", JDBCType.INTEGER);

        public final SqlColumn<String> dbNamePattern = column("db_name_pattern", JDBCType.VARCHAR);

        public final SqlColumn<String> dbRule = column("db_rule", JDBCType.VARCHAR);

        public final SqlColumn<String> tbPartitionKey = column("tb_partition_key", JDBCType.VARCHAR);

        public final SqlColumn<String> tbPartitionPolicy = column("tb_partition_policy", JDBCType.VARCHAR);

        public final SqlColumn<Integer> tbPartitionCount = column("tb_partition_count", JDBCType.INTEGER);

        public final SqlColumn<String> tbNamePattern = column("tb_name_pattern", JDBCType.VARCHAR);

        public final SqlColumn<String> tbRule = column("tb_rule", JDBCType.VARCHAR);

        public final SqlColumn<Integer> fullTableScan = column("full_table_scan", JDBCType.INTEGER);

        public final SqlColumn<Integer> broadcast = column("broadcast", JDBCType.INTEGER);

        public final SqlColumn<Long> version = column("version", JDBCType.BIGINT);

        public final SqlColumn<Integer> status = column("status", JDBCType.INTEGER);

        public final SqlColumn<Long> flag = column("flag", JDBCType.BIGINT);

        public final SqlColumn<String> dbMetaMap = column("db_meta_map", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> tbMetaMap = column("tb_meta_map", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> extPartitions = column("ext_partitions", JDBCType.LONGVARCHAR);

        public TablesExt() {
            super("tables_ext");
        }
    }
}