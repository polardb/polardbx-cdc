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

public final class ColumnarTaskConfigDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source Table: columnar_task_config")
    public static final ColumnarTaskConfig columnarTaskConfig = new ColumnarTaskConfig();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.id")
    public static final SqlColumn<Long> id = columnarTaskConfig.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.gmt_created")
    public static final SqlColumn<Date> gmtCreated = columnarTaskConfig.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.gmt_modified")
    public static final SqlColumn<Date> gmtModified = columnarTaskConfig.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.cluster_id")
    public static final SqlColumn<String> clusterId = columnarTaskConfig.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.container_id")
    public static final SqlColumn<String> containerId = columnarTaskConfig.containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.task_name")
    public static final SqlColumn<String> taskName = columnarTaskConfig.taskName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.vcpu")
    public static final SqlColumn<Integer> vcpu = columnarTaskConfig.vcpu;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.mem")
    public static final SqlColumn<Integer> mem = columnarTaskConfig.mem;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.ip")
    public static final SqlColumn<String> ip = columnarTaskConfig.ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.port")
    public static final SqlColumn<Integer> port = columnarTaskConfig.port;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.role")
    public static final SqlColumn<String> role = columnarTaskConfig.role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.status")
    public static final SqlColumn<Integer> status = columnarTaskConfig.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.version")
    public static final SqlColumn<Long> version = columnarTaskConfig.version;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task_config.config")
    public static final SqlColumn<String> config = columnarTaskConfig.config;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source Table: columnar_task_config")
    public static final class ColumnarTaskConfig extends SqlTable {
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

        public ColumnarTaskConfig() {
            super("columnar_task_config");
        }
    }
}