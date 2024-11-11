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

public final class RplTaskDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.969+08:00", comments="Source Table: rpl_task")
    public static final RplTask rplTask = new RplTask();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.969+08:00", comments="Source field: rpl_task.id")
    public static final SqlColumn<Long> id = rplTask.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.97+08:00", comments="Source field: rpl_task.gmt_created")
    public static final SqlColumn<Date> gmtCreated = rplTask.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.971+08:00", comments="Source field: rpl_task.gmt_modified")
    public static final SqlColumn<Date> gmtModified = rplTask.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.971+08:00", comments="Source field: rpl_task.gmt_heartbeat")
    public static final SqlColumn<Date> gmtHeartbeat = rplTask.gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.971+08:00", comments="Source field: rpl_task.status")
    public static final SqlColumn<String> status = rplTask.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.971+08:00", comments="Source field: rpl_task.service_id")
    public static final SqlColumn<Long> serviceId = rplTask.serviceId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.971+08:00", comments="Source field: rpl_task.state_machine_id")
    public static final SqlColumn<Long> stateMachineId = rplTask.stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.971+08:00", comments="Source field: rpl_task.type")
    public static final SqlColumn<String> type = rplTask.type;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.971+08:00", comments="Source field: rpl_task.master_host")
    public static final SqlColumn<String> masterHost = rplTask.masterHost;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.971+08:00", comments="Source field: rpl_task.master_port")
    public static final SqlColumn<Integer> masterPort = rplTask.masterPort;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.971+08:00", comments="Source field: rpl_task.position")
    public static final SqlColumn<String> position = rplTask.position;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.971+08:00", comments="Source field: rpl_task.worker")
    public static final SqlColumn<String> worker = rplTask.worker;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.972+08:00", comments="Source field: rpl_task.cluster_id")
    public static final SqlColumn<String> clusterId = rplTask.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.972+08:00", comments="Source field: rpl_task.extractor_config")
    public static final SqlColumn<String> extractorConfig = rplTask.extractorConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.972+08:00", comments="Source field: rpl_task.pipeline_config")
    public static final SqlColumn<String> pipelineConfig = rplTask.pipelineConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.972+08:00", comments="Source field: rpl_task.applier_config")
    public static final SqlColumn<String> applierConfig = rplTask.applierConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.972+08:00", comments="Source field: rpl_task.last_error")
    public static final SqlColumn<String> lastError = rplTask.lastError;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.972+08:00", comments="Source field: rpl_task.statistic")
    public static final SqlColumn<String> statistic = rplTask.statistic;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.972+08:00", comments="Source field: rpl_task.extra")
    public static final SqlColumn<String> extra = rplTask.extra;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.969+08:00", comments="Source Table: rpl_task")
    public static final class RplTask extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtHeartbeat = column("gmt_heartbeat", JDBCType.TIMESTAMP);

        public final SqlColumn<String> status = column("`status`", JDBCType.VARCHAR);

        public final SqlColumn<Long> serviceId = column("service_id", JDBCType.BIGINT);

        public final SqlColumn<Long> stateMachineId = column("state_machine_id", JDBCType.BIGINT);

        public final SqlColumn<String> type = column("`type`", JDBCType.VARCHAR);

        public final SqlColumn<String> masterHost = column("master_host", JDBCType.VARCHAR);

        public final SqlColumn<Integer> masterPort = column("master_port", JDBCType.INTEGER);

        public final SqlColumn<String> position = column("`position`", JDBCType.VARCHAR);

        public final SqlColumn<String> worker = column("worker", JDBCType.VARCHAR);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<String> extractorConfig = column("extractor_config", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> pipelineConfig = column("pipeline_config", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> applierConfig = column("applier_config", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> lastError = column("last_error", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> statistic = column("statistic", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> extra = column("extra", JDBCType.LONGVARCHAR);

        public RplTask() {
            super("rpl_task");
        }
    }
}