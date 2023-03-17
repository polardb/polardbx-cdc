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

import static com.aliyun.polardbx.binlog.dao.BinlogStorageSequenceDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.BinlogStorageSequence;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Generated;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.dynamic.sql.BasicColumn;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.delete.render.DeleteStatementProvider;
import org.mybatis.dynamic.sql.insert.render.InsertStatementProvider;
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
public interface BinlogStorageSequenceMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.68+08:00", comments="Source Table: binlog_storage_sequence")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, storageInstId, storageSeq);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.668+08:00", comments="Source Table: binlog_storage_sequence")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.669+08:00", comments="Source Table: binlog_storage_sequence")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.67+08:00", comments="Source Table: binlog_storage_sequence")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    @SelectKey(statement="SELECT LAST_INSERT_ID()", keyProperty="record.id", before=false, resultType=Long.class)
    int insert(InsertStatementProvider<BinlogStorageSequence> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.673+08:00", comments="Source Table: binlog_storage_sequence")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="storage_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_seq", javaType=Long.class, jdbcType=JdbcType.BIGINT)
    })
    Optional<BinlogStorageSequence> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.674+08:00", comments="Source Table: binlog_storage_sequence")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="storage_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_seq", javaType=Long.class, jdbcType=JdbcType.BIGINT)
    })
    List<BinlogStorageSequence> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.675+08:00", comments="Source Table: binlog_storage_sequence")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.675+08:00", comments="Source Table: binlog_storage_sequence")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogStorageSequence, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.675+08:00", comments="Source Table: binlog_storage_sequence")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogStorageSequence, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.676+08:00", comments="Source Table: binlog_storage_sequence")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.676+08:00", comments="Source Table: binlog_storage_sequence")
    default int insert(BinlogStorageSequence record) {
        return MyBatis3Utils.insert(this::insert, record, binlogStorageSequence, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(storageInstId).toProperty("storageInstId")
            .map(storageSeq).toProperty("storageSeq")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.678+08:00", comments="Source Table: binlog_storage_sequence")
    default int insertSelective(BinlogStorageSequence record) {
        return MyBatis3Utils.insert(this::insert, record, binlogStorageSequence, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(storageInstId).toPropertyWhenPresent("storageInstId", record::getStorageInstId)
            .map(storageSeq).toPropertyWhenPresent("storageSeq", record::getStorageSeq)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.681+08:00", comments="Source Table: binlog_storage_sequence")
    default Optional<BinlogStorageSequence> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogStorageSequence, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.681+08:00", comments="Source Table: binlog_storage_sequence")
    default List<BinlogStorageSequence> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogStorageSequence, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.682+08:00", comments="Source Table: binlog_storage_sequence")
    default List<BinlogStorageSequence> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogStorageSequence, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.682+08:00", comments="Source Table: binlog_storage_sequence")
    default Optional<BinlogStorageSequence> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.683+08:00", comments="Source Table: binlog_storage_sequence")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogStorageSequence, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.683+08:00", comments="Source Table: binlog_storage_sequence")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogStorageSequence record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(storageInstId).equalTo(record::getStorageInstId)
                .set(storageSeq).equalTo(record::getStorageSeq);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.684+08:00", comments="Source Table: binlog_storage_sequence")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogStorageSequence record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
                .set(storageSeq).equalToWhenPresent(record::getStorageSeq);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.685+08:00", comments="Source Table: binlog_storage_sequence")
    default int updateByPrimaryKey(BinlogStorageSequence record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(storageInstId).equalTo(record::getStorageInstId)
            .set(storageSeq).equalTo(record::getStorageSeq)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.686+08:00", comments="Source Table: binlog_storage_sequence")
    default int updateByPrimaryKeySelective(BinlogStorageSequence record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
            .set(storageSeq).equalToWhenPresent(record::getStorageSeq)
            .where(id, isEqualTo(record::getId))
        );
    }
}