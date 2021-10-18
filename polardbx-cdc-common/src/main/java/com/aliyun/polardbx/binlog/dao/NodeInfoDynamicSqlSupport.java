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

public final class NodeInfoDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.297+08:00", comments="Source Table: binlog_node_info")
    public static final NodeInfo nodeInfo = new NodeInfo();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.298+08:00", comments="Source field: binlog_node_info.id")
    public static final SqlColumn<Long> id = nodeInfo.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.298+08:00", comments="Source field: binlog_node_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = nodeInfo.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.298+08:00", comments="Source field: binlog_node_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = nodeInfo.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.298+08:00", comments="Source field: binlog_node_info.cluster_id")
    public static final SqlColumn<String> clusterId = nodeInfo.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.298+08:00", comments="Source field: binlog_node_info.container_id")
    public static final SqlColumn<String> containerId = nodeInfo.containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.298+08:00", comments="Source field: binlog_node_info.ip")
    public static final SqlColumn<String> ip = nodeInfo.ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.298+08:00", comments="Source field: binlog_node_info.daemon_port")
    public static final SqlColumn<Integer> daemonPort = nodeInfo.daemonPort;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.298+08:00", comments="Source field: binlog_node_info.available_ports")
    public static final SqlColumn<String> availablePorts = nodeInfo.availablePorts;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.299+08:00", comments="Source field: binlog_node_info.status")
    public static final SqlColumn<Integer> status = nodeInfo.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.299+08:00", comments="Source field: binlog_node_info.core")
    public static final SqlColumn<Long> core = nodeInfo.core;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.299+08:00", comments="Source field: binlog_node_info.mem")
    public static final SqlColumn<Long> mem = nodeInfo.mem;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.299+08:00", comments="Source field: binlog_node_info.role")
    public static final SqlColumn<String> role = nodeInfo.role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.299+08:00", comments="Source field: binlog_node_info.gmt_heartbeat")
    public static final SqlColumn<Date> gmtHeartbeat = nodeInfo.gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.299+08:00", comments="Source field: binlog_node_info.latest_cursor")
    public static final SqlColumn<String> latestCursor = nodeInfo.latestCursor;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-06-08T14:48:37.297+08:00", comments="Source Table: binlog_node_info")
    public static final class NodeInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<String> containerId = column("container_id", JDBCType.VARCHAR);

        public final SqlColumn<String> ip = column("ip", JDBCType.VARCHAR);

        public final SqlColumn<Integer> daemonPort = column("daemon_port", JDBCType.INTEGER);

        public final SqlColumn<String> availablePorts = column("available_ports", JDBCType.VARCHAR);

        public final SqlColumn<Integer> status = column("status", JDBCType.INTEGER);

        public final SqlColumn<Long> core = column("core", JDBCType.BIGINT);

        public final SqlColumn<Long> mem = column("mem", JDBCType.BIGINT);

        public final SqlColumn<String> role = column("role", JDBCType.VARCHAR);

        public final SqlColumn<Date> gmtHeartbeat = column("gmt_heartbeat", JDBCType.TIMESTAMP);

        public final SqlColumn<String> latestCursor = column("latest_cursor", JDBCType.VARCHAR);

        public NodeInfo() {
            super("binlog_node_info");
        }
    }
}