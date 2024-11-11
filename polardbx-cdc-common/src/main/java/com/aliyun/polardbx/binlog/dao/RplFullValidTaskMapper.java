/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.RplFullValidTaskDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplFullValidTask;
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
public interface RplFullValidTaskMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.760708+08:00", comments="Source Table: rpl_full_valid_task")
    BasicColumn[] selectList = BasicColumn.columnList(id, stateMachineId, srcLogicalDb, srcLogicalTable, dstLogicalDb, dstLogicalTable, taskStage, taskState, createTime, updateTime);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.756695+08:00", comments="Source Table: rpl_full_valid_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.757116+08:00", comments="Source Table: rpl_full_valid_task")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.757321+08:00", comments="Source Table: rpl_full_valid_task")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<RplFullValidTask> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.757707+08:00", comments="Source Table: rpl_full_valid_task")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<RplFullValidTask> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.757978+08:00", comments="Source Table: rpl_full_valid_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="state_machine_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="src_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_stage", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_state", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP)
    })
    Optional<RplFullValidTask> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.75862+08:00", comments="Source Table: rpl_full_valid_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="state_machine_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="src_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_stage", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_state", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP)
    })
    List<RplFullValidTask> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.758822+08:00", comments="Source Table: rpl_full_valid_task")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.758978+08:00", comments="Source Table: rpl_full_valid_task")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplFullValidTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.759152+08:00", comments="Source Table: rpl_full_valid_task")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplFullValidTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.759338+08:00", comments="Source Table: rpl_full_valid_task")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.759521+08:00", comments="Source Table: rpl_full_valid_task")
    default int insert(RplFullValidTask record) {
        return MyBatis3Utils.insert(this::insert, record, rplFullValidTask, c ->
            c.map(id).toProperty("id")
            .map(stateMachineId).toProperty("stateMachineId")
            .map(srcLogicalDb).toProperty("srcLogicalDb")
            .map(srcLogicalTable).toProperty("srcLogicalTable")
            .map(dstLogicalDb).toProperty("dstLogicalDb")
            .map(dstLogicalTable).toProperty("dstLogicalTable")
            .map(taskStage).toProperty("taskStage")
            .map(taskState).toProperty("taskState")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.759981+08:00", comments="Source Table: rpl_full_valid_task")
    default int insertMultiple(Collection<RplFullValidTask> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, rplFullValidTask, c ->
            c.map(id).toProperty("id")
            .map(stateMachineId).toProperty("stateMachineId")
            .map(srcLogicalDb).toProperty("srcLogicalDb")
            .map(srcLogicalTable).toProperty("srcLogicalTable")
            .map(dstLogicalDb).toProperty("dstLogicalDb")
            .map(dstLogicalTable).toProperty("dstLogicalTable")
            .map(taskStage).toProperty("taskStage")
            .map(taskState).toProperty("taskState")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.760196+08:00", comments="Source Table: rpl_full_valid_task")
    default int insertSelective(RplFullValidTask record) {
        return MyBatis3Utils.insert(this::insert, record, rplFullValidTask, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(stateMachineId).toPropertyWhenPresent("stateMachineId", record::getStateMachineId)
            .map(srcLogicalDb).toPropertyWhenPresent("srcLogicalDb", record::getSrcLogicalDb)
            .map(srcLogicalTable).toPropertyWhenPresent("srcLogicalTable", record::getSrcLogicalTable)
            .map(dstLogicalDb).toPropertyWhenPresent("dstLogicalDb", record::getDstLogicalDb)
            .map(dstLogicalTable).toPropertyWhenPresent("dstLogicalTable", record::getDstLogicalTable)
            .map(taskStage).toPropertyWhenPresent("taskStage", record::getTaskStage)
            .map(taskState).toPropertyWhenPresent("taskState", record::getTaskState)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
            .map(updateTime).toPropertyWhenPresent("updateTime", record::getUpdateTime)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.761106+08:00", comments="Source Table: rpl_full_valid_task")
    default Optional<RplFullValidTask> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplFullValidTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.761274+08:00", comments="Source Table: rpl_full_valid_task")
    default List<RplFullValidTask> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplFullValidTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76144+08:00", comments="Source Table: rpl_full_valid_task")
    default List<RplFullValidTask> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplFullValidTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.761618+08:00", comments="Source Table: rpl_full_valid_task")
    default Optional<RplFullValidTask> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.761822+08:00", comments="Source Table: rpl_full_valid_task")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplFullValidTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.762012+08:00", comments="Source Table: rpl_full_valid_task")
    static UpdateDSL<UpdateModel> updateAllColumns(RplFullValidTask record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(stateMachineId).equalTo(record::getStateMachineId)
                .set(srcLogicalDb).equalTo(record::getSrcLogicalDb)
                .set(srcLogicalTable).equalTo(record::getSrcLogicalTable)
                .set(dstLogicalDb).equalTo(record::getDstLogicalDb)
                .set(dstLogicalTable).equalTo(record::getDstLogicalTable)
                .set(taskStage).equalTo(record::getTaskStage)
                .set(taskState).equalTo(record::getTaskState)
                .set(createTime).equalTo(record::getCreateTime)
                .set(updateTime).equalTo(record::getUpdateTime);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76225+08:00", comments="Source Table: rpl_full_valid_task")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplFullValidTask record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
                .set(srcLogicalDb).equalToWhenPresent(record::getSrcLogicalDb)
                .set(srcLogicalTable).equalToWhenPresent(record::getSrcLogicalTable)
                .set(dstLogicalDb).equalToWhenPresent(record::getDstLogicalDb)
                .set(dstLogicalTable).equalToWhenPresent(record::getDstLogicalTable)
                .set(taskStage).equalToWhenPresent(record::getTaskStage)
                .set(taskState).equalToWhenPresent(record::getTaskState)
                .set(createTime).equalToWhenPresent(record::getCreateTime)
                .set(updateTime).equalToWhenPresent(record::getUpdateTime);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.762569+08:00", comments="Source Table: rpl_full_valid_task")
    default int updateByPrimaryKey(RplFullValidTask record) {
        return update(c ->
            c.set(stateMachineId).equalTo(record::getStateMachineId)
            .set(srcLogicalDb).equalTo(record::getSrcLogicalDb)
            .set(srcLogicalTable).equalTo(record::getSrcLogicalTable)
            .set(dstLogicalDb).equalTo(record::getDstLogicalDb)
            .set(dstLogicalTable).equalTo(record::getDstLogicalTable)
            .set(taskStage).equalTo(record::getTaskStage)
            .set(taskState).equalTo(record::getTaskState)
            .set(createTime).equalTo(record::getCreateTime)
            .set(updateTime).equalTo(record::getUpdateTime)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.762894+08:00", comments="Source Table: rpl_full_valid_task")
    default int updateByPrimaryKeySelective(RplFullValidTask record) {
        return update(c ->
            c.set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
            .set(srcLogicalDb).equalToWhenPresent(record::getSrcLogicalDb)
            .set(srcLogicalTable).equalToWhenPresent(record::getSrcLogicalTable)
            .set(dstLogicalDb).equalToWhenPresent(record::getDstLogicalDb)
            .set(dstLogicalTable).equalToWhenPresent(record::getDstLogicalTable)
            .set(taskStage).equalToWhenPresent(record::getTaskStage)
            .set(taskState).equalToWhenPresent(record::getTaskState)
            .set(createTime).equalToWhenPresent(record::getCreateTime)
            .set(updateTime).equalToWhenPresent(record::getUpdateTime)
            .where(id, isEqualTo(record::getId))
        );
    }
}