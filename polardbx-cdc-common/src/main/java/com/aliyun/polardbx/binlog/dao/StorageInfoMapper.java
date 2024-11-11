/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
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
public interface StorageInfoMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.076+08:00", comments="Source Table: storage_info")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, instId, storageInstId, storageMasterInstId, ip, port, xport, user, storageType, instKind, status, regionId, azoneId, idcId, maxConn, cpuCore, memSize, isVip, passwdEnc, extras);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.06+08:00", comments="Source Table: storage_info")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.062+08:00", comments="Source Table: storage_info")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.062+08:00", comments="Source Table: storage_info")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<StorageInfo> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.063+08:00", comments="Source Table: storage_info")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<StorageInfo> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.064+08:00", comments="Source Table: storage_info")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_master_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ip", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="port", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="xport", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="user", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_type", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="inst_kind", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="region_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="azone_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="idc_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="max_conn", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="cpu_core", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="mem_size", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="is_vip", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="passwd_enc", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="extras", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<StorageInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.067+08:00", comments="Source Table: storage_info")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_master_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ip", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="port", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="xport", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="user", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_type", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="inst_kind", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="region_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="azone_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="idc_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="max_conn", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="cpu_core", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="mem_size", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="is_vip", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="passwd_enc", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="extras", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<StorageInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.067+08:00", comments="Source Table: storage_info")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.068+08:00", comments="Source Table: storage_info")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, storageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.069+08:00", comments="Source Table: storage_info")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, storageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.07+08:00", comments="Source Table: storage_info")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.07+08:00", comments="Source Table: storage_info")
    default int insert(StorageInfo record) {
        return MyBatis3Utils.insert(this::insert, record, storageInfo, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(instId).toProperty("instId")
            .map(storageInstId).toProperty("storageInstId")
            .map(storageMasterInstId).toProperty("storageMasterInstId")
            .map(ip).toProperty("ip")
            .map(port).toProperty("port")
            .map(xport).toProperty("xport")
            .map(user).toProperty("user")
            .map(storageType).toProperty("storageType")
            .map(instKind).toProperty("instKind")
            .map(status).toProperty("status")
            .map(regionId).toProperty("regionId")
            .map(azoneId).toProperty("azoneId")
            .map(idcId).toProperty("idcId")
            .map(maxConn).toProperty("maxConn")
            .map(cpuCore).toProperty("cpuCore")
            .map(memSize).toProperty("memSize")
            .map(isVip).toProperty("isVip")
            .map(passwdEnc).toProperty("passwdEnc")
            .map(extras).toProperty("extras")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.073+08:00", comments="Source Table: storage_info")
    default int insertMultiple(Collection<StorageInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, storageInfo, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(instId).toProperty("instId")
            .map(storageInstId).toProperty("storageInstId")
            .map(storageMasterInstId).toProperty("storageMasterInstId")
            .map(ip).toProperty("ip")
            .map(port).toProperty("port")
            .map(xport).toProperty("xport")
            .map(user).toProperty("user")
            .map(storageType).toProperty("storageType")
            .map(instKind).toProperty("instKind")
            .map(status).toProperty("status")
            .map(regionId).toProperty("regionId")
            .map(azoneId).toProperty("azoneId")
            .map(idcId).toProperty("idcId")
            .map(maxConn).toProperty("maxConn")
            .map(cpuCore).toProperty("cpuCore")
            .map(memSize).toProperty("memSize")
            .map(isVip).toProperty("isVip")
            .map(passwdEnc).toProperty("passwdEnc")
            .map(extras).toProperty("extras")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.074+08:00", comments="Source Table: storage_info")
    default int insertSelective(StorageInfo record) {
        return MyBatis3Utils.insert(this::insert, record, storageInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(instId).toPropertyWhenPresent("instId", record::getInstId)
            .map(storageInstId).toPropertyWhenPresent("storageInstId", record::getStorageInstId)
            .map(storageMasterInstId).toPropertyWhenPresent("storageMasterInstId", record::getStorageMasterInstId)
            .map(ip).toPropertyWhenPresent("ip", record::getIp)
            .map(port).toPropertyWhenPresent("port", record::getPort)
            .map(xport).toPropertyWhenPresent("xport", record::getXport)
            .map(user).toPropertyWhenPresent("user", record::getUser)
            .map(storageType).toPropertyWhenPresent("storageType", record::getStorageType)
            .map(instKind).toPropertyWhenPresent("instKind", record::getInstKind)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(regionId).toPropertyWhenPresent("regionId", record::getRegionId)
            .map(azoneId).toPropertyWhenPresent("azoneId", record::getAzoneId)
            .map(idcId).toPropertyWhenPresent("idcId", record::getIdcId)
            .map(maxConn).toPropertyWhenPresent("maxConn", record::getMaxConn)
            .map(cpuCore).toPropertyWhenPresent("cpuCore", record::getCpuCore)
            .map(memSize).toPropertyWhenPresent("memSize", record::getMemSize)
            .map(isVip).toPropertyWhenPresent("isVip", record::getIsVip)
            .map(passwdEnc).toPropertyWhenPresent("passwdEnc", record::getPasswdEnc)
            .map(extras).toPropertyWhenPresent("extras", record::getExtras)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.078+08:00", comments="Source Table: storage_info")
    default Optional<StorageInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, storageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.078+08:00", comments="Source Table: storage_info")
    default List<StorageInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, storageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.079+08:00", comments="Source Table: storage_info")
    default List<StorageInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, storageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.08+08:00", comments="Source Table: storage_info")
    default Optional<StorageInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.081+08:00", comments="Source Table: storage_info")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, storageInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.081+08:00", comments="Source Table: storage_info")
    static UpdateDSL<UpdateModel> updateAllColumns(StorageInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(instId).equalTo(record::getInstId)
                .set(storageInstId).equalTo(record::getStorageInstId)
                .set(storageMasterInstId).equalTo(record::getStorageMasterInstId)
                .set(ip).equalTo(record::getIp)
                .set(port).equalTo(record::getPort)
                .set(xport).equalTo(record::getXport)
                .set(user).equalTo(record::getUser)
                .set(storageType).equalTo(record::getStorageType)
                .set(instKind).equalTo(record::getInstKind)
                .set(status).equalTo(record::getStatus)
                .set(regionId).equalTo(record::getRegionId)
                .set(azoneId).equalTo(record::getAzoneId)
                .set(idcId).equalTo(record::getIdcId)
                .set(maxConn).equalTo(record::getMaxConn)
                .set(cpuCore).equalTo(record::getCpuCore)
                .set(memSize).equalTo(record::getMemSize)
                .set(isVip).equalTo(record::getIsVip)
                .set(passwdEnc).equalTo(record::getPasswdEnc)
                .set(extras).equalTo(record::getExtras);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.082+08:00", comments="Source Table: storage_info")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(StorageInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(instId).equalToWhenPresent(record::getInstId)
                .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
                .set(storageMasterInstId).equalToWhenPresent(record::getStorageMasterInstId)
                .set(ip).equalToWhenPresent(record::getIp)
                .set(port).equalToWhenPresent(record::getPort)
                .set(xport).equalToWhenPresent(record::getXport)
                .set(user).equalToWhenPresent(record::getUser)
                .set(storageType).equalToWhenPresent(record::getStorageType)
                .set(instKind).equalToWhenPresent(record::getInstKind)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(regionId).equalToWhenPresent(record::getRegionId)
                .set(azoneId).equalToWhenPresent(record::getAzoneId)
                .set(idcId).equalToWhenPresent(record::getIdcId)
                .set(maxConn).equalToWhenPresent(record::getMaxConn)
                .set(cpuCore).equalToWhenPresent(record::getCpuCore)
                .set(memSize).equalToWhenPresent(record::getMemSize)
                .set(isVip).equalToWhenPresent(record::getIsVip)
                .set(passwdEnc).equalToWhenPresent(record::getPasswdEnc)
                .set(extras).equalToWhenPresent(record::getExtras);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.084+08:00", comments="Source Table: storage_info")
    default int updateByPrimaryKey(StorageInfo record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(instId).equalTo(record::getInstId)
            .set(storageInstId).equalTo(record::getStorageInstId)
            .set(storageMasterInstId).equalTo(record::getStorageMasterInstId)
            .set(ip).equalTo(record::getIp)
            .set(port).equalTo(record::getPort)
            .set(xport).equalTo(record::getXport)
            .set(user).equalTo(record::getUser)
            .set(storageType).equalTo(record::getStorageType)
            .set(instKind).equalTo(record::getInstKind)
            .set(status).equalTo(record::getStatus)
            .set(regionId).equalTo(record::getRegionId)
            .set(azoneId).equalTo(record::getAzoneId)
            .set(idcId).equalTo(record::getIdcId)
            .set(maxConn).equalTo(record::getMaxConn)
            .set(cpuCore).equalTo(record::getCpuCore)
            .set(memSize).equalTo(record::getMemSize)
            .set(isVip).equalTo(record::getIsVip)
            .set(passwdEnc).equalTo(record::getPasswdEnc)
            .set(extras).equalTo(record::getExtras)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-02T09:30:24.085+08:00", comments="Source Table: storage_info")
    default int updateByPrimaryKeySelective(StorageInfo record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(instId).equalToWhenPresent(record::getInstId)
            .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
            .set(storageMasterInstId).equalToWhenPresent(record::getStorageMasterInstId)
            .set(ip).equalToWhenPresent(record::getIp)
            .set(port).equalToWhenPresent(record::getPort)
            .set(xport).equalToWhenPresent(record::getXport)
            .set(user).equalToWhenPresent(record::getUser)
            .set(storageType).equalToWhenPresent(record::getStorageType)
            .set(instKind).equalToWhenPresent(record::getInstKind)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(regionId).equalToWhenPresent(record::getRegionId)
            .set(azoneId).equalToWhenPresent(record::getAzoneId)
            .set(idcId).equalToWhenPresent(record::getIdcId)
            .set(maxConn).equalToWhenPresent(record::getMaxConn)
            .set(cpuCore).equalToWhenPresent(record::getCpuCore)
            .set(memSize).equalToWhenPresent(record::getMemSize)
            .set(isVip).equalToWhenPresent(record::getIsVip)
            .set(passwdEnc).equalToWhenPresent(record::getPasswdEnc)
            .set(extras).equalToWhenPresent(record::getExtras)
            .where(id, isEqualTo(record::getId))
        );
    }
}