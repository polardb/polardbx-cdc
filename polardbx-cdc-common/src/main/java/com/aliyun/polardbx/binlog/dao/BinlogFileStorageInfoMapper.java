/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.BinlogFileStorageInfoDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.BinlogFileStorageInfo;
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
public interface BinlogFileStorageInfoMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.926+08:00", comments="Source Table: binlog_file_storage_info")
    BasicColumn[] selectList = BasicColumn.columnList(id, instId, engine, externalEndpoint, internalClassicEndpoint, internalVpcEndpoint, fileUri, accessKeyId, accessKeySecret, priority, regionId, azoneId, cachePolicy, deletePolicy, status, gmtCreated, gmtModified, endpointOrdinal, fileSystemConf);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.922+08:00", comments="Source Table: binlog_file_storage_info")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.922+08:00", comments="Source Table: binlog_file_storage_info")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.923+08:00", comments="Source Table: binlog_file_storage_info")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<BinlogFileStorageInfo> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.923+08:00", comments="Source Table: binlog_file_storage_info")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogFileStorageInfo> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.923+08:00", comments="Source Table: binlog_file_storage_info")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="engine", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="external_endpoint", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="internal_classic_endpoint", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="internal_vpc_endpoint", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="file_uri", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="access_key_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="access_key_secret", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="priority", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="region_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="azone_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cache_policy", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="delete_policy", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="status", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="endpoint_ordinal", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="file_system_conf", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<BinlogFileStorageInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.924+08:00", comments="Source Table: binlog_file_storage_info")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="engine", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="external_endpoint", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="internal_classic_endpoint", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="internal_vpc_endpoint", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="file_uri", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="access_key_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="access_key_secret", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="priority", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="region_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="azone_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cache_policy", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="delete_policy", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="status", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="endpoint_ordinal", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="file_system_conf", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<BinlogFileStorageInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.924+08:00", comments="Source Table: binlog_file_storage_info")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.924+08:00", comments="Source Table: binlog_file_storage_info")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogFileStorageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.924+08:00", comments="Source Table: binlog_file_storage_info")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogFileStorageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.924+08:00", comments="Source Table: binlog_file_storage_info")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.925+08:00", comments="Source Table: binlog_file_storage_info")
    default int insert(BinlogFileStorageInfo record) {
        return MyBatis3Utils.insert(this::insert, record, binlogFileStorageInfo, c ->
            c.map(id).toProperty("id")
            .map(instId).toProperty("instId")
            .map(engine).toProperty("engine")
            .map(externalEndpoint).toProperty("externalEndpoint")
            .map(internalClassicEndpoint).toProperty("internalClassicEndpoint")
            .map(internalVpcEndpoint).toProperty("internalVpcEndpoint")
            .map(fileUri).toProperty("fileUri")
            .map(accessKeyId).toProperty("accessKeyId")
            .map(accessKeySecret).toProperty("accessKeySecret")
            .map(priority).toProperty("priority")
            .map(regionId).toProperty("regionId")
            .map(azoneId).toProperty("azoneId")
            .map(cachePolicy).toProperty("cachePolicy")
            .map(deletePolicy).toProperty("deletePolicy")
            .map(status).toProperty("status")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(endpointOrdinal).toProperty("endpointOrdinal")
            .map(fileSystemConf).toProperty("fileSystemConf")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.925+08:00", comments="Source Table: binlog_file_storage_info")
    default int insertMultiple(Collection<BinlogFileStorageInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogFileStorageInfo, c ->
            c.map(id).toProperty("id")
            .map(instId).toProperty("instId")
            .map(engine).toProperty("engine")
            .map(externalEndpoint).toProperty("externalEndpoint")
            .map(internalClassicEndpoint).toProperty("internalClassicEndpoint")
            .map(internalVpcEndpoint).toProperty("internalVpcEndpoint")
            .map(fileUri).toProperty("fileUri")
            .map(accessKeyId).toProperty("accessKeyId")
            .map(accessKeySecret).toProperty("accessKeySecret")
            .map(priority).toProperty("priority")
            .map(regionId).toProperty("regionId")
            .map(azoneId).toProperty("azoneId")
            .map(cachePolicy).toProperty("cachePolicy")
            .map(deletePolicy).toProperty("deletePolicy")
            .map(status).toProperty("status")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(endpointOrdinal).toProperty("endpointOrdinal")
            .map(fileSystemConf).toProperty("fileSystemConf")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.925+08:00", comments="Source Table: binlog_file_storage_info")
    default int insertSelective(BinlogFileStorageInfo record) {
        return MyBatis3Utils.insert(this::insert, record, binlogFileStorageInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(instId).toPropertyWhenPresent("instId", record::getInstId)
            .map(engine).toPropertyWhenPresent("engine", record::getEngine)
            .map(externalEndpoint).toPropertyWhenPresent("externalEndpoint", record::getExternalEndpoint)
            .map(internalClassicEndpoint).toPropertyWhenPresent("internalClassicEndpoint", record::getInternalClassicEndpoint)
            .map(internalVpcEndpoint).toPropertyWhenPresent("internalVpcEndpoint", record::getInternalVpcEndpoint)
            .map(fileUri).toPropertyWhenPresent("fileUri", record::getFileUri)
            .map(accessKeyId).toPropertyWhenPresent("accessKeyId", record::getAccessKeyId)
            .map(accessKeySecret).toPropertyWhenPresent("accessKeySecret", record::getAccessKeySecret)
            .map(priority).toPropertyWhenPresent("priority", record::getPriority)
            .map(regionId).toPropertyWhenPresent("regionId", record::getRegionId)
            .map(azoneId).toPropertyWhenPresent("azoneId", record::getAzoneId)
            .map(cachePolicy).toPropertyWhenPresent("cachePolicy", record::getCachePolicy)
            .map(deletePolicy).toPropertyWhenPresent("deletePolicy", record::getDeletePolicy)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(endpointOrdinal).toPropertyWhenPresent("endpointOrdinal", record::getEndpointOrdinal)
            .map(fileSystemConf).toPropertyWhenPresent("fileSystemConf", record::getFileSystemConf)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.926+08:00", comments="Source Table: binlog_file_storage_info")
    default Optional<BinlogFileStorageInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogFileStorageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.926+08:00", comments="Source Table: binlog_file_storage_info")
    default List<BinlogFileStorageInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogFileStorageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.926+08:00", comments="Source Table: binlog_file_storage_info")
    default List<BinlogFileStorageInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogFileStorageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.926+08:00", comments="Source Table: binlog_file_storage_info")
    default Optional<BinlogFileStorageInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.927+08:00", comments="Source Table: binlog_file_storage_info")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogFileStorageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.927+08:00", comments="Source Table: binlog_file_storage_info")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogFileStorageInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(instId).equalTo(record::getInstId)
                .set(engine).equalTo(record::getEngine)
                .set(externalEndpoint).equalTo(record::getExternalEndpoint)
                .set(internalClassicEndpoint).equalTo(record::getInternalClassicEndpoint)
                .set(internalVpcEndpoint).equalTo(record::getInternalVpcEndpoint)
                .set(fileUri).equalTo(record::getFileUri)
                .set(accessKeyId).equalTo(record::getAccessKeyId)
                .set(accessKeySecret).equalTo(record::getAccessKeySecret)
                .set(priority).equalTo(record::getPriority)
                .set(regionId).equalTo(record::getRegionId)
                .set(azoneId).equalTo(record::getAzoneId)
                .set(cachePolicy).equalTo(record::getCachePolicy)
                .set(deletePolicy).equalTo(record::getDeletePolicy)
                .set(status).equalTo(record::getStatus)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(endpointOrdinal).equalTo(record::getEndpointOrdinal)
                .set(fileSystemConf).equalTo(record::getFileSystemConf);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.927+08:00", comments="Source Table: binlog_file_storage_info")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogFileStorageInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(instId).equalToWhenPresent(record::getInstId)
                .set(engine).equalToWhenPresent(record::getEngine)
                .set(externalEndpoint).equalToWhenPresent(record::getExternalEndpoint)
                .set(internalClassicEndpoint).equalToWhenPresent(record::getInternalClassicEndpoint)
                .set(internalVpcEndpoint).equalToWhenPresent(record::getInternalVpcEndpoint)
                .set(fileUri).equalToWhenPresent(record::getFileUri)
                .set(accessKeyId).equalToWhenPresent(record::getAccessKeyId)
                .set(accessKeySecret).equalToWhenPresent(record::getAccessKeySecret)
                .set(priority).equalToWhenPresent(record::getPriority)
                .set(regionId).equalToWhenPresent(record::getRegionId)
                .set(azoneId).equalToWhenPresent(record::getAzoneId)
                .set(cachePolicy).equalToWhenPresent(record::getCachePolicy)
                .set(deletePolicy).equalToWhenPresent(record::getDeletePolicy)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(endpointOrdinal).equalToWhenPresent(record::getEndpointOrdinal)
                .set(fileSystemConf).equalToWhenPresent(record::getFileSystemConf);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.927+08:00", comments="Source Table: binlog_file_storage_info")
    default int updateByPrimaryKey(BinlogFileStorageInfo record) {
        return update(c ->
            c.set(instId).equalTo(record::getInstId)
            .set(engine).equalTo(record::getEngine)
            .set(externalEndpoint).equalTo(record::getExternalEndpoint)
            .set(internalClassicEndpoint).equalTo(record::getInternalClassicEndpoint)
            .set(internalVpcEndpoint).equalTo(record::getInternalVpcEndpoint)
            .set(fileUri).equalTo(record::getFileUri)
            .set(accessKeyId).equalTo(record::getAccessKeyId)
            .set(accessKeySecret).equalTo(record::getAccessKeySecret)
            .set(priority).equalTo(record::getPriority)
            .set(regionId).equalTo(record::getRegionId)
            .set(azoneId).equalTo(record::getAzoneId)
            .set(cachePolicy).equalTo(record::getCachePolicy)
            .set(deletePolicy).equalTo(record::getDeletePolicy)
            .set(status).equalTo(record::getStatus)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(endpointOrdinal).equalTo(record::getEndpointOrdinal)
            .set(fileSystemConf).equalTo(record::getFileSystemConf)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.928+08:00", comments="Source Table: binlog_file_storage_info")
    default int updateByPrimaryKeySelective(BinlogFileStorageInfo record) {
        return update(c ->
            c.set(instId).equalToWhenPresent(record::getInstId)
            .set(engine).equalToWhenPresent(record::getEngine)
            .set(externalEndpoint).equalToWhenPresent(record::getExternalEndpoint)
            .set(internalClassicEndpoint).equalToWhenPresent(record::getInternalClassicEndpoint)
            .set(internalVpcEndpoint).equalToWhenPresent(record::getInternalVpcEndpoint)
            .set(fileUri).equalToWhenPresent(record::getFileUri)
            .set(accessKeyId).equalToWhenPresent(record::getAccessKeyId)
            .set(accessKeySecret).equalToWhenPresent(record::getAccessKeySecret)
            .set(priority).equalToWhenPresent(record::getPriority)
            .set(regionId).equalToWhenPresent(record::getRegionId)
            .set(azoneId).equalToWhenPresent(record::getAzoneId)
            .set(cachePolicy).equalToWhenPresent(record::getCachePolicy)
            .set(deletePolicy).equalToWhenPresent(record::getDeletePolicy)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(endpointOrdinal).equalToWhenPresent(record::getEndpointOrdinal)
            .set(fileSystemConf).equalToWhenPresent(record::getFileSystemConf)
            .where(id, isEqualTo(record::getId))
        );
    }
}