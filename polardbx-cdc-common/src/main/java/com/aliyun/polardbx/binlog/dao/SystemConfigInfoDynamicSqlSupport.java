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