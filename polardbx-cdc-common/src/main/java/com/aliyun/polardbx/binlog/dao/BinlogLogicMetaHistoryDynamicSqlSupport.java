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

public final class BinlogLogicMetaHistoryDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.173+08:00",
        comments = "Source Table: binlog_logic_meta_history")
    public static final BinlogLogicMetaHistory binlogLogicMetaHistory = new BinlogLogicMetaHistory();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.id")
    public static final SqlColumn<Integer> id = binlogLogicMetaHistory.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogLogicMetaHistory.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogLogicMetaHistory.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.tso")
    public static final SqlColumn<String> tso = binlogLogicMetaHistory.tso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.db_name")
    public static final SqlColumn<String> dbName = binlogLogicMetaHistory.dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.table_name")
    public static final SqlColumn<String> tableName = binlogLogicMetaHistory.tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.sql_kind")
    public static final SqlColumn<String> sqlKind = binlogLogicMetaHistory.sqlKind;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.type")
    public static final SqlColumn<Byte> type = binlogLogicMetaHistory.type;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.ddl")
    public static final SqlColumn<String> ddl = binlogLogicMetaHistory.ddl;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.topology")
    public static final SqlColumn<String> topology = binlogLogicMetaHistory.topology;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source field: binlog_logic_meta_history.ext_info")
    public static final SqlColumn<String> extInfo = binlogLogicMetaHistory.extInfo;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-03T16:05:49.174+08:00",
        comments = "Source Table: binlog_logic_meta_history")
    public static final class BinlogLogicMetaHistory extends SqlTable {
        public final SqlColumn<Integer> id = column("id", JDBCType.INTEGER);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> tso = column("tso", JDBCType.VARCHAR);

        public final SqlColumn<String> dbName = column("db_name", JDBCType.VARCHAR);

        public final SqlColumn<String> tableName = column("table_name", JDBCType.VARCHAR);

        public final SqlColumn<String> sqlKind = column("sql_kind", JDBCType.VARCHAR);

        public final SqlColumn<Byte> type = column("type", JDBCType.TINYINT);

        public final SqlColumn<String> ddl = column("ddl", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> topology = column("topology", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> extInfo = column("ext_info", JDBCType.LONGVARCHAR);

        public BinlogLogicMetaHistory() {
            super("binlog_logic_meta_history");
        }
    }
}