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

public final class RplFullValidSubTaskDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764132+08:00", comments="Source Table: rpl_full_valid_sub_task")
    public static final RplFullValidSubTask rplFullValidSubTask = new RplFullValidSubTask();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76423+08:00", comments="Source field: rpl_full_valid_sub_task.id")
    public static final SqlColumn<Long> id = rplFullValidSubTask.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764285+08:00", comments="Source field: rpl_full_valid_sub_task.state_machine_id")
    public static final SqlColumn<Long> stateMachineId = rplFullValidSubTask.stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76432+08:00", comments="Source field: rpl_full_valid_sub_task.task_id")
    public static final SqlColumn<Long> taskId = rplFullValidSubTask.taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76439+08:00", comments="Source field: rpl_full_valid_sub_task.task_stage")
    public static final SqlColumn<String> taskStage = rplFullValidSubTask.taskStage;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764466+08:00", comments="Source field: rpl_full_valid_sub_task.task_state")
    public static final SqlColumn<String> taskState = rplFullValidSubTask.taskState;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764512+08:00", comments="Source field: rpl_full_valid_sub_task.task_type")
    public static final SqlColumn<String> taskType = rplFullValidSubTask.taskType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764552+08:00", comments="Source field: rpl_full_valid_sub_task.create_time")
    public static final SqlColumn<Date> createTime = rplFullValidSubTask.createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76459+08:00", comments="Source field: rpl_full_valid_sub_task.update_time")
    public static final SqlColumn<Date> updateTime = rplFullValidSubTask.updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764623+08:00", comments="Source field: rpl_full_valid_sub_task.task_config")
    public static final SqlColumn<String> taskConfig = rplFullValidSubTask.taskConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764661+08:00", comments="Source field: rpl_full_valid_sub_task.summary")
    public static final SqlColumn<String> summary = rplFullValidSubTask.summary;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764194+08:00", comments="Source Table: rpl_full_valid_sub_task")
    public static final class RplFullValidSubTask extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Long> stateMachineId = column("state_machine_id", JDBCType.BIGINT);

        public final SqlColumn<Long> taskId = column("task_id", JDBCType.BIGINT);

        public final SqlColumn<String> taskStage = column("task_stage", JDBCType.VARCHAR);

        public final SqlColumn<String> taskState = column("task_state", JDBCType.VARCHAR);

        public final SqlColumn<String> taskType = column("task_type", JDBCType.VARCHAR);

        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> updateTime = column("update_time", JDBCType.TIMESTAMP);

        public final SqlColumn<String> taskConfig = column("task_config", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> summary = column("summary", JDBCType.LONGVARCHAR);

        public RplFullValidSubTask() {
            super("rpl_full_valid_sub_task");
        }
    }
}