/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.RplDdlDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplDdl;

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
public interface RplDdlMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.584+08:00",
        comments = "Source Table: rpl_ddl_main")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, fsmId, ddlTso, serviceId, token, state, asyncFlag,
            asyncState, schemaName, tableName, taskId, parallelSeq, ddlStmt);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.571+08:00",
        comments = "Source Table: rpl_ddl_main")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.572+08:00",
        comments = "Source Table: rpl_ddl_main")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.572+08:00",
        comments = "Source Table: rpl_ddl_main")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "record.id", before = false,
        resultType = Long.class)
    int insert(InsertStatementProvider<RplDdl> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.578+08:00",
        comments = "Source Table: rpl_ddl_main")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "fsm_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "ddl_tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "service_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "token", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "state", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "async_flag", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "async_state", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "schema_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "task_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "parallel_seq", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "ddl_stmt", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    Optional<RplDdl> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.579+08:00",
        comments = "Source Table: rpl_ddl_main")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "fsm_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "ddl_tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "service_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "token", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "state", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "async_flag", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "async_state", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "schema_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "task_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "parallel_seq", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "ddl_stmt", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    List<RplDdl> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.579+08:00",
        comments = "Source Table: rpl_ddl_main")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.58+08:00",
        comments = "Source Table: rpl_ddl_main")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplDdl, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.58+08:00",
        comments = "Source Table: rpl_ddl_main")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplDdl, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.581+08:00",
        comments = "Source Table: rpl_ddl_main")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.581+08:00",
        comments = "Source Table: rpl_ddl_main")
    default int insert(RplDdl record) {
        return MyBatis3Utils.insert(this::insert, record, rplDdl, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(fsmId).toProperty("fsmId")
                .map(ddlTso).toProperty("ddlTso")
                .map(serviceId).toProperty("serviceId")
                .map(token).toProperty("token")
                .map(state).toProperty("state")
                .map(asyncFlag).toProperty("asyncFlag")
                .map(asyncState).toProperty("asyncState")
                .map(schemaName).toProperty("schemaName")
                .map(tableName).toProperty("tableName")
                .map(taskId).toProperty("taskId")
                .map(parallelSeq).toProperty("parallelSeq")
                .map(ddlStmt).toProperty("ddlStmt")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.583+08:00",
        comments = "Source Table: rpl_ddl_main")
    default int insertSelective(RplDdl record) {
        return MyBatis3Utils.insert(this::insert, record, rplDdl, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(fsmId).toPropertyWhenPresent("fsmId", record::getFsmId)
                .map(ddlTso).toPropertyWhenPresent("ddlTso", record::getDdlTso)
                .map(serviceId).toPropertyWhenPresent("serviceId", record::getServiceId)
                .map(token).toPropertyWhenPresent("token", record::getToken)
                .map(state).toPropertyWhenPresent("state", record::getState)
                .map(asyncFlag).toPropertyWhenPresent("asyncFlag", record::getAsyncFlag)
                .map(asyncState).toPropertyWhenPresent("asyncState", record::getAsyncState)
                .map(schemaName).toPropertyWhenPresent("schemaName", record::getSchemaName)
                .map(tableName).toPropertyWhenPresent("tableName", record::getTableName)
                .map(taskId).toPropertyWhenPresent("taskId", record::getTaskId)
                .map(parallelSeq).toPropertyWhenPresent("parallelSeq", record::getParallelSeq)
                .map(ddlStmt).toPropertyWhenPresent("ddlStmt", record::getDdlStmt)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.585+08:00",
        comments = "Source Table: rpl_ddl_main")
    default Optional<RplDdl> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplDdl, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.585+08:00",
        comments = "Source Table: rpl_ddl_main")
    default List<RplDdl> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplDdl, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.586+08:00",
        comments = "Source Table: rpl_ddl_main")
    default List<RplDdl> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplDdl, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.586+08:00",
        comments = "Source Table: rpl_ddl_main")
    default Optional<RplDdl> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.586+08:00",
        comments = "Source Table: rpl_ddl_main")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplDdl, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.587+08:00",
        comments = "Source Table: rpl_ddl_main")
    static UpdateDSL<UpdateModel> updateAllColumns(RplDdl record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(fsmId).equalTo(record::getFsmId)
            .set(ddlTso).equalTo(record::getDdlTso)
            .set(serviceId).equalTo(record::getServiceId)
            .set(token).equalTo(record::getToken)
            .set(state).equalTo(record::getState)
            .set(asyncFlag).equalTo(record::getAsyncFlag)
            .set(asyncState).equalTo(record::getAsyncState)
            .set(schemaName).equalTo(record::getSchemaName)
            .set(tableName).equalTo(record::getTableName)
            .set(taskId).equalTo(record::getTaskId)
            .set(parallelSeq).equalTo(record::getParallelSeq)
            .set(ddlStmt).equalTo(record::getDdlStmt);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.587+08:00",
        comments = "Source Table: rpl_ddl_main")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplDdl record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(fsmId).equalToWhenPresent(record::getFsmId)
            .set(ddlTso).equalToWhenPresent(record::getDdlTso)
            .set(serviceId).equalToWhenPresent(record::getServiceId)
            .set(token).equalToWhenPresent(record::getToken)
            .set(state).equalToWhenPresent(record::getState)
            .set(asyncFlag).equalToWhenPresent(record::getAsyncFlag)
            .set(asyncState).equalToWhenPresent(record::getAsyncState)
            .set(schemaName).equalToWhenPresent(record::getSchemaName)
            .set(tableName).equalToWhenPresent(record::getTableName)
            .set(taskId).equalToWhenPresent(record::getTaskId)
            .set(parallelSeq).equalToWhenPresent(record::getParallelSeq)
            .set(ddlStmt).equalToWhenPresent(record::getDdlStmt);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.588+08:00",
        comments = "Source Table: rpl_ddl_main")
    default int updateByPrimaryKey(RplDdl record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(fsmId).equalTo(record::getFsmId)
                .set(ddlTso).equalTo(record::getDdlTso)
                .set(serviceId).equalTo(record::getServiceId)
                .set(token).equalTo(record::getToken)
                .set(state).equalTo(record::getState)
                .set(asyncFlag).equalTo(record::getAsyncFlag)
                .set(asyncState).equalTo(record::getAsyncState)
                .set(schemaName).equalTo(record::getSchemaName)
                .set(tableName).equalTo(record::getTableName)
                .set(taskId).equalTo(record::getTaskId)
                .set(parallelSeq).equalTo(record::getParallelSeq)
                .set(ddlStmt).equalTo(record::getDdlStmt)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.589+08:00",
        comments = "Source Table: rpl_ddl_main")
    default int updateByPrimaryKeySelective(RplDdl record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(fsmId).equalToWhenPresent(record::getFsmId)
                .set(ddlTso).equalToWhenPresent(record::getDdlTso)
                .set(serviceId).equalToWhenPresent(record::getServiceId)
                .set(token).equalToWhenPresent(record::getToken)
                .set(state).equalToWhenPresent(record::getState)
                .set(asyncFlag).equalToWhenPresent(record::getAsyncFlag)
                .set(asyncState).equalToWhenPresent(record::getAsyncState)
                .set(schemaName).equalToWhenPresent(record::getSchemaName)
                .set(tableName).equalToWhenPresent(record::getTableName)
                .set(taskId).equalToWhenPresent(record::getTaskId)
                .set(parallelSeq).equalToWhenPresent(record::getParallelSeq)
                .set(ddlStmt).equalToWhenPresent(record::getDdlStmt)
                .where(id, isEqualTo(record::getId))
        );
    }
}