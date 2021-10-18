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

import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;
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

import static com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport.binlogPolarxCommand;
import static com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport.command;
import static com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport.ext;
import static com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport.reply;
import static com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport.type;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface BinlogPolarxCommandMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.647+08:00",
        comments = "Source Table: binlog_polarx_command")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, type, command, ext, reply);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.634+08:00",
        comments = "Source Table: binlog_polarx_command")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.636+08:00",
        comments = "Source Table: binlog_polarx_command")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.636+08:00",
        comments = "Source Table: binlog_polarx_command")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<BinlogPolarxCommand> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.637+08:00",
        comments = "Source Table: binlog_polarx_command")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogPolarxCommand> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.638+08:00",
        comments = "Source Table: binlog_polarx_command")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "type", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "command", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "ext", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "reply", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    Optional<BinlogPolarxCommand> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.64+08:00",
        comments = "Source Table: binlog_polarx_command")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "type", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "command", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "ext", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "reply", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    List<BinlogPolarxCommand> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.64+08:00",
        comments = "Source Table: binlog_polarx_command")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.641+08:00",
        comments = "Source Table: binlog_polarx_command")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.641+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.642+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.642+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int insert(BinlogPolarxCommand record) {
        return MyBatis3Utils.insert(this::insert, record, binlogPolarxCommand, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(type).toProperty("type")
                .map(command).toProperty("command")
                .map(ext).toProperty("ext")
                .map(reply).toProperty("reply")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.644+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int insertMultiple(Collection<BinlogPolarxCommand> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogPolarxCommand, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(type).toProperty("type")
                .map(command).toProperty("command")
                .map(ext).toProperty("ext")
                .map(reply).toProperty("reply")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.645+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int insertSelective(BinlogPolarxCommand record) {
        return MyBatis3Utils.insert(this::insert, record, binlogPolarxCommand, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(type).toPropertyWhenPresent("type", record::getType)
                .map(command).toPropertyWhenPresent("command", record::getCommand)
                .map(ext).toPropertyWhenPresent("ext", record::getExt)
                .map(reply).toPropertyWhenPresent("reply", record::getReply)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.648+08:00",
        comments = "Source Table: binlog_polarx_command")
    default Optional<BinlogPolarxCommand> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.648+08:00",
        comments = "Source Table: binlog_polarx_command")
    default List<BinlogPolarxCommand> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.649+08:00",
        comments = "Source Table: binlog_polarx_command")
    default List<BinlogPolarxCommand> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.65+08:00",
        comments = "Source Table: binlog_polarx_command")
    default Optional<BinlogPolarxCommand> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.65+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogPolarxCommand, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.651+08:00",
        comments = "Source Table: binlog_polarx_command")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogPolarxCommand record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(type).equalTo(record::getType)
            .set(command).equalTo(record::getCommand)
            .set(ext).equalTo(record::getExt)
            .set(reply).equalTo(record::getReply);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.651+08:00",
        comments = "Source Table: binlog_polarx_command")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogPolarxCommand record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(type).equalToWhenPresent(record::getType)
            .set(command).equalToWhenPresent(record::getCommand)
            .set(ext).equalToWhenPresent(record::getExt)
            .set(reply).equalToWhenPresent(record::getReply);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.652+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int updateByPrimaryKey(BinlogPolarxCommand record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(type).equalTo(record::getType)
                .set(command).equalTo(record::getCommand)
                .set(ext).equalTo(record::getExt)
                .set(reply).equalTo(record::getReply)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.652+08:00",
        comments = "Source Table: binlog_polarx_command")
    default int updateByPrimaryKeySelective(BinlogPolarxCommand record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(type).equalToWhenPresent(record::getType)
                .set(command).equalToWhenPresent(record::getCommand)
                .set(ext).equalToWhenPresent(record::getExt)
                .set(reply).equalToWhenPresent(record::getReply)
                .where(id, isEqualTo(record::getId))
        );
    }
}