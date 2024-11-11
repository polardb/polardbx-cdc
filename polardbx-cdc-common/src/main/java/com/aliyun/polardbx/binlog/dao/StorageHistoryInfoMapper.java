/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
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
public interface StorageHistoryInfoMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.561+08:00", comments="Source Table: binlog_storage_history")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, tso, status, instructionId, clusterId, groupName, storageContent);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.548+08:00", comments="Source Table: binlog_storage_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.55+08:00", comments="Source Table: binlog_storage_history")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.55+08:00", comments="Source Table: binlog_storage_history")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<StorageHistoryInfo> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.551+08:00", comments="Source Table: binlog_storage_history")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<StorageHistoryInfo> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.552+08:00", comments="Source Table: binlog_storage_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="instruction_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="group_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_content", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<StorageHistoryInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.554+08:00", comments="Source Table: binlog_storage_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="instruction_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="group_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_content", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<StorageHistoryInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.555+08:00", comments="Source Table: binlog_storage_history")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.555+08:00", comments="Source Table: binlog_storage_history")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, storageHistoryInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.556+08:00", comments="Source Table: binlog_storage_history")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, storageHistoryInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.557+08:00", comments="Source Table: binlog_storage_history")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.557+08:00", comments="Source Table: binlog_storage_history")
    default int insert(StorageHistoryInfo record) {
        return MyBatis3Utils.insert(this::insert, record, storageHistoryInfo, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(tso).toProperty("tso")
            .map(status).toProperty("status")
            .map(instructionId).toProperty("instructionId")
            .map(clusterId).toProperty("clusterId")
            .map(groupName).toProperty("groupName")
            .map(storageContent).toProperty("storageContent")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.559+08:00", comments="Source Table: binlog_storage_history")
    default int insertMultiple(Collection<StorageHistoryInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, storageHistoryInfo, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(tso).toProperty("tso")
            .map(status).toProperty("status")
            .map(instructionId).toProperty("instructionId")
            .map(clusterId).toProperty("clusterId")
            .map(groupName).toProperty("groupName")
            .map(storageContent).toProperty("storageContent")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.56+08:00", comments="Source Table: binlog_storage_history")
    default int insertSelective(StorageHistoryInfo record) {
        return MyBatis3Utils.insert(this::insert, record, storageHistoryInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(tso).toPropertyWhenPresent("tso", record::getTso)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
            .map(instructionId).toPropertyWhenPresent("instructionId", record::getInstructionId)
            .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
            .map(groupName).toPropertyWhenPresent("groupName", record::getGroupName)
            .map(storageContent).toPropertyWhenPresent("storageContent", record::getStorageContent)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.563+08:00", comments="Source Table: binlog_storage_history")
    default Optional<StorageHistoryInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, storageHistoryInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.563+08:00", comments="Source Table: binlog_storage_history")
    default List<StorageHistoryInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, storageHistoryInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.564+08:00", comments="Source Table: binlog_storage_history")
    default List<StorageHistoryInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, storageHistoryInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.564+08:00", comments="Source Table: binlog_storage_history")
    default Optional<StorageHistoryInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.565+08:00", comments="Source Table: binlog_storage_history")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, storageHistoryInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.565+08:00", comments="Source Table: binlog_storage_history")
    static UpdateDSL<UpdateModel> updateAllColumns(StorageHistoryInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(tso).equalTo(record::getTso)
                .set(status).equalTo(record::getStatus)
                .set(instructionId).equalTo(record::getInstructionId)
                .set(clusterId).equalTo(record::getClusterId)
                .set(groupName).equalTo(record::getGroupName)
                .set(storageContent).equalTo(record::getStorageContent);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.566+08:00", comments="Source Table: binlog_storage_history")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(StorageHistoryInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(instructionId).equalToWhenPresent(record::getInstructionId)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .set(groupName).equalToWhenPresent(record::getGroupName)
                .set(storageContent).equalToWhenPresent(record::getStorageContent);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.567+08:00", comments="Source Table: binlog_storage_history")
    default int updateByPrimaryKey(StorageHistoryInfo record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(tso).equalTo(record::getTso)
            .set(status).equalTo(record::getStatus)
            .set(instructionId).equalTo(record::getInstructionId)
            .set(clusterId).equalTo(record::getClusterId)
            .set(groupName).equalTo(record::getGroupName)
            .set(storageContent).equalTo(record::getStorageContent)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.568+08:00", comments="Source Table: binlog_storage_history")
    default int updateByPrimaryKeySelective(StorageHistoryInfo record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(instructionId).equalToWhenPresent(record::getInstructionId)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .set(groupName).equalToWhenPresent(record::getGroupName)
            .set(storageContent).equalToWhenPresent(record::getStorageContent)
            .where(id, isEqualTo(record::getId))
        );
    }
}