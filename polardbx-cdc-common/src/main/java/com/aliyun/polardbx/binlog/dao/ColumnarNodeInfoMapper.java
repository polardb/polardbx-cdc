/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.ColumnarNodeInfoDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.ColumnarNodeInfo;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Generated;

import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
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
public interface ColumnarNodeInfoMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, clusterId, containerId, ip, daemonPort, availablePorts, status, core, mem, gmtHeartbeat, latestCursor, role, clusterType, groupName);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    @SelectKey(statement="SELECT LAST_INSERT_ID()", keyProperty="record.id", before=false, resultType=Long.class)
    int insert(InsertStatementProvider<ColumnarNodeInfo> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="container_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ip", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="daemon_port", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="available_ports", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="core", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="mem", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="gmt_heartbeat", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="latest_cursor", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="role", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cluster_type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="group_name", javaType=String.class, jdbcType=JdbcType.VARCHAR)
    })
    Optional<ColumnarNodeInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="container_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ip", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="daemon_port", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="available_ports", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="core", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="mem", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="gmt_heartbeat", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="latest_cursor", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="role", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cluster_type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="group_name", javaType=String.class, jdbcType=JdbcType.VARCHAR)
    })
    List<ColumnarNodeInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, columnarNodeInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, columnarNodeInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default int insert(ColumnarNodeInfo record) {
        return MyBatis3Utils.insert(this::insert, record, columnarNodeInfo, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
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
            .map(groupName).toProperty("groupName")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default int insertSelective(ColumnarNodeInfo record) {
        return MyBatis3Utils.insert(this::insert, record, columnarNodeInfo, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
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
            .map(groupName).toPropertyWhenPresent("groupName", record::getGroupName)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default Optional<ColumnarNodeInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, columnarNodeInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default List<ColumnarNodeInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, columnarNodeInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default List<ColumnarNodeInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, columnarNodeInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default Optional<ColumnarNodeInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, columnarNodeInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    static UpdateDSL<UpdateModel> updateAllColumns(ColumnarNodeInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
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
                .set(groupName).equalTo(record::getGroupName);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(ColumnarNodeInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
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
                .set(groupName).equalToWhenPresent(record::getGroupName);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default int updateByPrimaryKey(ColumnarNodeInfo record) {
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
            .set(groupName).equalTo(record::getGroupName)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    default int updateByPrimaryKeySelective(ColumnarNodeInfo record) {
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
            .set(groupName).equalToWhenPresent(record::getGroupName)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Update(
        "update columnar_node_info set gmt_heartbeat = now(), role = #{role} , cluster_type = #{clusterType} where id = #{id}")
    int updateNodeHeartbeat(@Param("id") Long id, @Param("role") String role,
                            @Param("clusterType") String clusterType);

    @Select(
        "select * from columnar_node_info where cluster_id = #{clusterId} and status = 0 and timestampdiff(MICROSECOND, gmt_heartbeat, now())/1000 <= #{heartbeatTimeoutMs} order by id"
    )
    List<ColumnarNodeInfo> getAliveNodes(@Param("clusterId") String clusterId,
                                 @Param("heartbeatTimeoutMs") int heartbeatTimeoutMs);

    @Select(
        "select * from columnar_node_info where cluster_id = #{clusterId} and status = 0 and timestampdiff(MICROSECOND, gmt_heartbeat, now())/1000 > #{heartbeatTimeoutMs} order by id"
    )
    List<ColumnarNodeInfo> getDeadNodes(@Param("clusterId") String clusterId,
                                @Param("heartbeatTimeoutMs") int heartbeatTimeoutMs);
}