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

import static com.aliyun.polardbx.binlog.dao.RplServiceDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplService;

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
public interface RplServiceMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.095+08:00",
        comments = "Source Table: rpl_service")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, stateMachineId, serviceType, stateList, channel, status);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.078+08:00",
        comments = "Source Table: rpl_service")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.079+08:00",
        comments = "Source Table: rpl_service")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.08+08:00",
        comments = "Source Table: rpl_service")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "record.id", before = false,
        resultType = Long.class)
    int insert(InsertStatementProvider<RplService> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.083+08:00",
        comments = "Source Table: rpl_service")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "state_machine_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "service_type", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "state_list", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "channel", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "status", javaType = Integer.class, jdbcType = JdbcType.INTEGER)
    })
    Optional<RplService> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.087+08:00",
        comments = "Source Table: rpl_service")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "state_machine_id", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "service_type", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "state_list", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "channel", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "status", javaType = Integer.class, jdbcType = JdbcType.INTEGER)
    })
    List<RplService> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.087+08:00",
        comments = "Source Table: rpl_service")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.087+08:00",
        comments = "Source Table: rpl_service")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplService, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.088+08:00",
        comments = "Source Table: rpl_service")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplService, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.088+08:00",
        comments = "Source Table: rpl_service")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.089+08:00",
        comments = "Source Table: rpl_service")
    default int insert(RplService record) {
        return MyBatis3Utils.insert(this::insert, record, rplService, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(stateMachineId).toProperty("stateMachineId")
                .map(serviceType).toProperty("serviceType")
                .map(stateList).toProperty("stateList")
                .map(channel).toProperty("channel")
                .map(status).toProperty("status")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.091+08:00",
        comments = "Source Table: rpl_service")
    default int insertSelective(RplService record) {
        return MyBatis3Utils.insert(this::insert, record, rplService, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(stateMachineId).toPropertyWhenPresent("stateMachineId", record::getStateMachineId)
                .map(serviceType).toPropertyWhenPresent("serviceType", record::getServiceType)
                .map(stateList).toPropertyWhenPresent("stateList", record::getStateList)
                .map(channel).toPropertyWhenPresent("channel", record::getChannel)
                .map(status).toPropertyWhenPresent("status", record::getStatus)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.097+08:00",
        comments = "Source Table: rpl_service")
    default Optional<RplService> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplService, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.099+08:00",
        comments = "Source Table: rpl_service")
    default List<RplService> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplService, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.099+08:00",
        comments = "Source Table: rpl_service")
    default List<RplService> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplService, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.099+08:00",
        comments = "Source Table: rpl_service")
    default Optional<RplService> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.1+08:00",
        comments = "Source Table: rpl_service")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplService, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.101+08:00",
        comments = "Source Table: rpl_service")
    static UpdateDSL<UpdateModel> updateAllColumns(RplService record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(stateMachineId).equalTo(record::getStateMachineId)
            .set(serviceType).equalTo(record::getServiceType)
            .set(stateList).equalTo(record::getStateList)
            .set(channel).equalTo(record::getChannel)
            .set(status).equalTo(record::getStatus);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.102+08:00",
        comments = "Source Table: rpl_service")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplService record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
            .set(serviceType).equalToWhenPresent(record::getServiceType)
            .set(stateList).equalToWhenPresent(record::getStateList)
            .set(channel).equalToWhenPresent(record::getChannel)
            .set(status).equalToWhenPresent(record::getStatus);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.103+08:00",
        comments = "Source Table: rpl_service")
    default int updateByPrimaryKey(RplService record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(stateMachineId).equalTo(record::getStateMachineId)
                .set(serviceType).equalTo(record::getServiceType)
                .set(stateList).equalTo(record::getStateList)
                .set(channel).equalTo(record::getChannel)
                .set(status).equalTo(record::getStatus)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-31T15:00:57.106+08:00",
        comments = "Source Table: rpl_service")
    default int updateByPrimaryKeySelective(RplService record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
                .set(serviceType).equalToWhenPresent(record::getServiceType)
                .set(stateList).equalToWhenPresent(record::getStateList)
                .set(channel).equalToWhenPresent(record::getChannel)
                .set(status).equalToWhenPresent(record::getStatus)
                .where(id, isEqualTo(record::getId))
        );
    }
}