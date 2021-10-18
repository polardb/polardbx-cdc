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

import com.aliyun.polardbx.binlog.domain.po.BinlogMetaSnapshot;
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
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.dao.BinlogMetaSnapshotDynamicSqlSupport.binlogMetaSnapshot;
import static com.aliyun.polardbx.binlog.dao.BinlogMetaSnapshotDynamicSqlSupport.dbName;
import static com.aliyun.polardbx.binlog.dao.BinlogMetaSnapshotDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.BinlogMetaSnapshotDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.BinlogMetaSnapshotDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.BinlogMetaSnapshotDynamicSqlSupport.tableName;
import static com.aliyun.polardbx.binlog.dao.BinlogMetaSnapshotDynamicSqlSupport.tableSchema;
import static com.aliyun.polardbx.binlog.dao.BinlogMetaSnapshotDynamicSqlSupport.tso;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface BinlogMetaSnapshotMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.393+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, tso, dbName, tableName, tableSchema);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.377+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.379+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.379+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<BinlogMetaSnapshot> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.38+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogMetaSnapshot> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.381+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Integer.class, jdbcType = JdbcType.INTEGER, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_schema", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    Optional<BinlogMetaSnapshot> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.386+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Integer.class, jdbcType = JdbcType.INTEGER, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_schema", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    List<BinlogMetaSnapshot> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.386+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.387+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogMetaSnapshot, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.388+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogMetaSnapshot, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.388+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default int deleteByPrimaryKey(Integer id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.389+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default int insert(BinlogMetaSnapshot record) {
        return MyBatis3Utils.insert(this::insert, record, binlogMetaSnapshot, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(tso).toProperty("tso")
                .map(dbName).toProperty("dbName")
                .map(tableName).toProperty("tableName")
                .map(tableSchema).toProperty("tableSchema")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.391+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default int insertMultiple(Collection<BinlogMetaSnapshot> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogMetaSnapshot, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(tso).toProperty("tso")
                .map(dbName).toProperty("dbName")
                .map(tableName).toProperty("tableName")
                .map(tableSchema).toProperty("tableSchema")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.391+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default int insertSelective(BinlogMetaSnapshot record) {
        return MyBatis3Utils.insert(this::insert, record, binlogMetaSnapshot, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(tso).toPropertyWhenPresent("tso", record::getTso)
                .map(dbName).toPropertyWhenPresent("dbName", record::getDbName)
                .map(tableName).toPropertyWhenPresent("tableName", record::getTableName)
                .map(tableSchema).toPropertyWhenPresent("tableSchema", record::getTableSchema)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.394+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default Optional<BinlogMetaSnapshot> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogMetaSnapshot, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.394+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default List<BinlogMetaSnapshot> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogMetaSnapshot, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.395+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default List<BinlogMetaSnapshot> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogMetaSnapshot, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.395+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default Optional<BinlogMetaSnapshot> selectByPrimaryKey(Integer id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.396+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogMetaSnapshot, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.396+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogMetaSnapshot record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(tso).equalTo(record::getTso)
            .set(dbName).equalTo(record::getDbName)
            .set(tableName).equalTo(record::getTableName)
            .set(tableSchema).equalTo(record::getTableSchema);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.397+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogMetaSnapshot record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(dbName).equalToWhenPresent(record::getDbName)
            .set(tableName).equalToWhenPresent(record::getTableName)
            .set(tableSchema).equalToWhenPresent(record::getTableSchema);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.398+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default int updateByPrimaryKey(BinlogMetaSnapshot record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(tso).equalTo(record::getTso)
                .set(dbName).equalTo(record::getDbName)
                .set(tableName).equalTo(record::getTableName)
                .set(tableSchema).equalTo(record::getTableSchema)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.398+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    default int updateByPrimaryKeySelective(BinlogMetaSnapshot record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(dbName).equalToWhenPresent(record::getDbName)
                .set(tableName).equalToWhenPresent(record::getTableName)
                .set(tableSchema).equalToWhenPresent(record::getTableSchema)
                .where(id, isEqualTo(record::getId))
        );
    }
}