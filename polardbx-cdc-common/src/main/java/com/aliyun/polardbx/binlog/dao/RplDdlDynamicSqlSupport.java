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

public final class RplDdlDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.564+08:00",
        comments = "Source Table: rpl_ddl_main")
    public static final RplDdl rplDdl = new RplDdl();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.567+08:00",
        comments = "Source field: rpl_ddl_main.id")
    public static final SqlColumn<Long> id = rplDdl.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.567+08:00",
        comments = "Source field: rpl_ddl_main.gmt_created")
    public static final SqlColumn<Date> gmtCreated = rplDdl.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.567+08:00",
        comments = "Source field: rpl_ddl_main.gmt_modified")
    public static final SqlColumn<Date> gmtModified = rplDdl.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.567+08:00",
        comments = "Source field: rpl_ddl_main.fsm_id")
    public static final SqlColumn<Long> fsmId = rplDdl.fsmId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.567+08:00",
        comments = "Source field: rpl_ddl_main.ddl_tso")
    public static final SqlColumn<String> ddlTso = rplDdl.ddlTso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.567+08:00",
        comments = "Source field: rpl_ddl_main.service_id")
    public static final SqlColumn<Long> serviceId = rplDdl.serviceId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.567+08:00",
        comments = "Source field: rpl_ddl_main.token")
    public static final SqlColumn<String> token = rplDdl.token;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.568+08:00",
        comments = "Source field: rpl_ddl_main.state")
    public static final SqlColumn<String> state = rplDdl.state;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.568+08:00",
        comments = "Source field: rpl_ddl_main.async_flag")
    public static final SqlColumn<Boolean> asyncFlag = rplDdl.asyncFlag;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.568+08:00",
        comments = "Source field: rpl_ddl_main.async_state")
    public static final SqlColumn<String> asyncState = rplDdl.asyncState;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.568+08:00",
        comments = "Source field: rpl_ddl_main.schema_name")
    public static final SqlColumn<String> schemaName = rplDdl.schemaName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.569+08:00",
        comments = "Source field: rpl_ddl_main.table_name")
    public static final SqlColumn<String> tableName = rplDdl.tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.569+08:00",
        comments = "Source field: rpl_ddl_main.task_id")
    public static final SqlColumn<Long> taskId = rplDdl.taskId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.569+08:00",
        comments = "Source field: rpl_ddl_main.parallel_seq")
    public static final SqlColumn<Integer> parallelSeq = rplDdl.parallelSeq;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.569+08:00",
        comments = "Source field: rpl_ddl_main.ddl_stmt")
    public static final SqlColumn<String> ddlStmt = rplDdl.ddlStmt;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.566+08:00",
        comments = "Source Table: rpl_ddl_main")
    public static final class RplDdl extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> fsmId = column("fsm_id", JDBCType.BIGINT);

        public final SqlColumn<String> ddlTso = column("ddl_tso", JDBCType.VARCHAR);

        public final SqlColumn<Long> serviceId = column("service_id", JDBCType.BIGINT);

        public final SqlColumn<String> token = column("token", JDBCType.VARCHAR);

        public final SqlColumn<String> state = column("`state`", JDBCType.VARCHAR);

        public final SqlColumn<Boolean> asyncFlag = column("async_flag", JDBCType.BIT);

        public final SqlColumn<String> asyncState = column("async_state", JDBCType.VARCHAR);

        public final SqlColumn<String> schemaName = column("`schema_name`", JDBCType.VARCHAR);

        public final SqlColumn<String> tableName = column("`table_name`", JDBCType.VARCHAR);

        public final SqlColumn<Long> taskId = column("task_id", JDBCType.BIGINT);

        public final SqlColumn<Integer> parallelSeq = column("parallel_seq", JDBCType.INTEGER);

        public final SqlColumn<String> ddlStmt = column("ddl_stmt", JDBCType.LONGVARCHAR);

        public RplDdl() {
            super("rpl_ddl_main");
        }
    }
}