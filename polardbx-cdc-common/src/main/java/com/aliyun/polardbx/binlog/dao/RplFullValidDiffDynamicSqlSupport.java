/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class RplFullValidDiffDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766243+08:00", comments="Source Table: rpl_full_valid_diff")
    public static final RplFullValidDiff rplFullValidDiff = new RplFullValidDiff();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766346+08:00", comments="Source field: rpl_full_valid_diff.id")
    public static final SqlColumn<Long> id = rplFullValidDiff.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766384+08:00", comments="Source field: rpl_full_valid_diff.task_id")
    public static final SqlColumn<Long> taskId = rplFullValidDiff.taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76642+08:00", comments="Source field: rpl_full_valid_diff.src_logical_db")
    public static final SqlColumn<String> srcLogicalDb = rplFullValidDiff.srcLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766454+08:00", comments="Source field: rpl_full_valid_diff.src_logical_table")
    public static final SqlColumn<String> srcLogicalTable = rplFullValidDiff.srcLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766488+08:00", comments="Source field: rpl_full_valid_diff.dst_logical_db")
    public static final SqlColumn<String> dstLogicalDb = rplFullValidDiff.dstLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766522+08:00", comments="Source field: rpl_full_valid_diff.dst_logical_table")
    public static final SqlColumn<String> dstLogicalTable = rplFullValidDiff.dstLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766559+08:00", comments="Source field: rpl_full_valid_diff.src_key_name")
    public static final SqlColumn<String> srcKeyName = rplFullValidDiff.srcKeyName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766597+08:00", comments="Source field: rpl_full_valid_diff.src_key_val")
    public static final SqlColumn<String> srcKeyVal = rplFullValidDiff.srcKeyVal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76663+08:00", comments="Source field: rpl_full_valid_diff.dst_key_name")
    public static final SqlColumn<String> dstKeyName = rplFullValidDiff.dstKeyName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766666+08:00", comments="Source field: rpl_full_valid_diff.dst_key_val")
    public static final SqlColumn<String> dstKeyVal = rplFullValidDiff.dstKeyVal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766698+08:00", comments="Source field: rpl_full_valid_diff.error_type")
    public static final SqlColumn<String> errorType = rplFullValidDiff.errorType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76673+08:00", comments="Source field: rpl_full_valid_diff.status")
    public static final SqlColumn<String> status = rplFullValidDiff.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766854+08:00", comments="Source field: rpl_full_valid_diff.create_time")
    public static final SqlColumn<Date> createTime = rplFullValidDiff.createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.7669+08:00", comments="Source field: rpl_full_valid_diff.update_time")
    public static final SqlColumn<Date> updateTime = rplFullValidDiff.updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766283+08:00", comments="Source Table: rpl_full_valid_diff")
    public static final class RplFullValidDiff extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Long> taskId = column("task_id", JDBCType.BIGINT);

        public final SqlColumn<String> srcLogicalDb = column("src_logical_db", JDBCType.VARCHAR);

        public final SqlColumn<String> srcLogicalTable = column("src_logical_table", JDBCType.VARCHAR);

        public final SqlColumn<String> dstLogicalDb = column("dst_logical_db", JDBCType.VARCHAR);

        public final SqlColumn<String> dstLogicalTable = column("dst_logical_table", JDBCType.VARCHAR);

        public final SqlColumn<String> srcKeyName = column("src_key_name", JDBCType.VARCHAR);

        public final SqlColumn<String> srcKeyVal = column("src_key_val", JDBCType.VARCHAR);

        public final SqlColumn<String> dstKeyName = column("dst_key_name", JDBCType.VARCHAR);

        public final SqlColumn<String> dstKeyVal = column("dst_key_val", JDBCType.VARCHAR);

        public final SqlColumn<String> errorType = column("error_type", JDBCType.VARCHAR);

        public final SqlColumn<String> status = column("`status`", JDBCType.VARCHAR);

        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> updateTime = column("update_time", JDBCType.TIMESTAMP);

        public RplFullValidDiff() {
            super("rpl_full_valid_diff");
        }
    }
}