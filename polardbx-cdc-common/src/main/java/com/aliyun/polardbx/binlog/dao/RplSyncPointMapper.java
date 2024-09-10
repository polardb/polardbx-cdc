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

import static com.aliyun.polardbx.binlog.dao.RplSyncPointDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplSyncPoint;
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
public interface RplSyncPointMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.064792+08:00", comments="Source Table: rpl_sync_point")
    BasicColumn[] selectList = BasicColumn.columnList(id, primaryTso, secondaryTso, createTime);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.060136+08:00", comments="Source Table: rpl_sync_point")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.060553+08:00", comments="Source Table: rpl_sync_point")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.060758+08:00", comments="Source Table: rpl_sync_point")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<RplSyncPoint> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.061055+08:00", comments="Source Table: rpl_sync_point")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<RplSyncPoint> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.061276+08:00", comments="Source Table: rpl_sync_point")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="primary_tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="secondary_tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP)
    })
    Optional<RplSyncPoint> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.061853+08:00", comments="Source Table: rpl_sync_point")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="primary_tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="secondary_tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP)
    })
    List<RplSyncPoint> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.062038+08:00", comments="Source Table: rpl_sync_point")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.062216+08:00", comments="Source Table: rpl_sync_point")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplSyncPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.062463+08:00", comments="Source Table: rpl_sync_point")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplSyncPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.062794+08:00", comments="Source Table: rpl_sync_point")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.063031+08:00", comments="Source Table: rpl_sync_point")
    default int insert(RplSyncPoint record) {
        return MyBatis3Utils.insert(this::insert, record, rplSyncPoint, c ->
            c.map(id).toProperty("id")
            .map(primaryTso).toProperty("primaryTso")
            .map(secondaryTso).toProperty("secondaryTso")
            .map(createTime).toProperty("createTime")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.063871+08:00", comments="Source Table: rpl_sync_point")
    default int insertMultiple(Collection<RplSyncPoint> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, rplSyncPoint, c ->
            c.map(id).toProperty("id")
            .map(primaryTso).toProperty("primaryTso")
            .map(secondaryTso).toProperty("secondaryTso")
            .map(createTime).toProperty("createTime")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.064195+08:00", comments="Source Table: rpl_sync_point")
    default int insertSelective(RplSyncPoint record) {
        return MyBatis3Utils.insert(this::insert, record, rplSyncPoint, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(primaryTso).toPropertyWhenPresent("primaryTso", record::getPrimaryTso)
            .map(secondaryTso).toPropertyWhenPresent("secondaryTso", record::getSecondaryTso)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.065279+08:00", comments="Source Table: rpl_sync_point")
    default Optional<RplSyncPoint> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplSyncPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.065478+08:00", comments="Source Table: rpl_sync_point")
    default List<RplSyncPoint> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplSyncPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.0657+08:00", comments="Source Table: rpl_sync_point")
    default List<RplSyncPoint> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplSyncPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.065941+08:00", comments="Source Table: rpl_sync_point")
    default Optional<RplSyncPoint> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.066155+08:00", comments="Source Table: rpl_sync_point")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplSyncPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.066341+08:00", comments="Source Table: rpl_sync_point")
    static UpdateDSL<UpdateModel> updateAllColumns(RplSyncPoint record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(primaryTso).equalTo(record::getPrimaryTso)
                .set(secondaryTso).equalTo(record::getSecondaryTso)
                .set(createTime).equalTo(record::getCreateTime);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.066551+08:00", comments="Source Table: rpl_sync_point")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplSyncPoint record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(primaryTso).equalToWhenPresent(record::getPrimaryTso)
                .set(secondaryTso).equalToWhenPresent(record::getSecondaryTso)
                .set(createTime).equalToWhenPresent(record::getCreateTime);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.066879+08:00", comments="Source Table: rpl_sync_point")
    default int updateByPrimaryKey(RplSyncPoint record) {
        return update(c ->
            c.set(primaryTso).equalTo(record::getPrimaryTso)
            .set(secondaryTso).equalTo(record::getSecondaryTso)
            .set(createTime).equalTo(record::getCreateTime)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.067086+08:00", comments="Source Table: rpl_sync_point")
    default int updateByPrimaryKeySelective(RplSyncPoint record) {
        return update(c ->
            c.set(primaryTso).equalToWhenPresent(record::getPrimaryTso)
            .set(secondaryTso).equalToWhenPresent(record::getSecondaryTso)
            .set(createTime).equalToWhenPresent(record::getCreateTime)
            .where(id, isEqualTo(record::getId))
        );
    }
}