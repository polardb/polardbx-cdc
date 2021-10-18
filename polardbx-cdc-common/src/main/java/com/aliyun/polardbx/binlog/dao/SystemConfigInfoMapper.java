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

import com.aliyun.polardbx.binlog.domain.po.SystemConfigInfo;
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

import static com.aliyun.polardbx.binlog.dao.SystemConfigInfoDynamicSqlSupport.configKey;
import static com.aliyun.polardbx.binlog.dao.SystemConfigInfoDynamicSqlSupport.configValue;
import static com.aliyun.polardbx.binlog.dao.SystemConfigInfoDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.SystemConfigInfoDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.SystemConfigInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.SystemConfigInfoDynamicSqlSupport.systemConfigInfo;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface SystemConfigInfoMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.633+08:00",
        comments = "Source Table: binlog_system_config")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, configKey, configValue);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.617+08:00",
        comments = "Source Table: binlog_system_config")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.619+08:00",
        comments = "Source Table: binlog_system_config")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.619+08:00",
        comments = "Source Table: binlog_system_config")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<SystemConfigInfo> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.62+08:00",
        comments = "Source Table: binlog_system_config")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<SystemConfigInfo> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.621+08:00",
        comments = "Source Table: binlog_system_config")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "config_key", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "config_value", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    Optional<SystemConfigInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.623+08:00",
        comments = "Source Table: binlog_system_config")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "config_key", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "config_value", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    List<SystemConfigInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.624+08:00",
        comments = "Source Table: binlog_system_config")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.625+08:00",
        comments = "Source Table: binlog_system_config")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, systemConfigInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.625+08:00",
        comments = "Source Table: binlog_system_config")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, systemConfigInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.626+08:00",
        comments = "Source Table: binlog_system_config")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.626+08:00",
        comments = "Source Table: binlog_system_config")
    default int insert(SystemConfigInfo record) {
        return MyBatis3Utils.insert(this::insert, record, systemConfigInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(configKey).toProperty("configKey")
                .map(configValue).toProperty("configValue")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.63+08:00",
        comments = "Source Table: binlog_system_config")
    default int insertMultiple(Collection<SystemConfigInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, systemConfigInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(configKey).toProperty("configKey")
                .map(configValue).toProperty("configValue")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.632+08:00",
        comments = "Source Table: binlog_system_config")
    default int insertSelective(SystemConfigInfo record) {
        return MyBatis3Utils.insert(this::insert, record, systemConfigInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(configKey).toPropertyWhenPresent("configKey", record::getConfigKey)
                .map(configValue).toPropertyWhenPresent("configValue", record::getConfigValue)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.636+08:00",
        comments = "Source Table: binlog_system_config")
    default Optional<SystemConfigInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, systemConfigInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.636+08:00",
        comments = "Source Table: binlog_system_config")
    default List<SystemConfigInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, systemConfigInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.637+08:00",
        comments = "Source Table: binlog_system_config")
    default List<SystemConfigInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, systemConfigInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.637+08:00",
        comments = "Source Table: binlog_system_config")
    default Optional<SystemConfigInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.638+08:00",
        comments = "Source Table: binlog_system_config")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, systemConfigInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.638+08:00",
        comments = "Source Table: binlog_system_config")
    static UpdateDSL<UpdateModel> updateAllColumns(SystemConfigInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(configKey).equalTo(record::getConfigKey)
            .set(configValue).equalTo(record::getConfigValue);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.64+08:00",
        comments = "Source Table: binlog_system_config")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(SystemConfigInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(configKey).equalToWhenPresent(record::getConfigKey)
            .set(configValue).equalToWhenPresent(record::getConfigValue);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.643+08:00",
        comments = "Source Table: binlog_system_config")
    default int updateByPrimaryKey(SystemConfigInfo record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(configKey).equalTo(record::getConfigKey)
                .set(configValue).equalTo(record::getConfigValue)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.645+08:00",
        comments = "Source Table: binlog_system_config")
    default int updateByPrimaryKeySelective(SystemConfigInfo record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(configKey).equalToWhenPresent(record::getConfigKey)
                .set(configValue).equalToWhenPresent(record::getConfigValue)
                .where(id, isEqualTo(record::getId))
        );
    }
}