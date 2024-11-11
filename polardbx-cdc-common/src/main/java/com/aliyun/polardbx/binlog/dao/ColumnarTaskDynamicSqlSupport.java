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

public final class ColumnarTaskDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source Table: columnar_task")
    public static final ColumnarTask columnarTask = new ColumnarTask();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.id")
    public static final SqlColumn<Long> id = columnarTask.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.gmt_created")
    public static final SqlColumn<Date> gmtCreated = columnarTask.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.gmt_modified")
    public static final SqlColumn<Date> gmtModified = columnarTask.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.cluster_id")
    public static final SqlColumn<String> clusterId = columnarTask.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.task_name")
    public static final SqlColumn<String> taskName = columnarTask.taskName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.ip")
    public static final SqlColumn<String> ip = columnarTask.ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.port")
    public static final SqlColumn<Integer> port = columnarTask.port;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.role")
    public static final SqlColumn<String> role = columnarTask.role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.status")
    public static final SqlColumn<Integer> status = columnarTask.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.gmt_heartbeat")
    public static final SqlColumn<Date> gmtHeartbeat = columnarTask.gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.container_id")
    public static final SqlColumn<String> containerId = columnarTask.containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.242+08:00", comments="Source field: columnar_task.version")
    public static final SqlColumn<Long> version = columnarTask.version;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source Table: columnar_task")
    public static final class ColumnarTask extends SqlTable {
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

        public ColumnarTask() {
            super("columnar_task");
        }
    }
}