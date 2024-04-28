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

public final class RplDdlSubDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.291+08:00",
        comments = "Source Table: rpl_ddl_sub")
    public static final RplDdlSub rplDdlSub = new RplDdlSub();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.292+08:00",
        comments = "Source field: rpl_ddl_sub.id")
    public static final SqlColumn<Long> id = rplDdlSub.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.292+08:00",
        comments = "Source field: rpl_ddl_sub.gmt_created")
    public static final SqlColumn<Date> gmtCreated = rplDdlSub.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.292+08:00",
        comments = "Source field: rpl_ddl_sub.gmt_modified")
    public static final SqlColumn<Date> gmtModified = rplDdlSub.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.293+08:00",
        comments = "Source field: rpl_ddl_sub.fsm_id")
    public static final SqlColumn<Long> fsmId = rplDdlSub.fsmId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.294+08:00",
        comments = "Source field: rpl_ddl_sub.ddl_tso")
    public static final SqlColumn<String> ddlTso = rplDdlSub.ddlTso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.294+08:00",
        comments = "Source field: rpl_ddl_sub.task_id")
    public static final SqlColumn<Long> taskId = rplDdlSub.taskId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.294+08:00",
        comments = "Source field: rpl_ddl_sub.service_id")
    public static final SqlColumn<Long> serviceId = rplDdlSub.serviceId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.294+08:00",
        comments = "Source field: rpl_ddl_sub.state")
    public static final SqlColumn<String> state = rplDdlSub.state;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.294+08:00",
        comments = "Source field: rpl_ddl_sub.schema_name")
    public static final SqlColumn<String> schemaName = rplDdlSub.schemaName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.295+08:00",
        comments = "Source field: rpl_ddl_sub.parallel_seq")
    public static final SqlColumn<Integer> parallelSeq = rplDdlSub.parallelSeq;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.292+08:00",
        comments = "Source Table: rpl_ddl_sub")
    public static final class RplDdlSub extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> fsmId = column("fsm_id", JDBCType.BIGINT);

        public final SqlColumn<String> ddlTso = column("ddl_tso", JDBCType.VARCHAR);

        public final SqlColumn<Long> taskId = column("task_id", JDBCType.BIGINT);

        public final SqlColumn<Long> serviceId = column("service_id", JDBCType.BIGINT);

        public final SqlColumn<String> state = column("`state`", JDBCType.VARCHAR);

        public final SqlColumn<String> schemaName = column("`schema_name`", JDBCType.VARCHAR);

        public final SqlColumn<Integer> parallelSeq = column("parallel_seq", JDBCType.INTEGER);

        public RplDdlSub() {
            super("rpl_ddl_sub");
        }
    }
}