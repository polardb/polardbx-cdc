/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.XTableStreamMappingDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.XTableStreamMapping;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Generated;
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

@Mapper
public interface XTableStreamMappingMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.423+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, dbName, tableName, streamSeq, clusterId);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.411+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.412+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.412+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<XTableStreamMapping> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.413+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<XTableStreamMapping> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.414+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="db_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="table_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="stream_seq", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR)
    })
    Optional<XTableStreamMapping> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.415+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="db_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="table_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="stream_seq", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR)
    })
    List<XTableStreamMapping> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.416+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.416+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, XTableStreamMapping, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.417+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, XTableStreamMapping, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.417+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.418+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default int insert(XTableStreamMapping record) {
        return MyBatis3Utils.insert(this::insert, record, XTableStreamMapping, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(dbName).toProperty("dbName")
            .map(tableName).toProperty("tableName")
            .map(streamSeq).toProperty("streamSeq")
            .map(clusterId).toProperty("clusterId")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.42+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default int insertMultiple(Collection<XTableStreamMapping> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, XTableStreamMapping, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(dbName).toProperty("dbName")
            .map(tableName).toProperty("tableName")
            .map(streamSeq).toProperty("streamSeq")
            .map(clusterId).toProperty("clusterId")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.421+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default int insertSelective(XTableStreamMapping record) {
        return MyBatis3Utils.insert(this::insert, record, XTableStreamMapping, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(dbName).toPropertyWhenPresent("dbName", record::getDbName)
            .map(tableName).toPropertyWhenPresent("tableName", record::getTableName)
            .map(streamSeq).toPropertyWhenPresent("streamSeq", record::getStreamSeq)
            .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.424+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default Optional<XTableStreamMapping> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, XTableStreamMapping, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.425+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default List<XTableStreamMapping> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, XTableStreamMapping, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.425+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default List<XTableStreamMapping> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, XTableStreamMapping, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.426+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default Optional<XTableStreamMapping> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.426+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, XTableStreamMapping, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.427+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    static UpdateDSL<UpdateModel> updateAllColumns(XTableStreamMapping record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(dbName).equalTo(record::getDbName)
                .set(tableName).equalTo(record::getTableName)
                .set(streamSeq).equalTo(record::getStreamSeq)
                .set(clusterId).equalTo(record::getClusterId);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.427+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(XTableStreamMapping record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(dbName).equalToWhenPresent(record::getDbName)
                .set(tableName).equalToWhenPresent(record::getTableName)
                .set(streamSeq).equalToWhenPresent(record::getStreamSeq)
                .set(clusterId).equalToWhenPresent(record::getClusterId);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.428+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default int updateByPrimaryKey(XTableStreamMapping record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(dbName).equalTo(record::getDbName)
            .set(tableName).equalTo(record::getTableName)
            .set(streamSeq).equalTo(record::getStreamSeq)
            .set(clusterId).equalTo(record::getClusterId)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.429+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    default int updateByPrimaryKeySelective(XTableStreamMapping record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(dbName).equalToWhenPresent(record::getDbName)
            .set(tableName).equalToWhenPresent(record::getTableName)
            .set(streamSeq).equalToWhenPresent(record::getStreamSeq)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .where(id, isEqualTo(record::getId))
        );
    }
}