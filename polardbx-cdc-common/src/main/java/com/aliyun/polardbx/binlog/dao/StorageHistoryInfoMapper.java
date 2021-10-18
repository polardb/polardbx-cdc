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

import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
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

import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.instructionId;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.status;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.storageContent;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.storageHistoryInfo;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.tso;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface StorageHistoryInfoMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.237+08:00",
        comments = "Source Table: binlog_storage_history")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, tso, status, instructionId, storageContent);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.226+08:00",
        comments = "Source Table: binlog_storage_history")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.227+08:00",
        comments = "Source Table: binlog_storage_history")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.228+08:00",
        comments = "Source Table: binlog_storage_history")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<StorageHistoryInfo> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.229+08:00",
        comments = "Source Table: binlog_storage_history")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<StorageHistoryInfo> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.229+08:00",
        comments = "Source Table: binlog_storage_history")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "instruction_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "storage_content", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    Optional<StorageHistoryInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.231+08:00",
        comments = "Source Table: binlog_storage_history")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "instruction_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "storage_content", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    List<StorageHistoryInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.232+08:00",
        comments = "Source Table: binlog_storage_history")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.232+08:00",
        comments = "Source Table: binlog_storage_history")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, storageHistoryInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.232+08:00",
        comments = "Source Table: binlog_storage_history")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, storageHistoryInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.233+08:00",
        comments = "Source Table: binlog_storage_history")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.233+08:00",
        comments = "Source Table: binlog_storage_history")
    default int insert(StorageHistoryInfo record) {
        return MyBatis3Utils.insert(this::insert, record, storageHistoryInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(tso).toProperty("tso")
                .map(status).toProperty("status")
                .map(instructionId).toProperty("instructionId")
                .map(storageContent).toProperty("storageContent")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.235+08:00",
        comments = "Source Table: binlog_storage_history")
    default int insertMultiple(Collection<StorageHistoryInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, storageHistoryInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(tso).toProperty("tso")
                .map(status).toProperty("status")
                .map(instructionId).toProperty("instructionId")
                .map(storageContent).toProperty("storageContent")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.236+08:00",
        comments = "Source Table: binlog_storage_history")
    default int insertSelective(StorageHistoryInfo record) {
        return MyBatis3Utils.insert(this::insert, record, storageHistoryInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(tso).toPropertyWhenPresent("tso", record::getTso)
                .map(status).toPropertyWhenPresent("status", record::getStatus)
                .map(instructionId).toPropertyWhenPresent("instructionId", record::getInstructionId)
                .map(storageContent).toPropertyWhenPresent("storageContent", record::getStorageContent)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.238+08:00",
        comments = "Source Table: binlog_storage_history")
    default Optional<StorageHistoryInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, storageHistoryInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.239+08:00",
        comments = "Source Table: binlog_storage_history")
    default List<StorageHistoryInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, storageHistoryInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.239+08:00",
        comments = "Source Table: binlog_storage_history")
    default List<StorageHistoryInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, storageHistoryInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.24+08:00",
        comments = "Source Table: binlog_storage_history")
    default Optional<StorageHistoryInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.24+08:00",
        comments = "Source Table: binlog_storage_history")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, storageHistoryInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.241+08:00",
        comments = "Source Table: binlog_storage_history")
    static UpdateDSL<UpdateModel> updateAllColumns(StorageHistoryInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(tso).equalTo(record::getTso)
            .set(status).equalTo(record::getStatus)
            .set(instructionId).equalTo(record::getInstructionId)
            .set(storageContent).equalTo(record::getStorageContent);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.241+08:00",
        comments = "Source Table: binlog_storage_history")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(StorageHistoryInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(instructionId).equalToWhenPresent(record::getInstructionId)
            .set(storageContent).equalToWhenPresent(record::getStorageContent);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.242+08:00",
        comments = "Source Table: binlog_storage_history")
    default int updateByPrimaryKey(StorageHistoryInfo record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(tso).equalTo(record::getTso)
                .set(status).equalTo(record::getStatus)
                .set(instructionId).equalTo(record::getInstructionId)
                .set(storageContent).equalTo(record::getStorageContent)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-26T21:06:05.243+08:00",
        comments = "Source Table: binlog_storage_history")
    default int updateByPrimaryKeySelective(StorageHistoryInfo record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(instructionId).equalToWhenPresent(record::getInstructionId)
                .set(storageContent).equalToWhenPresent(record::getStorageContent)
                .where(id, isEqualTo(record::getId))
        );
    }
}