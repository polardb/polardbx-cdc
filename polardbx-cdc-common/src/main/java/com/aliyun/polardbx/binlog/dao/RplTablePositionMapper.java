/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.RplTablePositionDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplTablePosition;

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
public interface RplTablePositionMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.931+08:00",
        comments = "Source Table: rpl_table_position")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, stateMachineId, serviceId, taskId, fullTableName, position);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.925+08:00",
        comments = "Source Table: rpl_table_position")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.925+08:00",
        comments = "Source Table: rpl_table_position")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.925+08:00",
        comments = "Source Table: rpl_table_position")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "record.id", before = false,
        resultType = Long.class)
    int insert(InsertStatementProvider<RplTablePosition> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.928+08:00",
        comments = "Source Table: rpl_table_position")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "state_machine_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "service_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "task_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "full_table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "position", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    Optional<RplTablePosition> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.929+08:00",
        comments = "Source Table: rpl_table_position")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "state_machine_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "service_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "task_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "full_table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "position", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    List<RplTablePosition> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.93+08:00",
        comments = "Source Table: rpl_table_position")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.93+08:00",
        comments = "Source Table: rpl_table_position")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplTablePosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.93+08:00",
        comments = "Source Table: rpl_table_position")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplTablePosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.93+08:00",
        comments = "Source Table: rpl_table_position")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.93+08:00",
        comments = "Source Table: rpl_table_position")
    default int insert(RplTablePosition record) {
        return MyBatis3Utils.insert(this::insert, record, rplTablePosition, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(stateMachineId).toProperty("stateMachineId")
                .map(serviceId).toProperty("serviceId")
                .map(taskId).toProperty("taskId")
                .map(fullTableName).toProperty("fullTableName")
                .map(position).toProperty("position")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.93+08:00",
        comments = "Source Table: rpl_table_position")
    default int insertSelective(RplTablePosition record) {
        return MyBatis3Utils.insert(this::insert, record, rplTablePosition, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(stateMachineId).toPropertyWhenPresent("stateMachineId", record::getStateMachineId)
                .map(serviceId).toPropertyWhenPresent("serviceId", record::getServiceId)
                .map(taskId).toPropertyWhenPresent("taskId", record::getTaskId)
                .map(fullTableName).toPropertyWhenPresent("fullTableName", record::getFullTableName)
                .map(position).toPropertyWhenPresent("position", record::getPosition)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.931+08:00",
        comments = "Source Table: rpl_table_position")
    default Optional<RplTablePosition> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplTablePosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.933+08:00",
        comments = "Source Table: rpl_table_position")
    default List<RplTablePosition> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplTablePosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.933+08:00",
        comments = "Source Table: rpl_table_position")
    default List<RplTablePosition> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplTablePosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.934+08:00",
        comments = "Source Table: rpl_table_position")
    default Optional<RplTablePosition> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.934+08:00",
        comments = "Source Table: rpl_table_position")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplTablePosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.934+08:00",
        comments = "Source Table: rpl_table_position")
    static UpdateDSL<UpdateModel> updateAllColumns(RplTablePosition record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(stateMachineId).equalTo(record::getStateMachineId)
            .set(serviceId).equalTo(record::getServiceId)
            .set(taskId).equalTo(record::getTaskId)
            .set(fullTableName).equalTo(record::getFullTableName)
            .set(position).equalTo(record::getPosition);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.934+08:00",
        comments = "Source Table: rpl_table_position")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplTablePosition record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
            .set(serviceId).equalToWhenPresent(record::getServiceId)
            .set(taskId).equalToWhenPresent(record::getTaskId)
            .set(fullTableName).equalToWhenPresent(record::getFullTableName)
            .set(position).equalToWhenPresent(record::getPosition);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.934+08:00",
        comments = "Source Table: rpl_table_position")
    default int updateByPrimaryKey(RplTablePosition record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(stateMachineId).equalTo(record::getStateMachineId)
                .set(serviceId).equalTo(record::getServiceId)
                .set(taskId).equalTo(record::getTaskId)
                .set(fullTableName).equalTo(record::getFullTableName)
                .set(position).equalTo(record::getPosition)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.935+08:00",
        comments = "Source Table: rpl_table_position")
    default int updateByPrimaryKeySelective(RplTablePosition record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
                .set(serviceId).equalToWhenPresent(record::getServiceId)
                .set(taskId).equalToWhenPresent(record::getTaskId)
                .set(fullTableName).equalToWhenPresent(record::getFullTableName)
                .set(position).equalToWhenPresent(record::getPosition)
                .where(id, isEqualTo(record::getId))
        );
    }
}