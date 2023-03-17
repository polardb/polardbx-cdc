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

import static com.aliyun.polardbx.binlog.dao.RplDbFullPositionDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplDbFullPosition;

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
public interface RplDbFullPositionMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.357+08:00",
        comments = "Source Table: rpl_db_full_position")
    BasicColumn[] selectList = BasicColumn
        .columnList(id, gmtCreated, gmtModified, stateMachineId, serviceId, taskId, fullTableName, totalCount,
            finishedCount, finished, position, endPosition);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.342+08:00",
        comments = "Source Table: rpl_db_full_position")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.343+08:00",
        comments = "Source Table: rpl_db_full_position")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.345+08:00",
        comments = "Source Table: rpl_db_full_position")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "record.id", before = false,
        resultType = Long.class)
    int insert(InsertStatementProvider<RplDbFullPosition> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.351+08:00",
        comments = "Source Table: rpl_db_full_position")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "state_machine_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "service_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "task_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "full_table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "total_count", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "finished_count", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "finished", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "position", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "end_position", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    Optional<RplDbFullPosition> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.352+08:00",
        comments = "Source Table: rpl_db_full_position")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "state_machine_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "service_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "task_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "full_table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "total_count", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "finished_count", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "finished", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "position", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "end_position", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    List<RplDbFullPosition> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.353+08:00",
        comments = "Source Table: rpl_db_full_position")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.353+08:00",
        comments = "Source Table: rpl_db_full_position")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplDbFullPosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.353+08:00",
        comments = "Source Table: rpl_db_full_position")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplDbFullPosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.353+08:00",
        comments = "Source Table: rpl_db_full_position")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.354+08:00",
        comments = "Source Table: rpl_db_full_position")
    default int insert(RplDbFullPosition record) {
        return MyBatis3Utils.insert(this::insert, record, rplDbFullPosition, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(stateMachineId).toProperty("stateMachineId")
                .map(serviceId).toProperty("serviceId")
                .map(taskId).toProperty("taskId")
                .map(fullTableName).toProperty("fullTableName")
                .map(totalCount).toProperty("totalCount")
                .map(finishedCount).toProperty("finishedCount")
                .map(finished).toProperty("finished")
                .map(position).toProperty("position")
                .map(endPosition).toProperty("endPosition")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.356+08:00",
        comments = "Source Table: rpl_db_full_position")
    default int insertSelective(RplDbFullPosition record) {
        return MyBatis3Utils.insert(this::insert, record, rplDbFullPosition, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(stateMachineId).toPropertyWhenPresent("stateMachineId", record::getStateMachineId)
                .map(serviceId).toPropertyWhenPresent("serviceId", record::getServiceId)
                .map(taskId).toPropertyWhenPresent("taskId", record::getTaskId)
                .map(fullTableName).toPropertyWhenPresent("fullTableName", record::getFullTableName)
                .map(totalCount).toPropertyWhenPresent("totalCount", record::getTotalCount)
                .map(finishedCount).toPropertyWhenPresent("finishedCount", record::getFinishedCount)
                .map(finished).toPropertyWhenPresent("finished", record::getFinished)
                .map(position).toPropertyWhenPresent("position", record::getPosition)
                .map(endPosition).toPropertyWhenPresent("endPosition", record::getEndPosition)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.358+08:00",
        comments = "Source Table: rpl_db_full_position")
    default Optional<RplDbFullPosition> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplDbFullPosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.359+08:00",
        comments = "Source Table: rpl_db_full_position")
    default List<RplDbFullPosition> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplDbFullPosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.359+08:00",
        comments = "Source Table: rpl_db_full_position")
    default List<RplDbFullPosition> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplDbFullPosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.36+08:00",
        comments = "Source Table: rpl_db_full_position")
    default Optional<RplDbFullPosition> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.36+08:00",
        comments = "Source Table: rpl_db_full_position")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplDbFullPosition, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.36+08:00",
        comments = "Source Table: rpl_db_full_position")
    static UpdateDSL<UpdateModel> updateAllColumns(RplDbFullPosition record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(stateMachineId).equalTo(record::getStateMachineId)
            .set(serviceId).equalTo(record::getServiceId)
            .set(taskId).equalTo(record::getTaskId)
            .set(fullTableName).equalTo(record::getFullTableName)
            .set(totalCount).equalTo(record::getTotalCount)
            .set(finishedCount).equalTo(record::getFinishedCount)
            .set(finished).equalTo(record::getFinished)
            .set(position).equalTo(record::getPosition)
            .set(endPosition).equalTo(record::getEndPosition);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.361+08:00",
        comments = "Source Table: rpl_db_full_position")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplDbFullPosition record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
            .set(serviceId).equalToWhenPresent(record::getServiceId)
            .set(taskId).equalToWhenPresent(record::getTaskId)
            .set(fullTableName).equalToWhenPresent(record::getFullTableName)
            .set(totalCount).equalToWhenPresent(record::getTotalCount)
            .set(finishedCount).equalToWhenPresent(record::getFinishedCount)
            .set(finished).equalToWhenPresent(record::getFinished)
            .set(position).equalToWhenPresent(record::getPosition)
            .set(endPosition).equalToWhenPresent(record::getEndPosition);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.362+08:00",
        comments = "Source Table: rpl_db_full_position")
    default int updateByPrimaryKey(RplDbFullPosition record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(stateMachineId).equalTo(record::getStateMachineId)
                .set(serviceId).equalTo(record::getServiceId)
                .set(taskId).equalTo(record::getTaskId)
                .set(fullTableName).equalTo(record::getFullTableName)
                .set(totalCount).equalTo(record::getTotalCount)
                .set(finishedCount).equalTo(record::getFinishedCount)
                .set(finished).equalTo(record::getFinished)
                .set(position).equalTo(record::getPosition)
                .set(endPosition).equalTo(record::getEndPosition)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.363+08:00",
        comments = "Source Table: rpl_db_full_position")
    default int updateByPrimaryKeySelective(RplDbFullPosition record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
                .set(serviceId).equalToWhenPresent(record::getServiceId)
                .set(taskId).equalToWhenPresent(record::getTaskId)
                .set(fullTableName).equalToWhenPresent(record::getFullTableName)
                .set(totalCount).equalToWhenPresent(record::getTotalCount)
                .set(finishedCount).equalToWhenPresent(record::getFinishedCount)
                .set(finished).equalToWhenPresent(record::getFinished)
                .set(position).equalToWhenPresent(record::getPosition)
                .set(endPosition).equalToWhenPresent(record::getEndPosition)
                .where(id, isEqualTo(record::getId))
        );
    }
}