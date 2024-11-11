/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.RplFullValidSubTaskDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplFullValidSubTask;
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
public interface RplFullValidSubTaskMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765123+08:00", comments="Source Table: rpl_full_valid_sub_task")
    BasicColumn[] selectList = BasicColumn.columnList(id, stateMachineId, taskId, taskStage, taskState, taskType, createTime, updateTime, taskConfig, summary);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764697+08:00", comments="Source Table: rpl_full_valid_sub_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764723+08:00", comments="Source Table: rpl_full_valid_sub_task")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764746+08:00", comments="Source Table: rpl_full_valid_sub_task")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<RplFullValidSubTask> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764769+08:00", comments="Source Table: rpl_full_valid_sub_task")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<RplFullValidSubTask> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764793+08:00", comments="Source Table: rpl_full_valid_sub_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="state_machine_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="task_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="task_stage", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_state", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="task_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="summary", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<RplFullValidSubTask> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764861+08:00", comments="Source Table: rpl_full_valid_sub_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="state_machine_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="task_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="task_stage", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_state", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="task_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="summary", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<RplFullValidSubTask> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764911+08:00", comments="Source Table: rpl_full_valid_sub_task")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764934+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplFullValidSubTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764954+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplFullValidSubTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764976+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764997+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default int insert(RplFullValidSubTask record) {
        return MyBatis3Utils.insert(this::insert, record, rplFullValidSubTask, c ->
            c.map(id).toProperty("id")
            .map(stateMachineId).toProperty("stateMachineId")
            .map(taskId).toProperty("taskId")
            .map(taskStage).toProperty("taskStage")
            .map(taskState).toProperty("taskState")
            .map(taskType).toProperty("taskType")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
            .map(taskConfig).toProperty("taskConfig")
            .map(summary).toProperty("summary")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765029+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default int insertMultiple(Collection<RplFullValidSubTask> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, rplFullValidSubTask, c ->
            c.map(id).toProperty("id")
            .map(stateMachineId).toProperty("stateMachineId")
            .map(taskId).toProperty("taskId")
            .map(taskStage).toProperty("taskStage")
            .map(taskState).toProperty("taskState")
            .map(taskType).toProperty("taskType")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
            .map(taskConfig).toProperty("taskConfig")
            .map(summary).toProperty("summary")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765058+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default int insertSelective(RplFullValidSubTask record) {
        return MyBatis3Utils.insert(this::insert, record, rplFullValidSubTask, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(stateMachineId).toPropertyWhenPresent("stateMachineId", record::getStateMachineId)
            .map(taskId).toPropertyWhenPresent("taskId", record::getTaskId)
            .map(taskStage).toPropertyWhenPresent("taskStage", record::getTaskStage)
            .map(taskState).toPropertyWhenPresent("taskState", record::getTaskState)
            .map(taskType).toPropertyWhenPresent("taskType", record::getTaskType)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
            .map(updateTime).toPropertyWhenPresent("updateTime", record::getUpdateTime)
            .map(taskConfig).toPropertyWhenPresent("taskConfig", record::getTaskConfig)
            .map(summary).toPropertyWhenPresent("summary", record::getSummary)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765147+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default Optional<RplFullValidSubTask> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplFullValidSubTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765217+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default List<RplFullValidSubTask> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplFullValidSubTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765243+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default List<RplFullValidSubTask> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplFullValidSubTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765289+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default Optional<RplFullValidSubTask> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765319+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplFullValidSubTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765338+08:00", comments="Source Table: rpl_full_valid_sub_task")
    static UpdateDSL<UpdateModel> updateAllColumns(RplFullValidSubTask record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(stateMachineId).equalTo(record::getStateMachineId)
                .set(taskId).equalTo(record::getTaskId)
                .set(taskStage).equalTo(record::getTaskStage)
                .set(taskState).equalTo(record::getTaskState)
                .set(taskType).equalTo(record::getTaskType)
                .set(createTime).equalTo(record::getCreateTime)
                .set(updateTime).equalTo(record::getUpdateTime)
                .set(taskConfig).equalTo(record::getTaskConfig)
                .set(summary).equalTo(record::getSummary);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765378+08:00", comments="Source Table: rpl_full_valid_sub_task")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplFullValidSubTask record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
                .set(taskId).equalToWhenPresent(record::getTaskId)
                .set(taskStage).equalToWhenPresent(record::getTaskStage)
                .set(taskState).equalToWhenPresent(record::getTaskState)
                .set(taskType).equalToWhenPresent(record::getTaskType)
                .set(createTime).equalToWhenPresent(record::getCreateTime)
                .set(updateTime).equalToWhenPresent(record::getUpdateTime)
                .set(taskConfig).equalToWhenPresent(record::getTaskConfig)
                .set(summary).equalToWhenPresent(record::getSummary);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765423+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default int updateByPrimaryKey(RplFullValidSubTask record) {
        return update(c ->
            c.set(stateMachineId).equalTo(record::getStateMachineId)
            .set(taskId).equalTo(record::getTaskId)
            .set(taskStage).equalTo(record::getTaskStage)
            .set(taskState).equalTo(record::getTaskState)
            .set(taskType).equalTo(record::getTaskType)
            .set(createTime).equalTo(record::getCreateTime)
            .set(updateTime).equalTo(record::getUpdateTime)
            .set(taskConfig).equalTo(record::getTaskConfig)
            .set(summary).equalTo(record::getSummary)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765464+08:00", comments="Source Table: rpl_full_valid_sub_task")
    default int updateByPrimaryKeySelective(RplFullValidSubTask record) {
        return update(c ->
            c.set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
            .set(taskId).equalToWhenPresent(record::getTaskId)
            .set(taskStage).equalToWhenPresent(record::getTaskStage)
            .set(taskState).equalToWhenPresent(record::getTaskState)
            .set(taskType).equalToWhenPresent(record::getTaskType)
            .set(createTime).equalToWhenPresent(record::getCreateTime)
            .set(updateTime).equalToWhenPresent(record::getUpdateTime)
            .set(taskConfig).equalToWhenPresent(record::getTaskConfig)
            .set(summary).equalToWhenPresent(record::getSummary)
            .where(id, isEqualTo(record::getId))
        );
    }
}