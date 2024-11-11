/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.RplStateMachineDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
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
public interface RplStateMachineMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.795+08:00", comments="Source Table: rpl_state_machine")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, type, className, channel, status, state, clusterId, config, context);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.774+08:00", comments="Source Table: rpl_state_machine")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.776+08:00", comments="Source Table: rpl_state_machine")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.778+08:00", comments="Source Table: rpl_state_machine")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    @SelectKey(statement="SELECT LAST_INSERT_ID()", keyProperty="record.id", before=false, resultType=Long.class)
    int insert(InsertStatementProvider<RplStateMachine> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.784+08:00", comments="Source Table: rpl_state_machine")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="class_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="channel", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="state", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="context", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<RplStateMachine> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.786+08:00", comments="Source Table: rpl_state_machine")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="class_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="channel", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="state", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="context", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<RplStateMachine> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.787+08:00", comments="Source Table: rpl_state_machine")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.787+08:00", comments="Source Table: rpl_state_machine")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplStateMachine, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.788+08:00", comments="Source Table: rpl_state_machine")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplStateMachine, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.789+08:00", comments="Source Table: rpl_state_machine")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.79+08:00", comments="Source Table: rpl_state_machine")
    default int insert(RplStateMachine record) {
        return MyBatis3Utils.insert(this::insert, record, rplStateMachine, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(type).toProperty("type")
            .map(className).toProperty("className")
            .map(channel).toProperty("channel")
            .map(status).toProperty("status")
            .map(state).toProperty("state")
            .map(clusterId).toProperty("clusterId")
            .map(config).toProperty("config")
            .map(context).toProperty("context")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.793+08:00", comments="Source Table: rpl_state_machine")
    default int insertSelective(RplStateMachine record) {
        return MyBatis3Utils.insert(this::insert, record, rplStateMachine, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(type).toPropertyWhenPresent("type", record::getType)
            .map(className).toPropertyWhenPresent("className", record::getClassName)
            .map(channel).toPropertyWhenPresent("channel", record::getChannel)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(state).toPropertyWhenPresent("state", record::getState)
            .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
            .map(config).toPropertyWhenPresent("config", record::getConfig)
            .map(context).toPropertyWhenPresent("context", record::getContext)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.796+08:00", comments="Source Table: rpl_state_machine")
    default Optional<RplStateMachine> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplStateMachine, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.797+08:00", comments="Source Table: rpl_state_machine")
    default List<RplStateMachine> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplStateMachine, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.797+08:00", comments="Source Table: rpl_state_machine")
    default List<RplStateMachine> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplStateMachine, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.798+08:00", comments="Source Table: rpl_state_machine")
    default Optional<RplStateMachine> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.798+08:00", comments="Source Table: rpl_state_machine")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplStateMachine, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.799+08:00", comments="Source Table: rpl_state_machine")
    static UpdateDSL<UpdateModel> updateAllColumns(RplStateMachine record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(type).equalTo(record::getType)
                .set(className).equalTo(record::getClassName)
                .set(channel).equalTo(record::getChannel)
                .set(status).equalTo(record::getStatus)
                .set(state).equalTo(record::getState)
                .set(clusterId).equalTo(record::getClusterId)
                .set(config).equalTo(record::getConfig)
                .set(context).equalTo(record::getContext);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.8+08:00", comments="Source Table: rpl_state_machine")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplStateMachine record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(type).equalToWhenPresent(record::getType)
                .set(className).equalToWhenPresent(record::getClassName)
                .set(channel).equalToWhenPresent(record::getChannel)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(state).equalToWhenPresent(record::getState)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .set(config).equalToWhenPresent(record::getConfig)
                .set(context).equalToWhenPresent(record::getContext);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.801+08:00", comments="Source Table: rpl_state_machine")
    default int updateByPrimaryKey(RplStateMachine record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(type).equalTo(record::getType)
            .set(className).equalTo(record::getClassName)
            .set(channel).equalTo(record::getChannel)
            .set(status).equalTo(record::getStatus)
            .set(state).equalTo(record::getState)
            .set(clusterId).equalTo(record::getClusterId)
            .set(config).equalTo(record::getConfig)
            .set(context).equalTo(record::getContext)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.802+08:00", comments="Source Table: rpl_state_machine")
    default int updateByPrimaryKeySelective(RplStateMachine record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(type).equalToWhenPresent(record::getType)
            .set(className).equalToWhenPresent(record::getClassName)
            .set(channel).equalToWhenPresent(record::getChannel)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(state).equalToWhenPresent(record::getState)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .set(config).equalToWhenPresent(record::getConfig)
            .set(context).equalToWhenPresent(record::getContext)
            .where(id, isEqualTo(record::getId))
        );
    }
}