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

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

import javax.annotation.Generated;
import java.sql.JDBCType;
import java.util.Date;

public final class BinlogTaskConfigDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.233+08:00",
        comments = "Source Table: binlog_task_config")
    public static final BinlogTaskConfig binlogTaskConfig = new BinlogTaskConfig();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.234+08:00",
        comments = "Source field: binlog_task_config.id")
    public static final SqlColumn<Long> id = binlogTaskConfig.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.235+08:00",
        comments = "Source field: binlog_task_config.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogTaskConfig.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.235+08:00",
        comments = "Source field: binlog_task_config.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogTaskConfig.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.235+08:00",
        comments = "Source field: binlog_task_config.cluster_id")
    public static final SqlColumn<String> clusterId = binlogTaskConfig.clusterId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.235+08:00",
        comments = "Source field: binlog_task_config.container_id")
    public static final SqlColumn<String> containerId = binlogTaskConfig.containerId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.235+08:00",
        comments = "Source field: binlog_task_config.task_name")
    public static final SqlColumn<String> taskName = binlogTaskConfig.taskName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.235+08:00",
        comments = "Source field: binlog_task_config.vcpu")
    public static final SqlColumn<Integer> vcpu = binlogTaskConfig.vcpu;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.235+08:00",
        comments = "Source field: binlog_task_config.mem")
    public static final SqlColumn<Integer> mem = binlogTaskConfig.mem;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.235+08:00",
        comments = "Source field: binlog_task_config.ip")
    public static final SqlColumn<String> ip = binlogTaskConfig.ip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.235+08:00",
        comments = "Source field: binlog_task_config.port")
    public static final SqlColumn<Integer> port = binlogTaskConfig.port;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.235+08:00",
        comments = "Source field: binlog_task_config.role")
    public static final SqlColumn<String> role = binlogTaskConfig.role;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.236+08:00",
        comments = "Source field: binlog_task_config.status")
    public static final SqlColumn<Integer> status = binlogTaskConfig.status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.236+08:00",
        comments = "Source field: binlog_task_config.version")
    public static final SqlColumn<Long> version = binlogTaskConfig.version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.236+08:00",
        comments = "Source field: binlog_task_config.config")
    public static final SqlColumn<String> config = binlogTaskConfig.config;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-05-20T15:40:59.234+08:00",
        comments = "Source Table: binlog_task_config")
    public static final class BinlogTaskConfig extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<String> containerId = column("container_id", JDBCType.VARCHAR);

        public final SqlColumn<String> taskName = column("task_name", JDBCType.VARCHAR);

        public final SqlColumn<Integer> vcpu = column("vcpu", JDBCType.INTEGER);

        public final SqlColumn<Integer> mem = column("mem", JDBCType.INTEGER);

        public final SqlColumn<String> ip = column("ip", JDBCType.VARCHAR);

        public final SqlColumn<Integer> port = column("port", JDBCType.INTEGER);

        public final SqlColumn<String> role = column("role", JDBCType.VARCHAR);

        public final SqlColumn<Integer> status = column("status", JDBCType.INTEGER);

        public final SqlColumn<Long> version = column("version", JDBCType.BIGINT);

        public final SqlColumn<String> config = column("config", JDBCType.LONGVARCHAR);

        public BinlogTaskConfig() {
            super("binlog_task_config");
        }
    }
}