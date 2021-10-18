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

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class DumperInfoDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.554+08:00", comments="Source Table: binlog_dumper_info")
    public static final DumperInfo dumperInfo = new DumperInfo();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.554+08:00", comments="Source field: binlog_dumper_info.id")
    public static final SqlColumn<Long> id = dumperInfo.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = dumperInfo.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = dumperInfo.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.cluster_id")
    public static final SqlColumn<String> clusterId = dumperInfo.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.task_name")
    public static final SqlColumn<String> taskName = dumperInfo.taskName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.ip")
    public static final SqlColumn<String> ip = dumperInfo.ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.port")
    public static final SqlColumn<Integer> port = dumperInfo.port;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.role")
    public static final SqlColumn<String> role = dumperInfo.role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.status")
    public static final SqlColumn<Integer> status = dumperInfo.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.gmt_heartbeat")
    public static final SqlColumn<Date> gmtHeartbeat = dumperInfo.gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.container_id")
    public static final SqlColumn<String> containerId = dumperInfo.containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.555+08:00", comments="Source field: binlog_dumper_info.version")
    public static final SqlColumn<Long> version = dumperInfo.version;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.554+08:00", comments="Source Table: binlog_dumper_info")
    public static final class DumperInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<String> taskName = column("task_name", JDBCType.VARCHAR);

        public final SqlColumn<String> ip = column("ip", JDBCType.VARCHAR);

        public final SqlColumn<Integer> port = column("port", JDBCType.INTEGER);

        public final SqlColumn<String> role = column("role", JDBCType.VARCHAR);

        public final SqlColumn<Integer> status = column("status", JDBCType.INTEGER);

        public final SqlColumn<Date> gmtHeartbeat = column("gmt_heartbeat", JDBCType.TIMESTAMP);

        public final SqlColumn<String> containerId = column("container_id", JDBCType.VARCHAR);

        public final SqlColumn<Long> version = column("version", JDBCType.BIGINT);

        public DumperInfo() {
            super("binlog_dumper_info");
        }
    }
}