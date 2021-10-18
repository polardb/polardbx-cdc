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

import com.aliyun.polardbx.binlog.domain.po.DbGroupInfo;
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

import static com.aliyun.polardbx.binlog.dao.DbGroupInfoDynamicSqlSupport.dbGroupInfo;
import static com.aliyun.polardbx.binlog.dao.DbGroupInfoDynamicSqlSupport.dbName;
import static com.aliyun.polardbx.binlog.dao.DbGroupInfoDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.DbGroupInfoDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.DbGroupInfoDynamicSqlSupport.groupName;
import static com.aliyun.polardbx.binlog.dao.DbGroupInfoDynamicSqlSupport.groupType;
import static com.aliyun.polardbx.binlog.dao.DbGroupInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.DbGroupInfoDynamicSqlSupport.phyDbName;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface DbGroupInfoMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.384+08:00",
        comments = "Source Table: db_group_info")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, dbName, groupName, phyDbName, groupType);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.37+08:00",
        comments = "Source Table: db_group_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.372+08:00",
        comments = "Source Table: db_group_info")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.373+08:00",
        comments = "Source Table: db_group_info")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<DbGroupInfo> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.374+08:00",
        comments = "Source Table: db_group_info")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<DbGroupInfo> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.375+08:00",
        comments = "Source Table: db_group_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "db_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "group_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "phy_db_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "group_type", javaType = Integer.class, jdbcType = JdbcType.INTEGER)
    })
    Optional<DbGroupInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.377+08:00",
        comments = "Source Table: db_group_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "db_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "group_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "phy_db_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "group_type", javaType = Integer.class, jdbcType = JdbcType.INTEGER)
    })
    List<DbGroupInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.378+08:00",
        comments = "Source Table: db_group_info")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.378+08:00",
        comments = "Source Table: db_group_info")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, dbGroupInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.379+08:00",
        comments = "Source Table: db_group_info")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, dbGroupInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.379+08:00",
        comments = "Source Table: db_group_info")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.38+08:00",
        comments = "Source Table: db_group_info")
    default int insert(DbGroupInfo record) {
        return MyBatis3Utils.insert(this::insert, record, dbGroupInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(dbName).toProperty("dbName")
                .map(groupName).toProperty("groupName")
                .map(phyDbName).toProperty("phyDbName")
                .map(groupType).toProperty("groupType")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.382+08:00",
        comments = "Source Table: db_group_info")
    default int insertMultiple(Collection<DbGroupInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, dbGroupInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(dbName).toProperty("dbName")
                .map(groupName).toProperty("groupName")
                .map(phyDbName).toProperty("phyDbName")
                .map(groupType).toProperty("groupType")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.383+08:00",
        comments = "Source Table: db_group_info")
    default int insertSelective(DbGroupInfo record) {
        return MyBatis3Utils.insert(this::insert, record, dbGroupInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(dbName).toPropertyWhenPresent("dbName", record::getDbName)
                .map(groupName).toPropertyWhenPresent("groupName", record::getGroupName)
                .map(phyDbName).toPropertyWhenPresent("phyDbName", record::getPhyDbName)
                .map(groupType).toPropertyWhenPresent("groupType", record::getGroupType)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.386+08:00",
        comments = "Source Table: db_group_info")
    default Optional<DbGroupInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, dbGroupInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.386+08:00",
        comments = "Source Table: db_group_info")
    default List<DbGroupInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, dbGroupInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.387+08:00",
        comments = "Source Table: db_group_info")
    default List<DbGroupInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, dbGroupInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.387+08:00",
        comments = "Source Table: db_group_info")
    default Optional<DbGroupInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.388+08:00",
        comments = "Source Table: db_group_info")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, dbGroupInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.389+08:00",
        comments = "Source Table: db_group_info")
    static UpdateDSL<UpdateModel> updateAllColumns(DbGroupInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(dbName).equalTo(record::getDbName)
            .set(groupName).equalTo(record::getGroupName)
            .set(phyDbName).equalTo(record::getPhyDbName)
            .set(groupType).equalTo(record::getGroupType);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.39+08:00",
        comments = "Source Table: db_group_info")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(DbGroupInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(dbName).equalToWhenPresent(record::getDbName)
            .set(groupName).equalToWhenPresent(record::getGroupName)
            .set(phyDbName).equalToWhenPresent(record::getPhyDbName)
            .set(groupType).equalToWhenPresent(record::getGroupType);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.391+08:00",
        comments = "Source Table: db_group_info")
    default int updateByPrimaryKey(DbGroupInfo record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(dbName).equalTo(record::getDbName)
                .set(groupName).equalTo(record::getGroupName)
                .set(phyDbName).equalTo(record::getPhyDbName)
                .set(groupType).equalTo(record::getGroupType)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.392+08:00",
        comments = "Source Table: db_group_info")
    default int updateByPrimaryKeySelective(DbGroupInfo record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(dbName).equalToWhenPresent(record::getDbName)
                .set(groupName).equalToWhenPresent(record::getGroupName)
                .set(phyDbName).equalToWhenPresent(record::getPhyDbName)
                .set(groupType).equalToWhenPresent(record::getGroupType)
                .where(id, isEqualTo(record::getId))
        );
    }
}