/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
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
public interface DumperInfoMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, clusterId, taskName, ip, port, role, status, gmtHeartbeat, containerId, version, polarxInstId);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.403+08:00", comments="Source Table: binlog_dumper_info")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.403+08:00", comments="Source Table: binlog_dumper_info")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.403+08:00", comments="Source Table: binlog_dumper_info")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<DumperInfo> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.403+08:00", comments="Source Table: binlog_dumper_info")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<DumperInfo> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
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
        @Arg(column="version", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="polarx_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR)
    })
    Optional<DumperInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
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
        @Arg(column="version", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="polarx_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR)
    })
    List<DumperInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, dumperInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, dumperInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default int insert(DumperInfo record) {
        return MyBatis3Utils.insert(this::insert, record, dumperInfo, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
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
            .map(polarxInstId).toProperty("polarxInstId")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default int insertMultiple(Collection<DumperInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, dumperInfo, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
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
            .map(polarxInstId).toProperty("polarxInstId")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default int insertSelective(DumperInfo record) {
        return MyBatis3Utils.insert(this::insert, record, dumperInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
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
            .map(polarxInstId).toPropertyWhenPresent("polarxInstId", record::getPolarxInstId)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default Optional<DumperInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, dumperInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default List<DumperInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, dumperInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default List<DumperInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, dumperInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default Optional<DumperInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, dumperInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.404+08:00", comments="Source Table: binlog_dumper_info")
    static UpdateDSL<UpdateModel> updateAllColumns(DumperInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
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
                .set(polarxInstId).equalTo(record::getPolarxInstId);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.405+08:00", comments="Source Table: binlog_dumper_info")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(DumperInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
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
                .set(polarxInstId).equalToWhenPresent(record::getPolarxInstId);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.405+08:00", comments="Source Table: binlog_dumper_info")
    default int updateByPrimaryKey(DumperInfo record) {
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
            .set(polarxInstId).equalTo(record::getPolarxInstId)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.405+08:00", comments="Source Table: binlog_dumper_info")
    default int updateByPrimaryKeySelective(DumperInfo record) {
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
            .set(polarxInstId).equalToWhenPresent(record::getPolarxInstId)
            .where(id, isEqualTo(record::getId))
        );
    }
}