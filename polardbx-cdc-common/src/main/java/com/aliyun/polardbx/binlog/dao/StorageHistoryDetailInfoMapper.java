/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.StorageHistoryDetailInfoDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.StorageHistoryDetailInfo;
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
public interface StorageHistoryDetailInfoMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.575+08:00", comments="Source Table: binlog_storage_history_detail")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, clusterId, tso, instructionId, streamName, status);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source Table: binlog_storage_history_detail")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source Table: binlog_storage_history_detail")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source Table: binlog_storage_history_detail")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<StorageHistoryDetailInfo> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source Table: binlog_storage_history_detail")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<StorageHistoryDetailInfo> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.574+08:00", comments="Source Table: binlog_storage_history_detail")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="instruction_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="stream_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER)
    })
    Optional<StorageHistoryDetailInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.574+08:00", comments="Source Table: binlog_storage_history_detail")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="instruction_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="stream_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="status", javaType=Integer.class, jdbcType=JdbcType.INTEGER)
    })
    List<StorageHistoryDetailInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.574+08:00", comments="Source Table: binlog_storage_history_detail")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.574+08:00", comments="Source Table: binlog_storage_history_detail")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, storageHistoryDetailInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.574+08:00", comments="Source Table: binlog_storage_history_detail")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, storageHistoryDetailInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.574+08:00", comments="Source Table: binlog_storage_history_detail")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.574+08:00", comments="Source Table: binlog_storage_history_detail")
    default int insert(StorageHistoryDetailInfo record) {
        return MyBatis3Utils.insert(this::insert, record, storageHistoryDetailInfo, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(clusterId).toProperty("clusterId")
            .map(tso).toProperty("tso")
            .map(instructionId).toProperty("instructionId")
            .map(streamName).toProperty("streamName")
            .map(status).toProperty("status")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.574+08:00", comments="Source Table: binlog_storage_history_detail")
    default int insertMultiple(Collection<StorageHistoryDetailInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, storageHistoryDetailInfo, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(clusterId).toProperty("clusterId")
            .map(tso).toProperty("tso")
            .map(instructionId).toProperty("instructionId")
            .map(streamName).toProperty("streamName")
            .map(status).toProperty("status")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.575+08:00", comments="Source Table: binlog_storage_history_detail")
    default int insertSelective(StorageHistoryDetailInfo record) {
        return MyBatis3Utils.insert(this::insert, record, storageHistoryDetailInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
            .map(tso).toPropertyWhenPresent("tso", record::getTso)
            .map(instructionId).toPropertyWhenPresent("instructionId", record::getInstructionId)
            .map(streamName).toPropertyWhenPresent("streamName", record::getStreamName)
            .map(status).toPropertyWhenPresent("status", record::getStatus)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.575+08:00", comments="Source Table: binlog_storage_history_detail")
    default Optional<StorageHistoryDetailInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, storageHistoryDetailInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.575+08:00", comments="Source Table: binlog_storage_history_detail")
    default List<StorageHistoryDetailInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, storageHistoryDetailInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.575+08:00", comments="Source Table: binlog_storage_history_detail")
    default List<StorageHistoryDetailInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, storageHistoryDetailInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.575+08:00", comments="Source Table: binlog_storage_history_detail")
    default Optional<StorageHistoryDetailInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.575+08:00", comments="Source Table: binlog_storage_history_detail")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, storageHistoryDetailInfo, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.575+08:00", comments="Source Table: binlog_storage_history_detail")
    static UpdateDSL<UpdateModel> updateAllColumns(StorageHistoryDetailInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(clusterId).equalTo(record::getClusterId)
                .set(tso).equalTo(record::getTso)
                .set(instructionId).equalTo(record::getInstructionId)
                .set(streamName).equalTo(record::getStreamName)
                .set(status).equalTo(record::getStatus);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.575+08:00", comments="Source Table: binlog_storage_history_detail")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(StorageHistoryDetailInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(instructionId).equalToWhenPresent(record::getInstructionId)
                .set(streamName).equalToWhenPresent(record::getStreamName)
                .set(status).equalToWhenPresent(record::getStatus);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.576+08:00", comments="Source Table: binlog_storage_history_detail")
    default int updateByPrimaryKey(StorageHistoryDetailInfo record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(clusterId).equalTo(record::getClusterId)
            .set(tso).equalTo(record::getTso)
            .set(instructionId).equalTo(record::getInstructionId)
            .set(streamName).equalTo(record::getStreamName)
            .set(status).equalTo(record::getStatus)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.576+08:00", comments="Source Table: binlog_storage_history_detail")
    default int updateByPrimaryKeySelective(StorageHistoryDetailInfo record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(instructionId).equalToWhenPresent(record::getInstructionId)
            .set(streamName).equalToWhenPresent(record::getStreamName)
            .set(status).equalToWhenPresent(record::getStatus)
            .where(id, isEqualTo(record::getId))
        );
    }
}