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

public final class BinlogOssRecordDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.761+08:00", comments="Source Table: binlog_oss_record")
    public static final BinlogOssRecord binlogOssRecord = new BinlogOssRecord();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.762+08:00", comments="Source field: binlog_oss_record.id")
    public static final SqlColumn<Integer> id = binlogOssRecord.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.762+08:00", comments="Source field: binlog_oss_record.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogOssRecord.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.762+08:00", comments="Source field: binlog_oss_record.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogOssRecord.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.762+08:00", comments="Source field: binlog_oss_record.binlog_file")
    public static final SqlColumn<String> binlogFile = binlogOssRecord.binlogFile;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.763+08:00", comments="Source field: binlog_oss_record.upload_status")
    public static final SqlColumn<Integer> uploadStatus = binlogOssRecord.uploadStatus;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.763+08:00", comments="Source field: binlog_oss_record.purge_status")
    public static final SqlColumn<Integer> purgeStatus = binlogOssRecord.purgeStatus;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.763+08:00", comments="Source field: binlog_oss_record.upload_host")
    public static final SqlColumn<String> uploadHost = binlogOssRecord.uploadHost;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.763+08:00", comments="Source field: binlog_oss_record.log_begin")
    public static final SqlColumn<Date> logBegin = binlogOssRecord.logBegin;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.763+08:00", comments="Source field: binlog_oss_record.log_end")
    public static final SqlColumn<Date> logEnd = binlogOssRecord.logEnd;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.763+08:00", comments="Source field: binlog_oss_record.log_size")
    public static final SqlColumn<Long> logSize = binlogOssRecord.logSize;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.763+08:00", comments="Source field: binlog_oss_record.group_id")
    public static final SqlColumn<String> groupId = binlogOssRecord.groupId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.763+08:00", comments="Source field: binlog_oss_record.stream_id")
    public static final SqlColumn<String> streamId = binlogOssRecord.streamId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.763+08:00", comments="Source field: binlog_oss_record.last_tso")
    public static final SqlColumn<String> lastTso = binlogOssRecord.lastTso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-06-21T17:44:39.762+08:00", comments="Source Table: binlog_oss_record")
    public static final class BinlogOssRecord extends SqlTable {
        public final SqlColumn<Integer> id = column("id", JDBCType.INTEGER);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> binlogFile = column("binlog_file", JDBCType.VARCHAR);

        public final SqlColumn<Integer> uploadStatus = column("upload_status", JDBCType.INTEGER);

        public final SqlColumn<Integer> purgeStatus = column("purge_status", JDBCType.INTEGER);

        public final SqlColumn<String> uploadHost = column("upload_host", JDBCType.VARCHAR);

        public final SqlColumn<Date> logBegin = column("log_begin", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> logEnd = column("log_end", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> logSize = column("log_size", JDBCType.BIGINT);

        public final SqlColumn<String> groupId = column("group_id", JDBCType.VARCHAR);

        public final SqlColumn<String> streamId = column("stream_id", JDBCType.VARCHAR);

        public final SqlColumn<String> lastTso = column("last_tso", JDBCType.VARCHAR);

        public BinlogOssRecord() {
            super("binlog_oss_record");
        }
    }
}