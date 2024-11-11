/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.RplFullValidDiffDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplFullValidDiff;
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
public interface RplFullValidDiffMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76729+08:00", comments="Source Table: rpl_full_valid_diff")
    BasicColumn[] selectList = BasicColumn.columnList(id, taskId, srcLogicalDb, srcLogicalTable, dstLogicalDb, dstLogicalTable, srcKeyName, srcKeyVal, dstKeyName, dstKeyVal, errorType, status, createTime, updateTime);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766929+08:00", comments="Source Table: rpl_full_valid_diff")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766951+08:00", comments="Source Table: rpl_full_valid_diff")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76697+08:00", comments="Source Table: rpl_full_valid_diff")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<RplFullValidDiff> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766989+08:00", comments="Source Table: rpl_full_valid_diff")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<RplFullValidDiff> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767011+08:00", comments="Source Table: rpl_full_valid_diff")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="task_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="src_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_key_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_key_val", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_key_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_key_val", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="error_type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP)
    })
    Optional<RplFullValidDiff> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767063+08:00", comments="Source Table: rpl_full_valid_diff")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="task_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="src_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_key_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_key_val", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_key_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_key_val", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="error_type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP)
    })
    List<RplFullValidDiff> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767108+08:00", comments="Source Table: rpl_full_valid_diff")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767125+08:00", comments="Source Table: rpl_full_valid_diff")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplFullValidDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767142+08:00", comments="Source Table: rpl_full_valid_diff")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplFullValidDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767158+08:00", comments="Source Table: rpl_full_valid_diff")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767174+08:00", comments="Source Table: rpl_full_valid_diff")
    default int insert(RplFullValidDiff record) {
        return MyBatis3Utils.insert(this::insert, record, rplFullValidDiff, c ->
            c.map(id).toProperty("id")
            .map(taskId).toProperty("taskId")
            .map(srcLogicalDb).toProperty("srcLogicalDb")
            .map(srcLogicalTable).toProperty("srcLogicalTable")
            .map(dstLogicalDb).toProperty("dstLogicalDb")
            .map(dstLogicalTable).toProperty("dstLogicalTable")
            .map(srcKeyName).toProperty("srcKeyName")
            .map(srcKeyVal).toProperty("srcKeyVal")
            .map(dstKeyName).toProperty("dstKeyName")
            .map(dstKeyVal).toProperty("dstKeyVal")
            .map(errorType).toProperty("errorType")
            .map(status).toProperty("status")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767203+08:00", comments="Source Table: rpl_full_valid_diff")
    default int insertMultiple(Collection<RplFullValidDiff> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, rplFullValidDiff, c ->
            c.map(id).toProperty("id")
            .map(taskId).toProperty("taskId")
            .map(srcLogicalDb).toProperty("srcLogicalDb")
            .map(srcLogicalTable).toProperty("srcLogicalTable")
            .map(dstLogicalDb).toProperty("dstLogicalDb")
            .map(dstLogicalTable).toProperty("dstLogicalTable")
            .map(srcKeyName).toProperty("srcKeyName")
            .map(srcKeyVal).toProperty("srcKeyVal")
            .map(dstKeyName).toProperty("dstKeyName")
            .map(dstKeyVal).toProperty("dstKeyVal")
            .map(errorType).toProperty("errorType")
            .map(status).toProperty("status")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767231+08:00", comments="Source Table: rpl_full_valid_diff")
    default int insertSelective(RplFullValidDiff record) {
        return MyBatis3Utils.insert(this::insert, record, rplFullValidDiff, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(taskId).toPropertyWhenPresent("taskId", record::getTaskId)
            .map(srcLogicalDb).toPropertyWhenPresent("srcLogicalDb", record::getSrcLogicalDb)
            .map(srcLogicalTable).toPropertyWhenPresent("srcLogicalTable", record::getSrcLogicalTable)
            .map(dstLogicalDb).toPropertyWhenPresent("dstLogicalDb", record::getDstLogicalDb)
            .map(dstLogicalTable).toPropertyWhenPresent("dstLogicalTable", record::getDstLogicalTable)
            .map(srcKeyName).toPropertyWhenPresent("srcKeyName", record::getSrcKeyName)
            .map(srcKeyVal).toPropertyWhenPresent("srcKeyVal", record::getSrcKeyVal)
            .map(dstKeyName).toPropertyWhenPresent("dstKeyName", record::getDstKeyName)
            .map(dstKeyVal).toPropertyWhenPresent("dstKeyVal", record::getDstKeyVal)
            .map(errorType).toPropertyWhenPresent("errorType", record::getErrorType)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
            .map(updateTime).toPropertyWhenPresent("updateTime", record::getUpdateTime)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767311+08:00", comments="Source Table: rpl_full_valid_diff")
    default Optional<RplFullValidDiff> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplFullValidDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767332+08:00", comments="Source Table: rpl_full_valid_diff")
    default List<RplFullValidDiff> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplFullValidDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767351+08:00", comments="Source Table: rpl_full_valid_diff")
    default List<RplFullValidDiff> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplFullValidDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767371+08:00", comments="Source Table: rpl_full_valid_diff")
    default Optional<RplFullValidDiff> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767391+08:00", comments="Source Table: rpl_full_valid_diff")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplFullValidDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767407+08:00", comments="Source Table: rpl_full_valid_diff")
    static UpdateDSL<UpdateModel> updateAllColumns(RplFullValidDiff record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(taskId).equalTo(record::getTaskId)
                .set(srcLogicalDb).equalTo(record::getSrcLogicalDb)
                .set(srcLogicalTable).equalTo(record::getSrcLogicalTable)
                .set(dstLogicalDb).equalTo(record::getDstLogicalDb)
                .set(dstLogicalTable).equalTo(record::getDstLogicalTable)
                .set(srcKeyName).equalTo(record::getSrcKeyName)
                .set(srcKeyVal).equalTo(record::getSrcKeyVal)
                .set(dstKeyName).equalTo(record::getDstKeyName)
                .set(dstKeyVal).equalTo(record::getDstKeyVal)
                .set(errorType).equalTo(record::getErrorType)
                .set(status).equalTo(record::getStatus)
                .set(createTime).equalTo(record::getCreateTime)
                .set(updateTime).equalTo(record::getUpdateTime);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76745+08:00", comments="Source Table: rpl_full_valid_diff")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplFullValidDiff record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(taskId).equalToWhenPresent(record::getTaskId)
                .set(srcLogicalDb).equalToWhenPresent(record::getSrcLogicalDb)
                .set(srcLogicalTable).equalToWhenPresent(record::getSrcLogicalTable)
                .set(dstLogicalDb).equalToWhenPresent(record::getDstLogicalDb)
                .set(dstLogicalTable).equalToWhenPresent(record::getDstLogicalTable)
                .set(srcKeyName).equalToWhenPresent(record::getSrcKeyName)
                .set(srcKeyVal).equalToWhenPresent(record::getSrcKeyVal)
                .set(dstKeyName).equalToWhenPresent(record::getDstKeyName)
                .set(dstKeyVal).equalToWhenPresent(record::getDstKeyVal)
                .set(errorType).equalToWhenPresent(record::getErrorType)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(createTime).equalToWhenPresent(record::getCreateTime)
                .set(updateTime).equalToWhenPresent(record::getUpdateTime);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767494+08:00", comments="Source Table: rpl_full_valid_diff")
    default int updateByPrimaryKey(RplFullValidDiff record) {
        return update(c ->
            c.set(taskId).equalTo(record::getTaskId)
            .set(srcLogicalDb).equalTo(record::getSrcLogicalDb)
            .set(srcLogicalTable).equalTo(record::getSrcLogicalTable)
            .set(dstLogicalDb).equalTo(record::getDstLogicalDb)
            .set(dstLogicalTable).equalTo(record::getDstLogicalTable)
            .set(srcKeyName).equalTo(record::getSrcKeyName)
            .set(srcKeyVal).equalTo(record::getSrcKeyVal)
            .set(dstKeyName).equalTo(record::getDstKeyName)
            .set(dstKeyVal).equalTo(record::getDstKeyVal)
            .set(errorType).equalTo(record::getErrorType)
            .set(status).equalTo(record::getStatus)
            .set(createTime).equalTo(record::getCreateTime)
            .set(updateTime).equalTo(record::getUpdateTime)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.767537+08:00", comments="Source Table: rpl_full_valid_diff")
    default int updateByPrimaryKeySelective(RplFullValidDiff record) {
        return update(c ->
            c.set(taskId).equalToWhenPresent(record::getTaskId)
            .set(srcLogicalDb).equalToWhenPresent(record::getSrcLogicalDb)
            .set(srcLogicalTable).equalToWhenPresent(record::getSrcLogicalTable)
            .set(dstLogicalDb).equalToWhenPresent(record::getDstLogicalDb)
            .set(dstLogicalTable).equalToWhenPresent(record::getDstLogicalTable)
            .set(srcKeyName).equalToWhenPresent(record::getSrcKeyName)
            .set(srcKeyVal).equalToWhenPresent(record::getSrcKeyVal)
            .set(dstKeyName).equalToWhenPresent(record::getDstKeyName)
            .set(dstKeyVal).equalToWhenPresent(record::getDstKeyVal)
            .set(errorType).equalToWhenPresent(record::getErrorType)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(createTime).equalToWhenPresent(record::getCreateTime)
            .set(updateTime).equalToWhenPresent(record::getUpdateTime)
            .where(id, isEqualTo(record::getId))
        );
    }
}