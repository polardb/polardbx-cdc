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

import static com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
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
public interface BinlogLogicMetaHistoryMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.37+08:00", comments="Source Table: binlog_logic_meta_history")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, tso, dbName, tableName, sqlKind, type, ddlRecordId, ddlJobId, instructionId, ddl, topology, extInfo);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.361+08:00", comments="Source Table: binlog_logic_meta_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.362+08:00", comments="Source Table: binlog_logic_meta_history")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.363+08:00", comments="Source Table: binlog_logic_meta_history")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<BinlogLogicMetaHistory> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.363+08:00", comments="Source Table: binlog_logic_meta_history")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogLogicMetaHistory> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.364+08:00", comments="Source Table: binlog_logic_meta_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Integer.class, jdbcType=JdbcType.INTEGER, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="db_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="table_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="sql_kind", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="type", javaType=Byte.class, jdbcType=JdbcType.TINYINT),
        @Arg(column="ddl_record_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="ddl_job_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="instruction_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ddl", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="topology", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="ext_info", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<BinlogLogicMetaHistory> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.365+08:00", comments="Source Table: binlog_logic_meta_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Integer.class, jdbcType=JdbcType.INTEGER, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="db_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="table_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="sql_kind", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="type", javaType=Byte.class, jdbcType=JdbcType.TINYINT),
        @Arg(column="ddl_record_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="ddl_job_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="instruction_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ddl", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="topology", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="ext_info", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<BinlogLogicMetaHistory> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.366+08:00", comments="Source Table: binlog_logic_meta_history")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.366+08:00", comments="Source Table: binlog_logic_meta_history")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogLogicMetaHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.366+08:00", comments="Source Table: binlog_logic_meta_history")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogLogicMetaHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.367+08:00", comments="Source Table: binlog_logic_meta_history")
    default int deleteByPrimaryKey(Integer id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.367+08:00", comments="Source Table: binlog_logic_meta_history")
    default int insert(BinlogLogicMetaHistory record) {
        return MyBatis3Utils.insert(this::insert, record, binlogLogicMetaHistory, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(tso).toProperty("tso")
            .map(dbName).toProperty("dbName")
            .map(tableName).toProperty("tableName")
            .map(sqlKind).toProperty("sqlKind")
            .map(type).toProperty("type")
            .map(ddlRecordId).toProperty("ddlRecordId")
            .map(ddlJobId).toProperty("ddlJobId")
            .map(instructionId).toProperty("instructionId")
            .map(ddl).toProperty("ddl")
            .map(topology).toProperty("topology")
            .map(extInfo).toProperty("extInfo")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.368+08:00", comments="Source Table: binlog_logic_meta_history")
    default int insertMultiple(Collection<BinlogLogicMetaHistory> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogLogicMetaHistory, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(tso).toProperty("tso")
            .map(dbName).toProperty("dbName")
            .map(tableName).toProperty("tableName")
            .map(sqlKind).toProperty("sqlKind")
            .map(type).toProperty("type")
            .map(ddlRecordId).toProperty("ddlRecordId")
            .map(ddlJobId).toProperty("ddlJobId")
            .map(instructionId).toProperty("instructionId")
            .map(ddl).toProperty("ddl")
            .map(topology).toProperty("topology")
            .map(extInfo).toProperty("extInfo")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.369+08:00", comments="Source Table: binlog_logic_meta_history")
    default int insertSelective(BinlogLogicMetaHistory record) {
        return MyBatis3Utils.insert(this::insert, record, binlogLogicMetaHistory, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(tso).toPropertyWhenPresent("tso", record::getTso)
            .map(dbName).toPropertyWhenPresent("dbName", record::getDbName)
            .map(tableName).toPropertyWhenPresent("tableName", record::getTableName)
            .map(sqlKind).toPropertyWhenPresent("sqlKind", record::getSqlKind)
            .map(type).toPropertyWhenPresent("type", record::getType)
            .map(ddlRecordId).toPropertyWhenPresent("ddlRecordId", record::getDdlRecordId)
            .map(ddlJobId).toPropertyWhenPresent("ddlJobId", record::getDdlJobId)
            .map(instructionId).toPropertyWhenPresent("instructionId", record::getInstructionId)
            .map(ddl).toPropertyWhenPresent("ddl", record::getDdl)
            .map(topology).toPropertyWhenPresent("topology", record::getTopology)
            .map(extInfo).toPropertyWhenPresent("extInfo", record::getExtInfo)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.37+08:00", comments="Source Table: binlog_logic_meta_history")
    default Optional<BinlogLogicMetaHistory> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogLogicMetaHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.371+08:00", comments="Source Table: binlog_logic_meta_history")
    default List<BinlogLogicMetaHistory> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogLogicMetaHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.371+08:00", comments="Source Table: binlog_logic_meta_history")
    default List<BinlogLogicMetaHistory> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogLogicMetaHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.371+08:00", comments="Source Table: binlog_logic_meta_history")
    default Optional<BinlogLogicMetaHistory> selectByPrimaryKey(Integer id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.372+08:00", comments="Source Table: binlog_logic_meta_history")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogLogicMetaHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.372+08:00", comments="Source Table: binlog_logic_meta_history")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogLogicMetaHistory record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(tso).equalTo(record::getTso)
                .set(dbName).equalTo(record::getDbName)
                .set(tableName).equalTo(record::getTableName)
                .set(sqlKind).equalTo(record::getSqlKind)
                .set(type).equalTo(record::getType)
                .set(ddlRecordId).equalTo(record::getDdlRecordId)
                .set(ddlJobId).equalTo(record::getDdlJobId)
                .set(instructionId).equalTo(record::getInstructionId)
                .set(ddl).equalTo(record::getDdl)
                .set(topology).equalTo(record::getTopology)
                .set(extInfo).equalTo(record::getExtInfo);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.372+08:00", comments="Source Table: binlog_logic_meta_history")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogLogicMetaHistory record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(dbName).equalToWhenPresent(record::getDbName)
                .set(tableName).equalToWhenPresent(record::getTableName)
                .set(sqlKind).equalToWhenPresent(record::getSqlKind)
                .set(type).equalToWhenPresent(record::getType)
                .set(ddlRecordId).equalToWhenPresent(record::getDdlRecordId)
                .set(ddlJobId).equalToWhenPresent(record::getDdlJobId)
                .set(instructionId).equalToWhenPresent(record::getInstructionId)
                .set(ddl).equalToWhenPresent(record::getDdl)
                .set(topology).equalToWhenPresent(record::getTopology)
                .set(extInfo).equalToWhenPresent(record::getExtInfo);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.373+08:00", comments="Source Table: binlog_logic_meta_history")
    default int updateByPrimaryKey(BinlogLogicMetaHistory record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(tso).equalTo(record::getTso)
            .set(dbName).equalTo(record::getDbName)
            .set(tableName).equalTo(record::getTableName)
            .set(sqlKind).equalTo(record::getSqlKind)
            .set(type).equalTo(record::getType)
            .set(ddlRecordId).equalTo(record::getDdlRecordId)
            .set(ddlJobId).equalTo(record::getDdlJobId)
            .set(instructionId).equalTo(record::getInstructionId)
            .set(ddl).equalTo(record::getDdl)
            .set(topology).equalTo(record::getTopology)
            .set(extInfo).equalTo(record::getExtInfo)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-01T19:47:23.373+08:00", comments="Source Table: binlog_logic_meta_history")
    default int updateByPrimaryKeySelective(BinlogLogicMetaHistory record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(dbName).equalToWhenPresent(record::getDbName)
            .set(tableName).equalToWhenPresent(record::getTableName)
            .set(sqlKind).equalToWhenPresent(record::getSqlKind)
            .set(type).equalToWhenPresent(record::getType)
            .set(ddlRecordId).equalToWhenPresent(record::getDdlRecordId)
            .set(ddlJobId).equalToWhenPresent(record::getDdlJobId)
            .set(instructionId).equalToWhenPresent(record::getInstructionId)
            .set(ddl).equalToWhenPresent(record::getDdl)
            .set(topology).equalToWhenPresent(record::getTopology)
            .set(extInfo).equalToWhenPresent(record::getExtInfo)
            .where(id, isEqualTo(record::getId))
        );
    }
}