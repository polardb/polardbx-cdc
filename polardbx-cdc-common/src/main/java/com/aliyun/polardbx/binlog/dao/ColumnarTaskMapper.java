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

import static com.aliyun.polardbx.binlog.dao.ColumnarTaskDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.ColumnarTask;
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
public interface ColumnarTaskMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, clusterId, taskName, ip, port, role, status, gmtHeartbeat, containerId, version);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    @SelectKey(statement="SELECT LAST_INSERT_ID()", keyProperty="record.id", before=false, resultType=Long.class)
    int insert(InsertStatementProvider<ColumnarTask> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ip", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="port", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="role", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="gmt_heartbeat", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="container_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="version", javaType=Long.class, jdbcType=JdbcType.BIGINT)
    })
    Optional<ColumnarTask> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ip", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="port", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="role", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="gmt_heartbeat", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="container_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="version", javaType=Long.class, jdbcType=JdbcType.BIGINT)
    })
    List<ColumnarTask> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, columnarTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, columnarTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default int insert(ColumnarTask record) {
        return MyBatis3Utils.insert(this::insert, record, columnarTask, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(clusterId).toProperty("clusterId")
            .map(taskName).toProperty("taskName")
            .map(ip).toProperty("ip")
            .map(port).toProperty("port")
            .map(role).toProperty("role")
            .map(status).toProperty("status")
            .map(gmtHeartbeat).toProperty("gmtHeartbeat")
            .map(containerId).toProperty("containerId")
            .map(version).toProperty("version")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default int insertSelective(ColumnarTask record) {
        return MyBatis3Utils.insert(this::insert, record, columnarTask, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
            .map(taskName).toPropertyWhenPresent("taskName", record::getTaskName)
            .map(ip).toPropertyWhenPresent("ip", record::getIp)
            .map(port).toPropertyWhenPresent("port", record::getPort)
            .map(role).toPropertyWhenPresent("role", record::getRole)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(gmtHeartbeat).toPropertyWhenPresent("gmtHeartbeat", record::getGmtHeartbeat)
            .map(containerId).toPropertyWhenPresent("containerId", record::getContainerId)
            .map(version).toPropertyWhenPresent("version", record::getVersion)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default Optional<ColumnarTask> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, columnarTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default List<ColumnarTask> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, columnarTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default List<ColumnarTask> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, columnarTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default Optional<ColumnarTask> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, columnarTask, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    static UpdateDSL<UpdateModel> updateAllColumns(ColumnarTask record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(clusterId).equalTo(record::getClusterId)
                .set(taskName).equalTo(record::getTaskName)
                .set(ip).equalTo(record::getIp)
                .set(port).equalTo(record::getPort)
                .set(role).equalTo(record::getRole)
                .set(status).equalTo(record::getStatus)
                .set(gmtHeartbeat).equalTo(record::getGmtHeartbeat)
                .set(containerId).equalTo(record::getContainerId)
                .set(version).equalTo(record::getVersion);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(ColumnarTask record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .set(taskName).equalToWhenPresent(record::getTaskName)
                .set(ip).equalToWhenPresent(record::getIp)
                .set(port).equalToWhenPresent(record::getPort)
                .set(role).equalToWhenPresent(record::getRole)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(gmtHeartbeat).equalToWhenPresent(record::getGmtHeartbeat)
                .set(containerId).equalToWhenPresent(record::getContainerId)
                .set(version).equalToWhenPresent(record::getVersion);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default int updateByPrimaryKey(ColumnarTask record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(clusterId).equalTo(record::getClusterId)
            .set(taskName).equalTo(record::getTaskName)
            .set(ip).equalTo(record::getIp)
            .set(port).equalTo(record::getPort)
            .set(role).equalTo(record::getRole)
            .set(status).equalTo(record::getStatus)
            .set(gmtHeartbeat).equalTo(record::getGmtHeartbeat)
            .set(containerId).equalTo(record::getContainerId)
            .set(version).equalTo(record::getVersion)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source Table: columnar_task")
    default int updateByPrimaryKeySelective(ColumnarTask record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .set(taskName).equalToWhenPresent(record::getTaskName)
            .set(ip).equalToWhenPresent(record::getIp)
            .set(port).equalToWhenPresent(record::getPort)
            .set(role).equalToWhenPresent(record::getRole)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(gmtHeartbeat).equalToWhenPresent(record::getGmtHeartbeat)
            .set(containerId).equalToWhenPresent(record::getContainerId)
            .set(version).equalToWhenPresent(record::getVersion)
            .where(id, isEqualTo(record::getId))
        );
    }
}