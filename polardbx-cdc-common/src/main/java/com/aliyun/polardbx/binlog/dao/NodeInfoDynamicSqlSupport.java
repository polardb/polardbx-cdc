/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class NodeInfoDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.389+08:00", comments="Source Table: binlog_node_info")
    public static final NodeInfo nodeInfo = new NodeInfo();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.389+08:00", comments="Source field: binlog_node_info.id")
    public static final SqlColumn<Long> id = nodeInfo.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.389+08:00", comments="Source field: binlog_node_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = nodeInfo.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.389+08:00", comments="Source field: binlog_node_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = nodeInfo.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.389+08:00", comments="Source field: binlog_node_info.cluster_id")
    public static final SqlColumn<String> clusterId = nodeInfo.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.389+08:00", comments="Source field: binlog_node_info.container_id")
    public static final SqlColumn<String> containerId = nodeInfo.containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.ip")
    public static final SqlColumn<String> ip = nodeInfo.ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.daemon_port")
    public static final SqlColumn<Integer> daemonPort = nodeInfo.daemonPort;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.available_ports")
    public static final SqlColumn<String> availablePorts = nodeInfo.availablePorts;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.status")
    public static final SqlColumn<Integer> status = nodeInfo.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.core")
    public static final SqlColumn<Long> core = nodeInfo.core;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.mem")
    public static final SqlColumn<Long> mem = nodeInfo.mem;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.gmt_heartbeat")
    public static final SqlColumn<Date> gmtHeartbeat = nodeInfo.gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.latest_cursor")
    public static final SqlColumn<String> latestCursor = nodeInfo.latestCursor;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.role")
    public static final SqlColumn<String> role = nodeInfo.role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.cluster_type")
    public static final SqlColumn<String> clusterType = nodeInfo.clusterType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.39+08:00", comments="Source field: binlog_node_info.group_name")
    public static final SqlColumn<String> groupName = nodeInfo.groupName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.391+08:00", comments="Source field: binlog_node_info.cluster_role")
    public static final SqlColumn<String> clusterRole = nodeInfo.clusterRole;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.391+08:00", comments="Source field: binlog_node_info.last_tso_heartbeat")
    public static final SqlColumn<Date> lastTsoHeartbeat = nodeInfo.lastTsoHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.391+08:00", comments="Source field: binlog_node_info.polarx_inst_id")
    public static final SqlColumn<String> polarxInstId = nodeInfo.polarxInstId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T11:43:39.389+08:00", comments="Source Table: binlog_node_info")
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

        public final SqlColumn<Date> gmtHeartbeat = column("gmt_heartbeat", JDBCType.TIMESTAMP);

        public final SqlColumn<String> latestCursor = column("latest_cursor", JDBCType.VARCHAR);

        public final SqlColumn<String> role = column("role", JDBCType.VARCHAR);

        public final SqlColumn<String> clusterType = column("cluster_type", JDBCType.VARCHAR);

        public final SqlColumn<String> groupName = column("group_name", JDBCType.VARCHAR);

        public final SqlColumn<String> clusterRole = column("cluster_role", JDBCType.VARCHAR);

        public final SqlColumn<Date> lastTsoHeartbeat = column("last_tso_heartbeat", JDBCType.TIMESTAMP);

        public final SqlColumn<String> polarxInstId = column("polarx_inst_id", JDBCType.VARCHAR);

        public NodeInfo() {
            super("binlog_node_info");
        }
    }
}