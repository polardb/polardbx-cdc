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

public final class XStreamGroupDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    public static final XStreamGroup XStreamGroup = new XStreamGroup();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source field: binlog_x_stream_group.id")
    public static final SqlColumn<Long> id = XStreamGroup.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source field: binlog_x_stream_group.gmt_created")
    public static final SqlColumn<Date> gmtCreated = XStreamGroup.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source field: binlog_x_stream_group.gmt_modified")
    public static final SqlColumn<Date> gmtModified = XStreamGroup.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source field: binlog_x_stream_group.group_name")
    public static final SqlColumn<String> groupName = XStreamGroup.groupName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source field: binlog_x_stream_group.group_desc")
    public static final SqlColumn<String> groupDesc = XStreamGroup.groupDesc;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    public static final class XStreamGroup extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> groupName = column("group_name", JDBCType.VARCHAR);

        public final SqlColumn<String> groupDesc = column("group_desc", JDBCType.VARCHAR);

        public XStreamGroup() {
            super("binlog_x_stream_group");
        }
    }
}