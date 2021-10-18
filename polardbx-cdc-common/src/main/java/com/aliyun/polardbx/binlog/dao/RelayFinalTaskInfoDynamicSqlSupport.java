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

public final class RelayFinalTaskInfoDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source Table: binlog_task_info")
    public static final RelayFinalTaskInfo relayFinalTaskInfo = new RelayFinalTaskInfo();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.id")
    public static final SqlColumn<Long> id = relayFinalTaskInfo.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = relayFinalTaskInfo.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = relayFinalTaskInfo.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.cluster_id")
    public static final SqlColumn<String> clusterId = relayFinalTaskInfo.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.task_name")
    public static final SqlColumn<String> taskName = relayFinalTaskInfo.taskName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.ip")
    public static final SqlColumn<String> ip = relayFinalTaskInfo.ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.port")
    public static final SqlColumn<Integer> port = relayFinalTaskInfo.port;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.role")
    public static final SqlColumn<String> role = relayFinalTaskInfo.role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.status")
    public static final SqlColumn<Integer> status = relayFinalTaskInfo.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.gmt_heartbeat")
    public static final SqlColumn<Date> gmtHeartbeat = relayFinalTaskInfo.gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.container_id")
    public static final SqlColumn<String> containerId = relayFinalTaskInfo.containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source field: binlog_task_info.version")
    public static final SqlColumn<Long> version = relayFinalTaskInfo.version;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-22T21:03:05.579+08:00", comments="Source Table: binlog_task_info")
    public static final class RelayFinalTaskInfo extends SqlTable {
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

        public RelayFinalTaskInfo() {
            super("binlog_task_info");
        }
    }
}