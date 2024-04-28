/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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