/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.InstConfigDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.InstConfig;
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
public interface InstConfigMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.067+08:00", comments="Source Table: inst_config")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, instId, paramKey, paramVal);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.059+08:00", comments="Source Table: inst_config")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.06+08:00", comments="Source Table: inst_config")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.06+08:00", comments="Source Table: inst_config")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<InstConfig> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.061+08:00", comments="Source Table: inst_config")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<InstConfig> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.061+08:00", comments="Source Table: inst_config")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="param_key", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="param_val", javaType=String.class, jdbcType=JdbcType.VARCHAR)
    })
    Optional<InstConfig> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.063+08:00", comments="Source Table: inst_config")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="param_key", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="param_val", javaType=String.class, jdbcType=JdbcType.VARCHAR)
    })
    List<InstConfig> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.063+08:00", comments="Source Table: inst_config")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.063+08:00", comments="Source Table: inst_config")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, instConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.064+08:00", comments="Source Table: inst_config")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, instConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.064+08:00", comments="Source Table: inst_config")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.064+08:00", comments="Source Table: inst_config")
    default int insert(InstConfig record) {
        return MyBatis3Utils.insert(this::insert, record, instConfig, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(instId).toProperty("instId")
            .map(paramKey).toProperty("paramKey")
            .map(paramVal).toProperty("paramVal")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.066+08:00", comments="Source Table: inst_config")
    default int insertMultiple(Collection<InstConfig> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, instConfig, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(instId).toProperty("instId")
            .map(paramKey).toProperty("paramKey")
            .map(paramVal).toProperty("paramVal")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.066+08:00", comments="Source Table: inst_config")
    default int insertSelective(InstConfig record) {
        return MyBatis3Utils.insert(this::insert, record, instConfig, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(instId).toPropertyWhenPresent("instId", record::getInstId)
            .map(paramKey).toPropertyWhenPresent("paramKey", record::getParamKey)
            .map(paramVal).toPropertyWhenPresent("paramVal", record::getParamVal)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.068+08:00", comments="Source Table: inst_config")
    default Optional<InstConfig> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, instConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.068+08:00", comments="Source Table: inst_config")
    default List<InstConfig> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, instConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.069+08:00", comments="Source Table: inst_config")
    default List<InstConfig> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, instConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.069+08:00", comments="Source Table: inst_config")
    default Optional<InstConfig> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.069+08:00", comments="Source Table: inst_config")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, instConfig, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.07+08:00", comments="Source Table: inst_config")
    static UpdateDSL<UpdateModel> updateAllColumns(InstConfig record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(instId).equalTo(record::getInstId)
                .set(paramKey).equalTo(record::getParamKey)
                .set(paramVal).equalTo(record::getParamVal);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.07+08:00", comments="Source Table: inst_config")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(InstConfig record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(instId).equalToWhenPresent(record::getInstId)
                .set(paramKey).equalToWhenPresent(record::getParamKey)
                .set(paramVal).equalToWhenPresent(record::getParamVal);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.071+08:00", comments="Source Table: inst_config")
    default int updateByPrimaryKey(InstConfig record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(instId).equalTo(record::getInstId)
            .set(paramKey).equalTo(record::getParamKey)
            .set(paramVal).equalTo(record::getParamVal)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.071+08:00", comments="Source Table: inst_config")
    default int updateByPrimaryKeySelective(InstConfig record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(instId).equalToWhenPresent(record::getInstId)
            .set(paramKey).equalToWhenPresent(record::getParamKey)
            .set(paramVal).equalToWhenPresent(record::getParamVal)
            .where(id, isEqualTo(record::getId))
        );
    }
}