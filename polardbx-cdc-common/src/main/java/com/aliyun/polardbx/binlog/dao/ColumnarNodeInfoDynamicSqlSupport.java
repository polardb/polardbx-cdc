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

public final class ColumnarNodeInfoDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    public static final ColumnarNodeInfo columnarNodeInfo = new ColumnarNodeInfo();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.id")
    public static final SqlColumn<Long> id = columnarNodeInfo.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = columnarNodeInfo.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = columnarNodeInfo.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.cluster_id")
    public static final SqlColumn<String> clusterId = columnarNodeInfo.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.container_id")
    public static final SqlColumn<String> containerId = columnarNodeInfo.containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.ip")
    public static final SqlColumn<String> ip = columnarNodeInfo.ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.daemon_port")
    public static final SqlColumn<Integer> daemonPort = columnarNodeInfo.daemonPort;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.available_ports")
    public static final SqlColumn<String> availablePorts = columnarNodeInfo.availablePorts;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.status")
    public static final SqlColumn<Integer> status = columnarNodeInfo.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.core")
    public static final SqlColumn<Long> core = columnarNodeInfo.core;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.mem")
    public static final SqlColumn<Long> mem = columnarNodeInfo.mem;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.gmt_heartbeat")
    public static final SqlColumn<Date> gmtHeartbeat = columnarNodeInfo.gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.latest_cursor")
    public static final SqlColumn<String> latestCursor = columnarNodeInfo.latestCursor;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.role")
    public static final SqlColumn<String> role = columnarNodeInfo.role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.cluster_type")
    public static final SqlColumn<String> clusterType = columnarNodeInfo.clusterType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source field: columnar_node_info.group_name")
    public static final SqlColumn<String> groupName = columnarNodeInfo.groupName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.24+08:00", comments="Source Table: columnar_node_info")
    public static final class ColumnarNodeInfo extends SqlTable {
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

        public final SqlColumn<Date> gmtHeartbeat = column("gmt_heartbeat", JDBCType.TIMESTAMP);

        public final SqlColumn<String> latestCursor = column("latest_cursor", JDBCType.VARCHAR);

        public final SqlColumn<String> role = column("role", JDBCType.VARCHAR);

        public final SqlColumn<String> clusterType = column("cluster_type", JDBCType.VARCHAR);

        public final SqlColumn<String> groupName = column("group_name", JDBCType.VARCHAR);

        public ColumnarNodeInfo() {
            super("columnar_node_info");
        }
    }
}