/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

import javax.annotation.Generated;
import java.sql.JDBCType;
import java.util.Date;

public final class BinlogDDLHistoryDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.402+08:00",
        comments = "Source Table: binlog_ddl_history")
    public static final BinlogDDLHistory binlogDDLHistory = new BinlogDDLHistory();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.402+08:00",
        comments = "Source field: binlog_ddl_history.id")
    public static final SqlColumn<Integer> id = binlogDDLHistory.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.402+08:00",
        comments = "Source field: binlog_ddl_history.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogDDLHistory.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.402+08:00",
        comments = "Source field: binlog_ddl_history.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogDDLHistory.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.402+08:00",
        comments = "Source field: binlog_ddl_history.tso")
    public static final SqlColumn<String> tso = binlogDDLHistory.tso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source field: binlog_ddl_history.binlog_file")
    public static final SqlColumn<String> binlogFile = binlogDDLHistory.binlogFile;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source field: binlog_ddl_history.pos")
    public static final SqlColumn<Integer> pos = binlogDDLHistory.pos;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source field: binlog_ddl_history.storage_inst_id")
    public static final SqlColumn<String> storageInstId = binlogDDLHistory.storageInstId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source field: binlog_ddl_history.db_name")
    public static final SqlColumn<String> dbName = binlogDDLHistory.dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source field: binlog_ddl_history.table_name")
    public static final SqlColumn<String> tableName = binlogDDLHistory.tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source field: binlog_ddl_history.extra")
    public static final SqlColumn<String> extra = binlogDDLHistory.extra;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.403+08:00",
        comments = "Source field: binlog_ddl_history.ddl")
    public static final SqlColumn<String> ddl = binlogDDLHistory.ddl;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.402+08:00",
        comments = "Source Table: binlog_ddl_history")
    public static final class BinlogDDLHistory extends SqlTable {
        public final SqlColumn<Integer> id = column("id", JDBCType.INTEGER);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> tso = column("tso", JDBCType.VARCHAR);

        public final SqlColumn<String> binlogFile = column("binlog_file", JDBCType.VARCHAR);

        public final SqlColumn<Integer> pos = column("pos", JDBCType.INTEGER);

        public final SqlColumn<String> storageInstId = column("storage_inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> dbName = column("db_name", JDBCType.VARCHAR);

        public final SqlColumn<String> tableName = column("table_name", JDBCType.VARCHAR);

        public final SqlColumn<String> extra = column("extra", JDBCType.VARCHAR);

        public final SqlColumn<String> ddl = column("ddl", JDBCType.LONGVARCHAR);

        public BinlogDDLHistory() {
            super("binlog_ddl_history");
        }
    }
}