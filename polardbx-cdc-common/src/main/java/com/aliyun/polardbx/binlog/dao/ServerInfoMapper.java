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

import com.aliyun.polardbx.binlog.domain.po.ServerInfo;
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

import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.azoneId;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.cpuCore;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.extras;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.gmtModified;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.htapPort;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.idcId;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instId;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instType;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.ip;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.memSize;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.mgrPort;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.mppPort;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.port;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.regionId;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.serverInfo;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Mapper
public interface ServerInfoMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.337+08:00",
        comments = "Source Table: server_info")
    BasicColumn[] selectList = BasicColumn
        .columnList(id, gmtCreated, gmtModified, instId, instType, ip, port, htapPort, mgrPort, mppPort, status,
            regionId, azoneId, idcId, cpuCore, memSize, extras);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.327+08:00",
        comments = "Source Table: server_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.328+08:00",
        comments = "Source Table: server_info")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.328+08:00",
        comments = "Source Table: server_info")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<ServerInfo> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.329+08:00",
        comments = "Source Table: server_info")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<ServerInfo> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.33+08:00",
        comments = "Source Table: server_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "inst_type", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "ip", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "htap_port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "mgr_port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "mpp_port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "region_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "azone_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "idc_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cpu_core", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "mem_size", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "extras", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    Optional<ServerInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.331+08:00",
        comments = "Source Table: server_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "inst_type", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "ip", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "htap_port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "mgr_port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "mpp_port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "status", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "region_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "azone_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "idc_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "cpu_core", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "mem_size", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "extras", javaType = String.class, jdbcType = JdbcType.LONGVARCHAR)
    })
    List<ServerInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.332+08:00",
        comments = "Source Table: server_info")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.332+08:00",
        comments = "Source Table: server_info")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, serverInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.333+08:00",
        comments = "Source Table: server_info")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, serverInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.333+08:00",
        comments = "Source Table: server_info")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.334+08:00",
        comments = "Source Table: server_info")
    default int insert(ServerInfo record) {
        return MyBatis3Utils.insert(this::insert, record, serverInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(instId).toProperty("instId")
                .map(instType).toProperty("instType")
                .map(ip).toProperty("ip")
                .map(port).toProperty("port")
                .map(htapPort).toProperty("htapPort")
                .map(mgrPort).toProperty("mgrPort")
                .map(mppPort).toProperty("mppPort")
                .map(status).toProperty("status")
                .map(regionId).toProperty("regionId")
                .map(azoneId).toProperty("azoneId")
                .map(idcId).toProperty("idcId")
                .map(cpuCore).toProperty("cpuCore")
                .map(memSize).toProperty("memSize")
                .map(extras).toProperty("extras")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.335+08:00",
        comments = "Source Table: server_info")
    default int insertMultiple(Collection<ServerInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, serverInfo, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(instId).toProperty("instId")
                .map(instType).toProperty("instType")
                .map(ip).toProperty("ip")
                .map(port).toProperty("port")
                .map(htapPort).toProperty("htapPort")
                .map(mgrPort).toProperty("mgrPort")
                .map(mppPort).toProperty("mppPort")
                .map(status).toProperty("status")
                .map(regionId).toProperty("regionId")
                .map(azoneId).toProperty("azoneId")
                .map(idcId).toProperty("idcId")
                .map(cpuCore).toProperty("cpuCore")
                .map(memSize).toProperty("memSize")
                .map(extras).toProperty("extras")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.336+08:00",
        comments = "Source Table: server_info")
    default int insertSelective(ServerInfo record) {
        return MyBatis3Utils.insert(this::insert, record, serverInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(instId).toPropertyWhenPresent("instId", record::getInstId)
                .map(instType).toPropertyWhenPresent("instType", record::getInstType)
                .map(ip).toPropertyWhenPresent("ip", record::getIp)
                .map(port).toPropertyWhenPresent("port", record::getPort)
                .map(htapPort).toPropertyWhenPresent("htapPort", record::getHtapPort)
                .map(mgrPort).toPropertyWhenPresent("mgrPort", record::getMgrPort)
                .map(mppPort).toPropertyWhenPresent("mppPort", record::getMppPort)
                .map(status).toPropertyWhenPresent("status", record::getStatus)
                .map(regionId).toPropertyWhenPresent("regionId", record::getRegionId)
                .map(azoneId).toPropertyWhenPresent("azoneId", record::getAzoneId)
                .map(idcId).toPropertyWhenPresent("idcId", record::getIdcId)
                .map(cpuCore).toPropertyWhenPresent("cpuCore", record::getCpuCore)
                .map(memSize).toPropertyWhenPresent("memSize", record::getMemSize)
                .map(extras).toPropertyWhenPresent("extras", record::getExtras)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.338+08:00",
        comments = "Source Table: server_info")
    default Optional<ServerInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, serverInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.339+08:00",
        comments = "Source Table: server_info")
    default List<ServerInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, serverInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.339+08:00",
        comments = "Source Table: server_info")
    default List<ServerInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, serverInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.34+08:00",
        comments = "Source Table: server_info")
    default Optional<ServerInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.34+08:00",
        comments = "Source Table: server_info")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, serverInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.341+08:00",
        comments = "Source Table: server_info")
    static UpdateDSL<UpdateModel> updateAllColumns(ServerInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(instId).equalTo(record::getInstId)
            .set(instType).equalTo(record::getInstType)
            .set(ip).equalTo(record::getIp)
            .set(port).equalTo(record::getPort)
            .set(htapPort).equalTo(record::getHtapPort)
            .set(mgrPort).equalTo(record::getMgrPort)
            .set(mppPort).equalTo(record::getMppPort)
            .set(status).equalTo(record::getStatus)
            .set(regionId).equalTo(record::getRegionId)
            .set(azoneId).equalTo(record::getAzoneId)
            .set(idcId).equalTo(record::getIdcId)
            .set(cpuCore).equalTo(record::getCpuCore)
            .set(memSize).equalTo(record::getMemSize)
            .set(extras).equalTo(record::getExtras);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.341+08:00",
        comments = "Source Table: server_info")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(ServerInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(instId).equalToWhenPresent(record::getInstId)
            .set(instType).equalToWhenPresent(record::getInstType)
            .set(ip).equalToWhenPresent(record::getIp)
            .set(port).equalToWhenPresent(record::getPort)
            .set(htapPort).equalToWhenPresent(record::getHtapPort)
            .set(mgrPort).equalToWhenPresent(record::getMgrPort)
            .set(mppPort).equalToWhenPresent(record::getMppPort)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(regionId).equalToWhenPresent(record::getRegionId)
            .set(azoneId).equalToWhenPresent(record::getAzoneId)
            .set(idcId).equalToWhenPresent(record::getIdcId)
            .set(cpuCore).equalToWhenPresent(record::getCpuCore)
            .set(memSize).equalToWhenPresent(record::getMemSize)
            .set(extras).equalToWhenPresent(record::getExtras);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.342+08:00",
        comments = "Source Table: server_info")
    default int updateByPrimaryKey(ServerInfo record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(instId).equalTo(record::getInstId)
                .set(instType).equalTo(record::getInstType)
                .set(ip).equalTo(record::getIp)
                .set(port).equalTo(record::getPort)
                .set(htapPort).equalTo(record::getHtapPort)
                .set(mgrPort).equalTo(record::getMgrPort)
                .set(mppPort).equalTo(record::getMppPort)
                .set(status).equalTo(record::getStatus)
                .set(regionId).equalTo(record::getRegionId)
                .set(azoneId).equalTo(record::getAzoneId)
                .set(idcId).equalTo(record::getIdcId)
                .set(cpuCore).equalTo(record::getCpuCore)
                .set(memSize).equalTo(record::getMemSize)
                .set(extras).equalTo(record::getExtras)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.342+08:00",
        comments = "Source Table: server_info")
    default int updateByPrimaryKeySelective(ServerInfo record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(instId).equalToWhenPresent(record::getInstId)
                .set(instType).equalToWhenPresent(record::getInstType)
                .set(ip).equalToWhenPresent(record::getIp)
                .set(port).equalToWhenPresent(record::getPort)
                .set(htapPort).equalToWhenPresent(record::getHtapPort)
                .set(mgrPort).equalToWhenPresent(record::getMgrPort)
                .set(mppPort).equalToWhenPresent(record::getMppPort)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(regionId).equalToWhenPresent(record::getRegionId)
                .set(azoneId).equalToWhenPresent(record::getAzoneId)
                .set(idcId).equalToWhenPresent(record::getIdcId)
                .set(cpuCore).equalToWhenPresent(record::getCpuCore)
                .set(memSize).equalToWhenPresent(record::getMemSize)
                .set(extras).equalToWhenPresent(record::getExtras)
                .where(id, isEqualTo(record::getId))
        );
    }
}