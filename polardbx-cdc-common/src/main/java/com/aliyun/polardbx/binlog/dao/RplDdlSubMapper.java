/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.RplDdlSubDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplDdlSub;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Generated;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.dynamic.sql.BasicColumn;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.delete.render.DeleteStatementProvider;
import org.mybatis.dynamic.sql.insert.render.InsertStatementProvider;
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
public interface RplDdlSubMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, fsmId, ddlTso, taskId, serviceId, state, schemaName,
            parallelSeq);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.295+08:00",
        comments = "Source Table: rpl_ddl_sub")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.295+08:00",
        comments = "Source Table: rpl_ddl_sub")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.295+08:00",
        comments = "Source Table: rpl_ddl_sub")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "record.id", before = false,
        resultType = Long.class)
    int insert(InsertStatementProvider<RplDdlSub> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.295+08:00",
        comments = "Source Table: rpl_ddl_sub")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "fsm_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "ddl_tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "task_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "service_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "state", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "schema_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "parallel_seq", javaType = Integer.class, jdbcType = JdbcType.INTEGER)
    })
    Optional<RplDdlSub> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.295+08:00",
        comments = "Source Table: rpl_ddl_sub")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "fsm_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "ddl_tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "task_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "service_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "state", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "schema_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "parallel_seq", javaType = Integer.class, jdbcType = JdbcType.INTEGER)
    })
    List<RplDdlSub> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.295+08:00",
        comments = "Source Table: rpl_ddl_sub")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.295+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplDdlSub, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.295+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplDdlSub, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default int insert(RplDdlSub record) {
        return MyBatis3Utils.insert(this::insert, record, rplDdlSub, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(fsmId).toProperty("fsmId")
                .map(ddlTso).toProperty("ddlTso")
                .map(taskId).toProperty("taskId")
                .map(serviceId).toProperty("serviceId")
                .map(state).toProperty("state")
                .map(schemaName).toProperty("schemaName")
                .map(parallelSeq).toProperty("parallelSeq")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default int insertSelective(RplDdlSub record) {
        return MyBatis3Utils.insert(this::insert, record, rplDdlSub, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(fsmId).toPropertyWhenPresent("fsmId", record::getFsmId)
                .map(ddlTso).toPropertyWhenPresent("ddlTso", record::getDdlTso)
                .map(taskId).toPropertyWhenPresent("taskId", record::getTaskId)
                .map(serviceId).toPropertyWhenPresent("serviceId", record::getServiceId)
                .map(state).toPropertyWhenPresent("state", record::getState)
                .map(schemaName).toPropertyWhenPresent("schemaName", record::getSchemaName)
                .map(parallelSeq).toPropertyWhenPresent("parallelSeq", record::getParallelSeq)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default Optional<RplDdlSub> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplDdlSub, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default List<RplDdlSub> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplDdlSub, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default List<RplDdlSub> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplDdlSub, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default Optional<RplDdlSub> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplDdlSub, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    static UpdateDSL<UpdateModel> updateAllColumns(RplDdlSub record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(fsmId).equalTo(record::getFsmId)
            .set(ddlTso).equalTo(record::getDdlTso)
            .set(taskId).equalTo(record::getTaskId)
            .set(serviceId).equalTo(record::getServiceId)
            .set(state).equalTo(record::getState)
            .set(schemaName).equalTo(record::getSchemaName)
            .set(parallelSeq).equalTo(record::getParallelSeq);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplDdlSub record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(fsmId).equalToWhenPresent(record::getFsmId)
            .set(ddlTso).equalToWhenPresent(record::getDdlTso)
            .set(taskId).equalToWhenPresent(record::getTaskId)
            .set(serviceId).equalToWhenPresent(record::getServiceId)
            .set(state).equalToWhenPresent(record::getState)
            .set(schemaName).equalToWhenPresent(record::getSchemaName)
            .set(parallelSeq).equalToWhenPresent(record::getParallelSeq);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.296+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default int updateByPrimaryKey(RplDdlSub record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(fsmId).equalTo(record::getFsmId)
                .set(ddlTso).equalTo(record::getDdlTso)
                .set(taskId).equalTo(record::getTaskId)
                .set(serviceId).equalTo(record::getServiceId)
                .set(state).equalTo(record::getState)
                .set(schemaName).equalTo(record::getSchemaName)
                .set(parallelSeq).equalTo(record::getParallelSeq)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.297+08:00",
        comments = "Source Table: rpl_ddl_sub")
    default int updateByPrimaryKeySelective(RplDdlSub record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(fsmId).equalToWhenPresent(record::getFsmId)
                .set(ddlTso).equalToWhenPresent(record::getDdlTso)
                .set(taskId).equalToWhenPresent(record::getTaskId)
                .set(serviceId).equalToWhenPresent(record::getServiceId)
                .set(state).equalToWhenPresent(record::getState)
                .set(schemaName).equalToWhenPresent(record::getSchemaName)
                .set(parallelSeq).equalToWhenPresent(record::getParallelSeq)
                .where(id, isEqualTo(record::getId))
        );
    }
}