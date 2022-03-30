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

import static com.aliyun.polardbx.binlog.dao.PolarxCNodeInfoDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.PolarxCNodeInfo;

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
public interface PolarxCNodeInfoMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.095+08:00",
        comments = "Source Table: node_info")
    BasicColumn[] selectList = BasicColumn
        .columnList(id, cluster, instId, nodeid, version, ip, port, rpcPort, role, status, gmtCreated, gmtModified);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.081+08:00",
        comments = "Source Table: node_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.084+08:00",
        comments = "Source Table: node_info")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.085+08:00",
        comments = "Source Table: node_info")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<PolarxCNodeInfo> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.085+08:00",
        comments = "Source Table: node_info")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<PolarxCNodeInfo> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.086+08:00",
        comments = "Source Table: node_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "cluster", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "nodeid", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "version", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "ip", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "rpc_port", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "role", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "status", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP)
    })
    Optional<PolarxCNodeInfo> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.088+08:00",
        comments = "Source Table: node_info")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "cluster", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "inst_id", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "nodeid", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "version", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "ip", javaType = String.class, jdbcType = JdbcType.VARCHAR),
        @Arg(column = "port", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "rpc_port", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "role", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "status", javaType = Long.class, jdbcType = JdbcType.BIGINT),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP)
    })
    List<PolarxCNodeInfo> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.089+08:00",
        comments = "Source Table: node_info")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.089+08:00",
        comments = "Source Table: node_info")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, polarxCNodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.09+08:00",
        comments = "Source Table: node_info")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, polarxCNodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.091+08:00",
        comments = "Source Table: node_info")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.091+08:00",
        comments = "Source Table: node_info")
    default int insert(PolarxCNodeInfo record) {
        return MyBatis3Utils.insert(this::insert, record, polarxCNodeInfo, c ->
            c.map(id).toProperty("id")
                .map(cluster).toProperty("cluster")
                .map(instId).toProperty("instId")
                .map(nodeid).toProperty("nodeid")
                .map(version).toProperty("version")
                .map(ip).toProperty("ip")
                .map(port).toProperty("port")
                .map(rpcPort).toProperty("rpcPort")
                .map(role).toProperty("role")
                .map(status).toProperty("status")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.093+08:00",
        comments = "Source Table: node_info")
    default int insertMultiple(Collection<PolarxCNodeInfo> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, polarxCNodeInfo, c ->
            c.map(id).toProperty("id")
                .map(cluster).toProperty("cluster")
                .map(instId).toProperty("instId")
                .map(nodeid).toProperty("nodeid")
                .map(version).toProperty("version")
                .map(ip).toProperty("ip")
                .map(port).toProperty("port")
                .map(rpcPort).toProperty("rpcPort")
                .map(role).toProperty("role")
                .map(status).toProperty("status")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.094+08:00",
        comments = "Source Table: node_info")
    default int insertSelective(PolarxCNodeInfo record) {
        return MyBatis3Utils.insert(this::insert, record, polarxCNodeInfo, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(cluster).toPropertyWhenPresent("cluster", record::getCluster)
                .map(instId).toPropertyWhenPresent("instId", record::getInstId)
                .map(nodeid).toPropertyWhenPresent("nodeid", record::getNodeid)
                .map(version).toPropertyWhenPresent("version", record::getVersion)
                .map(ip).toPropertyWhenPresent("ip", record::getIp)
                .map(port).toPropertyWhenPresent("port", record::getPort)
                .map(rpcPort).toPropertyWhenPresent("rpcPort", record::getRpcPort)
                .map(role).toPropertyWhenPresent("role", record::getRole)
                .map(status).toPropertyWhenPresent("status", record::getStatus)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.097+08:00",
        comments = "Source Table: node_info")
    default Optional<PolarxCNodeInfo> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, polarxCNodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.098+08:00",
        comments = "Source Table: node_info")
    default List<PolarxCNodeInfo> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, polarxCNodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.099+08:00",
        comments = "Source Table: node_info")
    default List<PolarxCNodeInfo> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, polarxCNodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.099+08:00",
        comments = "Source Table: node_info")
    default Optional<PolarxCNodeInfo> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.1+08:00",
        comments = "Source Table: node_info")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, polarxCNodeInfo, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.101+08:00",
        comments = "Source Table: node_info")
    static UpdateDSL<UpdateModel> updateAllColumns(PolarxCNodeInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(cluster).equalTo(record::getCluster)
            .set(instId).equalTo(record::getInstId)
            .set(nodeid).equalTo(record::getNodeid)
            .set(version).equalTo(record::getVersion)
            .set(ip).equalTo(record::getIp)
            .set(port).equalTo(record::getPort)
            .set(rpcPort).equalTo(record::getRpcPort)
            .set(role).equalTo(record::getRole)
            .set(status).equalTo(record::getStatus)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.101+08:00",
        comments = "Source Table: node_info")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(PolarxCNodeInfo record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(cluster).equalToWhenPresent(record::getCluster)
            .set(instId).equalToWhenPresent(record::getInstId)
            .set(nodeid).equalToWhenPresent(record::getNodeid)
            .set(version).equalToWhenPresent(record::getVersion)
            .set(ip).equalToWhenPresent(record::getIp)
            .set(port).equalToWhenPresent(record::getPort)
            .set(rpcPort).equalToWhenPresent(record::getRpcPort)
            .set(role).equalToWhenPresent(record::getRole)
            .set(status).equalToWhenPresent(record::getStatus)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.102+08:00",
        comments = "Source Table: node_info")
    default int updateByPrimaryKey(PolarxCNodeInfo record) {
        return update(c ->
            c.set(cluster).equalTo(record::getCluster)
                .set(instId).equalTo(record::getInstId)
                .set(nodeid).equalTo(record::getNodeid)
                .set(version).equalTo(record::getVersion)
                .set(ip).equalTo(record::getIp)
                .set(port).equalTo(record::getPort)
                .set(rpcPort).equalTo(record::getRpcPort)
                .set(role).equalTo(record::getRole)
                .set(status).equalTo(record::getStatus)
                .set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.103+08:00",
        comments = "Source Table: node_info")
    default int updateByPrimaryKeySelective(PolarxCNodeInfo record) {
        return update(c ->
            c.set(cluster).equalToWhenPresent(record::getCluster)
                .set(instId).equalToWhenPresent(record::getInstId)
                .set(nodeid).equalToWhenPresent(record::getNodeid)
                .set(version).equalToWhenPresent(record::getVersion)
                .set(ip).equalToWhenPresent(record::getIp)
                .set(port).equalToWhenPresent(record::getPort)
                .set(rpcPort).equalToWhenPresent(record::getRpcPort)
                .set(role).equalToWhenPresent(record::getRole)
                .set(status).equalToWhenPresent(record::getStatus)
                .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .where(id, isEqualTo(record::getId))
        );
    }
}