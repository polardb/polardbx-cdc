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

import static com.aliyun.polardbx.binlog.dao.BinlogScheduleHistoryDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.BinlogScheduleHistory;
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
public interface BinlogScheduleHistoryMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.783+08:00", comments="Source Table: binlog_schedule_history")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, version, clusterId, content);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.775+08:00", comments="Source Table: binlog_schedule_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.776+08:00", comments="Source Table: binlog_schedule_history")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.777+08:00", comments="Source Table: binlog_schedule_history")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<BinlogScheduleHistory> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.777+08:00", comments="Source Table: binlog_schedule_history")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogScheduleHistory> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.778+08:00", comments="Source Table: binlog_schedule_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="version", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="content", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<BinlogScheduleHistory> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.779+08:00", comments="Source Table: binlog_schedule_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="version", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="content", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<BinlogScheduleHistory> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.779+08:00", comments="Source Table: binlog_schedule_history")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.78+08:00", comments="Source Table: binlog_schedule_history")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogScheduleHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.78+08:00", comments="Source Table: binlog_schedule_history")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogScheduleHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.78+08:00", comments="Source Table: binlog_schedule_history")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.781+08:00", comments="Source Table: binlog_schedule_history")
    default int insert(BinlogScheduleHistory record) {
        return MyBatis3Utils.insert(this::insert, record, binlogScheduleHistory, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(version).toProperty("version")
            .map(clusterId).toProperty("clusterId")
            .map(content).toProperty("content")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.782+08:00", comments="Source Table: binlog_schedule_history")
    default int insertMultiple(Collection<BinlogScheduleHistory> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogScheduleHistory, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(version).toProperty("version")
            .map(clusterId).toProperty("clusterId")
            .map(content).toProperty("content")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.782+08:00", comments="Source Table: binlog_schedule_history")
    default int insertSelective(BinlogScheduleHistory record) {
        return MyBatis3Utils.insert(this::insert, record, binlogScheduleHistory, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(version).toPropertyWhenPresent("version", record::getVersion)
            .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
            .map(content).toPropertyWhenPresent("content", record::getContent)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.784+08:00", comments="Source Table: binlog_schedule_history")
    default Optional<BinlogScheduleHistory> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogScheduleHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.784+08:00", comments="Source Table: binlog_schedule_history")
    default List<BinlogScheduleHistory> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogScheduleHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.785+08:00", comments="Source Table: binlog_schedule_history")
    default List<BinlogScheduleHistory> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogScheduleHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.785+08:00", comments="Source Table: binlog_schedule_history")
    default Optional<BinlogScheduleHistory> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.785+08:00", comments="Source Table: binlog_schedule_history")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogScheduleHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.786+08:00", comments="Source Table: binlog_schedule_history")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogScheduleHistory record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(version).equalTo(record::getVersion)
                .set(clusterId).equalTo(record::getClusterId)
                .set(content).equalTo(record::getContent);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.786+08:00", comments="Source Table: binlog_schedule_history")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogScheduleHistory record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(version).equalToWhenPresent(record::getVersion)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .set(content).equalToWhenPresent(record::getContent);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.787+08:00", comments="Source Table: binlog_schedule_history")
    default int updateByPrimaryKey(BinlogScheduleHistory record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(version).equalTo(record::getVersion)
            .set(clusterId).equalTo(record::getClusterId)
            .set(content).equalTo(record::getContent)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-27T21:13:35.787+08:00", comments="Source Table: binlog_schedule_history")
    default int updateByPrimaryKeySelective(BinlogScheduleHistory record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(version).equalToWhenPresent(record::getVersion)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .set(content).equalToWhenPresent(record::getContent)
            .where(id, isEqualTo(record::getId))
        );
    }
}