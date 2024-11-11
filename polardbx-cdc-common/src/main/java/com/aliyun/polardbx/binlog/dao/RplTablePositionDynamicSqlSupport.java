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

public final class RplTablePositionDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.922+08:00",
        comments = "Source Table: rpl_table_position")
    public static final RplTablePosition rplTablePosition = new RplTablePosition();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.922+08:00",
        comments = "Source field: rpl_table_position.id")
    public static final SqlColumn<Long> id = rplTablePosition.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.922+08:00",
        comments = "Source field: rpl_table_position.gmt_created")
    public static final SqlColumn<Date> gmtCreated = rplTablePosition.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.923+08:00",
        comments = "Source field: rpl_table_position.gmt_modified")
    public static final SqlColumn<Date> gmtModified = rplTablePosition.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.923+08:00",
        comments = "Source field: rpl_table_position.state_machine_id")
    public static final SqlColumn<Long> stateMachineId = rplTablePosition.stateMachineId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.924+08:00",
        comments = "Source field: rpl_table_position.service_id")
    public static final SqlColumn<Long> serviceId = rplTablePosition.serviceId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.925+08:00",
        comments = "Source field: rpl_table_position.task_id")
    public static final SqlColumn<Long> taskId = rplTablePosition.taskId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.925+08:00",
        comments = "Source field: rpl_table_position.full_table_name")
    public static final SqlColumn<String> fullTableName = rplTablePosition.fullTableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.925+08:00",
        comments = "Source field: rpl_table_position.position")
    public static final SqlColumn<String> position = rplTablePosition.position;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-08-19T17:32:16.922+08:00",
        comments = "Source Table: rpl_table_position")
    public static final class RplTablePosition extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> stateMachineId = column("state_machine_id", JDBCType.BIGINT);

        public final SqlColumn<Long> serviceId = column("service_id", JDBCType.BIGINT);

        public final SqlColumn<Long> taskId = column("task_id", JDBCType.BIGINT);

        public final SqlColumn<String> fullTableName = column("full_table_name", JDBCType.VARCHAR);

        public final SqlColumn<String> position = column("position", JDBCType.VARCHAR);

        public RplTablePosition() {
            super("rpl_table_position");
        }
    }
}