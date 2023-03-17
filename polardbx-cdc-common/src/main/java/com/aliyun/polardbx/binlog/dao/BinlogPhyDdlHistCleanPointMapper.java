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

import static com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistCleanPointDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistCleanPoint;
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
public interface BinlogPhyDdlHistCleanPointMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.856+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, tso, storageInstId, ext);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.848+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.849+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.849+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    int insert(InsertStatementProvider<BinlogPhyDdlHistCleanPoint> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.85+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    @InsertProvider(type=SqlProviderAdapter.class, method="insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<BinlogPhyDdlHistCleanPoint> multipleInsertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.85+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Integer.class, jdbcType=JdbcType.INTEGER, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ext", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    Optional<BinlogPhyDdlHistCleanPoint> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.852+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Integer.class, jdbcType=JdbcType.INTEGER, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="tso", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="storage_inst_id", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="ext", javaType=String.class, jdbcType=JdbcType.LONGVARCHAR)
    })
    List<BinlogPhyDdlHistCleanPoint> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.852+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.852+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, binlogPhyDdlHistCleanPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.853+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, binlogPhyDdlHistCleanPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.853+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default int deleteByPrimaryKey(Integer id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.853+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default int insert(BinlogPhyDdlHistCleanPoint record) {
        return MyBatis3Utils.insert(this::insert, record, binlogPhyDdlHistCleanPoint, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(tso).toProperty("tso")
            .map(storageInstId).toProperty("storageInstId")
            .map(ext).toProperty("ext")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.855+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default int insertMultiple(Collection<BinlogPhyDdlHistCleanPoint> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, binlogPhyDdlHistCleanPoint, c ->
            c.map(id).toProperty("id")
            .map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(tso).toProperty("tso")
            .map(storageInstId).toProperty("storageInstId")
            .map(ext).toProperty("ext")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.855+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default int insertSelective(BinlogPhyDdlHistCleanPoint record) {
        return MyBatis3Utils.insert(this::insert, record, binlogPhyDdlHistCleanPoint, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
            .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(tso).toPropertyWhenPresent("tso", record::getTso)
            .map(storageInstId).toPropertyWhenPresent("storageInstId", record::getStorageInstId)
            .map(ext).toPropertyWhenPresent("ext", record::getExt)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.857+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default Optional<BinlogPhyDdlHistCleanPoint> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, binlogPhyDdlHistCleanPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.857+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default List<BinlogPhyDdlHistCleanPoint> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, binlogPhyDdlHistCleanPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.858+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default List<BinlogPhyDdlHistCleanPoint> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, binlogPhyDdlHistCleanPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.858+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default Optional<BinlogPhyDdlHistCleanPoint> selectByPrimaryKey(Integer id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.858+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, binlogPhyDdlHistCleanPoint, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.859+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    static UpdateDSL<UpdateModel> updateAllColumns(BinlogPhyDdlHistCleanPoint record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(tso).equalTo(record::getTso)
                .set(storageInstId).equalTo(record::getStorageInstId)
                .set(ext).equalTo(record::getExt);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.859+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(BinlogPhyDdlHistCleanPoint record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(tso).equalToWhenPresent(record::getTso)
                .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
                .set(ext).equalToWhenPresent(record::getExt);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.86+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default int updateByPrimaryKey(BinlogPhyDdlHistCleanPoint record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(tso).equalTo(record::getTso)
            .set(storageInstId).equalTo(record::getStorageInstId)
            .set(ext).equalTo(record::getExt)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.86+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    default int updateByPrimaryKeySelective(BinlogPhyDdlHistCleanPoint record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(tso).equalToWhenPresent(record::getTso)
            .set(storageInstId).equalToWhenPresent(record::getStorageInstId)
            .set(ext).equalToWhenPresent(record::getExt)
            .where(id, isEqualTo(record::getId))
        );
    }
}