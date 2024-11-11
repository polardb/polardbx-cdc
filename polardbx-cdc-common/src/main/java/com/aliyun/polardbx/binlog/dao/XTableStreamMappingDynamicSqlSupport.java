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

public final class XTableStreamMappingDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.409+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    public static final XTableStreamMapping XTableStreamMapping = new XTableStreamMapping();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.409+08:00", comments="Source field: binlog_x_table_stream_mapping.id")
    public static final SqlColumn<Long> id = XTableStreamMapping.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.409+08:00", comments="Source field: binlog_x_table_stream_mapping.gmt_created")
    public static final SqlColumn<Date> gmtCreated = XTableStreamMapping.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.41+08:00", comments="Source field: binlog_x_table_stream_mapping.gmt_modified")
    public static final SqlColumn<Date> gmtModified = XTableStreamMapping.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.41+08:00", comments="Source field: binlog_x_table_stream_mapping.db_name")
    public static final SqlColumn<String> dbName = XTableStreamMapping.dbName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.41+08:00", comments="Source field: binlog_x_table_stream_mapping.table_name")
    public static final SqlColumn<String> tableName = XTableStreamMapping.tableName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.41+08:00", comments="Source field: binlog_x_table_stream_mapping.stream_seq")
    public static final SqlColumn<Long> streamSeq = XTableStreamMapping.streamSeq;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.41+08:00", comments="Source field: binlog_x_table_stream_mapping.cluster_id")
    public static final SqlColumn<String> clusterId = XTableStreamMapping.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-12-14T13:49:22.409+08:00", comments="Source Table: binlog_x_table_stream_mapping")
    public static final class XTableStreamMapping extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> dbName = column("db_name", JDBCType.VARCHAR);

        public final SqlColumn<String> tableName = column("table_name", JDBCType.VARCHAR);

        public final SqlColumn<Long> streamSeq = column("stream_seq", JDBCType.BIGINT);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public XTableStreamMapping() {
            super("binlog_x_table_stream_mapping");
        }
    }
}