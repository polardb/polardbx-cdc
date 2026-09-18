/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class BinlogScheduleHistoryDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.882336+08:00", comments="Source Table: binlog_schedule_history")
    public static final BinlogScheduleHistory binlogScheduleHistory = new BinlogScheduleHistory();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.882518+08:00", comments="Source field: binlog_schedule_history.id")
    public static final SqlColumn<Long> id = binlogScheduleHistory.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.882685+08:00", comments="Source field: binlog_schedule_history.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogScheduleHistory.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.88274+08:00", comments="Source field: binlog_schedule_history.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogScheduleHistory.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.882786+08:00", comments="Source field: binlog_schedule_history.version")
    public static final SqlColumn<Long> version = binlogScheduleHistory.version;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.882833+08:00", comments="Source field: binlog_schedule_history.cluster_id")
    public static final SqlColumn<String> clusterId = binlogScheduleHistory.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.882879+08:00", comments="Source field: binlog_schedule_history.sub_version")
    public static final SqlColumn<Long> subVersion = binlogScheduleHistory.subVersion;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.882925+08:00", comments="Source field: binlog_schedule_history.content")
    public static final SqlColumn<String> content = binlogScheduleHistory.content;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-16T15:00:01.88245+08:00", comments="Source Table: binlog_schedule_history")
    public static final class BinlogScheduleHistory extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> version = column("version", JDBCType.BIGINT);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<Long> subVersion = column("sub_version", JDBCType.BIGINT);

        public final SqlColumn<String> content = column("content", JDBCType.LONGVARCHAR);

        public BinlogScheduleHistory() {
            super("binlog_schedule_history");
        }
    }
}