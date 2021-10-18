/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.domain.po.GroupDetailInfo;
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

import javax.annotation.Generated;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.dao.GroupDetailInfoDynamicSqlSupport.dbName;
import static com.aliyun.polardbx.binlog.dao.GroupDetailInfoDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.GroupDetailInfoDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.GroupDetailInfoDynamicSqlSupport.groupDetailInfo;
import static com.aliyun.polardbx.binlog.dao.GroupDetailInfoDynamicSqlSupport.groupName;
import static com.aliyun.polardbx.binlog.dao.GroupDetailInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.GroupDetailInfoDynamicSqlSupport.instId;
import static com.aliyun.polardbx.binlog.dao.GroupDetailInfoDynamicSqlSupport.storageInstId;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface GroupDetailInfoMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.459+08:00",
        comments = "Source Table: group_detail_info")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, instId, dbName, groupName, storageInstId);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.457+08:00",
        comments = "Source Table: group_detail_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.457+08:00",
        comments = "Source Table: group_detail_info")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.457+08:00",
        comments = "Source Table: group_detail_info")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<GroupDetailInfo> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.457+08:00",
        comments = "Source Table: group_detail_info")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<GroupDetailInfo> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.457+08:00",
        comments = "Source Table: group_detail_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "group_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "storage_inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    Optional<GroupDetailInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.458+08:00",
        comments = "Source Table: group_detail_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "group_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "storage_inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    List<GroupDetailInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.458+08:00",
        comments = "Source Table: group_detail_info")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.458+08:00",
        comments = "Source Table: group_detail_info")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, groupDetailInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.458+08:00",
        comments = "Source Table: group_detail_info")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, groupDetailInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.458+08:00",
        comments = "Source Table: group_detail_info")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.459+08:00",
        comments = "Source Table: group_detail_info")
    default int insert(GroupDetailInfo record) {
        return MyBatis3Utils.insert(this::insert, record, groupDetailInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(instId).toProperty("instId")
                .map(dbName).toProperty("dbName")
                .map(groupName).toProperty("groupName")
                .map(storageInstId).toProperty("storageInstId")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.459+08:00",
        comments = "Source Table: group_detail_info")
    default int insertMultiple(Collection<GroupDetailInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, groupDetailInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(instId).toProperty("instId")
                .map(dbName).toProperty("dbName")
                .map(groupName).toProperty("groupName")
                .map(storageInstId).toProperty("storageInstId")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.459+08:00",
        comments = "Source Table: group_detail_info")
    default int insertSelective(GroupDetailInfo record) {
        return MyBatis3Utils.insert(this::insert, record, groupDetailInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(instId).toPropertyWhenPresent("instId", record::getInstId)
                .map(dbName).toPropertyWhenPresent("dbName", record::getDbName)
                .map(groupName).toPropertyWhenPresent("groupName", record::getGroupName)
                .map(storageInstId).toPropertyWhenPresent("storageInstId", record::getStorageInstId)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.459+08:00",
        comments = "Source Table: group_detail_info")
    default Optional<GroupDetailInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, groupDetailInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.459+08:00",
        comments = "Source Table: group_detail_info")
    default List<GroupDetailInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, groupDetailInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.459+08:00",
        comments = "Source Table: group_detail_info")
    default List<GroupDetailInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, groupDetailInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.46+08:00",
        comments = "Source Table: group_detail_info")
    default Optional<GroupDetailInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.46+08:00",
        comments = "Source Table: group_detail_info")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, groupDetailInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.46+08:00",
        comments = "Source Table: group_detail_info")
    static UpdateDSL<UpdateModel> updateAllColumns(GroupDetailInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(instId).equalTo(record::getInstId)
            .set(dbName).equalTo(record::getDbName)
            .set(groupName).equalTo(record::getGroupName)
            .set(storageInstId).equalTo(record::getStorageInstId);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.46+08:00",
        comments = "Source Table: group_detail_info")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(GroupDetailInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(instId).equalToWhenPresent(record::getInstId)
            .set(dbName).equalToWhenPresent(record::getDbName)
            .set(groupName).equalToWhenPresent(record::getGroupName)
            .set(storageInstId).equalToWhenPresent(record::getStorageInstId);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.46+08:00",
        comments = "Source Table: group_detail_info")
    default int updateByPrimaryKey(GroupDetailInfo record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(instId).equalTo(record::getInstId)
                .set(dbName).equalTo(record::getDbName)
                .set(groupName).equalTo(record::getGroupName)
                .set(storageInstId).equalTo(record::getStorageInstId)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.46+08:00",
        comments = "Source Table: group_detail_info")
    default int updateByPrimaryKeySelective(GroupDetailInfo record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(instId).equalToWhenPresent(record::getInstId)
                .set(dbName).equalToWhenPresent(record::getDbName)
                .set(groupName).equalToWhenPresent(record::getGroupName)
                .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
                .where(id, isEqualTo(record::getId))
        );
    }
}