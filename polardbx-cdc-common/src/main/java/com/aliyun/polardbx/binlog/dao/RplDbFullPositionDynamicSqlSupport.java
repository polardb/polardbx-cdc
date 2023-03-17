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

public final class RplDbFullPositionDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.335+08:00",
        comments = "Source Table: rpl_db_full_position")
    public static final RplDbFullPosition rplDbFullPosition = new RplDbFullPosition();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.34+08:00",
        comments = "Source field: rpl_db_full_position.id")
    public static final SqlColumn<Long> id = rplDbFullPosition.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.34+08:00",
        comments = "Source field: rpl_db_full_position.gmt_created")
    public static final SqlColumn<Date> gmtCreated = rplDbFullPosition.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.341+08:00",
        comments = "Source field: rpl_db_full_position.gmt_modified")
    public static final SqlColumn<Date> gmtModified = rplDbFullPosition.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.341+08:00",
        comments = "Source field: rpl_db_full_position.state_machine_id")
    public static final SqlColumn<Long> stateMachineId = rplDbFullPosition.stateMachineId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.341+08:00",
        comments = "Source field: rpl_db_full_position.service_id")
    public static final SqlColumn<Long> serviceId = rplDbFullPosition.serviceId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.341+08:00",
        comments = "Source field: rpl_db_full_position.task_id")
    public static final SqlColumn<Long> taskId = rplDbFullPosition.taskId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.341+08:00",
        comments = "Source field: rpl_db_full_position.full_table_name")
    public static final SqlColumn<String> fullTableName = rplDbFullPosition.fullTableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.341+08:00",
        comments = "Source field: rpl_db_full_position.total_count")
    public static final SqlColumn<Long> totalCount = rplDbFullPosition.totalCount;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.341+08:00",
        comments = "Source field: rpl_db_full_position.finished_count")
    public static final SqlColumn<Long> finishedCount = rplDbFullPosition.finishedCount;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.341+08:00",
        comments = "Source field: rpl_db_full_position.finished")
    public static final SqlColumn<Integer> finished = rplDbFullPosition.finished;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.341+08:00",
        comments = "Source field: rpl_db_full_position.position")
    public static final SqlColumn<String> position = rplDbFullPosition.position;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.341+08:00",
        comments = "Source field: rpl_db_full_position.end_position")
    public static final SqlColumn<String> endPosition = rplDbFullPosition.endPosition;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-28T19:08:09.339+08:00",
        comments = "Source Table: rpl_db_full_position")
    public static final class RplDbFullPosition extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> stateMachineId = column("state_machine_id", JDBCType.BIGINT);

        public final SqlColumn<Long> serviceId = column("service_id", JDBCType.BIGINT);

        public final SqlColumn<Long> taskId = column("task_id", JDBCType.BIGINT);

        public final SqlColumn<String> fullTableName = column("full_table_name", JDBCType.VARCHAR);

        public final SqlColumn<Long> totalCount = column("total_count", JDBCType.BIGINT);

        public final SqlColumn<Long> finishedCount = column("finished_count", JDBCType.BIGINT);

        public final SqlColumn<Integer> finished = column("finished", JDBCType.INTEGER);

        public final SqlColumn<String> position = column("position", JDBCType.VARCHAR);

        public final SqlColumn<String> endPosition = column("end_position", JDBCType.VARCHAR);

        public RplDbFullPosition() {
            super("rpl_db_full_position");
        }
    }
}