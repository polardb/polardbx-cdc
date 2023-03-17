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