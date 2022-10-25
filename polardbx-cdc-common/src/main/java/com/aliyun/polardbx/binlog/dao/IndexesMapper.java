/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.domain.po.Indexes;
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

import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.cardinality;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.collation;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.columnName;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.comment;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.flag;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.indexColumnType;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.indexComment;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.indexLocation;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.indexName;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.indexSchema;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.indexStatus;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.indexTableName;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.indexType;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.indexes;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.nonUnique;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.nullable;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.packed;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.seqInIndex;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.subPart;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.tableCatalog;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.tableName;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.tableSchema;
import static com.aliyun.polardbx.binlog.dao.IndexesDynamicSqlSupport.version;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface IndexesMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.467+08:00",
        comments = "Source Table: indexes")
    BasicColumn[] selectList = BasicColumn
        .columnList(id, tableCatalog, tableSchema, tableName, nonUnique, indexSchema, indexName, seqInIndex, columnName,
            collation, cardinality, subPart, packed, nullable, indexType, comment, indexComment, indexColumnType,
            indexLocation, indexTableName, indexStatus, version, flag);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source Table: indexes")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source Table: indexes")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source Table: indexes")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<Indexes> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source Table: indexes")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<Indexes> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.466+08:00",
        comments = "Source Table: indexes")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "table_catalog", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_schema", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "non_unique", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "index_schema", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "index_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "seq_in_index", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "column_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "collation", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cardinality", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "sub_part", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "packed", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "nullable", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "index_type", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "comment", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "index_comment", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "index_column_type", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "index_location", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "index_table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "index_status", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "version", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "flag", javaType = Long.class, jdbcType = JdbcType.BIGINT)
    })
    Optional<Indexes> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.467+08:00",
        comments = "Source Table: indexes")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "table_catalog", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_schema", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "non_unique", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "index_schema", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "index_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "seq_in_index", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "column_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "collation", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cardinality", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "sub_part", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "packed", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "nullable", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "index_type", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "comment", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "index_comment", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "index_column_type", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "index_location", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "index_table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "index_status", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "version", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "flag", javaType = Long.class, jdbcType = JdbcType.BIGINT)
    })
    List<Indexes> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.467+08:00",
        comments = "Source Table: indexes")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.467+08:00",
        comments = "Source Table: indexes")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, indexes, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.467+08:00",
        comments = "Source Table: indexes")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, indexes, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.467+08:00",
        comments = "Source Table: indexes")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.467+08:00",
        comments = "Source Table: indexes")
    default int insert(Indexes record) {
        return MyBatis3Utils.insert(this::insert, record, indexes, c ->
            c.map(id).toProperty("id")
                .map(tableCatalog).toProperty("tableCatalog")
                .map(tableSchema).toProperty("tableSchema")
                .map(tableName).toProperty("tableName")
                .map(nonUnique).toProperty("nonUnique")
                .map(indexSchema).toProperty("indexSchema")
                .map(indexName).toProperty("indexName")
                .map(seqInIndex).toProperty("seqInIndex")
                .map(columnName).toProperty("columnName")
                .map(collation).toProperty("collation")
                .map(cardinality).toProperty("cardinality")
                .map(subPart).toProperty("subPart")
                .map(packed).toProperty("packed")
                .map(nullable).toProperty("nullable")
                .map(indexType).toProperty("indexType")
                .map(comment).toProperty("comment")
                .map(indexComment).toProperty("indexComment")
                .map(indexColumnType).toProperty("indexColumnType")
                .map(indexLocation).toProperty("indexLocation")
                .map(indexTableName).toProperty("indexTableName")
                .map(indexStatus).toProperty("indexStatus")
                .map(version).toProperty("version")
                .map(flag).toProperty("flag")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.467+08:00",
        comments = "Source Table: indexes")
    default int insertMultiple(Collection<Indexes> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, indexes, c ->
            c.map(id).toProperty("id")
                .map(tableCatalog).toProperty("tableCatalog")
                .map(tableSchema).toProperty("tableSchema")
                .map(tableName).toProperty("tableName")
                .map(nonUnique).toProperty("nonUnique")
                .map(indexSchema).toProperty("indexSchema")
                .map(indexName).toProperty("indexName")
                .map(seqInIndex).toProperty("seqInIndex")
                .map(columnName).toProperty("columnName")
                .map(collation).toProperty("collation")
                .map(cardinality).toProperty("cardinality")
                .map(subPart).toProperty("subPart")
                .map(packed).toProperty("packed")
                .map(nullable).toProperty("nullable")
                .map(indexType).toProperty("indexType")
                .map(comment).toProperty("comment")
                .map(indexComment).toProperty("indexComment")
                .map(indexColumnType).toProperty("indexColumnType")
                .map(indexLocation).toProperty("indexLocation")
                .map(indexTableName).toProperty("indexTableName")
                .map(indexStatus).toProperty("indexStatus")
                .map(version).toProperty("version")
                .map(flag).toProperty("flag")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.467+08:00",
        comments = "Source Table: indexes")
    default int insertSelective(Indexes record) {
        return MyBatis3Utils.insert(this::insert, record, indexes, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(tableCatalog).toPropertyWhenPresent("tableCatalog", record::getTableCatalog)
                .map(tableSchema).toPropertyWhenPresent("tableSchema", record::getTableSchema)
                .map(tableName).toPropertyWhenPresent("tableName", record::getTableName)
                .map(nonUnique).toPropertyWhenPresent("nonUnique", record::getNonUnique)
                .map(indexSchema).toPropertyWhenPresent("indexSchema", record::getIndexSchema)
                .map(indexName).toPropertyWhenPresent("indexName", record::getIndexName)
                .map(seqInIndex).toPropertyWhenPresent("seqInIndex", record::getSeqInIndex)
                .map(columnName).toPropertyWhenPresent("columnName", record::getColumnName)
                .map(collation).toPropertyWhenPresent("collation", record::getCollation)
                .map(cardinality).toPropertyWhenPresent("cardinality", record::getCardinality)
                .map(subPart).toPropertyWhenPresent("subPart", record::getSubPart)
                .map(packed).toPropertyWhenPresent("packed", record::getPacked)
                .map(nullable).toPropertyWhenPresent("nullable", record::getNullable)
                .map(indexType).toPropertyWhenPresent("indexType", record::getIndexType)
                .map(comment).toPropertyWhenPresent("comment", record::getComment)
                .map(indexComment).toPropertyWhenPresent("indexComment", record::getIndexComment)
                .map(indexColumnType).toPropertyWhenPresent("indexColumnType", record::getIndexColumnType)
                .map(indexLocation).toPropertyWhenPresent("indexLocation", record::getIndexLocation)
                .map(indexTableName).toPropertyWhenPresent("indexTableName", record::getIndexTableName)
                .map(indexStatus).toPropertyWhenPresent("indexStatus", record::getIndexStatus)
                .map(version).toPropertyWhenPresent("version", record::getVersion)
                .map(flag).toPropertyWhenPresent("flag", record::getFlag)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.467+08:00",
        comments = "Source Table: indexes")
    default Optional<Indexes> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, indexes, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.468+08:00",
        comments = "Source Table: indexes")
    default List<Indexes> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, indexes, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.468+08:00",
        comments = "Source Table: indexes")
    default List<Indexes> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, indexes, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.468+08:00",
        comments = "Source Table: indexes")
    default Optional<Indexes> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.468+08:00",
        comments = "Source Table: indexes")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, indexes, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.468+08:00",
        comments = "Source Table: indexes")
    static UpdateDSL<UpdateModel> updateAllColumns(Indexes record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(tableCatalog).equalTo(record::getTableCatalog)
            .set(tableSchema).equalTo(record::getTableSchema)
            .set(tableName).equalTo(record::getTableName)
            .set(nonUnique).equalTo(record::getNonUnique)
            .set(indexSchema).equalTo(record::getIndexSchema)
            .set(indexName).equalTo(record::getIndexName)
            .set(seqInIndex).equalTo(record::getSeqInIndex)
            .set(columnName).equalTo(record::getColumnName)
            .set(collation).equalTo(record::getCollation)
            .set(cardinality).equalTo(record::getCardinality)
            .set(subPart).equalTo(record::getSubPart)
            .set(packed).equalTo(record::getPacked)
            .set(nullable).equalTo(record::getNullable)
            .set(indexType).equalTo(record::getIndexType)
            .set(comment).equalTo(record::getComment)
            .set(indexComment).equalTo(record::getIndexComment)
            .set(indexColumnType).equalTo(record::getIndexColumnType)
            .set(indexLocation).equalTo(record::getIndexLocation)
            .set(indexTableName).equalTo(record::getIndexTableName)
            .set(indexStatus).equalTo(record::getIndexStatus)
            .set(version).equalTo(record::getVersion)
            .set(flag).equalTo(record::getFlag);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.468+08:00",
        comments = "Source Table: indexes")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(Indexes record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(tableCatalog).equalToWhenPresent(record::getTableCatalog)
            .set(tableSchema).equalToWhenPresent(record::getTableSchema)
            .set(tableName).equalToWhenPresent(record::getTableName)
            .set(nonUnique).equalToWhenPresent(record::getNonUnique)
            .set(indexSchema).equalToWhenPresent(record::getIndexSchema)
            .set(indexName).equalToWhenPresent(record::getIndexName)
            .set(seqInIndex).equalToWhenPresent(record::getSeqInIndex)
            .set(columnName).equalToWhenPresent(record::getColumnName)
            .set(collation).equalToWhenPresent(record::getCollation)
            .set(cardinality).equalToWhenPresent(record::getCardinality)
            .set(subPart).equalToWhenPresent(record::getSubPart)
            .set(packed).equalToWhenPresent(record::getPacked)
            .set(nullable).equalToWhenPresent(record::getNullable)
            .set(indexType).equalToWhenPresent(record::getIndexType)
            .set(comment).equalToWhenPresent(record::getComment)
            .set(indexComment).equalToWhenPresent(record::getIndexComment)
            .set(indexColumnType).equalToWhenPresent(record::getIndexColumnType)
            .set(indexLocation).equalToWhenPresent(record::getIndexLocation)
            .set(indexTableName).equalToWhenPresent(record::getIndexTableName)
            .set(indexStatus).equalToWhenPresent(record::getIndexStatus)
            .set(version).equalToWhenPresent(record::getVersion)
            .set(flag).equalToWhenPresent(record::getFlag);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.468+08:00",
        comments = "Source Table: indexes")
    default int updateByPrimaryKey(Indexes record) {
        return update(c ->
            c.set(tableCatalog).equalTo(record::getTableCatalog)
                .set(tableSchema).equalTo(record::getTableSchema)
                .set(tableName).equalTo(record::getTableName)
                .set(nonUnique).equalTo(record::getNonUnique)
                .set(indexSchema).equalTo(record::getIndexSchema)
                .set(indexName).equalTo(record::getIndexName)
                .set(seqInIndex).equalTo(record::getSeqInIndex)
                .set(columnName).equalTo(record::getColumnName)
                .set(collation).equalTo(record::getCollation)
                .set(cardinality).equalTo(record::getCardinality)
                .set(subPart).equalTo(record::getSubPart)
                .set(packed).equalTo(record::getPacked)
                .set(nullable).equalTo(record::getNullable)
                .set(indexType).equalTo(record::getIndexType)
                .set(comment).equalTo(record::getComment)
                .set(indexComment).equalTo(record::getIndexComment)
                .set(indexColumnType).equalTo(record::getIndexColumnType)
                .set(indexLocation).equalTo(record::getIndexLocation)
                .set(indexTableName).equalTo(record::getIndexTableName)
                .set(indexStatus).equalTo(record::getIndexStatus)
                .set(version).equalTo(record::getVersion)
                .set(flag).equalTo(record::getFlag)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.469+08:00",
        comments = "Source Table: indexes")
    default int updateByPrimaryKeySelective(Indexes record) {
        return update(c ->
            c.set(tableCatalog).equalToWhenPresent(record::getTableCatalog)
                .set(tableSchema).equalToWhenPresent(record::getTableSchema)
                .set(tableName).equalToWhenPresent(record::getTableName)
                .set(nonUnique).equalToWhenPresent(record::getNonUnique)
                .set(indexSchema).equalToWhenPresent(record::getIndexSchema)
                .set(indexName).equalToWhenPresent(record::getIndexName)
                .set(seqInIndex).equalToWhenPresent(record::getSeqInIndex)
                .set(columnName).equalToWhenPresent(record::getColumnName)
                .set(collation).equalToWhenPresent(record::getCollation)
                .set(cardinality).equalToWhenPresent(record::getCardinality)
                .set(subPart).equalToWhenPresent(record::getSubPart)
                .set(packed).equalToWhenPresent(record::getPacked)
                .set(nullable).equalToWhenPresent(record::getNullable)
                .set(indexType).equalToWhenPresent(record::getIndexType)
                .set(comment).equalToWhenPresent(record::getComment)
                .set(indexComment).equalToWhenPresent(record::getIndexComment)
                .set(indexColumnType).equalToWhenPresent(record::getIndexColumnType)
                .set(indexLocation).equalToWhenPresent(record::getIndexLocation)
                .set(indexTableName).equalToWhenPresent(record::getIndexTableName)
                .set(indexStatus).equalToWhenPresent(record::getIndexStatus)
                .set(version).equalToWhenPresent(record::getVersion)
                .set(flag).equalToWhenPresent(record::getFlag)
                .where(id, isEqualTo(record::getId))
        );
    }
}