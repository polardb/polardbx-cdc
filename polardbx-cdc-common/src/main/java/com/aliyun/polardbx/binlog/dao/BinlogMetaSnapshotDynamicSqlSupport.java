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

public final class BinlogMetaSnapshotDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.375+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    public static final BinlogMetaSnapshot binlogMetaSnapshot = new BinlogMetaSnapshot();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.376+08:00",
        comments = "Source field: binlog_meta_snapshot.id")
    public static final SqlColumn<Integer> id = binlogMetaSnapshot.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.376+08:00",
        comments = "Source field: binlog_meta_snapshot.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogMetaSnapshot.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.376+08:00",
        comments = "Source field: binlog_meta_snapshot.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogMetaSnapshot.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.376+08:00",
        comments = "Source field: binlog_meta_snapshot.tso")
    public static final SqlColumn<String> tso = binlogMetaSnapshot.tso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.376+08:00",
        comments = "Source field: binlog_meta_snapshot.db_name")
    public static final SqlColumn<String> dbName = binlogMetaSnapshot.dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.377+08:00",
        comments = "Source field: binlog_meta_snapshot.table_name")
    public static final SqlColumn<String> tableName = binlogMetaSnapshot.tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.377+08:00",
        comments = "Source field: binlog_meta_snapshot.table_schema")
    public static final SqlColumn<String> tableSchema = binlogMetaSnapshot.tableSchema;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-11T17:41:50.376+08:00",
        comments = "Source Table: binlog_meta_snapshot")
    public static final class BinlogMetaSnapshot extends SqlTable {
        public final SqlColumn<Integer> id = column("id", JDBCType.INTEGER);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> tso = column("tso", JDBCType.VARCHAR);

        public final SqlColumn<String> dbName = column("db_name", JDBCType.VARCHAR);

        public final SqlColumn<String> tableName = column("table_name", JDBCType.VARCHAR);

        public final SqlColumn<String> tableSchema = column("table_schema", JDBCType.LONGVARCHAR);

        public BinlogMetaSnapshot() {
            super("binlog_meta_snapshot");
        }
    }
}