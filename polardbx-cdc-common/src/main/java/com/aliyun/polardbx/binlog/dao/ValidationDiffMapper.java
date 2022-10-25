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

import static com.aliyun.polardbx.binlog.dao.ValidationDiffDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.ValidationDiff;
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
public interface ValidationDiffMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.506+08:00", comments="Source Table: validation_diff")
    BasicColumn[] selectList = BasicColumn.columnList(id, stateMachineId, serviceId, taskId, validationTaskId, type, state, srcLogicalDb, srcLogicalTable, srcLogicalKeyCol, srcPhyDb, srcPhyTable, srcPhyKeyCol, srcKeyColVal, dstLogicalDb, dstLogicalTable, dstLogicalKeyCol, dstKeyColVal, deleted, createTime, updateTime, diff);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.493+08:00", comments="Source Table: validation_diff")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.495+08:00", comments="Source Table: validation_diff")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.495+08:00", comments="Source Table: validation_diff")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<ValidationDiff> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.496+08:00", comments="Source Table: validation_diff")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<ValidationDiff> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.496+08:00", comments="Source Table: validation_diff")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="state_machine_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="service_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="validation_task_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="type", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="state", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="src_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_key_col_val", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_key_col_val", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="deleted", javaType=Boolean.class, jdbcType=JdbcType.BIT),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="diff", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<ValidationDiff> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.498+08:00", comments="Source Table: validation_diff")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="state_machine_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="service_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="validation_task_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="type", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="state", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="src_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_logical_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_phy_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="src_key_col_val", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_db", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_logical_key_col", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="dst_key_col_val", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="deleted", javaType=Boolean.class, jdbcType=JdbcType.BIT),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="diff", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<ValidationDiff> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.499+08:00", comments="Source Table: validation_diff")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.5+08:00", comments="Source Table: validation_diff")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, validationDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.5+08:00", comments="Source Table: validation_diff")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, validationDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.501+08:00", comments="Source Table: validation_diff")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.501+08:00", comments="Source Table: validation_diff")
    default int insert(ValidationDiff record) {
        return MyBatis3Utils.insert(this::insert, record, validationDiff, c ->
            c.map(id).toProperty("id")
            .map(stateMachineId).toProperty("stateMachineId")
            .map(serviceId).toProperty("serviceId")
            .map(taskId).toProperty("taskId")
            .map(validationTaskId).toProperty("validationTaskId")
            .map(type).toProperty("type")
            .map(state).toProperty("state")
            .map(srcLogicalDb).toProperty("srcLogicalDb")
            .map(srcLogicalTable).toProperty("srcLogicalTable")
            .map(srcLogicalKeyCol).toProperty("srcLogicalKeyCol")
            .map(srcPhyDb).toProperty("srcPhyDb")
            .map(srcPhyTable).toProperty("srcPhyTable")
            .map(srcPhyKeyCol).toProperty("srcPhyKeyCol")
            .map(srcKeyColVal).toProperty("srcKeyColVal")
            .map(dstLogicalDb).toProperty("dstLogicalDb")
            .map(dstLogicalTable).toProperty("dstLogicalTable")
            .map(dstLogicalKeyCol).toProperty("dstLogicalKeyCol")
            .map(dstKeyColVal).toProperty("dstKeyColVal")
            .map(deleted).toProperty("deleted")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
            .map(diff).toProperty("diff")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.504+08:00", comments="Source Table: validation_diff")
    default int insertMultiple(Collection<ValidationDiff> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, validationDiff, c ->
            c.map(id).toProperty("id")
            .map(stateMachineId).toProperty("stateMachineId")
            .map(serviceId).toProperty("serviceId")
            .map(taskId).toProperty("taskId")
            .map(validationTaskId).toProperty("validationTaskId")
            .map(type).toProperty("type")
            .map(state).toProperty("state")
            .map(srcLogicalDb).toProperty("srcLogicalDb")
            .map(srcLogicalTable).toProperty("srcLogicalTable")
            .map(srcLogicalKeyCol).toProperty("srcLogicalKeyCol")
            .map(srcPhyDb).toProperty("srcPhyDb")
            .map(srcPhyTable).toProperty("srcPhyTable")
            .map(srcPhyKeyCol).toProperty("srcPhyKeyCol")
            .map(srcKeyColVal).toProperty("srcKeyColVal")
            .map(dstLogicalDb).toProperty("dstLogicalDb")
            .map(dstLogicalTable).toProperty("dstLogicalTable")
            .map(dstLogicalKeyCol).toProperty("dstLogicalKeyCol")
            .map(dstKeyColVal).toProperty("dstKeyColVal")
            .map(deleted).toProperty("deleted")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
            .map(diff).toProperty("diff")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.505+08:00", comments="Source Table: validation_diff")
    default int insertSelective(ValidationDiff record) {
        return MyBatis3Utils.insert(this::insert, record, validationDiff, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(stateMachineId).toPropertyWhenPresent("stateMachineId", record::getStateMachineId)
            .map(serviceId).toPropertyWhenPresent("serviceId", record::getServiceId)
            .map(taskId).toPropertyWhenPresent("taskId", record::getTaskId)
            .map(validationTaskId).toPropertyWhenPresent("validationTaskId", record::getValidationTaskId)
            .map(type).toPropertyWhenPresent("type", record::getType)
            .map(state).toPropertyWhenPresent("state", record::getState)
            .map(srcLogicalDb).toPropertyWhenPresent("srcLogicalDb", record::getSrcLogicalDb)
            .map(srcLogicalTable).toPropertyWhenPresent("srcLogicalTable", record::getSrcLogicalTable)
            .map(srcLogicalKeyCol).toPropertyWhenPresent("srcLogicalKeyCol", record::getSrcLogicalKeyCol)
            .map(srcPhyDb).toPropertyWhenPresent("srcPhyDb", record::getSrcPhyDb)
            .map(srcPhyTable).toPropertyWhenPresent("srcPhyTable", record::getSrcPhyTable)
            .map(srcPhyKeyCol).toPropertyWhenPresent("srcPhyKeyCol", record::getSrcPhyKeyCol)
            .map(srcKeyColVal).toPropertyWhenPresent("srcKeyColVal", record::getSrcKeyColVal)
            .map(dstLogicalDb).toPropertyWhenPresent("dstLogicalDb", record::getDstLogicalDb)
            .map(dstLogicalTable).toPropertyWhenPresent("dstLogicalTable", record::getDstLogicalTable)
            .map(dstLogicalKeyCol).toPropertyWhenPresent("dstLogicalKeyCol", record::getDstLogicalKeyCol)
            .map(dstKeyColVal).toPropertyWhenPresent("dstKeyColVal", record::getDstKeyColVal)
            .map(deleted).toPropertyWhenPresent("deleted", record::getDeleted)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
            .map(updateTime).toPropertyWhenPresent("updateTime", record::getUpdateTime)
            .map(diff).toPropertyWhenPresent("diff", record::getDiff)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.508+08:00", comments="Source Table: validation_diff")
    default Optional<ValidationDiff> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, validationDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.509+08:00", comments="Source Table: validation_diff")
    default List<ValidationDiff> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, validationDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.509+08:00", comments="Source Table: validation_diff")
    default List<ValidationDiff> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, validationDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.51+08:00", comments="Source Table: validation_diff")
    default Optional<ValidationDiff> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.511+08:00", comments="Source Table: validation_diff")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, validationDiff, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.511+08:00", comments="Source Table: validation_diff")
    static UpdateDSL<UpdateModel> updateAllColumns(ValidationDiff record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(stateMachineId).equalTo(record::getStateMachineId)
                .set(serviceId).equalTo(record::getServiceId)
                .set(taskId).equalTo(record::getTaskId)
                .set(validationTaskId).equalTo(record::getValidationTaskId)
                .set(type).equalTo(record::getType)
                .set(state).equalTo(record::getState)
                .set(srcLogicalDb).equalTo(record::getSrcLogicalDb)
                .set(srcLogicalTable).equalTo(record::getSrcLogicalTable)
                .set(srcLogicalKeyCol).equalTo(record::getSrcLogicalKeyCol)
                .set(srcPhyDb).equalTo(record::getSrcPhyDb)
                .set(srcPhyTable).equalTo(record::getSrcPhyTable)
                .set(srcPhyKeyCol).equalTo(record::getSrcPhyKeyCol)
                .set(srcKeyColVal).equalTo(record::getSrcKeyColVal)
                .set(dstLogicalDb).equalTo(record::getDstLogicalDb)
                .set(dstLogicalTable).equalTo(record::getDstLogicalTable)
                .set(dstLogicalKeyCol).equalTo(record::getDstLogicalKeyCol)
                .set(dstKeyColVal).equalTo(record::getDstKeyColVal)
                .set(deleted).equalTo(record::getDeleted)
                .set(createTime).equalTo(record::getCreateTime)
                .set(updateTime).equalTo(record::getUpdateTime)
                .set(diff).equalTo(record::getDiff);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.512+08:00", comments="Source Table: validation_diff")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(ValidationDiff record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
                .set(serviceId).equalToWhenPresent(record::getServiceId)
                .set(taskId).equalToWhenPresent(record::getTaskId)
                .set(validationTaskId).equalToWhenPresent(record::getValidationTaskId)
                .set(type).equalToWhenPresent(record::getType)
                .set(state).equalToWhenPresent(record::getState)
                .set(srcLogicalDb).equalToWhenPresent(record::getSrcLogicalDb)
                .set(srcLogicalTable).equalToWhenPresent(record::getSrcLogicalTable)
                .set(srcLogicalKeyCol).equalToWhenPresent(record::getSrcLogicalKeyCol)
                .set(srcPhyDb).equalToWhenPresent(record::getSrcPhyDb)
                .set(srcPhyTable).equalToWhenPresent(record::getSrcPhyTable)
                .set(srcPhyKeyCol).equalToWhenPresent(record::getSrcPhyKeyCol)
                .set(srcKeyColVal).equalToWhenPresent(record::getSrcKeyColVal)
                .set(dstLogicalDb).equalToWhenPresent(record::getDstLogicalDb)
                .set(dstLogicalTable).equalToWhenPresent(record::getDstLogicalTable)
                .set(dstLogicalKeyCol).equalToWhenPresent(record::getDstLogicalKeyCol)
                .set(dstKeyColVal).equalToWhenPresent(record::getDstKeyColVal)
                .set(deleted).equalToWhenPresent(record::getDeleted)
                .set(createTime).equalToWhenPresent(record::getCreateTime)
                .set(updateTime).equalToWhenPresent(record::getUpdateTime)
                .set(diff).equalToWhenPresent(record::getDiff);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.513+08:00", comments="Source Table: validation_diff")
    default int updateByPrimaryKey(ValidationDiff record) {
        return update(c ->
            c.set(stateMachineId).equalTo(record::getStateMachineId)
            .set(serviceId).equalTo(record::getServiceId)
            .set(taskId).equalTo(record::getTaskId)
            .set(validationTaskId).equalTo(record::getValidationTaskId)
            .set(type).equalTo(record::getType)
            .set(state).equalTo(record::getState)
            .set(srcLogicalDb).equalTo(record::getSrcLogicalDb)
            .set(srcLogicalTable).equalTo(record::getSrcLogicalTable)
            .set(srcLogicalKeyCol).equalTo(record::getSrcLogicalKeyCol)
            .set(srcPhyDb).equalTo(record::getSrcPhyDb)
            .set(srcPhyTable).equalTo(record::getSrcPhyTable)
            .set(srcPhyKeyCol).equalTo(record::getSrcPhyKeyCol)
            .set(srcKeyColVal).equalTo(record::getSrcKeyColVal)
            .set(dstLogicalDb).equalTo(record::getDstLogicalDb)
            .set(dstLogicalTable).equalTo(record::getDstLogicalTable)
            .set(dstLogicalKeyCol).equalTo(record::getDstLogicalKeyCol)
            .set(dstKeyColVal).equalTo(record::getDstKeyColVal)
            .set(deleted).equalTo(record::getDeleted)
            .set(createTime).equalTo(record::getCreateTime)
            .set(updateTime).equalTo(record::getUpdateTime)
            .set(diff).equalTo(record::getDiff)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-12-28T11:07:16.514+08:00", comments="Source Table: validation_diff")
    default int updateByPrimaryKeySelective(ValidationDiff record) {
        return update(c ->
            c.set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
            .set(serviceId).equalToWhenPresent(record::getServiceId)
            .set(taskId).equalToWhenPresent(record::getTaskId)
            .set(validationTaskId).equalToWhenPresent(record::getValidationTaskId)
            .set(type).equalToWhenPresent(record::getType)
            .set(state).equalToWhenPresent(record::getState)
            .set(srcLogicalDb).equalToWhenPresent(record::getSrcLogicalDb)
            .set(srcLogicalTable).equalToWhenPresent(record::getSrcLogicalTable)
            .set(srcLogicalKeyCol).equalToWhenPresent(record::getSrcLogicalKeyCol)
            .set(srcPhyDb).equalToWhenPresent(record::getSrcPhyDb)
            .set(srcPhyTable).equalToWhenPresent(record::getSrcPhyTable)
            .set(srcPhyKeyCol).equalToWhenPresent(record::getSrcPhyKeyCol)
            .set(srcKeyColVal).equalToWhenPresent(record::getSrcKeyColVal)
            .set(dstLogicalDb).equalToWhenPresent(record::getDstLogicalDb)
            .set(dstLogicalTable).equalToWhenPresent(record::getDstLogicalTable)
            .set(dstLogicalKeyCol).equalToWhenPresent(record::getDstLogicalKeyCol)
            .set(dstKeyColVal).equalToWhenPresent(record::getDstKeyColVal)
            .set(deleted).equalToWhenPresent(record::getDeleted)
            .set(createTime).equalToWhenPresent(record::getCreateTime)
            .set(updateTime).equalToWhenPresent(record::getUpdateTime)
            .set(diff).equalToWhenPresent(record::getDiff)
            .where(id, isEqualTo(record::getId))
        );
    }
}