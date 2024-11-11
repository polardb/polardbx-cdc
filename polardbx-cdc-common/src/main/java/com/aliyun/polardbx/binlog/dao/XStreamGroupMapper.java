/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.XStreamGroupDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.XStreamGroup;
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
public interface XStreamGroupMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, groupName, groupDesc);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<XStreamGroup> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<XStreamGroup> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="group_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="group_desc", javaType=String.class, jdbcType=JdbcType.VARCHAR)
    })
    Optional<XStreamGroup> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="group_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="group_desc", javaType=String.class, jdbcType=JdbcType.VARCHAR)
    })
    List<XStreamGroup> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, XStreamGroup, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, XStreamGroup, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    default int insert(XStreamGroup record) {
        return MyBatis3Utils.insert(this::insert, record, XStreamGroup, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(groupName).toProperty("groupName")
            .map(groupDesc).toProperty("groupDesc")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    default int insertMultiple(Collection<XStreamGroup> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, XStreamGroup, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(groupName).toProperty("groupName")
            .map(groupDesc).toProperty("groupDesc")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    default int insertSelective(XStreamGroup record) {
        return MyBatis3Utils.insert(this::insert, record, XStreamGroup, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(groupName).toPropertyWhenPresent("groupName", record::getGroupName)
            .map(groupDesc).toPropertyWhenPresent("groupDesc", record::getGroupDesc)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    default Optional<XStreamGroup> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, XStreamGroup, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    default List<XStreamGroup> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, XStreamGroup, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    default List<XStreamGroup> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, XStreamGroup, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    default Optional<XStreamGroup> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, XStreamGroup, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    static UpdateDSL<UpdateModel> updateAllColumns(XStreamGroup record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(groupName).equalTo(record::getGroupName)
                .set(groupDesc).equalTo(record::getGroupDesc);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(XStreamGroup record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(groupName).equalToWhenPresent(record::getGroupName)
                .set(groupDesc).equalToWhenPresent(record::getGroupDesc);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    default int updateByPrimaryKey(XStreamGroup record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(groupName).equalTo(record::getGroupName)
            .set(groupDesc).equalTo(record::getGroupDesc)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.213+08:00", comments="Source Table: binlog_x_stream_group")
    default int updateByPrimaryKeySelective(XStreamGroup record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(groupName).equalToWhenPresent(record::getGroupName)
            .set(groupDesc).equalToWhenPresent(record::getGroupDesc)
            .where(id, isEqualTo(record::getId))
        );
    }
}