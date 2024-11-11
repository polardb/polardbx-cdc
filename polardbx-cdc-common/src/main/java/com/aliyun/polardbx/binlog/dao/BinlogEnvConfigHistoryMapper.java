/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.BinlogEnvConfigHistoryDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.BinlogEnvConfigHistory;

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
public interface BinlogEnvConfigHistoryMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.621+08:00",
        comments = "Source Table: binlog_env_config_history")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, tso, instructionId, changeEnvContent);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.613+08:00",
        comments = "Source Table: binlog_env_config_history")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.614+08:00",
        comments = "Source Table: binlog_env_config_history")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.614+08:00",
        comments = "Source Table: binlog_env_config_history")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<BinlogEnvConfigHistory> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.615+08:00",
        comments = "Source Table: binlog_env_config_history")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogEnvConfigHistory> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.615+08:00",
        comments = "Source Table: binlog_env_config_history")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "instruction_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "change_env_content", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    Optional<BinlogEnvConfigHistory> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.616+08:00",
        comments = "Source Table: binlog_env_config_history")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "instruction_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "change_env_content", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    List<BinlogEnvConfigHistory> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.617+08:00",
        comments = "Source Table: binlog_env_config_history")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.617+08:00",
        comments = "Source Table: binlog_env_config_history")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogEnvConfigHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.617+08:00",
        comments = "Source Table: binlog_env_config_history")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogEnvConfigHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.618+08:00",
        comments = "Source Table: binlog_env_config_history")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.618+08:00",
        comments = "Source Table: binlog_env_config_history")
    default int insert(BinlogEnvConfigHistory record) {
        return MyBatis3Utils.insert(this::insert, record, binlogEnvConfigHistory, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(tso).toProperty("tso")
                .map(instructionId).toProperty("instructionId")
                .map(changeEnvContent).toProperty("changeEnvContent")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.62+08:00",
        comments = "Source Table: binlog_env_config_history")
    default int insertMultiple(Collection<BinlogEnvConfigHistory> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogEnvConfigHistory, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(tso).toProperty("tso")
                .map(instructionId).toProperty("instructionId")
                .map(changeEnvContent).toProperty("changeEnvContent")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.62+08:00",
        comments = "Source Table: binlog_env_config_history")
    default int insertSelective(BinlogEnvConfigHistory record) {
        return MyBatis3Utils.insert(this::insert, record, binlogEnvConfigHistory, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(tso).toPropertyWhenPresent("tso", record::getTso)
                .map(instructionId).toPropertyWhenPresent("instructionId", record::getInstructionId)
                .map(changeEnvContent).toPropertyWhenPresent("changeEnvContent", record::getChangeEnvContent)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.622+08:00",
        comments = "Source Table: binlog_env_config_history")
    default Optional<BinlogEnvConfigHistory> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogEnvConfigHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.622+08:00",
        comments = "Source Table: binlog_env_config_history")
    default List<BinlogEnvConfigHistory> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogEnvConfigHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.622+08:00",
        comments = "Source Table: binlog_env_config_history")
    default List<BinlogEnvConfigHistory> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogEnvConfigHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.623+08:00",
        comments = "Source Table: binlog_env_config_history")
    default Optional<BinlogEnvConfigHistory> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.623+08:00",
        comments = "Source Table: binlog_env_config_history")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogEnvConfigHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.623+08:00",
        comments = "Source Table: binlog_env_config_history")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogEnvConfigHistory record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(tso).equalTo(record::getTso)
            .set(instructionId).equalTo(record::getInstructionId)
            .set(changeEnvContent).equalTo(record::getChangeEnvContent);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.624+08:00",
        comments = "Source Table: binlog_env_config_history")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogEnvConfigHistory record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(instructionId).equalToWhenPresent(record::getInstructionId)
            .set(changeEnvContent).equalToWhenPresent(record::getChangeEnvContent);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.624+08:00",
        comments = "Source Table: binlog_env_config_history")
    default int updateByPrimaryKey(BinlogEnvConfigHistory record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(tso).equalTo(record::getTso)
                .set(instructionId).equalTo(record::getInstructionId)
                .set(changeEnvContent).equalTo(record::getChangeEnvContent)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.625+08:00",
        comments = "Source Table: binlog_env_config_history")
    default int updateByPrimaryKeySelective(BinlogEnvConfigHistory record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(instructionId).equalToWhenPresent(record::getInstructionId)
                .set(changeEnvContent).equalToWhenPresent(record::getChangeEnvContent)
                .where(id, isEqualTo(record::getId))
        );
    }
}