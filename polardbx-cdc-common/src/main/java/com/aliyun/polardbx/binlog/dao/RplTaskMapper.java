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

import static com.aliyun.polardbx.binlog.dao.RplTaskDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplTask;
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
public interface RplTaskMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, gmtHeartbeat, status, serviceId, stateMachineId, type, masterHost, masterPort, position, worker, clusterId, extractorConfig, pipelineConfig, applierConfig, lastError, statistic, extra);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.972+08:00", comments="Source Table: rpl_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.972+08:00", comments="Source Table: rpl_task")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.972+08:00", comments="Source Table: rpl_task")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    @SelectKey(statement="SELECT LAST_INSERT_ID()", keyProperty="record.id", before=false, resultType=Long.class)
    int insert(InsertStatementProvider<RplTask> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.973+08:00", comments="Source Table: rpl_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_heartbeat", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="status", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="service_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="state_machine_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="master_host", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="master_port", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="position", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="worker", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="extractor_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="pipeline_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="applier_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="last_error", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="statistic", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="extra", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<RplTask> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_heartbeat", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="status", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="service_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="state_machine_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="master_host", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="master_port", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="position", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="worker", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="extractor_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="pipeline_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="applier_config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="last_error", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="statistic", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="extra", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<RplTask> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    default int insert(RplTask record) {
        return MyBatis3Utils.insert(this::insert, record, rplTask, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(gmtHeartbeat).toProperty("gmtHeartbeat")
            .map(status).toProperty("status")
            .map(serviceId).toProperty("serviceId")
            .map(stateMachineId).toProperty("stateMachineId")
            .map(type).toProperty("type")
            .map(masterHost).toProperty("masterHost")
            .map(masterPort).toProperty("masterPort")
            .map(position).toProperty("position")
            .map(worker).toProperty("worker")
            .map(clusterId).toProperty("clusterId")
            .map(extractorConfig).toProperty("extractorConfig")
            .map(pipelineConfig).toProperty("pipelineConfig")
            .map(applierConfig).toProperty("applierConfig")
            .map(lastError).toProperty("lastError")
            .map(statistic).toProperty("statistic")
            .map(extra).toProperty("extra")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    default int insertSelective(RplTask record) {
        return MyBatis3Utils.insert(this::insert, record, rplTask, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(gmtHeartbeat).toPropertyWhenPresent("gmtHeartbeat", record::getGmtHeartbeat)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(serviceId).toPropertyWhenPresent("serviceId", record::getServiceId)
            .map(stateMachineId).toPropertyWhenPresent("stateMachineId", record::getStateMachineId)
            .map(type).toPropertyWhenPresent("type", record::getType)
            .map(masterHost).toPropertyWhenPresent("masterHost", record::getMasterHost)
            .map(masterPort).toPropertyWhenPresent("masterPort", record::getMasterPort)
            .map(position).toPropertyWhenPresent("position", record::getPosition)
            .map(worker).toPropertyWhenPresent("worker", record::getWorker)
            .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
            .map(extractorConfig).toPropertyWhenPresent("extractorConfig", record::getExtractorConfig)
            .map(pipelineConfig).toPropertyWhenPresent("pipelineConfig", record::getPipelineConfig)
            .map(applierConfig).toPropertyWhenPresent("applierConfig", record::getApplierConfig)
            .map(lastError).toPropertyWhenPresent("lastError", record::getLastError)
            .map(statistic).toPropertyWhenPresent("statistic", record::getStatistic)
            .map(extra).toPropertyWhenPresent("extra", record::getExtra)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    default Optional<RplTask> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    default List<RplTask> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.974+08:00", comments="Source Table: rpl_task")
    default List<RplTask> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.975+08:00", comments="Source Table: rpl_task")
    default Optional<RplTask> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.975+08:00", comments="Source Table: rpl_task")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.975+08:00", comments="Source Table: rpl_task")
    static UpdateDSL<UpdateModel> updateAllColumns(RplTask record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(gmtHeartbeat).equalTo(record::getGmtHeartbeat)
                .set(status).equalTo(record::getStatus)
                .set(serviceId).equalTo(record::getServiceId)
                .set(stateMachineId).equalTo(record::getStateMachineId)
                .set(type).equalTo(record::getType)
                .set(masterHost).equalTo(record::getMasterHost)
                .set(masterPort).equalTo(record::getMasterPort)
                .set(position).equalTo(record::getPosition)
                .set(worker).equalTo(record::getWorker)
                .set(clusterId).equalTo(record::getClusterId)
                .set(extractorConfig).equalTo(record::getExtractorConfig)
                .set(pipelineConfig).equalTo(record::getPipelineConfig)
                .set(applierConfig).equalTo(record::getApplierConfig)
                .set(lastError).equalTo(record::getLastError)
                .set(statistic).equalTo(record::getStatistic)
                .set(extra).equalTo(record::getExtra);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.975+08:00", comments="Source Table: rpl_task")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplTask record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(gmtHeartbeat).equalToWhenPresent(record::getGmtHeartbeat)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(serviceId).equalToWhenPresent(record::getServiceId)
                .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
                .set(type).equalToWhenPresent(record::getType)
                .set(masterHost).equalToWhenPresent(record::getMasterHost)
                .set(masterPort).equalToWhenPresent(record::getMasterPort)
                .set(position).equalToWhenPresent(record::getPosition)
                .set(worker).equalToWhenPresent(record::getWorker)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .set(extractorConfig).equalToWhenPresent(record::getExtractorConfig)
                .set(pipelineConfig).equalToWhenPresent(record::getPipelineConfig)
                .set(applierConfig).equalToWhenPresent(record::getApplierConfig)
                .set(lastError).equalToWhenPresent(record::getLastError)
                .set(statistic).equalToWhenPresent(record::getStatistic)
                .set(extra).equalToWhenPresent(record::getExtra);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.975+08:00", comments="Source Table: rpl_task")
    default int updateByPrimaryKey(RplTask record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(gmtHeartbeat).equalTo(record::getGmtHeartbeat)
            .set(status).equalTo(record::getStatus)
            .set(serviceId).equalTo(record::getServiceId)
            .set(stateMachineId).equalTo(record::getStateMachineId)
            .set(type).equalTo(record::getType)
            .set(masterHost).equalTo(record::getMasterHost)
            .set(masterPort).equalTo(record::getMasterPort)
            .set(position).equalTo(record::getPosition)
            .set(worker).equalTo(record::getWorker)
            .set(clusterId).equalTo(record::getClusterId)
            .set(extractorConfig).equalTo(record::getExtractorConfig)
            .set(pipelineConfig).equalTo(record::getPipelineConfig)
            .set(applierConfig).equalTo(record::getApplierConfig)
            .set(lastError).equalTo(record::getLastError)
            .set(statistic).equalTo(record::getStatistic)
            .set(extra).equalTo(record::getExtra)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.975+08:00", comments="Source Table: rpl_task")
    default int updateByPrimaryKeySelective(RplTask record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(gmtHeartbeat).equalToWhenPresent(record::getGmtHeartbeat)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(serviceId).equalToWhenPresent(record::getServiceId)
            .set(stateMachineId).equalToWhenPresent(record::getStateMachineId)
            .set(type).equalToWhenPresent(record::getType)
            .set(masterHost).equalToWhenPresent(record::getMasterHost)
            .set(masterPort).equalToWhenPresent(record::getMasterPort)
            .set(position).equalToWhenPresent(record::getPosition)
            .set(worker).equalToWhenPresent(record::getWorker)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .set(extractorConfig).equalToWhenPresent(record::getExtractorConfig)
            .set(pipelineConfig).equalToWhenPresent(record::getPipelineConfig)
            .set(applierConfig).equalToWhenPresent(record::getApplierConfig)
            .set(lastError).equalToWhenPresent(record::getLastError)
            .set(statistic).equalToWhenPresent(record::getStatistic)
            .set(extra).equalToWhenPresent(record::getExtra)
            .where(id, isEqualTo(record::getId))
        );
    }
}