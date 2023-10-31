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

import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
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

import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.binlogFile;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.binlogOssRecord;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.clusterId;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.groupId;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.lastTso;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.logBegin;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.logEnd;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.logSize;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.purgeStatus;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.streamId;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.uploadHost;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.uploadStatus;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface BinlogOssRecordMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.534+08:00",
        comments = "Source Table: binlog_oss_record")
    BasicColumn[] selectList =
        BasicColumn.columnList(id, gmtCreated, gmtModified, binlogFile, uploadStatus, purgeStatus, uploadHost, logBegin,
            logEnd, logSize, lastTso, groupId, streamId, clusterId);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.527+08:00",
        comments = "Source Table: binlog_oss_record")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.528+08:00",
        comments = "Source Table: binlog_oss_record")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.528+08:00",
        comments = "Source Table: binlog_oss_record")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<BinlogOssRecord> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.528+08:00",
        comments = "Source Table: binlog_oss_record")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogOssRecord> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.529+08:00",
        comments = "Source Table: binlog_oss_record")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Integer.class, jdbcType = JdbcType.INTEGER, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "binlog_file", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "upload_status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "purge_status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "upload_host", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "log_begin", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "log_end", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "log_size", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "last_tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "group_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "stream_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cluster_id", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    Optional<BinlogOssRecord> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.529+08:00",
        comments = "Source Table: binlog_oss_record")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Integer.class, jdbcType = JdbcType.INTEGER, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "binlog_file", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "upload_status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "purge_status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "upload_host", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "log_begin", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "log_end", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "log_size", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "last_tso", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "group_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "stream_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cluster_id", javaType = String.class, jdbcType = JdbcType.VARCHAR)
    })
    List<BinlogOssRecord> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.53+08:00",
        comments = "Source Table: binlog_oss_record")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.531+08:00",
        comments = "Source Table: binlog_oss_record")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogOssRecord, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.531+08:00",
        comments = "Source Table: binlog_oss_record")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogOssRecord, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.531+08:00",
        comments = "Source Table: binlog_oss_record")
    default int deleteByPrimaryKey(Integer id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.532+08:00",
        comments = "Source Table: binlog_oss_record")
    default int insert(BinlogOssRecord record) {
        return MyBatis3Utils.insert(this::insert, record, binlogOssRecord, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(binlogFile).toProperty("binlogFile")
                .map(uploadStatus).toProperty("uploadStatus")
                .map(purgeStatus).toProperty("purgeStatus")
                .map(uploadHost).toProperty("uploadHost")
                .map(logBegin).toProperty("logBegin")
                .map(logEnd).toProperty("logEnd")
                .map(logSize).toProperty("logSize")
                .map(lastTso).toProperty("lastTso")
                .map(groupId).toProperty("groupId")
                .map(streamId).toProperty("streamId")
                .map(clusterId).toProperty("clusterId")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.533+08:00",
        comments = "Source Table: binlog_oss_record")
    default int insertMultiple(Collection<BinlogOssRecord> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogOssRecord, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(binlogFile).toProperty("binlogFile")
                .map(uploadStatus).toProperty("uploadStatus")
                .map(purgeStatus).toProperty("purgeStatus")
                .map(uploadHost).toProperty("uploadHost")
                .map(logBegin).toProperty("logBegin")
                .map(logEnd).toProperty("logEnd")
                .map(logSize).toProperty("logSize")
                .map(lastTso).toProperty("lastTso")
                .map(groupId).toProperty("groupId")
                .map(streamId).toProperty("streamId")
                .map(clusterId).toProperty("clusterId")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.533+08:00",
        comments = "Source Table: binlog_oss_record")
    default int insertSelective(BinlogOssRecord record) {
        return MyBatis3Utils.insert(this::insert, record, binlogOssRecord, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(binlogFile).toPropertyWhenPresent("binlogFile", record::getBinlogFile)
                .map(uploadStatus).toPropertyWhenPresent("uploadStatus", record::getUploadStatus)
                .map(purgeStatus).toPropertyWhenPresent("purgeStatus", record::getPurgeStatus)
                .map(uploadHost).toPropertyWhenPresent("uploadHost", record::getUploadHost)
                .map(logBegin).toPropertyWhenPresent("logBegin", record::getLogBegin)
                .map(logEnd).toPropertyWhenPresent("logEnd", record::getLogEnd)
                .map(logSize).toPropertyWhenPresent("logSize", record::getLogSize)
                .map(lastTso).toPropertyWhenPresent("lastTso", record::getLastTso)
                .map(groupId).toPropertyWhenPresent("groupId", record::getGroupId)
                .map(streamId).toPropertyWhenPresent("streamId", record::getStreamId)
                .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.534+08:00",
        comments = "Source Table: binlog_oss_record")
    default Optional<BinlogOssRecord> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogOssRecord, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.534+08:00",
        comments = "Source Table: binlog_oss_record")
    default List<BinlogOssRecord> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogOssRecord, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.535+08:00",
        comments = "Source Table: binlog_oss_record")
    default List<BinlogOssRecord> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogOssRecord, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.535+08:00",
        comments = "Source Table: binlog_oss_record")
    default Optional<BinlogOssRecord> selectByPrimaryKey(Integer id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.535+08:00",
        comments = "Source Table: binlog_oss_record")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogOssRecord, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.535+08:00",
        comments = "Source Table: binlog_oss_record")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogOssRecord record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(binlogFile).equalTo(record::getBinlogFile)
            .set(uploadStatus).equalTo(record::getUploadStatus)
            .set(purgeStatus).equalTo(record::getPurgeStatus)
            .set(uploadHost).equalTo(record::getUploadHost)
            .set(logBegin).equalTo(record::getLogBegin)
            .set(logEnd).equalTo(record::getLogEnd)
            .set(logSize).equalTo(record::getLogSize)
            .set(lastTso).equalTo(record::getLastTso)
            .set(groupId).equalTo(record::getGroupId)
            .set(streamId).equalTo(record::getStreamId)
            .set(clusterId).equalTo(record::getClusterId);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.536+08:00",
        comments = "Source Table: binlog_oss_record")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogOssRecord record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(binlogFile).equalToWhenPresent(record::getBinlogFile)
            .set(uploadStatus).equalToWhenPresent(record::getUploadStatus)
            .set(purgeStatus).equalToWhenPresent(record::getPurgeStatus)
            .set(uploadHost).equalToWhenPresent(record::getUploadHost)
            .set(logBegin).equalToWhenPresent(record::getLogBegin)
            .set(logEnd).equalToWhenPresent(record::getLogEnd)
            .set(logSize).equalToWhenPresent(record::getLogSize)
            .set(lastTso).equalToWhenPresent(record::getLastTso)
            .set(groupId).equalToWhenPresent(record::getGroupId)
            .set(streamId).equalToWhenPresent(record::getStreamId)
            .set(clusterId).equalToWhenPresent(record::getClusterId);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.536+08:00",
        comments = "Source Table: binlog_oss_record")
    default int updateByPrimaryKey(BinlogOssRecord record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(binlogFile).equalTo(record::getBinlogFile)
                .set(uploadStatus).equalTo(record::getUploadStatus)
                .set(purgeStatus).equalTo(record::getPurgeStatus)
                .set(uploadHost).equalTo(record::getUploadHost)
                .set(logBegin).equalTo(record::getLogBegin)
                .set(logEnd).equalTo(record::getLogEnd)
                .set(logSize).equalTo(record::getLogSize)
                .set(lastTso).equalTo(record::getLastTso)
                .set(groupId).equalTo(record::getGroupId)
                .set(streamId).equalTo(record::getStreamId)
                .set(clusterId).equalTo(record::getClusterId)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.537+08:00",
        comments = "Source Table: binlog_oss_record")
    default int updateByPrimaryKeySelective(BinlogOssRecord record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(binlogFile).equalToWhenPresent(record::getBinlogFile)
                .set(uploadStatus).equalToWhenPresent(record::getUploadStatus)
                .set(purgeStatus).equalToWhenPresent(record::getPurgeStatus)
                .set(uploadHost).equalToWhenPresent(record::getUploadHost)
                .set(logBegin).equalToWhenPresent(record::getLogBegin)
                .set(logEnd).equalToWhenPresent(record::getLogEnd)
                .set(logSize).equalToWhenPresent(record::getLogSize)
                .set(lastTso).equalToWhenPresent(record::getLastTso)
                .set(groupId).equalToWhenPresent(record::getGroupId)
                .set(streamId).equalToWhenPresent(record::getStreamId)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .where(id, isEqualTo(record::getId))
        );
    }
}