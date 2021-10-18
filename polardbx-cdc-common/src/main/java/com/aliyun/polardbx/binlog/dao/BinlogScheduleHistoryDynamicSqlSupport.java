/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.dao;

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class BinlogScheduleHistoryDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-20T23:25:47.3+08:00", comments="Source Table: binlog_schedule_history")
    public static final BinlogScheduleHistory binlogScheduleHistory = new BinlogScheduleHistory();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-20T23:25:47.301+08:00", comments="Source field: binlog_schedule_history.id")
    public static final SqlColumn<Long> id = binlogScheduleHistory.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-20T23:25:47.301+08:00", comments="Source field: binlog_schedule_history.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogScheduleHistory.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-20T23:25:47.301+08:00", comments="Source field: binlog_schedule_history.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogScheduleHistory.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-20T23:25:47.301+08:00", comments="Source field: binlog_schedule_history.version")
    public static final SqlColumn<Long> version = binlogScheduleHistory.version;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-20T23:25:47.301+08:00", comments="Source field: binlog_schedule_history.content")
    public static final SqlColumn<String> content = binlogScheduleHistory.content;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2021-05-20T23:25:47.3+08:00", comments="Source Table: binlog_schedule_history")
    public static final class BinlogScheduleHistory extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> version = column("version", JDBCType.BIGINT);

        public final SqlColumn<String> content = column("content", JDBCType.LONGVARCHAR);

        public BinlogScheduleHistory() {
            super("binlog_schedule_history");
        }
    }
}