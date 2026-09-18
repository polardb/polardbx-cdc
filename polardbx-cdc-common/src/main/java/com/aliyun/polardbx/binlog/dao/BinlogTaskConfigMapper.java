/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
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
public interface BinlogTaskConfigMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.266416+08:00", comments="Source Table: binlog_task_config")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, clusterId, containerId, taskName, vcpu, mem, ip, port, role, status, version, subVersion, config);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.263357+08:00", comments="Source Table: binlog_task_config")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.263679+08:00", comments="Source Table: binlog_task_config")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.263831+08:00", comments="Source Table: binlog_task_config")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<BinlogTaskConfig> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.264073+08:00", comments="Source Table: binlog_task_config")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogTaskConfig> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.264268+08:00", comments="Source Table: binlog_task_config")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="container_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="vcpu", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="mem", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="ip", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="port", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="role", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="version", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="sub_version", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<BinlogTaskConfig> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.26474+08:00", comments="Source Table: binlog_task_config")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="container_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="task_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="vcpu", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="mem", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="ip", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="port", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="role", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="version", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="sub_version", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="config", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<BinlogTaskConfig> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.264925+08:00", comments="Source Table: binlog_task_config")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.26505+08:00", comments="Source Table: binlog_task_config")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.265167+08:00", comments="Source Table: binlog_task_config")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.265296+08:00", comments="Source Table: binlog_task_config")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.265448+08:00", comments="Source Table: binlog_task_config")
    default int insert(BinlogTaskConfig record) {
        return MyBatis3Utils.insert(this::insert, record, binlogTaskConfig, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(clusterId).toProperty("clusterId")
            .map(containerId).toProperty("containerId")
            .map(taskName).toProperty("taskName")
            .map(vcpu).toProperty("vcpu")
            .map(mem).toProperty("mem")
            .map(ip).toProperty("ip")
            .map(port).toProperty("port")
            .map(role).toProperty("role")
            .map(status).toProperty("status")
            .map(version).toProperty("version")
            .map(subVersion).toProperty("subVersion")
            .map(config).toProperty("config")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.265844+08:00", comments="Source Table: binlog_task_config")
    default int insertMultiple(Collection<BinlogTaskConfig> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogTaskConfig, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(clusterId).toProperty("clusterId")
            .map(containerId).toProperty("containerId")
            .map(taskName).toProperty("taskName")
            .map(vcpu).toProperty("vcpu")
            .map(mem).toProperty("mem")
            .map(ip).toProperty("ip")
            .map(port).toProperty("port")
            .map(role).toProperty("role")
            .map(status).toProperty("status")
            .map(version).toProperty("version")
            .map(subVersion).toProperty("subVersion")
            .map(config).toProperty("config")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.265998+08:00", comments="Source Table: binlog_task_config")
    default int insertSelective(BinlogTaskConfig record) {
        return MyBatis3Utils.insert(this::insert, record, binlogTaskConfig, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
            .map(containerId).toPropertyWhenPresent("containerId", record::getContainerId)
            .map(taskName).toPropertyWhenPresent("taskName", record::getTaskName)
            .map(vcpu).toPropertyWhenPresent("vcpu", record::getVcpu)
            .map(mem).toPropertyWhenPresent("mem", record::getMem)
            .map(ip).toPropertyWhenPresent("ip", record::getIp)
            .map(port).toPropertyWhenPresent("port", record::getPort)
            .map(role).toPropertyWhenPresent("role", record::getRole)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(version).toPropertyWhenPresent("version", record::getVersion)
            .map(subVersion).toPropertyWhenPresent("subVersion", record::getSubVersion)
            .map(config).toPropertyWhenPresent("config", record::getConfig)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.266713+08:00", comments="Source Table: binlog_task_config")
    default Optional<BinlogTaskConfig> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.266844+08:00", comments="Source Table: binlog_task_config")
    default List<BinlogTaskConfig> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.266973+08:00", comments="Source Table: binlog_task_config")
    default List<BinlogTaskConfig> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.267108+08:00", comments="Source Table: binlog_task_config")
    default Optional<BinlogTaskConfig> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.26728+08:00", comments="Source Table: binlog_task_config")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogTaskConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.267417+08:00", comments="Source Table: binlog_task_config")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogTaskConfig record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(clusterId).equalTo(record::getClusterId)
                .set(containerId).equalTo(record::getContainerId)
                .set(taskName).equalTo(record::getTaskName)
                .set(vcpu).equalTo(record::getVcpu)
                .set(mem).equalTo(record::getMem)
                .set(ip).equalTo(record::getIp)
                .set(port).equalTo(record::getPort)
                .set(role).equalTo(record::getRole)
                .set(status).equalTo(record::getStatus)
                .set(version).equalTo(record::getVersion)
                .set(subVersion).equalTo(record::getSubVersion)
                .set(config).equalTo(record::getConfig);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.267593+08:00", comments="Source Table: binlog_task_config")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogTaskConfig record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .set(containerId).equalToWhenPresent(record::getContainerId)
                .set(taskName).equalToWhenPresent(record::getTaskName)
                .set(vcpu).equalToWhenPresent(record::getVcpu)
                .set(mem).equalToWhenPresent(record::getMem)
                .set(ip).equalToWhenPresent(record::getIp)
                .set(port).equalToWhenPresent(record::getPort)
                .set(role).equalToWhenPresent(record::getRole)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(version).equalToWhenPresent(record::getVersion)
                .set(subVersion).equalToWhenPresent(record::getSubVersion)
                .set(config).equalToWhenPresent(record::getConfig);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.267937+08:00", comments="Source Table: binlog_task_config")
    default int updateByPrimaryKey(BinlogTaskConfig record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(clusterId).equalTo(record::getClusterId)
            .set(containerId).equalTo(record::getContainerId)
            .set(taskName).equalTo(record::getTaskName)
            .set(vcpu).equalTo(record::getVcpu)
            .set(mem).equalTo(record::getMem)
            .set(ip).equalTo(record::getIp)
            .set(port).equalTo(record::getPort)
            .set(role).equalTo(record::getRole)
            .set(status).equalTo(record::getStatus)
            .set(version).equalTo(record::getVersion)
            .set(subVersion).equalTo(record::getSubVersion)
            .set(config).equalTo(record::getConfig)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-07-04T11:08:01.268172+08:00", comments="Source Table: binlog_task_config")
    default int updateByPrimaryKeySelective(BinlogTaskConfig record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .set(containerId).equalToWhenPresent(record::getContainerId)
            .set(taskName).equalToWhenPresent(record::getTaskName)
            .set(vcpu).equalToWhenPresent(record::getVcpu)
            .set(mem).equalToWhenPresent(record::getMem)
            .set(ip).equalToWhenPresent(record::getIp)
            .set(port).equalToWhenPresent(record::getPort)
            .set(role).equalToWhenPresent(record::getRole)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(version).equalToWhenPresent(record::getVersion)
            .set(subVersion).equalToWhenPresent(record::getSubVersion)
            .set(config).equalToWhenPresent(record::getConfig)
            .where(id, isEqualTo(record::getId))
        );
    }
}