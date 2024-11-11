/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.ColumnarCheckpointsDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.ColumnarCheckpoints;
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
public interface ColumnarCheckpointsMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.315+08:00", comments="Source Table: columnar_checkpoints")
    BasicColumn[] selectList = BasicColumn.columnList(id, logicalSchema, logicalTable, partitionName, checkpointTso, checkpointType, createTime, updateTime, offset, extra);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.309+08:00", comments="Source Table: columnar_checkpoints")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.31+08:00", comments="Source Table: columnar_checkpoints")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.31+08:00", comments="Source Table: columnar_checkpoints")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<ColumnarCheckpoints> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.31+08:00", comments="Source Table: columnar_checkpoints")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<ColumnarCheckpoints> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.311+08:00", comments="Source Table: columnar_checkpoints")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="logical_schema", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="partition_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="checkpoint_tso", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="checkpoint_type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="offset", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="extra", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<ColumnarCheckpoints> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.312+08:00", comments="Source Table: columnar_checkpoints")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="logical_schema", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="logical_table", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="partition_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="checkpoint_tso", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="checkpoint_type", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="create_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="update_time", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="offset", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR),
        @Arg(column="extra", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<ColumnarCheckpoints> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.312+08:00", comments="Source Table: columnar_checkpoints")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.312+08:00", comments="Source Table: columnar_checkpoints")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, columnarCheckpoints, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.313+08:00", comments="Source Table: columnar_checkpoints")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, columnarCheckpoints, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.313+08:00", comments="Source Table: columnar_checkpoints")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.313+08:00", comments="Source Table: columnar_checkpoints")
    default int insert(ColumnarCheckpoints record) {
        return MyBatis3Utils.insert(this::insert, record, columnarCheckpoints, c ->
            c.map(id).toProperty("id")
            .map(logicalSchema).toProperty("logicalSchema")
            .map(logicalTable).toProperty("logicalTable")
            .map(partitionName).toProperty("partitionName")
            .map(checkpointTso).toProperty("checkpointTso")
            .map(checkpointType).toProperty("checkpointType")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
            .map(offset).toProperty("offset")
            .map(extra).toProperty("extra")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.314+08:00", comments="Source Table: columnar_checkpoints")
    default int insertMultiple(Collection<ColumnarCheckpoints> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, columnarCheckpoints, c ->
            c.map(id).toProperty("id")
            .map(logicalSchema).toProperty("logicalSchema")
            .map(logicalTable).toProperty("logicalTable")
            .map(partitionName).toProperty("partitionName")
            .map(checkpointTso).toProperty("checkpointTso")
            .map(checkpointType).toProperty("checkpointType")
            .map(createTime).toProperty("createTime")
            .map(updateTime).toProperty("updateTime")
            .map(offset).toProperty("offset")
            .map(extra).toProperty("extra")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.314+08:00", comments="Source Table: columnar_checkpoints")
    default int insertSelective(ColumnarCheckpoints record) {
        return MyBatis3Utils.insert(this::insert, record, columnarCheckpoints, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(logicalSchema).toPropertyWhenPresent("logicalSchema", record::getLogicalSchema)
            .map(logicalTable).toPropertyWhenPresent("logicalTable", record::getLogicalTable)
            .map(partitionName).toPropertyWhenPresent("partitionName", record::getPartitionName)
            .map(checkpointTso).toPropertyWhenPresent("checkpointTso", record::getCheckpointTso)
            .map(checkpointType).toPropertyWhenPresent("checkpointType", record::getCheckpointType)
            .map(createTime).toPropertyWhenPresent("createTime", record::getCreateTime)
            .map(updateTime).toPropertyWhenPresent("updateTime", record::getUpdateTime)
            .map(offset).toPropertyWhenPresent("offset", record::getOffset)
            .map(extra).toPropertyWhenPresent("extra", record::getExtra)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.315+08:00", comments="Source Table: columnar_checkpoints")
    default Optional<ColumnarCheckpoints> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, columnarCheckpoints, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.316+08:00", comments="Source Table: columnar_checkpoints")
    default List<ColumnarCheckpoints> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, columnarCheckpoints, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.316+08:00", comments="Source Table: columnar_checkpoints")
    default List<ColumnarCheckpoints> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, columnarCheckpoints, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.316+08:00", comments="Source Table: columnar_checkpoints")
    default Optional<ColumnarCheckpoints> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.316+08:00", comments="Source Table: columnar_checkpoints")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, columnarCheckpoints, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.317+08:00", comments="Source Table: columnar_checkpoints")
    static UpdateDSL<UpdateModel> updateAllColumns(ColumnarCheckpoints record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(logicalSchema).equalTo(record::getLogicalSchema)
                .set(logicalTable).equalTo(record::getLogicalTable)
                .set(partitionName).equalTo(record::getPartitionName)
                .set(checkpointTso).equalTo(record::getCheckpointTso)
                .set(checkpointType).equalTo(record::getCheckpointType)
                .set(createTime).equalTo(record::getCreateTime)
                .set(updateTime).equalTo(record::getUpdateTime)
                .set(offset).equalTo(record::getOffset)
                .set(extra).equalTo(record::getExtra);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.317+08:00", comments="Source Table: columnar_checkpoints")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(ColumnarCheckpoints record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(logicalSchema).equalToWhenPresent(record::getLogicalSchema)
                .set(logicalTable).equalToWhenPresent(record::getLogicalTable)
                .set(partitionName).equalToWhenPresent(record::getPartitionName)
                .set(checkpointTso).equalToWhenPresent(record::getCheckpointTso)
                .set(checkpointType).equalToWhenPresent(record::getCheckpointType)
                .set(createTime).equalToWhenPresent(record::getCreateTime)
                .set(updateTime).equalToWhenPresent(record::getUpdateTime)
                .set(offset).equalToWhenPresent(record::getOffset)
                .set(extra).equalToWhenPresent(record::getExtra);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.317+08:00", comments="Source Table: columnar_checkpoints")
    default int updateByPrimaryKey(ColumnarCheckpoints record) {
        return update(c ->
            c.set(logicalSchema).equalTo(record::getLogicalSchema)
            .set(logicalTable).equalTo(record::getLogicalTable)
            .set(partitionName).equalTo(record::getPartitionName)
            .set(checkpointTso).equalTo(record::getCheckpointTso)
            .set(checkpointType).equalTo(record::getCheckpointType)
            .set(createTime).equalTo(record::getCreateTime)
            .set(updateTime).equalTo(record::getUpdateTime)
            .set(offset).equalTo(record::getOffset)
            .set(extra).equalTo(record::getExtra)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.318+08:00", comments="Source Table: columnar_checkpoints")
    default int updateByPrimaryKeySelective(ColumnarCheckpoints record) {
        return update(c ->
            c.set(logicalSchema).equalToWhenPresent(record::getLogicalSchema)
            .set(logicalTable).equalToWhenPresent(record::getLogicalTable)
            .set(partitionName).equalToWhenPresent(record::getPartitionName)
            .set(checkpointTso).equalToWhenPresent(record::getCheckpointTso)
            .set(checkpointType).equalToWhenPresent(record::getCheckpointType)
            .set(createTime).equalToWhenPresent(record::getCreateTime)
            .set(updateTime).equalToWhenPresent(record::getUpdateTime)
            .set(offset).equalToWhenPresent(record::getOffset)
            .set(extra).equalToWhenPresent(record::getExtra)
            .where(id, isEqualTo(record::getId))
        );
    }
}