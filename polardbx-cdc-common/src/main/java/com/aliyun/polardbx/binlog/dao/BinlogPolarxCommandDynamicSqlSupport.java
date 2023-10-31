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

public final class BinlogPolarxCommandDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.236+08:00", comments="Source Table: binlog_polarx_command")
    public static final BinlogPolarxCommand binlogPolarxCommand = new BinlogPolarxCommand();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.237+08:00", comments="Source field: binlog_polarx_command.id")
    public static final SqlColumn<Long> id = binlogPolarxCommand.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.237+08:00", comments="Source field: binlog_polarx_command.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogPolarxCommand.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.238+08:00", comments="Source field: binlog_polarx_command.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogPolarxCommand.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.238+08:00", comments="Source field: binlog_polarx_command.cmd_id")
    public static final SqlColumn<String> cmdId = binlogPolarxCommand.cmdId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.238+08:00", comments="Source field: binlog_polarx_command.cmd_type")
    public static final SqlColumn<String> cmdType = binlogPolarxCommand.cmdType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.238+08:00", comments="Source field: binlog_polarx_command.cmd_status")
    public static final SqlColumn<Long> cmdStatus = binlogPolarxCommand.cmdStatus;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.238+08:00", comments="Source field: binlog_polarx_command.cluster_id")
    public static final SqlColumn<String> clusterId = binlogPolarxCommand.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.238+08:00", comments="Source field: binlog_polarx_command.cmd_request")
    public static final SqlColumn<String> cmdRequest = binlogPolarxCommand.cmdRequest;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.238+08:00", comments="Source field: binlog_polarx_command.cmd_reply")
    public static final SqlColumn<String> cmdReply = binlogPolarxCommand.cmdReply;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T12:04:39.236+08:00", comments="Source Table: binlog_polarx_command")
    public static final class BinlogPolarxCommand extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> cmdId = column("cmd_id", JDBCType.VARCHAR);

        public final SqlColumn<String> cmdType = column("cmd_type", JDBCType.VARCHAR);

        public final SqlColumn<Long> cmdStatus = column("cmd_status", JDBCType.BIGINT);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<String> cmdRequest = column("cmd_request", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> cmdReply = column("cmd_reply", JDBCType.LONGVARCHAR);

        public BinlogPolarxCommand() {
            super("binlog_polarx_command");
        }
    }
}