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

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class UserPrivDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.504+08:00",
        comments = "Source Table: user_priv")
    public static final UserPriv userPriv = new UserPriv();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.504+08:00",
        comments = "Source field: user_priv.id")
    public static final SqlColumn<Long> id = userPriv.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.505+08:00",
        comments = "Source field: user_priv.gmt_created")
    public static final SqlColumn<Date> gmtCreated = userPriv.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.505+08:00",
        comments = "Source field: user_priv.gmt_modified")
    public static final SqlColumn<Date> gmtModified = userPriv.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.505+08:00",
        comments = "Source field: user_priv.user_name")
    public static final SqlColumn<String> userName = userPriv.userName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.505+08:00",
        comments = "Source field: user_priv.host")
    public static final SqlColumn<String> host = userPriv.host;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.505+08:00",
        comments = "Source field: user_priv.password")
    public static final SqlColumn<String> password = userPriv.password;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.505+08:00",
        comments = "Source field: user_priv.select_priv")
    public static final SqlColumn<Boolean> selectPriv = userPriv.selectPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.505+08:00",
        comments = "Source field: user_priv.insert_priv")
    public static final SqlColumn<Boolean> insertPriv = userPriv.insertPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.506+08:00",
        comments = "Source field: user_priv.update_priv")
    public static final SqlColumn<Boolean> updatePriv = userPriv.updatePriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.506+08:00",
        comments = "Source field: user_priv.delete_priv")
    public static final SqlColumn<Boolean> deletePriv = userPriv.deletePriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.506+08:00",
        comments = "Source field: user_priv.create_priv")
    public static final SqlColumn<Boolean> createPriv = userPriv.createPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.506+08:00",
        comments = "Source field: user_priv.drop_priv")
    public static final SqlColumn<Boolean> dropPriv = userPriv.dropPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.506+08:00",
        comments = "Source field: user_priv.grant_priv")
    public static final SqlColumn<Boolean> grantPriv = userPriv.grantPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.506+08:00",
        comments = "Source field: user_priv.index_priv")
    public static final SqlColumn<Boolean> indexPriv = userPriv.indexPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.506+08:00",
        comments = "Source field: user_priv.alter_priv")
    public static final SqlColumn<Boolean> alterPriv = userPriv.alterPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.506+08:00",
        comments = "Source field: user_priv.show_view_priv")
    public static final SqlColumn<Integer> showViewPriv = userPriv.showViewPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.507+08:00",
        comments = "Source field: user_priv.create_view_priv")
    public static final SqlColumn<Integer> createViewPriv = userPriv.createViewPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.507+08:00",
        comments = "Source field: user_priv.create_user_priv")
    public static final SqlColumn<Integer> createUserPriv = userPriv.createUserPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.507+08:00",
        comments = "Source field: user_priv.meta_db_priv")
    public static final SqlColumn<Integer> metaDbPriv = userPriv.metaDbPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.507+08:00",
        comments = "Source field: user_priv.account_type")
    public static final SqlColumn<Boolean> accountType = userPriv.accountType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.507+08:00",
        comments = "Source field: user_priv.show_audit_log_priv")
    public static final SqlColumn<Integer> showAuditLogPriv = userPriv.showAuditLogPriv;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T15:14:09.504+08:00",
        comments = "Source Table: user_priv")
    public static final class UserPriv extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> userName = column("user_name", JDBCType.CHAR);

        public final SqlColumn<String> host = column("host", JDBCType.CHAR);

        public final SqlColumn<String> password = column("password", JDBCType.CHAR);

        public final SqlColumn<Boolean> selectPriv = column("select_priv", JDBCType.BIT);

        public final SqlColumn<Boolean> insertPriv = column("insert_priv", JDBCType.BIT);

        public final SqlColumn<Boolean> updatePriv = column("update_priv", JDBCType.BIT);

        public final SqlColumn<Boolean> deletePriv = column("delete_priv", JDBCType.BIT);

        public final SqlColumn<Boolean> createPriv = column("create_priv", JDBCType.BIT);

        public final SqlColumn<Boolean> dropPriv = column("drop_priv", JDBCType.BIT);

        public final SqlColumn<Boolean> grantPriv = column("grant_priv", JDBCType.BIT);

        public final SqlColumn<Boolean> indexPriv = column("index_priv", JDBCType.BIT);

        public final SqlColumn<Boolean> alterPriv = column("alter_priv", JDBCType.BIT);

        public final SqlColumn<Integer> showViewPriv = column("show_view_priv", JDBCType.INTEGER);

        public final SqlColumn<Integer> createViewPriv = column("create_view_priv", JDBCType.INTEGER);

        public final SqlColumn<Integer> createUserPriv = column("create_user_priv", JDBCType.INTEGER);

        public final SqlColumn<Integer> metaDbPriv = column("meta_db_priv", JDBCType.INTEGER);

        public final SqlColumn<Boolean> accountType = column("account_type", JDBCType.BIT);

        public final SqlColumn<Integer> showAuditLogPriv = column("show_audit_log_priv", JDBCType.INTEGER);

        public UserPriv() {
            super("user_priv");
        }
    }
}