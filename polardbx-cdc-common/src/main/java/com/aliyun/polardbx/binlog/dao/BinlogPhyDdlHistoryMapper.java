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

import static com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
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
public interface BinlogPhyDdlHistoryMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.719+08:00", comments="Source Table: binlog_phy_ddl_history")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, tso, binlogFile, pos, storageInstId, dbName, extra, clusterId, ddl);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.711+08:00", comments="Source Table: binlog_phy_ddl_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.712+08:00", comments="Source Table: binlog_phy_ddl_history")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.712+08:00", comments="Source Table: binlog_phy_ddl_history")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<BinlogPhyDdlHistory> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.712+08:00", comments="Source Table: binlog_phy_ddl_history")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogPhyDdlHistory> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.713+08:00", comments="Source Table: binlog_phy_ddl_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Integer.class, jdbcType=JdbcType.INTEGER, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="binlog_file", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="pos", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="storage_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="db_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="extra", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ddl", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<BinlogPhyDdlHistory> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.714+08:00", comments="Source Table: binlog_phy_ddl_history")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Integer.class, jdbcType=JdbcType.INTEGER, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="binlog_file", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="pos", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="storage_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="db_name", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="extra", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="cluster_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ddl", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<BinlogPhyDdlHistory> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.715+08:00", comments="Source Table: binlog_phy_ddl_history")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.715+08:00", comments="Source Table: binlog_phy_ddl_history")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogPhyDdlHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.715+08:00", comments="Source Table: binlog_phy_ddl_history")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogPhyDdlHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.716+08:00", comments="Source Table: binlog_phy_ddl_history")
    default int deleteByPrimaryKey(Integer id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.716+08:00", comments="Source Table: binlog_phy_ddl_history")
    default int insert(BinlogPhyDdlHistory record) {
        return MyBatis3Utils.insert(this::insert, record, binlogPhyDdlHistory, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(tso).toProperty("tso")
            .map(binlogFile).toProperty("binlogFile")
            .map(pos).toProperty("pos")
            .map(storageInstId).toProperty("storageInstId")
            .map(dbName).toProperty("dbName")
            .map(extra).toProperty("extra")
            .map(clusterId).toProperty("clusterId")
            .map(ddl).toProperty("ddl")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.717+08:00", comments="Source Table: binlog_phy_ddl_history")
    default int insertMultiple(Collection<BinlogPhyDdlHistory> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogPhyDdlHistory, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(tso).toProperty("tso")
            .map(binlogFile).toProperty("binlogFile")
            .map(pos).toProperty("pos")
            .map(storageInstId).toProperty("storageInstId")
            .map(dbName).toProperty("dbName")
            .map(extra).toProperty("extra")
            .map(clusterId).toProperty("clusterId")
            .map(ddl).toProperty("ddl")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.718+08:00", comments="Source Table: binlog_phy_ddl_history")
    default int insertSelective(BinlogPhyDdlHistory record) {
        return MyBatis3Utils.insert(this::insert, record, binlogPhyDdlHistory, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(tso).toPropertyWhenPresent("tso", record::getTso)
            .map(binlogFile).toPropertyWhenPresent("binlogFile", record::getBinlogFile)
            .map(pos).toPropertyWhenPresent("pos", record::getPos)
            .map(storageInstId).toPropertyWhenPresent("storageInstId", record::getStorageInstId)
            .map(dbName).toPropertyWhenPresent("dbName", record::getDbName)
            .map(extra).toPropertyWhenPresent("extra", record::getExtra)
            .map(clusterId).toPropertyWhenPresent("clusterId", record::getClusterId)
            .map(ddl).toPropertyWhenPresent("ddl", record::getDdl)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.72+08:00", comments="Source Table: binlog_phy_ddl_history")
    default Optional<BinlogPhyDdlHistory> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogPhyDdlHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.72+08:00", comments="Source Table: binlog_phy_ddl_history")
    default List<BinlogPhyDdlHistory> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogPhyDdlHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.72+08:00", comments="Source Table: binlog_phy_ddl_history")
    default List<BinlogPhyDdlHistory> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogPhyDdlHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.721+08:00", comments="Source Table: binlog_phy_ddl_history")
    default Optional<BinlogPhyDdlHistory> selectByPrimaryKey(Integer id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.721+08:00", comments="Source Table: binlog_phy_ddl_history")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogPhyDdlHistory, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.721+08:00", comments="Source Table: binlog_phy_ddl_history")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogPhyDdlHistory record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(tso).equalTo(record::getTso)
                .set(binlogFile).equalTo(record::getBinlogFile)
                .set(pos).equalTo(record::getPos)
                .set(storageInstId).equalTo(record::getStorageInstId)
                .set(dbName).equalTo(record::getDbName)
                .set(extra).equalTo(record::getExtra)
                .set(clusterId).equalTo(record::getClusterId)
                .set(ddl).equalTo(record::getDdl);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.722+08:00", comments="Source Table: binlog_phy_ddl_history")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogPhyDdlHistory record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(binlogFile).equalToWhenPresent(record::getBinlogFile)
                .set(pos).equalToWhenPresent(record::getPos)
                .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
                .set(dbName).equalToWhenPresent(record::getDbName)
                .set(extra).equalToWhenPresent(record::getExtra)
                .set(clusterId).equalToWhenPresent(record::getClusterId)
                .set(ddl).equalToWhenPresent(record::getDdl);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.722+08:00", comments="Source Table: binlog_phy_ddl_history")
    default int updateByPrimaryKey(BinlogPhyDdlHistory record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(tso).equalTo(record::getTso)
            .set(binlogFile).equalTo(record::getBinlogFile)
            .set(pos).equalTo(record::getPos)
            .set(storageInstId).equalTo(record::getStorageInstId)
            .set(dbName).equalTo(record::getDbName)
            .set(extra).equalTo(record::getExtra)
            .set(clusterId).equalTo(record::getClusterId)
            .set(ddl).equalTo(record::getDdl)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-07-06T17:08:09.723+08:00", comments="Source Table: binlog_phy_ddl_history")
    default int updateByPrimaryKeySelective(BinlogPhyDdlHistory record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(binlogFile).equalToWhenPresent(record::getBinlogFile)
            .set(pos).equalToWhenPresent(record::getPos)
            .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
            .set(dbName).equalToWhenPresent(record::getDbName)
            .set(extra).equalToWhenPresent(record::getExtra)
            .set(clusterId).equalToWhenPresent(record::getClusterId)
            .set(ddl).equalToWhenPresent(record::getDdl)
            .where(id, isEqualTo(record::getId))
        );
    }
}