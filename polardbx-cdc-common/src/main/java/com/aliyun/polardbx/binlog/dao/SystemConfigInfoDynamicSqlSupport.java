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

public final class SystemConfigInfoDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.614+08:00",
        comments = "Source Table: binlog_system_config")
    public static final SystemConfigInfo systemConfigInfo = new SystemConfigInfo();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.615+08:00",
        comments = "Source field: binlog_system_config.id")
    public static final SqlColumn<Long> id = systemConfigInfo.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.615+08:00",
        comments = "Source field: binlog_system_config.gmt_created")
    public static final SqlColumn<Date> gmtCreated = systemConfigInfo.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.616+08:00",
        comments = "Source field: binlog_system_config.gmt_modified")
    public static final SqlColumn<Date> gmtModified = systemConfigInfo.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.616+08:00",
        comments = "Source field: binlog_system_config.config_key")
    public static final SqlColumn<String> configKey = systemConfigInfo.configKey;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.616+08:00",
        comments = "Source field: binlog_system_config.config_value")
    public static final SqlColumn<String> configValue = systemConfigInfo.configValue;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.615+08:00",
        comments = "Source Table: binlog_system_config")
    public static final class SystemConfigInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> configKey = column("config_key", JDBCType.VARCHAR);

        public final SqlColumn<String> configValue = column("config_value", JDBCType.LONGVARCHAR);

        public SystemConfigInfo() {
            super("binlog_system_config");
        }
    }
}