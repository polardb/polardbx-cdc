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

public final class XStreamDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.889+08:00", comments="Source Table: binlog_x_stream")
    public static final XStream XStream = new XStream();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.889+08:00", comments="Source field: binlog_x_stream.id")
    public static final SqlColumn<Long> id = XStream.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.89+08:00", comments="Source field: binlog_x_stream.gmt_created")
    public static final SqlColumn<Date> gmtCreated = XStream.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.89+08:00", comments="Source field: binlog_x_stream.gmt_modified")
    public static final SqlColumn<Date> gmtModified = XStream.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.89+08:00", comments="Source field: binlog_x_stream.stream_name")
    public static final SqlColumn<String> streamName = XStream.streamName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.89+08:00", comments="Source field: binlog_x_stream.stream_desc")
    public static final SqlColumn<String> streamDesc = XStream.streamDesc;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.89+08:00", comments="Source field: binlog_x_stream.group_name")
    public static final SqlColumn<String> groupName = XStream.groupName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.89+08:00", comments="Source field: binlog_x_stream.expected_storage_tso")
    public static final SqlColumn<String> expectedStorageTso = XStream.expectedStorageTso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.89+08:00", comments="Source field: binlog_x_stream.latest_cursor")
    public static final SqlColumn<String> latestCursor = XStream.latestCursor;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.89+08:00", comments="Source field: binlog_x_stream.endpoint")
    public static final SqlColumn<String> endpoint = XStream.endpoint;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-07T19:50:52.889+08:00", comments="Source Table: binlog_x_stream")
    public static final class XStream extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> streamName = column("stream_name", JDBCType.VARCHAR);

        public final SqlColumn<String> streamDesc = column("stream_desc", JDBCType.VARCHAR);

        public final SqlColumn<String> groupName = column("group_name", JDBCType.VARCHAR);

        public final SqlColumn<String> expectedStorageTso = column("expected_storage_tso", JDBCType.VARCHAR);

        public final SqlColumn<String> latestCursor = column("latest_cursor", JDBCType.VARCHAR);

        public final SqlColumn<String> endpoint = column("endpoint", JDBCType.LONGVARCHAR);

        public XStream() {
            super("binlog_x_stream");
        }
    }
}