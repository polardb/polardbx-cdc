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

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class BinlogEnvConfigHistoryDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.612+08:00",
        comments = "Source Table: binlog_env_config_history")
    public static final BinlogEnvConfigHistory binlogEnvConfigHistory = new BinlogEnvConfigHistory();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.612+08:00",
        comments = "Source field: binlog_env_config_history.id")
    public static final SqlColumn<Long> id = binlogEnvConfigHistory.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.613+08:00",
        comments = "Source field: binlog_env_config_history.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogEnvConfigHistory.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.613+08:00",
        comments = "Source field: binlog_env_config_history.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogEnvConfigHistory.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.613+08:00",
        comments = "Source field: binlog_env_config_history.tso")
    public static final SqlColumn<String> tso = binlogEnvConfigHistory.tso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.613+08:00",
        comments = "Source field: binlog_env_config_history.instruction_id")
    public static final SqlColumn<String> instructionId = binlogEnvConfigHistory.instructionId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.613+08:00",
        comments = "Source field: binlog_env_config_history.change_env_content")
    public static final SqlColumn<String> changeEnvContent = binlogEnvConfigHistory.changeEnvContent;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.612+08:00",
        comments = "Source Table: binlog_env_config_history")
    public static final class BinlogEnvConfigHistory extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> tso = column("tso", JDBCType.VARCHAR);

        public final SqlColumn<String> instructionId = column("instruction_id", JDBCType.VARCHAR);

        public final SqlColumn<String> changeEnvContent = column("change_env_content", JDBCType.LONGVARCHAR);

        public BinlogEnvConfigHistory() {
            super("binlog_env_config_history");
        }
    }
}