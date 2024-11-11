/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.XStream;
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
public interface XStreamMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.902+08:00", comments="Source Table: binlog_x_stream")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, streamName, streamDesc, groupName, expectedStorageTso, latestCursor, endpoint);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.891+08:00", comments="Source Table: binlog_x_stream")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.892+08:00", comments="Source Table: binlog_x_stream")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.893+08:00", comments="Source Table: binlog_x_stream")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<XStream> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.893+08:00", comments="Source Table: binlog_x_stream")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<XStream> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.894+08:00", comments="Source Table: binlog_x_stream")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="stream_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="stream_desc", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="group_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="expected_storage_tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="latest_cursor", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="endpoint", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<XStream> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.896+08:00", comments="Source Table: binlog_x_stream")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="stream_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="stream_desc", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="group_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="expected_storage_tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="latest_cursor", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="endpoint", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<XStream> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.896+08:00", comments="Source Table: binlog_x_stream")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.897+08:00", comments="Source Table: binlog_x_stream")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, XStream, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.897+08:00", comments="Source Table: binlog_x_stream")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, XStream, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.898+08:00", comments="Source Table: binlog_x_stream")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.898+08:00", comments="Source Table: binlog_x_stream")
    default int insert(XStream record) {
        return MyBatis3Utils.insert(this::insert, record, XStream, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(streamName).toProperty("streamName")
            .map(streamDesc).toProperty("streamDesc")
            .map(groupName).toProperty("groupName")
            .map(expectedStorageTso).toProperty("expectedStorageTso")
            .map(latestCursor).toProperty("latestCursor")
            .map(endpoint).toProperty("endpoint")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.9+08:00", comments="Source Table: binlog_x_stream")
    default int insertMultiple(Collection<XStream> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, XStream, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(streamName).toProperty("streamName")
            .map(streamDesc).toProperty("streamDesc")
            .map(groupName).toProperty("groupName")
            .map(expectedStorageTso).toProperty("expectedStorageTso")
            .map(latestCursor).toProperty("latestCursor")
            .map(endpoint).toProperty("endpoint")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.901+08:00", comments="Source Table: binlog_x_stream")
    default int insertSelective(XStream record) {
        return MyBatis3Utils.insert(this::insert, record, XStream, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(streamName).toPropertyWhenPresent("streamName", record::getStreamName)
            .map(streamDesc).toPropertyWhenPresent("streamDesc", record::getStreamDesc)
            .map(groupName).toPropertyWhenPresent("groupName", record::getGroupName)
            .map(expectedStorageTso).toPropertyWhenPresent("expectedStorageTso", record::getExpectedStorageTso)
            .map(latestCursor).toPropertyWhenPresent("latestCursor", record::getLatestCursor)
            .map(endpoint).toPropertyWhenPresent("endpoint", record::getEndpoint)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.903+08:00", comments="Source Table: binlog_x_stream")
    default Optional<XStream> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, XStream, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.904+08:00", comments="Source Table: binlog_x_stream")
    default List<XStream> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, XStream, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.904+08:00", comments="Source Table: binlog_x_stream")
    default List<XStream> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, XStream, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.904+08:00", comments="Source Table: binlog_x_stream")
    default Optional<XStream> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.905+08:00", comments="Source Table: binlog_x_stream")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, XStream, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.905+08:00", comments="Source Table: binlog_x_stream")
    static UpdateDSL<UpdateModel> updateAllColumns(XStream record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(streamName).equalTo(record::getStreamName)
                .set(streamDesc).equalTo(record::getStreamDesc)
                .set(groupName).equalTo(record::getGroupName)
                .set(expectedStorageTso).equalTo(record::getExpectedStorageTso)
                .set(latestCursor).equalTo(record::getLatestCursor)
                .set(endpoint).equalTo(record::getEndpoint);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.906+08:00", comments="Source Table: binlog_x_stream")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(XStream record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(streamName).equalToWhenPresent(record::getStreamName)
                .set(streamDesc).equalToWhenPresent(record::getStreamDesc)
                .set(groupName).equalToWhenPresent(record::getGroupName)
                .set(expectedStorageTso).equalToWhenPresent(record::getExpectedStorageTso)
                .set(latestCursor).equalToWhenPresent(record::getLatestCursor)
                .set(endpoint).equalToWhenPresent(record::getEndpoint);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.907+08:00", comments="Source Table: binlog_x_stream")
    default int updateByPrimaryKey(XStream record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(streamName).equalTo(record::getStreamName)
            .set(streamDesc).equalTo(record::getStreamDesc)
            .set(groupName).equalTo(record::getGroupName)
            .set(expectedStorageTso).equalTo(record::getExpectedStorageTso)
            .set(latestCursor).equalTo(record::getLatestCursor)
            .set(endpoint).equalTo(record::getEndpoint)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.907+08:00", comments="Source Table: binlog_x_stream")
    default int updateByPrimaryKeySelective(XStream record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(streamName).equalToWhenPresent(record::getStreamName)
            .set(streamDesc).equalToWhenPresent(record::getStreamDesc)
            .set(groupName).equalToWhenPresent(record::getGroupName)
            .set(expectedStorageTso).equalToWhenPresent(record::getExpectedStorageTso)
            .set(latestCursor).equalToWhenPresent(record::getLatestCursor)
            .set(endpoint).equalToWhenPresent(record::getEndpoint)
            .where(id, isEqualTo(record::getId))
        );
    }
}