/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.UserPrivDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.UserPriv;

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
public interface UserPrivMapper {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.52+08:00",
        comments = "Source Table: user_priv")
    BasicColumn[] selectList = BasicColumn
        .columnList(id, gmtCreated, gmtModified, userName, host, password, selectPriv, insertPriv, updatePriv,
            deletePriv, createPriv, dropPriv, grantPriv, indexPriv, alterPriv, showViewPriv, createViewPriv,
            createUserPriv, metaDbPriv, accountType, showAuditLogPriv);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.508+08:00",
        comments = "Source Table: user_priv")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.509+08:00",
        comments = "Source Table: user_priv")
    @DeleteProvider(type = SqlProviderAdapter.class, method = "delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.51+08:00",
        comments = "Source Table: user_priv")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insert")
    int insert(InsertStatementProvider<UserPriv> insertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.51+08:00",
        comments = "Source Table: user_priv")
    @InsertProvider(type = SqlProviderAdapter.class, method = "insertMultiple")
    int insertMultiple(MultiRowInsertStatementProvider<UserPriv> multipleInsertStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.511+08:00",
        comments = "Source Table: user_priv")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "user_name", javaType = String.class, jdbcType = JdbcType.CHAR),
        @Arg(column = "host", javaType = String.class, jdbcType = JdbcType.CHAR),
        @Arg(column = "password", javaType = String.class, jdbcType = JdbcType.CHAR),
        @Arg(column = "select_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "insert_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "update_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "delete_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "create_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "drop_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "grant_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "index_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "alter_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "show_view_priv", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "create_view_priv", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "create_user_priv", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "meta_db_priv", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "account_type", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "show_audit_log_priv", javaType = Integer.class, jdbcType = JdbcType.INTEGER)
    })
    Optional<UserPriv> selectOne(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.513+08:00",
        comments = "Source Table: user_priv")
    @SelectProvider(type = SqlProviderAdapter.class, method = "select")
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class, jdbcType = JdbcType.BIGINT, id = true),
        @Arg(column = "gmt_created", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "gmt_modified", javaType = Date.class, jdbcType = JdbcType.TIMESTAMP),
        @Arg(column = "user_name", javaType = String.class, jdbcType = JdbcType.CHAR),
        @Arg(column = "host", javaType = String.class, jdbcType = JdbcType.CHAR),
        @Arg(column = "password", javaType = String.class, jdbcType = JdbcType.CHAR),
        @Arg(column = "select_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "insert_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "update_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "delete_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "create_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "drop_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "grant_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "index_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "alter_priv", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "show_view_priv", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "create_view_priv", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "create_user_priv", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "meta_db_priv", javaType = Integer.class, jdbcType = JdbcType.INTEGER),
        @Arg(column = "account_type", javaType = Boolean.class, jdbcType = JdbcType.BIT),
        @Arg(column = "show_audit_log_priv", javaType = Integer.class, jdbcType = JdbcType.INTEGER)
    })
    List<UserPriv> selectMany(SelectStatementProvider selectStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.514+08:00",
        comments = "Source Table: user_priv")
    @UpdateProvider(type = SqlProviderAdapter.class, method = "update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.514+08:00",
        comments = "Source Table: user_priv")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, userPriv, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.514+08:00",
        comments = "Source Table: user_priv")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, userPriv, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.515+08:00",
        comments = "Source Table: user_priv")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.515+08:00",
        comments = "Source Table: user_priv")
    default int insert(UserPriv record) {
        return MyBatis3Utils.insert(this::insert, record, userPriv, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(userName).toProperty("userName")
                .map(host).toProperty("host")
                .map(password).toProperty("password")
                .map(selectPriv).toProperty("selectPriv")
                .map(insertPriv).toProperty("insertPriv")
                .map(updatePriv).toProperty("updatePriv")
                .map(deletePriv).toProperty("deletePriv")
                .map(createPriv).toProperty("createPriv")
                .map(dropPriv).toProperty("dropPriv")
                .map(grantPriv).toProperty("grantPriv")
                .map(indexPriv).toProperty("indexPriv")
                .map(alterPriv).toProperty("alterPriv")
                .map(showViewPriv).toProperty("showViewPriv")
                .map(createViewPriv).toProperty("createViewPriv")
                .map(createUserPriv).toProperty("createUserPriv")
                .map(metaDbPriv).toProperty("metaDbPriv")
                .map(accountType).toProperty("accountType")
                .map(showAuditLogPriv).toProperty("showAuditLogPriv")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.517+08:00",
        comments = "Source Table: user_priv")
    default int insertMultiple(Collection<UserPriv> records) {
        return MyBatis3Utils.insertMultiple(this::insertMultiple, records, userPriv, c ->
            c.map(id).toProperty("id")
                .map(gmtCreated).toProperty("gmtCreated")
                .map(gmtModified).toProperty("gmtModified")
                .map(userName).toProperty("userName")
                .map(host).toProperty("host")
                .map(password).toProperty("password")
                .map(selectPriv).toProperty("selectPriv")
                .map(insertPriv).toProperty("insertPriv")
                .map(updatePriv).toProperty("updatePriv")
                .map(deletePriv).toProperty("deletePriv")
                .map(createPriv).toProperty("createPriv")
                .map(dropPriv).toProperty("dropPriv")
                .map(grantPriv).toProperty("grantPriv")
                .map(indexPriv).toProperty("indexPriv")
                .map(alterPriv).toProperty("alterPriv")
                .map(showViewPriv).toProperty("showViewPriv")
                .map(createViewPriv).toProperty("createViewPriv")
                .map(createUserPriv).toProperty("createUserPriv")
                .map(metaDbPriv).toProperty("metaDbPriv")
                .map(accountType).toProperty("accountType")
                .map(showAuditLogPriv).toProperty("showAuditLogPriv")
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.518+08:00",
        comments = "Source Table: user_priv")
    default int insertSelective(UserPriv record) {
        return MyBatis3Utils.insert(this::insert, record, userPriv, c ->
            c.map(id).toPropertyWhenPresent("id", record::getId)
                .map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
                .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
                .map(userName).toPropertyWhenPresent("userName", record::getUserName)
                .map(host).toPropertyWhenPresent("host", record::getHost)
                .map(password).toPropertyWhenPresent("password", record::getPassword)
                .map(selectPriv).toPropertyWhenPresent("selectPriv", record::getSelectPriv)
                .map(insertPriv).toPropertyWhenPresent("insertPriv", record::getInsertPriv)
                .map(updatePriv).toPropertyWhenPresent("updatePriv", record::getUpdatePriv)
                .map(deletePriv).toPropertyWhenPresent("deletePriv", record::getDeletePriv)
                .map(createPriv).toPropertyWhenPresent("createPriv", record::getCreatePriv)
                .map(dropPriv).toPropertyWhenPresent("dropPriv", record::getDropPriv)
                .map(grantPriv).toPropertyWhenPresent("grantPriv", record::getGrantPriv)
                .map(indexPriv).toPropertyWhenPresent("indexPriv", record::getIndexPriv)
                .map(alterPriv).toPropertyWhenPresent("alterPriv", record::getAlterPriv)
                .map(showViewPriv).toPropertyWhenPresent("showViewPriv", record::getShowViewPriv)
                .map(createViewPriv).toPropertyWhenPresent("createViewPriv", record::getCreateViewPriv)
                .map(createUserPriv).toPropertyWhenPresent("createUserPriv", record::getCreateUserPriv)
                .map(metaDbPriv).toPropertyWhenPresent("metaDbPriv", record::getMetaDbPriv)
                .map(accountType).toPropertyWhenPresent("accountType", record::getAccountType)
                .map(showAuditLogPriv).toPropertyWhenPresent("showAuditLogPriv", record::getShowAuditLogPriv)
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.521+08:00",
        comments = "Source Table: user_priv")
    default Optional<UserPriv> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, userPriv, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.522+08:00",
        comments = "Source Table: user_priv")
    default List<UserPriv> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, userPriv, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.522+08:00",
        comments = "Source Table: user_priv")
    default List<UserPriv> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, userPriv, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.522+08:00",
        comments = "Source Table: user_priv")
    default Optional<UserPriv> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.523+08:00",
        comments = "Source Table: user_priv")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, userPriv, completer);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.524+08:00",
        comments = "Source Table: user_priv")
    static UpdateDSL<UpdateModel> updateAllColumns(UserPriv record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalTo(record::getId)
            .set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(userName).equalTo(record::getUserName)
            .set(host).equalTo(record::getHost)
            .set(password).equalTo(record::getPassword)
            .set(selectPriv).equalTo(record::getSelectPriv)
            .set(insertPriv).equalTo(record::getInsertPriv)
            .set(updatePriv).equalTo(record::getUpdatePriv)
            .set(deletePriv).equalTo(record::getDeletePriv)
            .set(createPriv).equalTo(record::getCreatePriv)
            .set(dropPriv).equalTo(record::getDropPriv)
            .set(grantPriv).equalTo(record::getGrantPriv)
            .set(indexPriv).equalTo(record::getIndexPriv)
            .set(alterPriv).equalTo(record::getAlterPriv)
            .set(showViewPriv).equalTo(record::getShowViewPriv)
            .set(createViewPriv).equalTo(record::getCreateViewPriv)
            .set(createUserPriv).equalTo(record::getCreateUserPriv)
            .set(metaDbPriv).equalTo(record::getMetaDbPriv)
            .set(accountType).equalTo(record::getAccountType)
            .set(showAuditLogPriv).equalTo(record::getShowAuditLogPriv);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.524+08:00",
        comments = "Source Table: user_priv")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(UserPriv record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(id).equalToWhenPresent(record::getId)
            .set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(userName).equalToWhenPresent(record::getUserName)
            .set(host).equalToWhenPresent(record::getHost)
            .set(password).equalToWhenPresent(record::getPassword)
            .set(selectPriv).equalToWhenPresent(record::getSelectPriv)
            .set(insertPriv).equalToWhenPresent(record::getInsertPriv)
            .set(updatePriv).equalToWhenPresent(record::getUpdatePriv)
            .set(deletePriv).equalToWhenPresent(record::getDeletePriv)
            .set(createPriv).equalToWhenPresent(record::getCreatePriv)
            .set(dropPriv).equalToWhenPresent(record::getDropPriv)
            .set(grantPriv).equalToWhenPresent(record::getGrantPriv)
            .set(indexPriv).equalToWhenPresent(record::getIndexPriv)
            .set(alterPriv).equalToWhenPresent(record::getAlterPriv)
            .set(showViewPriv).equalToWhenPresent(record::getShowViewPriv)
            .set(createViewPriv).equalToWhenPresent(record::getCreateViewPriv)
            .set(createUserPriv).equalToWhenPresent(record::getCreateUserPriv)
            .set(metaDbPriv).equalToWhenPresent(record::getMetaDbPriv)
            .set(accountType).equalToWhenPresent(record::getAccountType)
            .set(showAuditLogPriv).equalToWhenPresent(record::getShowAuditLogPriv);
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.525+08:00",
        comments = "Source Table: user_priv")
    default int updateByPrimaryKey(UserPriv record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(userName).equalTo(record::getUserName)
                .set(host).equalTo(record::getHost)
                .set(password).equalTo(record::getPassword)
                .set(selectPriv).equalTo(record::getSelectPriv)
                .set(insertPriv).equalTo(record::getInsertPriv)
                .set(updatePriv).equalTo(record::getUpdatePriv)
                .set(deletePriv).equalTo(record::getDeletePriv)
                .set(createPriv).equalTo(record::getCreatePriv)
                .set(dropPriv).equalTo(record::getDropPriv)
                .set(grantPriv).equalTo(record::getGrantPriv)
                .set(indexPriv).equalTo(record::getIndexPriv)
                .set(alterPriv).equalTo(record::getAlterPriv)
                .set(showViewPriv).equalTo(record::getShowViewPriv)
                .set(createViewPriv).equalTo(record::getCreateViewPriv)
                .set(createUserPriv).equalTo(record::getCreateUserPriv)
                .set(metaDbPriv).equalTo(record::getMetaDbPriv)
                .set(accountType).equalTo(record::getAccountType)
                .set(showAuditLogPriv).equalTo(record::getShowAuditLogPriv)
                .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.526+08:00",
        comments = "Source Table: user_priv")
    default int updateByPrimaryKeySelective(UserPriv record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(userName).equalToWhenPresent(record::getUserName)
                .set(host).equalToWhenPresent(record::getHost)
                .set(password).equalToWhenPresent(record::getPassword)
                .set(selectPriv).equalToWhenPresent(record::getSelectPriv)
                .set(insertPriv).equalToWhenPresent(record::getInsertPriv)
                .set(updatePriv).equalToWhenPresent(record::getUpdatePriv)
                .set(deletePriv).equalToWhenPresent(record::getDeletePriv)
                .set(createPriv).equalToWhenPresent(record::getCreatePriv)
                .set(dropPriv).equalToWhenPresent(record::getDropPriv)
                .set(grantPriv).equalToWhenPresent(record::getGrantPriv)
                .set(indexPriv).equalToWhenPresent(record::getIndexPriv)
                .set(alterPriv).equalToWhenPresent(record::getAlterPriv)
                .set(showViewPriv).equalToWhenPresent(record::getShowViewPriv)
                .set(createViewPriv).equalToWhenPresent(record::getCreateViewPriv)
                .set(createUserPriv).equalToWhenPresent(record::getCreateUserPriv)
                .set(metaDbPriv).equalToWhenPresent(record::getMetaDbPriv)
                .set(accountType).equalToWhenPresent(record::getAccountType)
                .set(showAuditLogPriv).equalToWhenPresent(record::getShowAuditLogPriv)
                .where(id, isEqualTo(record::getId))
        );
    }
}