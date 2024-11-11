/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

import javax.annotation.Generated;
import java.sql.JDBCType;
import java.util.Date;

public final class DbGroupInfoDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.366+08:00",
        comments = "Source Table: db_group_info")
    public static final DbGroupInfo dbGroupInfo = new DbGroupInfo();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.367+08:00",
        comments = "Source field: db_group_info.id")
    public static final SqlColumn<Long> id = dbGroupInfo.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.367+08:00",
        comments = "Source field: db_group_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = dbGroupInfo.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.368+08:00",
        comments = "Source field: db_group_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = dbGroupInfo.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.368+08:00",
        comments = "Source field: db_group_info.db_name")
    public static final SqlColumn<String> dbName = dbGroupInfo.dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.368+08:00",
        comments = "Source field: db_group_info.group_name")
    public static final SqlColumn<String> groupName = dbGroupInfo.groupName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.368+08:00",
        comments = "Source field: db_group_info.phy_db_name")
    public static final SqlColumn<String> phyDbName = dbGroupInfo.phyDbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.369+08:00",
        comments = "Source field: db_group_info.group_type")
    public static final SqlColumn<Integer> groupType = dbGroupInfo.groupType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.367+08:00",
        comments = "Source Table: db_group_info")
    public static final class DbGroupInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> dbName = column("db_name", JDBCType.VARCHAR);

        public final SqlColumn<String> groupName = column("group_name", JDBCType.VARCHAR);

        public final SqlColumn<String> phyDbName = column("phy_db_name", JDBCType.VARCHAR);

        public final SqlColumn<Integer> groupType = column("group_type", JDBCType.INTEGER);

        public DbGroupInfo() {
            super("db_group_info");
        }
    }
}