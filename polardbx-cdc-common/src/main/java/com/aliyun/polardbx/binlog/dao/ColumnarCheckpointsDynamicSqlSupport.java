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

public final class ColumnarCheckpointsDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.307+08:00", comments="Source Table: columnar_checkpoints")
    public static final ColumnarCheckpoints columnarCheckpoints = new ColumnarCheckpoints();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.308+08:00", comments="Source field: columnar_checkpoints.id")
    public static final SqlColumn<Long> id = columnarCheckpoints.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.308+08:00", comments="Source field: columnar_checkpoints.logical_schema")
    public static final SqlColumn<String> logicalSchema = columnarCheckpoints.logicalSchema;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.308+08:00", comments="Source field: columnar_checkpoints.logical_table")
    public static final SqlColumn<String> logicalTable = columnarCheckpoints.logicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.308+08:00", comments="Source field: columnar_checkpoints.partition_name")
    public static final SqlColumn<String> partitionName = columnarCheckpoints.partitionName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.308+08:00", comments="Source field: columnar_checkpoints.checkpoint_tso")
    public static final SqlColumn<Long> checkpointTso = columnarCheckpoints.checkpointTso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.308+08:00", comments="Source field: columnar_checkpoints.checkpoint_type")
    public static final SqlColumn<String> checkpointType = columnarCheckpoints.checkpointType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.308+08:00", comments="Source field: columnar_checkpoints.create_time")
    public static final SqlColumn<Date> createTime = columnarCheckpoints.createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.308+08:00", comments="Source field: columnar_checkpoints.update_time")
    public static final SqlColumn<Date> updateTime = columnarCheckpoints.updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.308+08:00", comments="Source field: columnar_checkpoints.offset")
    public static final SqlColumn<String> offset = columnarCheckpoints.offset;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.309+08:00", comments="Source field: columnar_checkpoints.extra")
    public static final SqlColumn<String> extra = columnarCheckpoints.extra;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.307+08:00", comments="Source Table: columnar_checkpoints")
    public static final class ColumnarCheckpoints extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<String> logicalSchema = column("logical_schema", JDBCType.VARCHAR);

        public final SqlColumn<String> logicalTable = column("logical_table", JDBCType.VARCHAR);

        public final SqlColumn<String> partitionName = column("partition_name", JDBCType.VARCHAR);

        public final SqlColumn<Long> checkpointTso = column("checkpoint_tso", JDBCType.BIGINT);

        public final SqlColumn<String> checkpointType = column("checkpoint_type", JDBCType.VARCHAR);

        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> updateTime = column("update_time", JDBCType.TIMESTAMP);

        public final SqlColumn<String> offset = column("`offset`", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> extra = column("extra", JDBCType.LONGVARCHAR);

        public ColumnarCheckpoints() {
            super("columnar_checkpoints");
        }
    }
}