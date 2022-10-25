/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.domain.po.BinlogTaskSchedule;
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

import static com.aliyun.polardbx.binlog.dao.BinlogTaskScheduleDynamicSqlSupport.binlogTaskSchedule;
import static com.aliyun.polardbx.binlog.dao.BinlogTaskScheduleDynamicSqlSupport.clusterId;
import static com.aliyun.polardbx.binlog.dao.BinlogTaskScheduleDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.BinlogTaskScheduleDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.BinlogTaskScheduleDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.BinlogTaskScheduleDynamicSqlSupport.op;
import static com.aliyun.polardbx.binlog.dao.BinlogTaskScheduleDynamicSqlSupport.status;
import static com.aliyun.polardbx.binlog.dao.BinlogTaskScheduleDynamicSqlSupport.taskName;
import static com.aliyun.polardbx.binlog.dao.BinlogTaskScheduleDynamicSqlSupport.version;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface BinlogTaskScheduleMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.905+08:00",
        comments = "Source Table: binlog_task_schedule")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, clusterId, taskName, status, version, op);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<BinlogTaskSchedule> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogTaskSchedule> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "cluster_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "task_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "status", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "version", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "op", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    Optional<BinlogTaskSchedule> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "cluster_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "task_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "status", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "version", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "op", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    List<BinlogTaskSchedule> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogTaskSchedule, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogTaskSchedule, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    default int insert(BinlogTaskSchedule record) {
        return MyBatis3Utils.insert(this::insert, record, binlogTaskSchedule, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(clusterId).toProperty("clusterId")
                .map(taskName).toProperty("taskName")
                .map(status).toProperty("status")
                .map(version).toProperty("version")
                .map(op).toProperty("op")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source Table: binlog_task_schedule")
    default int insertMultiple(Collection<BinlogTaskSchedule> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogTaskSchedule, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(clusterId).toProperty("clusterId")
                .map(taskName).toProperty("taskName")
                .map(status).toProperty("status")
                .map(version).toProperty("version")
                .map(op).toProperty("op")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.905+08:00",
        comments = "Source Table: binlog_task_schedule")
    default int insertSelective(BinlogTaskSchedule record) {
        return MyBatis3Utils.insert(this::insert, record, binlogTaskSchedule, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
                .map(taskName).toPropertyWhenPresent("taskName", record::getTaskName)
                .map(status).toPropertyWhenPresent("status", record::getStatus)
                .map(version).toPropertyWhenPresent("version", record::getVersion)
                .map(op).toPropertyWhenPresent("op", record::getOp)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.905+08:00",
        comments = "Source Table: binlog_task_schedule")
    default Optional<BinlogTaskSchedule> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogTaskSchedule, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.905+08:00",
        comments = "Source Table: binlog_task_schedule")
    default List<BinlogTaskSchedule> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogTaskSchedule, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.905+08:00",
        comments = "Source Table: binlog_task_schedule")
    default List<BinlogTaskSchedule> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogTaskSchedule, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.905+08:00",
        comments = "Source Table: binlog_task_schedule")
    default Optional<BinlogTaskSchedule> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.905+08:00",
        comments = "Source Table: binlog_task_schedule")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogTaskSchedule, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.905+08:00",
        comments = "Source Table: binlog_task_schedule")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogTaskSchedule record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(clusterId).equalTo(record::getClusterId)
            .set(taskName).equalTo(record::getTaskName)
            .set(status).equalTo(record::getStatus)
            .set(version).equalTo(record::getVersion)
            .set(op).equalTo(record::getOp);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.905+08:00",
        comments = "Source Table: binlog_task_schedule")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogTaskSchedule record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .set(taskName).equalToWhenPresent(record::getTaskName)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(version).equalToWhenPresent(record::getVersion)
            .set(op).equalToWhenPresent(record::getOp);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.906+08:00",
        comments = "Source Table: binlog_task_schedule")
    default int updateByPrimaryKey(BinlogTaskSchedule record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(clusterId).equalTo(record::getClusterId)
                .set(taskName).equalTo(record::getTaskName)
                .set(status).equalTo(record::getStatus)
                .set(version).equalTo(record::getVersion)
                .set(op).equalTo(record::getOp)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.906+08:00",
        comments = "Source Table: binlog_task_schedule")
    default int updateByPrimaryKeySelective(BinlogTaskSchedule record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .set(taskName).equalToWhenPresent(record::getTaskName)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(version).equalToWhenPresent(record::getVersion)
                .set(op).equalToWhenPresent(record::getOp)
                .where(id, isEqualTo(record::getId))
        );
    }
}