/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.dao;

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

import javax.annotation.Generated;
import java.sql.JDBCType;
import java.util.Date;

public final class BinlogTaskScheduleDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source Table: binlog_task_schedule")
    public static final BinlogTaskSchedule binlogTaskSchedule = new BinlogTaskSchedule();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.id")
    public static final SqlColumn<Long> id = binlogTaskSchedule.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogTaskSchedule.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogTaskSchedule.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source field: binlog_task_schedule.cluster_id")
    public static final SqlColumn<String> clusterId = binlogTaskSchedule.clusterId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source field: binlog_task_schedule.task_name")
    public static final SqlColumn<String> taskName = binlogTaskSchedule.taskName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source field: binlog_task_schedule.status")
    public static final SqlColumn<String> status = binlogTaskSchedule.status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source field: binlog_task_schedule.version")
    public static final SqlColumn<Integer> version = binlogTaskSchedule.version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.904+08:00",
        comments = "Source field: binlog_task_schedule.op")
    public static final SqlColumn<String> op = binlogTaskSchedule.op;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T11:42:54.903+08:00",
        comments = "Source Table: binlog_task_schedule")
    public static final class BinlogTaskSchedule extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<String> taskName = column("task_name", JDBCType.VARCHAR);

        public final SqlColumn<String> status = column("status", JDBCType.VARCHAR);

        public final SqlColumn<Integer> version = column("version", JDBCType.INTEGER);

        public final SqlColumn<String> op = column("op", JDBCType.VARCHAR);

        public BinlogTaskSchedule() {
            super("binlog_task_schedule");
        }
    }
}