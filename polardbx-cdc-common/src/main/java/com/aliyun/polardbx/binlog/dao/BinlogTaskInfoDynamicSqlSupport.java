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

public final class BinlogTaskInfoDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.862+08:00", comments="Source Table: binlog_task_info")
    public static final BinlogTaskInfo binlogTaskInfo = new BinlogTaskInfo();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.862+08:00", comments="Source field: binlog_task_info.id")
    public static final SqlColumn<Long> id = binlogTaskInfo.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.863+08:00", comments="Source field: binlog_task_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogTaskInfo.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.863+08:00", comments="Source field: binlog_task_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogTaskInfo.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.863+08:00", comments="Source field: binlog_task_info.cluster_id")
    public static final SqlColumn<String> clusterId = binlogTaskInfo.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.863+08:00", comments="Source field: binlog_task_info.task_name")
    public static final SqlColumn<String> taskName = binlogTaskInfo.taskName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.863+08:00", comments="Source field: binlog_task_info.ip")
    public static final SqlColumn<String> ip = binlogTaskInfo.ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.863+08:00", comments="Source field: binlog_task_info.port")
    public static final SqlColumn<Integer> port = binlogTaskInfo.port;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.863+08:00", comments="Source field: binlog_task_info.role")
    public static final SqlColumn<String> role = binlogTaskInfo.role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.863+08:00", comments="Source field: binlog_task_info.status")
    public static final SqlColumn<Integer> status = binlogTaskInfo.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.863+08:00", comments="Source field: binlog_task_info.gmt_heartbeat")
    public static final SqlColumn<Date> gmtHeartbeat = binlogTaskInfo.gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.864+08:00", comments="Source field: binlog_task_info.container_id")
    public static final SqlColumn<String> containerId = binlogTaskInfo.containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.864+08:00", comments="Source field: binlog_task_info.version")
    public static final SqlColumn<Long> version = binlogTaskInfo.version;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.864+08:00", comments="Source field: binlog_task_info.polarx_inst_id")
    public static final SqlColumn<String> polarxInstId = binlogTaskInfo.polarxInstId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.864+08:00", comments="Source field: binlog_task_info.sources_list")
    public static final SqlColumn<String> sourcesList = binlogTaskInfo.sourcesList;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-06-08T15:33:05.862+08:00", comments="Source Table: binlog_task_info")
    public static final class BinlogTaskInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<String> taskName = column("task_name", JDBCType.VARCHAR);

        public final SqlColumn<String> ip = column("ip", JDBCType.VARCHAR);

        public final SqlColumn<Integer> port = column("port", JDBCType.INTEGER);

        public final SqlColumn<String> role = column("role", JDBCType.VARCHAR);

        public final SqlColumn<Integer> status = column("status", JDBCType.INTEGER);

        public final SqlColumn<Date> gmtHeartbeat = column("gmt_heartbeat", JDBCType.TIMESTAMP);

        public final SqlColumn<String> containerId = column("container_id", JDBCType.VARCHAR);

        public final SqlColumn<Long> version = column("version", JDBCType.BIGINT);

        public final SqlColumn<String> polarxInstId = column("polarx_inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> sourcesList = column("sources_list", JDBCType.LONGVARCHAR);

        public BinlogTaskInfo() {
            super("binlog_task_info");
        }
    }
}