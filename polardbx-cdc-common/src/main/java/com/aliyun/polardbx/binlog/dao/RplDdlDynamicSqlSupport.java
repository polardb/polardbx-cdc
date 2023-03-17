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

public final class RplDdlDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.762+08:00",
        comments = "Source Table: rpl_ddl")
    public static final RplDdl rplDdl = new RplDdl();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.763+08:00",
        comments = "Source field: rpl_ddl.id")
    public static final SqlColumn<Long> id = rplDdl.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.763+08:00",
        comments = "Source field: rpl_ddl.gmt_created")
    public static final SqlColumn<Date> gmtCreated = rplDdl.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.763+08:00",
        comments = "Source field: rpl_ddl.gmt_modified")
    public static final SqlColumn<Date> gmtModified = rplDdl.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.763+08:00",
        comments = "Source field: rpl_ddl.state_machine_id")
    public static final SqlColumn<Long> stateMachineId = rplDdl.stateMachineId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.763+08:00",
        comments = "Source field: rpl_ddl.service_id")
    public static final SqlColumn<Long> serviceId = rplDdl.serviceId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.763+08:00",
        comments = "Source field: rpl_ddl.task_id")
    public static final SqlColumn<Long> taskId = rplDdl.taskId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.763+08:00",
        comments = "Source field: rpl_ddl.ddl_tso")
    public static final SqlColumn<String> ddlTso = rplDdl.ddlTso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.763+08:00",
        comments = "Source field: rpl_ddl.job_id")
    public static final SqlColumn<Long> jobId = rplDdl.jobId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.763+08:00",
        comments = "Source field: rpl_ddl.state")
    public static final SqlColumn<Integer> state = rplDdl.state;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.763+08:00",
        comments = "Source field: rpl_ddl.ddl_stmt")
    public static final SqlColumn<String> ddlStmt = rplDdl.ddlStmt;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.762+08:00",
        comments = "Source Table: rpl_ddl")
    public static final class RplDdl extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> stateMachineId = column("state_machine_id", JDBCType.BIGINT);

        public final SqlColumn<Long> serviceId = column("service_id", JDBCType.BIGINT);

        public final SqlColumn<Long> taskId = column("task_id", JDBCType.BIGINT);

        public final SqlColumn<String> ddlTso = column("ddl_tso", JDBCType.VARCHAR);

        public final SqlColumn<Long> jobId = column("job_id", JDBCType.BIGINT);

        public final SqlColumn<Integer> state = column("state", JDBCType.INTEGER);

        public final SqlColumn<String> ddlStmt = column("ddl_stmt", JDBCType.LONGVARCHAR);

        public RplDdl() {
            super("rpl_ddl");
        }
    }
}