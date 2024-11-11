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

public final class RplTaskConfigDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.627+08:00", comments="Source Table: rpl_task_config")
    public static final RplTaskConfig rplTaskConfig = new RplTaskConfig();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.63+08:00", comments="Source field: rpl_task_config.id")
    public static final SqlColumn<Long> id = rplTaskConfig.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.631+08:00", comments="Source field: rpl_task_config.gmt_created")
    public static final SqlColumn<Date> gmtCreated = rplTaskConfig.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.631+08:00", comments="Source field: rpl_task_config.gmt_modified")
    public static final SqlColumn<Date> gmtModified = rplTaskConfig.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.631+08:00", comments="Source field: rpl_task_config.task_id")
    public static final SqlColumn<Long> taskId = rplTaskConfig.taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.631+08:00", comments="Source field: rpl_task_config.memory")
    public static final SqlColumn<Integer> memory = rplTaskConfig.memory;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.631+08:00", comments="Source field: rpl_task_config.extractor_config")
    public static final SqlColumn<String> extractorConfig = rplTaskConfig.extractorConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.631+08:00", comments="Source field: rpl_task_config.pipeline_config")
    public static final SqlColumn<String> pipelineConfig = rplTaskConfig.pipelineConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.631+08:00", comments="Source field: rpl_task_config.applier_config")
    public static final SqlColumn<String> applierConfig = rplTaskConfig.applierConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-08-10T11:42:33.63+08:00", comments="Source Table: rpl_task_config")
    public static final class RplTaskConfig extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> taskId = column("task_id", JDBCType.BIGINT);

        public final SqlColumn<Integer> memory = column("memory", JDBCType.INTEGER);

        public final SqlColumn<String> extractorConfig = column("extractor_config", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> pipelineConfig = column("pipeline_config", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> applierConfig = column("applier_config", JDBCType.LONGVARCHAR);

        public RplTaskConfig() {
            super("rpl_task_config");
        }
    }
}