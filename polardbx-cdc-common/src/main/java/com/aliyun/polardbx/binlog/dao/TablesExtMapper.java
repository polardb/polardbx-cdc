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
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.domain.po.TablesExt;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.dynamic.sql.BasicColumn;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.delete.render.DeleteStatementProvider;
import org.mybatis.dynamic.sql.insert.render.InsertStatementProvider;
import org.mybatis.dynamic.sql.insert.render.MultiRowInsertStatementProvider;
import org.mybatis.dynamic.sql.select.CountDSLCompleter;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.UpdateDSL;
import org.mybatis.dynamic.sql.update.UpdateDSLCompleter;
import org.mybatis.dynamic.sql.update.UpdateModel;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;
import org.mybatis.dynamic.sql.util.SqlProviderAdapter;
import org.mybatis.dynamic.sql.util.mybatis3.MyBatis3Utils;

import javax.annotation.Generated;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.broadcast;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.dbMetaMap;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.dbNamePattern;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.dbPartitionCount;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.dbPartitionKey;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.dbPartitionPolicy;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.dbRule;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.extPartitions;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.flag;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.fullTableScan;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.newTableName;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.status;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tableCatalog;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tableName;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tableSchema;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tableType;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tablesExt;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tbMetaMap;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tbNamePattern;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tbPartitionCount;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tbPartitionKey;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tbPartitionPolicy;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.tbRule;
import static com.aliyun.polardbx.binlog.dao.TablesExtDynamicSqlSupport.version;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface TablesExtMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.409+08:00",
        comments = "Source Table: tables_ext")
    BasicColumn[] selectList = BasicColumn
        .columnList(id, tableCatalog, tableSchema, tableName, newTableName, tableType, dbPartitionKey,
            dbPartitionPolicy, dbPartitionCount, dbNamePattern, dbRule, tbPartitionKey, tbPartitionPolicy,
            tbPartitionCount, tbNamePattern, tbRule, fullTableScan, broadcast, version, status, flag, dbMetaMap,
            tbMetaMap, extPartitions);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.407+08:00",
        comments = "Source Table: tables_ext")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.407+08:00",
        comments = "Source Table: tables_ext")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.407+08:00",
        comments = "Source Table: tables_ext")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<TablesExt> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.408+08:00",
        comments = "Source Table: tables_ext")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<TablesExt> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.408+08:00",
        comments = "Source Table: tables_ext")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "table_catalog", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_schema", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "new_table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_type", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "db_partition_key", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_partition_policy", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_partition_count", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "db_name_pattern", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_rule", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "tb_partition_key", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "tb_partition_policy", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "tb_partition_count", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "tb_name_pattern", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "tb_rule", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "full_table_scan", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "broadcast", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "version", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "flag", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "db_meta_map", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR),
        @Arg(column = "tb_meta_map", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR),
        @Arg(column = "ext_partitions", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    Optional<TablesExt> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.408+08:00",
        comments = "Source Table: tables_ext")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "table_catalog", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_schema", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "new_table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_type", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "db_partition_key", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_partition_policy", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_partition_count", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "db_name_pattern", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_rule", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "tb_partition_key", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "tb_partition_policy", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "tb_partition_count", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "tb_name_pattern", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "tb_rule", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "full_table_scan", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "broadcast", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "version", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "flag", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "db_meta_map", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR),
        @Arg(column = "tb_meta_map", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR),
        @Arg(column = "ext_partitions", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    List<TablesExt> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.408+08:00",
        comments = "Source Table: tables_ext")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.408+08:00",
        comments = "Source Table: tables_ext")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, tablesExt, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.408+08:00",
        comments = "Source Table: tables_ext")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, tablesExt, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.408+08:00",
        comments = "Source Table: tables_ext")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.409+08:00",
        comments = "Source Table: tables_ext")
    default int insert(TablesExt record) {
        return MyBatis3Utils.insert(this::insert, record, tablesExt, c ->
            c.map(id).toProperty("id")
                .map(tableCatalog).toProperty("tableCatalog")
                .map(tableSchema).toProperty("tableSchema")
                .map(tableName).toProperty("tableName")
                .map(newTableName).toProperty("newTableName")
                .map(tableType).toProperty("tableType")
                .map(dbPartitionKey).toProperty("dbPartitionKey")
                .map(dbPartitionPolicy).toProperty("dbPartitionPolicy")
                .map(dbPartitionCount).toProperty("dbPartitionCount")
                .map(dbNamePattern).toProperty("dbNamePattern")
                .map(dbRule).toProperty("dbRule")
                .map(tbPartitionKey).toProperty("tbPartitionKey")
                .map(tbPartitionPolicy).toProperty("tbPartitionPolicy")
                .map(tbPartitionCount).toProperty("tbPartitionCount")
                .map(tbNamePattern).toProperty("tbNamePattern")
                .map(tbRule).toProperty("tbRule")
                .map(fullTableScan).toProperty("fullTableScan")
                .map(broadcast).toProperty("broadcast")
                .map(version).toProperty("version")
                .map(status).toProperty("status")
                .map(flag).toProperty("flag")
                .map(dbMetaMap).toProperty("dbMetaMap")
                .map(tbMetaMap).toProperty("tbMetaMap")
                .map(extPartitions).toProperty("extPartitions")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.409+08:00",
        comments = "Source Table: tables_ext")
    default int insertMultiple(Collection<TablesExt> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, tablesExt, c ->
            c.map(id).toProperty("id")
                .map(tableCatalog).toProperty("tableCatalog")
                .map(tableSchema).toProperty("tableSchema")
                .map(tableName).toProperty("tableName")
                .map(newTableName).toProperty("newTableName")
                .map(tableType).toProperty("tableType")
                .map(dbPartitionKey).toProperty("dbPartitionKey")
                .map(dbPartitionPolicy).toProperty("dbPartitionPolicy")
                .map(dbPartitionCount).toProperty("dbPartitionCount")
                .map(dbNamePattern).toProperty("dbNamePattern")
                .map(dbRule).toProperty("dbRule")
                .map(tbPartitionKey).toProperty("tbPartitionKey")
                .map(tbPartitionPolicy).toProperty("tbPartitionPolicy")
                .map(tbPartitionCount).toProperty("tbPartitionCount")
                .map(tbNamePattern).toProperty("tbNamePattern")
                .map(tbRule).toProperty("tbRule")
                .map(fullTableScan).toProperty("fullTableScan")
                .map(broadcast).toProperty("broadcast")
                .map(version).toProperty("version")
                .map(status).toProperty("status")
                .map(flag).toProperty("flag")
                .map(dbMetaMap).toProperty("dbMetaMap")
                .map(tbMetaMap).toProperty("tbMetaMap")
                .map(extPartitions).toProperty("extPartitions")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.409+08:00",
        comments = "Source Table: tables_ext")
    default int insertSelective(TablesExt record) {
        return MyBatis3Utils.insert(this::insert, record, tablesExt, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(tableCatalog).toPropertyWhenPresent("tableCatalog", record::getTableCatalog)
                .map(tableSchema).toPropertyWhenPresent("tableSchema", record::getTableSchema)
                .map(tableName).toPropertyWhenPresent("tableName", record::getTableName)
                .map(newTableName).toPropertyWhenPresent("newTableName", record::getNewTableName)
                .map(tableType).toPropertyWhenPresent("tableType", record::getTableType)
                .map(dbPartitionKey).toPropertyWhenPresent("dbPartitionKey", record::getDbPartitionKey)
                .map(dbPartitionPolicy).toPropertyWhenPresent("dbPartitionPolicy", record::getDbPartitionPolicy)
                .map(dbPartitionCount).toPropertyWhenPresent("dbPartitionCount", record::getDbPartitionCount)
                .map(dbNamePattern).toPropertyWhenPresent("dbNamePattern", record::getDbNamePattern)
                .map(dbRule).toPropertyWhenPresent("dbRule", record::getDbRule)
                .map(tbPartitionKey).toPropertyWhenPresent("tbPartitionKey", record::getTbPartitionKey)
                .map(tbPartitionPolicy).toPropertyWhenPresent("tbPartitionPolicy", record::getTbPartitionPolicy)
                .map(tbPartitionCount).toPropertyWhenPresent("tbPartitionCount", record::getTbPartitionCount)
                .map(tbNamePattern).toPropertyWhenPresent("tbNamePattern", record::getTbNamePattern)
                .map(tbRule).toPropertyWhenPresent("tbRule", record::getTbRule)
                .map(fullTableScan).toPropertyWhenPresent("fullTableScan", record::getFullTableScan)
                .map(broadcast).toPropertyWhenPresent("broadcast", record::getBroadcast)
                .map(version).toPropertyWhenPresent("version", record::getVersion)
                .map(status).toPropertyWhenPresent("status", record::getStatus)
                .map(flag).toPropertyWhenPresent("flag", record::getFlag)
                .map(dbMetaMap).toPropertyWhenPresent("dbMetaMap", record::getDbMetaMap)
                .map(tbMetaMap).toPropertyWhenPresent("tbMetaMap", record::getTbMetaMap)
                .map(extPartitions).toPropertyWhenPresent("extPartitions", record::getExtPartitions)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.409+08:00",
        comments = "Source Table: tables_ext")
    default Optional<TablesExt> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, tablesExt, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.409+08:00",
        comments = "Source Table: tables_ext")
    default List<TablesExt> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, tablesExt, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.41+08:00",
        comments = "Source Table: tables_ext")
    default List<TablesExt> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, tablesExt, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.41+08:00",
        comments = "Source Table: tables_ext")
    default Optional<TablesExt> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.41+08:00",
        comments = "Source Table: tables_ext")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, tablesExt, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.41+08:00",
        comments = "Source Table: tables_ext")
    static UpdateDSL<UpdateModel> updateAllColumns(TablesExt record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(tableCatalog).equalTo(record::getTableCatalog)
            .set(tableSchema).equalTo(record::getTableSchema)
            .set(tableName).equalTo(record::getTableName)
            .set(newTableName).equalTo(record::getNewTableName)
            .set(tableType).equalTo(record::getTableType)
            .set(dbPartitionKey).equalTo(record::getDbPartitionKey)
            .set(dbPartitionPolicy).equalTo(record::getDbPartitionPolicy)
            .set(dbPartitionCount).equalTo(record::getDbPartitionCount)
            .set(dbNamePattern).equalTo(record::getDbNamePattern)
            .set(dbRule).equalTo(record::getDbRule)
            .set(tbPartitionKey).equalTo(record::getTbPartitionKey)
            .set(tbPartitionPolicy).equalTo(record::getTbPartitionPolicy)
            .set(tbPartitionCount).equalTo(record::getTbPartitionCount)
            .set(tbNamePattern).equalTo(record::getTbNamePattern)
            .set(tbRule).equalTo(record::getTbRule)
            .set(fullTableScan).equalTo(record::getFullTableScan)
            .set(broadcast).equalTo(record::getBroadcast)
            .set(version).equalTo(record::getVersion)
            .set(status).equalTo(record::getStatus)
            .set(flag).equalTo(record::getFlag)
            .set(dbMetaMap).equalTo(record::getDbMetaMap)
            .set(tbMetaMap).equalTo(record::getTbMetaMap)
            .set(extPartitions).equalTo(record::getExtPartitions);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.41+08:00",
        comments = "Source Table: tables_ext")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(TablesExt record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(tableCatalog).equalToWhenPresent(record::getTableCatalog)
            .set(tableSchema).equalToWhenPresent(record::getTableSchema)
            .set(tableName).equalToWhenPresent(record::getTableName)
            .set(newTableName).equalToWhenPresent(record::getNewTableName)
            .set(tableType).equalToWhenPresent(record::getTableType)
            .set(dbPartitionKey).equalToWhenPresent(record::getDbPartitionKey)
            .set(dbPartitionPolicy).equalToWhenPresent(record::getDbPartitionPolicy)
            .set(dbPartitionCount).equalToWhenPresent(record::getDbPartitionCount)
            .set(dbNamePattern).equalToWhenPresent(record::getDbNamePattern)
            .set(dbRule).equalToWhenPresent(record::getDbRule)
            .set(tbPartitionKey).equalToWhenPresent(record::getTbPartitionKey)
            .set(tbPartitionPolicy).equalToWhenPresent(record::getTbPartitionPolicy)
            .set(tbPartitionCount).equalToWhenPresent(record::getTbPartitionCount)
            .set(tbNamePattern).equalToWhenPresent(record::getTbNamePattern)
            .set(tbRule).equalToWhenPresent(record::getTbRule)
            .set(fullTableScan).equalToWhenPresent(record::getFullTableScan)
            .set(broadcast).equalToWhenPresent(record::getBroadcast)
            .set(version).equalToWhenPresent(record::getVersion)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(flag).equalToWhenPresent(record::getFlag)
            .set(dbMetaMap).equalToWhenPresent(record::getDbMetaMap)
            .set(tbMetaMap).equalToWhenPresent(record::getTbMetaMap)
            .set(extPartitions).equalToWhenPresent(record::getExtPartitions);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.411+08:00",
        comments = "Source Table: tables_ext")
    default int updateByPrimaryKey(TablesExt record) {
        return update(c ->
            c.set(tableCatalog).equalTo(record::getTableCatalog)
                .set(tableSchema).equalTo(record::getTableSchema)
                .set(tableName).equalTo(record::getTableName)
                .set(newTableName).equalTo(record::getNewTableName)
                .set(tableType).equalTo(record::getTableType)
                .set(dbPartitionKey).equalTo(record::getDbPartitionKey)
                .set(dbPartitionPolicy).equalTo(record::getDbPartitionPolicy)
                .set(dbPartitionCount).equalTo(record::getDbPartitionCount)
                .set(dbNamePattern).equalTo(record::getDbNamePattern)
                .set(dbRule).equalTo(record::getDbRule)
                .set(tbPartitionKey).equalTo(record::getTbPartitionKey)
                .set(tbPartitionPolicy).equalTo(record::getTbPartitionPolicy)
                .set(tbPartitionCount).equalTo(record::getTbPartitionCount)
                .set(tbNamePattern).equalTo(record::getTbNamePattern)
                .set(tbRule).equalTo(record::getTbRule)
                .set(fullTableScan).equalTo(record::getFullTableScan)
                .set(broadcast).equalTo(record::getBroadcast)
                .set(version).equalTo(record::getVersion)
                .set(status).equalTo(record::getStatus)
                .set(flag).equalTo(record::getFlag)
                .set(dbMetaMap).equalTo(record::getDbMetaMap)
                .set(tbMetaMap).equalTo(record::getTbMetaMap)
                .set(extPartitions).equalTo(record::getExtPartitions)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.411+08:00",
        comments = "Source Table: tables_ext")
    default int updateByPrimaryKeySelective(TablesExt record) {
        return update(c ->
            c.set(tableCatalog).equalToWhenPresent(record::getTableCatalog)
                .set(tableSchema).equalToWhenPresent(record::getTableSchema)
                .set(tableName).equalToWhenPresent(record::getTableName)
                .set(newTableName).equalToWhenPresent(record::getNewTableName)
                .set(tableType).equalToWhenPresent(record::getTableType)
                .set(dbPartitionKey).equalToWhenPresent(record::getDbPartitionKey)
                .set(dbPartitionPolicy).equalToWhenPresent(record::getDbPartitionPolicy)
                .set(dbPartitionCount).equalToWhenPresent(record::getDbPartitionCount)
                .set(dbNamePattern).equalToWhenPresent(record::getDbNamePattern)
                .set(dbRule).equalToWhenPresent(record::getDbRule)
                .set(tbPartitionKey).equalToWhenPresent(record::getTbPartitionKey)
                .set(tbPartitionPolicy).equalToWhenPresent(record::getTbPartitionPolicy)
                .set(tbPartitionCount).equalToWhenPresent(record::getTbPartitionCount)
                .set(tbNamePattern).equalToWhenPresent(record::getTbNamePattern)
                .set(tbRule).equalToWhenPresent(record::getTbRule)
                .set(fullTableScan).equalToWhenPresent(record::getFullTableScan)
                .set(broadcast).equalToWhenPresent(record::getBroadcast)
                .set(version).equalToWhenPresent(record::getVersion)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(flag).equalToWhenPresent(record::getFlag)
                .set(dbMetaMap).equalToWhenPresent(record::getDbMetaMap)
                .set(tbMetaMap).equalToWhenPresent(record::getTbMetaMap)
                .set(extPartitions).equalToWhenPresent(record::getExtPartitions)
                .where(id, isEqualTo(record::getId))
        );
    }
}