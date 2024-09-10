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

import static com.aliyun.polardbx.binlog.dao.CdcSyncPointMetaDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.CdcSyncPointMeta;
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
public interface CdcSyncPointMetaMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.880641+08:00", comments="Source Table: cdc_sync_point_meta")
    BasicColumn[] selectList = BasicColumn.columnList(id, participants, tso, valid, gmtCreated, gmtModified);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.876375+08:00", comments="Source Table: cdc_sync_point_meta")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.876825+08:00", comments="Source Table: cdc_sync_point_meta")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.877047+08:00", comments="Source Table: cdc_sync_point_meta")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<CdcSyncPointMeta> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.877369+08:00", comments="Source Table: cdc_sync_point_meta")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<CdcSyncPointMeta> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.877609+08:00", comments="Source Table: cdc_sync_point_meta")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=String.class, jdbcType=JdbcType.CHAR, id=true),
        @Arg(column="participants", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="tso", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="valid", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP)
    })
    Optional<CdcSyncPointMeta> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.878258+08:00", comments="Source Table: cdc_sync_point_meta")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=String.class, jdbcType=JdbcType.CHAR, id=true),
        @Arg(column="participants", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="tso", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="valid", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP)
    })
    List<CdcSyncPointMeta> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.878458+08:00", comments="Source Table: cdc_sync_point_meta")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.878695+08:00", comments="Source Table: cdc_sync_point_meta")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, cdcSyncPointMeta, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.878884+08:00", comments="Source Table: cdc_sync_point_meta")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, cdcSyncPointMeta, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.879106+08:00", comments="Source Table: cdc_sync_point_meta")
    default int deleteByPrimaryKey(String id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.879317+08:00", comments="Source Table: cdc_sync_point_meta")
    default int insert(CdcSyncPointMeta record) {
        return MyBatis3Utils.insert(this::insert, record, cdcSyncPointMeta, c ->
            c.map(id).toProperty("id")
            .map(participants).toProperty("participants")
            .map(tso).toProperty("tso")
            .map(valid).toProperty("valid")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.879855+08:00", comments="Source Table: cdc_sync_point_meta")
    default int insertMultiple(Collection<CdcSyncPointMeta> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, cdcSyncPointMeta, c ->
            c.map(id).toProperty("id")
            .map(participants).toProperty("participants")
            .map(tso).toProperty("tso")
            .map(valid).toProperty("valid")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.8801+08:00", comments="Source Table: cdc_sync_point_meta")
    default int insertSelective(CdcSyncPointMeta record) {
        return MyBatis3Utils.insert(this::insert, record, cdcSyncPointMeta, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(participants).toPropertyWhenPresent("participants", record::getParticipants)
            .map(tso).toPropertyWhenPresent("tso", record::getTso)
            .map(valid).toPropertyWhenPresent("valid", record::getValid)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.881071+08:00", comments="Source Table: cdc_sync_point_meta")
    default Optional<CdcSyncPointMeta> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, cdcSyncPointMeta, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.881253+08:00", comments="Source Table: cdc_sync_point_meta")
    default List<CdcSyncPointMeta> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, cdcSyncPointMeta, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.881436+08:00", comments="Source Table: cdc_sync_point_meta")
    default List<CdcSyncPointMeta> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, cdcSyncPointMeta, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.881639+08:00", comments="Source Table: cdc_sync_point_meta")
    default Optional<CdcSyncPointMeta> selectByPrimaryKey(String id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.881802+08:00", comments="Source Table: cdc_sync_point_meta")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, cdcSyncPointMeta, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.881991+08:00", comments="Source Table: cdc_sync_point_meta")
    static UpdateDSL<UpdateModel> updateAllColumns(CdcSyncPointMeta record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(participants).equalTo(record::getParticipants)
                .set(tso).equalTo(record::getTso)
                .set(valid).equalTo(record::getValid)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.882215+08:00", comments="Source Table: cdc_sync_point_meta")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(CdcSyncPointMeta record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(participants).equalToWhenPresent(record::getParticipants)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(valid).equalToWhenPresent(record::getValid)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.882682+08:00", comments="Source Table: cdc_sync_point_meta")
    default int updateByPrimaryKey(CdcSyncPointMeta record) {
        return update(c ->
            c.set(participants).equalTo(record::getParticipants)
            .set(tso).equalTo(record::getTso)
            .set(valid).equalTo(record::getValid)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.882943+08:00", comments="Source Table: cdc_sync_point_meta")
    default int updateByPrimaryKeySelective(CdcSyncPointMeta record) {
        return update(c ->
            c.set(participants).equalToWhenPresent(record::getParticipants)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(valid).equalToWhenPresent(record::getValid)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .where(id, isEqualTo(record::getId))
        );
    }
}