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

public final class RplSyncPointDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.059262+08:00", comments="Source Table: rpl_sync_point")
    public static final RplSyncPoint rplSyncPoint = new RplSyncPoint();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.059457+08:00", comments="Source field: rpl_sync_point.id")
    public static final SqlColumn<Long> id = rplSyncPoint.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.059672+08:00", comments="Source field: rpl_sync_point.primary_tso")
    public static final SqlColumn<String> primaryTso = rplSyncPoint.primaryTso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.059726+08:00", comments="Source field: rpl_sync_point.secondary_tso")
    public static final SqlColumn<String> secondaryTso = rplSyncPoint.secondaryTso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.059793+08:00", comments="Source field: rpl_sync_point.create_time")
    public static final SqlColumn<Date> createTime = rplSyncPoint.createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T16:35:26.059379+08:00", comments="Source Table: rpl_sync_point")
    public static final class RplSyncPoint extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<String> primaryTso = column("primary_tso", JDBCType.VARCHAR);

        public final SqlColumn<String> secondaryTso = column("secondary_tso", JDBCType.VARCHAR);

        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);

        public RplSyncPoint() {
            super("rpl_sync_point");
        }
    }
}