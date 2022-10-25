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

import static com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.NodeInfo;

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
public interface NodeInfoMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.63+08:00",
        comments = "Source Table: binlog_node_info")
    BasicColumn[] selectList = BasicColumn
        .columnList(id, gmtCreated, gmtModified, clusterId, containerId, ip, daemonPort, availablePorts, status, core,
            mem, gmtHeartbeat, latestCursor, role, clusterType);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.616+08:00",
        comments = "Source Table: binlog_node_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.617+08:00",
        comments = "Source Table: binlog_node_info")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.617+08:00",
        comments = "Source Table: binlog_node_info")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<NodeInfo> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.619+08:00",
        comments = "Source Table: binlog_node_info")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<NodeInfo> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.619+08:00",
        comments = "Source Table: binlog_node_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "cluster_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "container_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "ip", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "daemon_port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "available_ports", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "core", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "mem", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "gmt_heartbeat", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "latest_cursor", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "role", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cluster_type", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    Optional<NodeInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.622+08:00",
        comments = "Source Table: binlog_node_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "cluster_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "container_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "ip", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "daemon_port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "available_ports", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "core", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "mem", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "gmt_heartbeat", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "latest_cursor", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "role", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cluster_type", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    List<NodeInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.623+08:00",
        comments = "Source Table: binlog_node_info")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.624+08:00",
        comments = "Source Table: binlog_node_info")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, nodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.624+08:00",
        comments = "Source Table: binlog_node_info")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, nodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.624+08:00",
        comments = "Source Table: binlog_node_info")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.625+08:00",
        comments = "Source Table: binlog_node_info")
    default int insert(NodeInfo record) {
        return MyBatis3Utils.insert(this::insert, record, nodeInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(clusterId).toProperty("clusterId")
                .map(containerId).toProperty("containerId")
                .map(ip).toProperty("ip")
                .map(daemonPort).toProperty("daemonPort")
                .map(availablePorts).toProperty("availablePorts")
                .map(status).toProperty("status")
                .map(core).toProperty("core")
                .map(mem).toProperty("mem")
                .map(gmtHeartbeat).toProperty("gmtHeartbeat")
                .map(latestCursor).toProperty("latestCursor")
                .map(role).toProperty("role")
                .map(clusterType).toProperty("clusterType")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.628+08:00",
        comments = "Source Table: binlog_node_info")
    default int insertMultiple(Collection<NodeInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, nodeInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(clusterId).toProperty("clusterId")
                .map(containerId).toProperty("containerId")
                .map(ip).toProperty("ip")
                .map(daemonPort).toProperty("daemonPort")
                .map(availablePorts).toProperty("availablePorts")
                .map(status).toProperty("status")
                .map(core).toProperty("core")
                .map(mem).toProperty("mem")
                .map(gmtHeartbeat).toProperty("gmtHeartbeat")
                .map(latestCursor).toProperty("latestCursor")
                .map(role).toProperty("role")
                .map(clusterType).toProperty("clusterType")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.628+08:00",
        comments = "Source Table: binlog_node_info")
    default int insertSelective(NodeInfo record) {
        return MyBatis3Utils.insert(this::insert, record, nodeInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
                .map(containerId).toPropertyWhenPresent("containerId", record::getContainerId)
                .map(ip).toPropertyWhenPresent("ip", record::getIp)
                .map(daemonPort).toPropertyWhenPresent("daemonPort", record::getDaemonPort)
                .map(availablePorts).toPropertyWhenPresent("availablePorts", record::getAvailablePorts)
                .map(status).toPropertyWhenPresent("status", record::getStatus)
                .map(core).toPropertyWhenPresent("core", record::getCore)
                .map(mem).toPropertyWhenPresent("mem", record::getMem)
                .map(gmtHeartbeat).toPropertyWhenPresent("gmtHeartbeat", record::getGmtHeartbeat)
                .map(latestCursor).toPropertyWhenPresent("latestCursor", record::getLatestCursor)
                .map(role).toPropertyWhenPresent("role", record::getRole)
                .map(clusterType).toPropertyWhenPresent("clusterType", record::getClusterType)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.631+08:00",
        comments = "Source Table: binlog_node_info")
    default Optional<NodeInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, nodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.632+08:00",
        comments = "Source Table: binlog_node_info")
    default List<NodeInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, nodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.632+08:00",
        comments = "Source Table: binlog_node_info")
    default List<NodeInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, nodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.632+08:00",
        comments = "Source Table: binlog_node_info")
    default Optional<NodeInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.633+08:00",
        comments = "Source Table: binlog_node_info")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, nodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.634+08:00",
        comments = "Source Table: binlog_node_info")
    static UpdateDSL<UpdateModel> updateAllColumns(NodeInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(clusterId).equalTo(record::getClusterId)
            .set(containerId).equalTo(record::getContainerId)
            .set(ip).equalTo(record::getIp)
            .set(daemonPort).equalTo(record::getDaemonPort)
            .set(availablePorts).equalTo(record::getAvailablePorts)
            .set(status).equalTo(record::getStatus)
            .set(core).equalTo(record::getCore)
            .set(mem).equalTo(record::getMem)
            .set(gmtHeartbeat).equalTo(record::getGmtHeartbeat)
            .set(latestCursor).equalTo(record::getLatestCursor)
            .set(role).equalTo(record::getRole)
            .set(clusterType).equalTo(record::getClusterType);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.634+08:00",
        comments = "Source Table: binlog_node_info")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(NodeInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .set(containerId).equalToWhenPresent(record::getContainerId)
            .set(ip).equalToWhenPresent(record::getIp)
            .set(daemonPort).equalToWhenPresent(record::getDaemonPort)
            .set(availablePorts).equalToWhenPresent(record::getAvailablePorts)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(core).equalToWhenPresent(record::getCore)
            .set(mem).equalToWhenPresent(record::getMem)
            .set(gmtHeartbeat).equalToWhenPresent(record::getGmtHeartbeat)
            .set(latestCursor).equalToWhenPresent(record::getLatestCursor)
            .set(role).equalToWhenPresent(record::getRole)
            .set(clusterType).equalToWhenPresent(record::getClusterType);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.635+08:00",
        comments = "Source Table: binlog_node_info")
    default int updateByPrimaryKey(NodeInfo record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(clusterId).equalTo(record::getClusterId)
                .set(containerId).equalTo(record::getContainerId)
                .set(ip).equalTo(record::getIp)
                .set(daemonPort).equalTo(record::getDaemonPort)
                .set(availablePorts).equalTo(record::getAvailablePorts)
                .set(status).equalTo(record::getStatus)
                .set(core).equalTo(record::getCore)
                .set(mem).equalTo(record::getMem)
                .set(gmtHeartbeat).equalTo(record::getGmtHeartbeat)
                .set(latestCursor).equalTo(record::getLatestCursor)
                .set(role).equalTo(record::getRole)
                .set(clusterType).equalTo(record::getClusterType)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-18T18:12:42.636+08:00",
        comments = "Source Table: binlog_node_info")
    default int updateByPrimaryKeySelective(NodeInfo record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .set(containerId).equalToWhenPresent(record::getContainerId)
                .set(ip).equalToWhenPresent(record::getIp)
                .set(daemonPort).equalToWhenPresent(record::getDaemonPort)
                .set(availablePorts).equalToWhenPresent(record::getAvailablePorts)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(core).equalToWhenPresent(record::getCore)
                .set(mem).equalToWhenPresent(record::getMem)
                .set(gmtHeartbeat).equalToWhenPresent(record::getGmtHeartbeat)
                .set(latestCursor).equalToWhenPresent(record::getLatestCursor)
                .set(role).equalToWhenPresent(record::getRole)
                .set(clusterType).equalToWhenPresent(record::getClusterType)
                .where(id, isEqualTo(record::getId))
        );
    }
}