/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.ValidationTask;
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
public interface ValidationTaskMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    BasicColumn[] selectList = BasicColumn.columnList(id, externalId, stateMachineId, serviceId, taskId, type, state, drdsInsId, rdsInsId, srcLogicalDb, srcLogicalTable, srcLogicalKeyCol, srcPhyDb, srcPhyTable, srcPhyKeyCol, polardbxInsId, dstLogicalDb, dstLogicalTable, dstLogicalKeyCol, taskRange, deleted, createTime, updateTime, config, stats);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source Table: validation_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source Table: validation_task")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source Table: validation_task")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    @SelectKey(statement="SELECT LAST_INSERT_ID()", keyProperty="record.id", before=false, resultType=Long.class)
    int insert(InsertStatementProvider<ValidationTask> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source Table: validation_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="external_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="state_machine_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="service_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="type", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="state", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="drds_ins_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="rds_ins_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="polardbx_ins_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_range", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="deleted", javaType=Boolean.class, jdbcType=JdbcType.BIT),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="stats", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<ValidationTask> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source Table: validation_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="external_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="state_machine_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="service_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="type", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="state", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="drds_ins_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="rds_ins_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="polardbx_ins_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_range", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="deleted", javaType=Boolean.class, jdbcType=JdbcType.BIT),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="stats", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<ValidationTask> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, validationTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, validationTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default int insert(ValidationTask record) {
        return MyBatis3Utils.insert(this::insert, record, validationTask, c ->
            c.map(externalId).toProperty("externalId")
            .map(stateMachineId).toProperty("stateMachineId")
            .map(serviceId).toProperty("serviceId")
            .map(taskId).toProperty("taskId")
            .map(type).toProperty("type")
            .map(state).toProperty("state")
            .map(drdsInsId).toProperty("drdsInsId")
            .map(rdsInsId).toProperty("rdsInsId")
            .map(srcLogicalDb).toProperty("srcLogicalDb")
            .map(srcLogicalTable).toProperty("srcLogicalTable")
            .map(srcLogicalKeyCol).toProperty("srcLogicalKeyCol")
            .map(srcPhyDb).toProperty("srcPhyDb")
            .map(srcPhyTable).toProperty("srcPhyTable")
            .map(srcPhyKeyCol).toProperty("srcPhyKeyCol")
            .map(polardbxInsId).toProperty("polardbxInsId")
            .map(dstLogicalDb).toProperty("dstLogicalDb")
            .map(dstLogicalTable).toProperty("dstLogicalTable")
            .map(dstLogicalKeyCol).toProperty("dstLogicalKeyCol")
            .map(taskRange).toProperty("taskRange")
            .map(deleted).toProperty("deleted")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
            .map(config).toProperty("config")
            .map(stats).toProperty("stats")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default int insertSelective(ValidationTask record) {
        return MyBatis3Utils.insert(this::insert, record, validationTask, c ->
            c.map(externalId).toPropertyWhenPresent("externalId", record::getExternalId)
            .map(stateMachineId).toPropertyWhenPresent("stateMachineId", record::getStateMachineId)
            .map(serviceId).toPropertyWhenPresent("serviceId", record::getServiceId)
            .map(taskId).toPropertyWhenPresent("taskId", record::getTaskId)
            .map(type).toPropertyWhenPresent("type", record::getType)
            .map(state).toPropertyWhenPresent("state", record::getState)
            .map(drdsInsId).toPropertyWhenPresent("drdsInsId", record::getDrdsInsId)
            .map(rdsInsId).toPropertyWhenPresent("rdsInsId", record::getRdsInsId)
            .map(srcLogicalDb).toPropertyWhenPresent("srcLogicalDb", record::getSrcLogicalDb)
            .map(srcLogicalTable).toPropertyWhenPresent("srcLogicalTable", record::getSrcLogicalTable)
            .map(srcLogicalKeyCol).toPropertyWhenPresent("srcLogicalKeyCol", record::getSrcLogicalKeyCol)
            .map(srcPhyDb).toPropertyWhenPresent("srcPhyDb", record::getSrcPhyDb)
            .map(srcPhyTable).toPropertyWhenPresent("srcPhyTable", record::getSrcPhyTable)
            .map(srcPhyKeyCol).toPropertyWhenPresent("srcPhyKeyCol", record::getSrcPhyKeyCol)
            .map(polardbxInsId).toPropertyWhenPresent("polardbxInsId", record::getPolardbxInsId)
            .map(dstLogicalDb).toPropertyWhenPresent("dstLogicalDb", record::getDstLogicalDb)
            .map(dstLogicalTable).toPropertyWhenPresent("dstLogicalTable", record::getDstLogicalTable)
            .map(dstLogicalKeyCol).toPropertyWhenPresent("dstLogicalKeyCol", record::getDstLogicalKeyCol)
            .map(taskRange).toPropertyWhenPresent("taskRange", record::getTaskRange)
            .map(deleted).toPropertyWhenPresent("deleted", record::getDeleted)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
            .map(updateTime).toPropertyWhenPresent("updateTime", record::getUpdateTime)
            .map(config).toPropertyWhenPresent("config", record::getConfig)
            .map(stats).toPropertyWhenPresent("stats", record::getStats)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default Optional<ValidationTask> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, validationTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default List<ValidationTask> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, validationTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default List<ValidationTask> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, validationTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default Optional<ValidationTask> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, validationTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    static UpdateDSL<UpdateModel> updateAllColumns(ValidationTask record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(externalId).equalTo(record::getExternalId)
                .set(stateMachineId).equalTo(record::getStateMachineId)
                .set(serviceId).equalTo(record::getServiceId)
                .set(taskId).equalTo(record::getTaskId)
                .set(type).equalTo(record::getType)
                .set(state).equalTo(record::getState)
                .set(drdsInsId).equalTo(record::getDrdsInsId)
                .set(rdsInsId).equalTo(record::getRdsInsId)
                .set(srcLogicalDb).equalTo(record::getSrcLogicalDb)
                .set(srcLogicalTable).equalTo(record::getSrcLogicalTable)
                .set(srcLogicalKeyCol).equalTo(record::getSrcLogicalKeyCol)
                .set(srcPhyDb).equalTo(record::getSrcPhyDb)
                .set(srcPhyTable).equalTo(record::getSrcPhyTable)
                .set(srcPhyKeyCol).equalTo(record::getSrcPhyKeyCol)
                .set(polardbxInsId).equalTo(record::getPolardbxInsId)
                .set(dstLogicalDb).equalTo(record::getDstLogicalDb)
                .set(dstLogicalTable).equalTo(record::getDstLogicalTable)
                .set(dstLogicalKeyCol).equalTo(record::getDstLogicalKeyCol)
                .set(taskRange).equalTo(record::getTaskRange)
                .set(deleted).equalTo(record::getDeleted)
                .set(createTime).equalTo(record::getCreateTime)
                .set(updateTime).equalTo(record::getUpdateTime)
                .set(config).equalTo(record::getConfig)
                .set(stats).equalTo(record::getStats);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(ValidationTask record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(externalId).equalToWhenPresent(record::getExternalId)
                .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
                .set(serviceId).equalToWhenPresent(record::getServiceId)
                .set(taskId).equalToWhenPresent(record::getTaskId)
                .set(type).equalToWhenPresent(record::getType)
                .set(state).equalToWhenPresent(record::getState)
                .set(drdsInsId).equalToWhenPresent(record::getDrdsInsId)
                .set(rdsInsId).equalToWhenPresent(record::getRdsInsId)
                .set(srcLogicalDb).equalToWhenPresent(record::getSrcLogicalDb)
                .set(srcLogicalTable).equalToWhenPresent(record::getSrcLogicalTable)
                .set(srcLogicalKeyCol).equalToWhenPresent(record::getSrcLogicalKeyCol)
                .set(srcPhyDb).equalToWhenPresent(record::getSrcPhyDb)
                .set(srcPhyTable).equalToWhenPresent(record::getSrcPhyTable)
                .set(srcPhyKeyCol).equalToWhenPresent(record::getSrcPhyKeyCol)
                .set(polardbxInsId).equalToWhenPresent(record::getPolardbxInsId)
                .set(dstLogicalDb).equalToWhenPresent(record::getDstLogicalDb)
                .set(dstLogicalTable).equalToWhenPresent(record::getDstLogicalTable)
                .set(dstLogicalKeyCol).equalToWhenPresent(record::getDstLogicalKeyCol)
                .set(taskRange).equalToWhenPresent(record::getTaskRange)
                .set(deleted).equalToWhenPresent(record::getDeleted)
                .set(createTime).equalToWhenPresent(record::getCreateTime)
                .set(updateTime).equalToWhenPresent(record::getUpdateTime)
                .set(config).equalToWhenPresent(record::getConfig)
                .set(stats).equalToWhenPresent(record::getStats);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default int updateByPrimaryKey(ValidationTask record) {
        return update(c ->
            c.set(externalId).equalTo(record::getExternalId)
            .set(stateMachineId).equalTo(record::getStateMachineId)
            .set(serviceId).equalTo(record::getServiceId)
            .set(taskId).equalTo(record::getTaskId)
            .set(type).equalTo(record::getType)
            .set(state).equalTo(record::getState)
            .set(drdsInsId).equalTo(record::getDrdsInsId)
            .set(rdsInsId).equalTo(record::getRdsInsId)
            .set(srcLogicalDb).equalTo(record::getSrcLogicalDb)
            .set(srcLogicalTable).equalTo(record::getSrcLogicalTable)
            .set(srcLogicalKeyCol).equalTo(record::getSrcLogicalKeyCol)
            .set(srcPhyDb).equalTo(record::getSrcPhyDb)
            .set(srcPhyTable).equalTo(record::getSrcPhyTable)
            .set(srcPhyKeyCol).equalTo(record::getSrcPhyKeyCol)
            .set(polardbxInsId).equalTo(record::getPolardbxInsId)
            .set(dstLogicalDb).equalTo(record::getDstLogicalDb)
            .set(dstLogicalTable).equalTo(record::getDstLogicalTable)
            .set(dstLogicalKeyCol).equalTo(record::getDstLogicalKeyCol)
            .set(taskRange).equalTo(record::getTaskRange)
            .set(deleted).equalTo(record::getDeleted)
            .set(createTime).equalTo(record::getCreateTime)
            .set(updateTime).equalTo(record::getUpdateTime)
            .set(config).equalTo(record::getConfig)
            .set(stats).equalTo(record::getStats)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.944+08:00", comments="Source Table: validation_task")
    default int updateByPrimaryKeySelective(ValidationTask record) {
        return update(c ->
            c.set(externalId).equalToWhenPresent(record::getExternalId)
            .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
            .set(serviceId).equalToWhenPresent(record::getServiceId)
            .set(taskId).equalToWhenPresent(record::getTaskId)
            .set(type).equalToWhenPresent(record::getType)
            .set(state).equalToWhenPresent(record::getState)
            .set(drdsInsId).equalToWhenPresent(record::getDrdsInsId)
            .set(rdsInsId).equalToWhenPresent(record::getRdsInsId)
            .set(srcLogicalDb).equalToWhenPresent(record::getSrcLogicalDb)
            .set(srcLogicalTable).equalToWhenPresent(record::getSrcLogicalTable)
            .set(srcLogicalKeyCol).equalToWhenPresent(record::getSrcLogicalKeyCol)
            .set(srcPhyDb).equalToWhenPresent(record::getSrcPhyDb)
            .set(srcPhyTable).equalToWhenPresent(record::getSrcPhyTable)
            .set(srcPhyKeyCol).equalToWhenPresent(record::getSrcPhyKeyCol)
            .set(polardbxInsId).equalToWhenPresent(record::getPolardbxInsId)
            .set(dstLogicalDb).equalToWhenPresent(record::getDstLogicalDb)
            .set(dstLogicalTable).equalToWhenPresent(record::getDstLogicalTable)
            .set(dstLogicalKeyCol).equalToWhenPresent(record::getDstLogicalKeyCol)
            .set(taskRange).equalToWhenPresent(record::getTaskRange)
            .set(deleted).equalToWhenPresent(record::getDeleted)
            .set(createTime).equalToWhenPresent(record::getCreateTime)
            .set(updateTime).equalToWhenPresent(record::getUpdateTime)
            .set(config).equalToWhenPresent(record::getConfig)
            .set(stats).equalToWhenPresent(record::getStats)
            .where(id, isEqualTo(record::getId))
        );
    }
}