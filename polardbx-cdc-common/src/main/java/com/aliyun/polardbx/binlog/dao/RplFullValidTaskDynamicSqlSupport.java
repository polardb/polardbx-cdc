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

public final class RplFullValidTaskDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.755532+08:00", comments="Source Table: rpl_full_valid_task")
    public static final RplFullValidTask rplFullValidTask = new RplFullValidTask();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.755745+08:00", comments="Source field: rpl_full_valid_task.id")
    public static final SqlColumn<Long> id = rplFullValidTask.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.755946+08:00", comments="Source field: rpl_full_valid_task.state_machine_id")
    public static final SqlColumn<Long> stateMachineId = rplFullValidTask.stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.756001+08:00", comments="Source field: rpl_full_valid_task.src_logical_db")
    public static final SqlColumn<String> srcLogicalDb = rplFullValidTask.srcLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.756055+08:00", comments="Source field: rpl_full_valid_task.src_logical_table")
    public static final SqlColumn<String> srcLogicalTable = rplFullValidTask.srcLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.756115+08:00", comments="Source field: rpl_full_valid_task.dst_logical_db")
    public static final SqlColumn<String> dstLogicalDb = rplFullValidTask.dstLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.756163+08:00", comments="Source field: rpl_full_valid_task.dst_logical_table")
    public static final SqlColumn<String> dstLogicalTable = rplFullValidTask.dstLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.756211+08:00", comments="Source field: rpl_full_valid_task.task_stage")
    public static final SqlColumn<String> taskStage = rplFullValidTask.taskStage;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.75626+08:00", comments="Source field: rpl_full_valid_task.task_state")
    public static final SqlColumn<String> taskState = rplFullValidTask.taskState;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.756309+08:00", comments="Source field: rpl_full_valid_task.create_time")
    public static final SqlColumn<Date> createTime = rplFullValidTask.createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.756357+08:00", comments="Source field: rpl_full_valid_task.update_time")
    public static final SqlColumn<Date> updateTime = rplFullValidTask.updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.755678+08:00", comments="Source Table: rpl_full_valid_task")
    public static final class RplFullValidTask extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Long> stateMachineId = column("state_machine_id", JDBCType.BIGINT);

        public final SqlColumn<String> srcLogicalDb = column("src_logical_db", JDBCType.VARCHAR);

        public final SqlColumn<String> srcLogicalTable = column("src_logical_table", JDBCType.VARCHAR);

        public final SqlColumn<String> dstLogicalDb = column("dst_logical_db", JDBCType.VARCHAR);

        public final SqlColumn<String> dstLogicalTable = column("dst_logical_table", JDBCType.VARCHAR);

        public final SqlColumn<String> taskStage = column("task_stage", JDBCType.VARCHAR);

        public final SqlColumn<String> taskState = column("task_state", JDBCType.VARCHAR);

        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> updateTime = column("update_time", JDBCType.TIMESTAMP);

        public RplFullValidTask() {
            super("rpl_full_valid_task");
        }
    }
}