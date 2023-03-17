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

import static com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;

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
public interface BinlogPolarxCommandMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.929+08:00",
        comments = "Source Table: binlog_polarx_command")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, cmdId, cmdType, cmdStatus, cmdRequest, cmdReply);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.918+08:00",
        comments = "Source Table: binlog_polarx_command")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.919+08:00",
        comments = "Source Table: binlog_polarx_command")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.919+08:00",
        comments = "Source Table: binlog_polarx_command")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<BinlogPolarxCommand> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.92+08:00",
        comments = "Source Table: binlog_polarx_command")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogPolarxCommand> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.921+08:00",
        comments = "Source Table: binlog_polarx_command")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "cmd_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cmd_type", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cmd_status", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "cmd_request", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR),
        @Arg(column = "cmd_reply", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    Optional<BinlogPolarxCommand> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.923+08:00",
        comments = "Source Table: binlog_polarx_command")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "cmd_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cmd_type", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cmd_status", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "cmd_request", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR),
        @Arg(column = "cmd_reply", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    List<BinlogPolarxCommand> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.923+08:00",
        comments = "Source Table: binlog_polarx_command")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.924+08:00",
        comments = "Source Table: binlog_polarx_command")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.924+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.925+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.925+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int insert(BinlogPolarxCommand record) {
        return MyBatis3Utils.insert(this::insert, record, binlogPolarxCommand, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(cmdId).toProperty("cmdId")
                .map(cmdType).toProperty("cmdType")
                .map(cmdStatus).toProperty("cmdStatus")
                .map(cmdRequest).toProperty("cmdRequest")
                .map(cmdReply).toProperty("cmdReply")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.927+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int insertMultiple(Collection<BinlogPolarxCommand> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogPolarxCommand, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(cmdId).toProperty("cmdId")
                .map(cmdType).toProperty("cmdType")
                .map(cmdStatus).toProperty("cmdStatus")
                .map(cmdRequest).toProperty("cmdRequest")
                .map(cmdReply).toProperty("cmdReply")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.927+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int insertSelective(BinlogPolarxCommand record) {
        return MyBatis3Utils.insert(this::insert, record, binlogPolarxCommand, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(cmdId).toPropertyWhenPresent("cmdId", record::getCmdId)
                .map(cmdType).toPropertyWhenPresent("cmdType", record::getCmdType)
                .map(cmdStatus).toPropertyWhenPresent("cmdStatus", record::getCmdStatus)
                .map(cmdRequest).toPropertyWhenPresent("cmdRequest", record::getCmdRequest)
                .map(cmdReply).toPropertyWhenPresent("cmdReply", record::getCmdReply)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.93+08:00",
        comments = "Source Table: binlog_polarx_command")
    default Optional<BinlogPolarxCommand> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.93+08:00",
        comments = "Source Table: binlog_polarx_command")
    default List<BinlogPolarxCommand> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.931+08:00",
        comments = "Source Table: binlog_polarx_command")
    default List<BinlogPolarxCommand> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.931+08:00",
        comments = "Source Table: binlog_polarx_command")
    default Optional<BinlogPolarxCommand> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.932+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.932+08:00",
        comments = "Source Table: binlog_polarx_command")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogPolarxCommand record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(cmdId).equalTo(record::getCmdId)
            .set(cmdType).equalTo(record::getCmdType)
            .set(cmdStatus).equalTo(record::getCmdStatus)
            .set(cmdRequest).equalTo(record::getCmdRequest)
            .set(cmdReply).equalTo(record::getCmdReply);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.933+08:00",
        comments = "Source Table: binlog_polarx_command")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogPolarxCommand record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(cmdId).equalToWhenPresent(record::getCmdId)
            .set(cmdType).equalToWhenPresent(record::getCmdType)
            .set(cmdStatus).equalToWhenPresent(record::getCmdStatus)
            .set(cmdRequest).equalToWhenPresent(record::getCmdRequest)
            .set(cmdReply).equalToWhenPresent(record::getCmdReply);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.933+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int updateByPrimaryKey(BinlogPolarxCommand record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(cmdId).equalTo(record::getCmdId)
                .set(cmdType).equalTo(record::getCmdType)
                .set(cmdStatus).equalTo(record::getCmdStatus)
                .set(cmdRequest).equalTo(record::getCmdRequest)
                .set(cmdReply).equalTo(record::getCmdReply)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-10-01T17:37:42.934+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int updateByPrimaryKeySelective(BinlogPolarxCommand record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(cmdId).equalToWhenPresent(record::getCmdId)
                .set(cmdType).equalToWhenPresent(record::getCmdType)
                .set(cmdStatus).equalToWhenPresent(record::getCmdStatus)
                .set(cmdRequest).equalToWhenPresent(record::getCmdRequest)
                .set(cmdReply).equalToWhenPresent(record::getCmdReply)
                .where(id, isEqualTo(record::getId))
        );
    }
}