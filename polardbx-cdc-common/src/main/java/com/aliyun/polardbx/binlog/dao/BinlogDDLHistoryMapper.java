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

import com.aliyun.polardbx.binlog.domain.po.BinlogDDLHistory;
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

import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.binlogDDLHistory;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.binlogFile;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.dbName;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.ddl;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.extra;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.pos;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.storageInstId;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.tableName;
import static com.aliyun.polardbx.binlog.dao.BinlogDDLHistoryDynamicSqlSupport.tso;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface BinlogDDLHistoryMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.405+08:00",
        comments = "Source Table: binlog_ddl_history")
    BasicColumn[] selectList = BasicColumn
        .columnList(id, gmtCreated, gmtModified, tso, binlogFile, pos, storageInstId, dbName, tableName, extra, ddl);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source Table: binlog_ddl_history")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source Table: binlog_ddl_history")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source Table: binlog_ddl_history")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<BinlogDDLHistory> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source Table: binlog_ddl_history")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogDDLHistory> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.404+08:00",
        comments = "Source Table: binlog_ddl_history")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Integer.class, jdbcType = JdbcType.INTEGER, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "binlog_file", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "pos", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "storage_inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "extra", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "ddl", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    Optional<BinlogDDLHistory> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.404+08:00",
        comments = "Source Table: binlog_ddl_history")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Integer.class, jdbcType = JdbcType.INTEGER, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "binlog_file", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "pos", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "storage_inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "db_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "table_name", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "extra", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "ddl", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    List<BinlogDDLHistory> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.404+08:00",
        comments = "Source Table: binlog_ddl_history")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.404+08:00",
        comments = "Source Table: binlog_ddl_history")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogDDLHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.404+08:00",
        comments = "Source Table: binlog_ddl_history")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogDDLHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.404+08:00",
        comments = "Source Table: binlog_ddl_history")
    default int deleteByPrimaryKey(Integer id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.404+08:00",
        comments = "Source Table: binlog_ddl_history")
    default int insert(BinlogDDLHistory record) {
        return MyBatis3Utils.insert(this::insert, record, binlogDDLHistory, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(tso).toProperty("tso")
                .map(binlogFile).toProperty("binlogFile")
                .map(pos).toProperty("pos")
                .map(storageInstId).toProperty("storageInstId")
                .map(dbName).toProperty("dbName")
                .map(tableName).toProperty("tableName")
                .map(extra).toProperty("extra")
                .map(ddl).toProperty("ddl")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.404+08:00",
        comments = "Source Table: binlog_ddl_history")
    default int insertMultiple(Collection<BinlogDDLHistory> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogDDLHistory, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(tso).toProperty("tso")
                .map(binlogFile).toProperty("binlogFile")
                .map(pos).toProperty("pos")
                .map(storageInstId).toProperty("storageInstId")
                .map(dbName).toProperty("dbName")
                .map(tableName).toProperty("tableName")
                .map(extra).toProperty("extra")
                .map(ddl).toProperty("ddl")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.405+08:00",
        comments = "Source Table: binlog_ddl_history")
    default int insertSelective(BinlogDDLHistory record) {
        return MyBatis3Utils.insert(this::insert, record, binlogDDLHistory, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(tso).toPropertyWhenPresent("tso", record::getTso)
                .map(binlogFile).toPropertyWhenPresent("binlogFile", record::getBinlogFile)
                .map(pos).toPropertyWhenPresent("pos", record::getPos)
                .map(storageInstId).toPropertyWhenPresent("storageInstId", record::getStorageInstId)
                .map(dbName).toPropertyWhenPresent("dbName", record::getDbName)
                .map(tableName).toPropertyWhenPresent("tableName", record::getTableName)
                .map(extra).toPropertyWhenPresent("extra", record::getExtra)
                .map(ddl).toPropertyWhenPresent("ddl", record::getDdl)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.405+08:00",
        comments = "Source Table: binlog_ddl_history")
    default Optional<BinlogDDLHistory> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogDDLHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.405+08:00",
        comments = "Source Table: binlog_ddl_history")
    default List<BinlogDDLHistory> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogDDLHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.405+08:00",
        comments = "Source Table: binlog_ddl_history")
    default List<BinlogDDLHistory> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogDDLHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.405+08:00",
        comments = "Source Table: binlog_ddl_history")
    default Optional<BinlogDDLHistory> selectByPrimaryKey(Integer id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.405+08:00",
        comments = "Source Table: binlog_ddl_history")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogDDLHistory, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.405+08:00",
        comments = "Source Table: binlog_ddl_history")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogDDLHistory record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(tso).equalTo(record::getTso)
            .set(binlogFile).equalTo(record::getBinlogFile)
            .set(pos).equalTo(record::getPos)
            .set(storageInstId).equalTo(record::getStorageInstId)
            .set(dbName).equalTo(record::getDbName)
            .set(tableName).equalTo(record::getTableName)
            .set(extra).equalTo(record::getExtra)
            .set(ddl).equalTo(record::getDdl);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.405+08:00",
        comments = "Source Table: binlog_ddl_history")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogDDLHistory record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(binlogFile).equalToWhenPresent(record::getBinlogFile)
            .set(pos).equalToWhenPresent(record::getPos)
            .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
            .set(dbName).equalToWhenPresent(record::getDbName)
            .set(tableName).equalToWhenPresent(record::getTableName)
            .set(extra).equalToWhenPresent(record::getExtra)
            .set(ddl).equalToWhenPresent(record::getDdl);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.405+08:00",
        comments = "Source Table: binlog_ddl_history")
    default int updateByPrimaryKey(BinlogDDLHistory record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(tso).equalTo(record::getTso)
                .set(binlogFile).equalTo(record::getBinlogFile)
                .set(pos).equalTo(record::getPos)
                .set(storageInstId).equalTo(record::getStorageInstId)
                .set(dbName).equalTo(record::getDbName)
                .set(tableName).equalTo(record::getTableName)
                .set(extra).equalTo(record::getExtra)
                .set(ddl).equalTo(record::getDdl)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.406+08:00",
        comments = "Source Table: binlog_ddl_history")
    default int updateByPrimaryKeySelective(BinlogDDLHistory record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(binlogFile).equalToWhenPresent(record::getBinlogFile)
                .set(pos).equalToWhenPresent(record::getPos)
                .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
                .set(dbName).equalToWhenPresent(record::getDbName)
                .set(tableName).equalToWhenPresent(record::getTableName)
                .set(extra).equalToWhenPresent(record::getExtra)
                .set(ddl).equalToWhenPresent(record::getDdl)
                .where(id, isEqualTo(record::getId))
        );
    }
}