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

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

import javax.annotation.Generated;
import java.sql.JDBCType;
import java.util.Date;

public final class BinlogPolarxCommandDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.632+08:00",
        comments = "Source Table: binlog_polarx_command")
    public static final BinlogPolarxCommand binlogPolarxCommand = new BinlogPolarxCommand();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.633+08:00",
        comments = "Source field: binlog_polarx_command.id")
    public static final SqlColumn<Long> id = binlogPolarxCommand.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.633+08:00",
        comments = "Source field: binlog_polarx_command.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogPolarxCommand.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.633+08:00",
        comments = "Source field: binlog_polarx_command.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogPolarxCommand.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.633+08:00",
        comments = "Source field: binlog_polarx_command.type")
    public static final SqlColumn<Integer> type = binlogPolarxCommand.type;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.633+08:00",
        comments = "Source field: binlog_polarx_command.command")
    public static final SqlColumn<String> command = binlogPolarxCommand.command;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.633+08:00",
        comments = "Source field: binlog_polarx_command.ext")
    public static final SqlColumn<String> ext = binlogPolarxCommand.ext;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.634+08:00",
        comments = "Source field: binlog_polarx_command.reply")
    public static final SqlColumn<String> reply = binlogPolarxCommand.reply;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-22T19:53:40.632+08:00",
        comments = "Source Table: binlog_polarx_command")
    public static final class BinlogPolarxCommand extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Integer> type = column("type", JDBCType.INTEGER);

        public final SqlColumn<String> command = column("command", JDBCType.VARCHAR);

        public final SqlColumn<String> ext = column("ext", JDBCType.VARCHAR);

        public final SqlColumn<String> reply = column("reply", JDBCType.LONGVARCHAR);

        public BinlogPolarxCommand() {
            super("binlog_polarx_command");
        }
    }
}