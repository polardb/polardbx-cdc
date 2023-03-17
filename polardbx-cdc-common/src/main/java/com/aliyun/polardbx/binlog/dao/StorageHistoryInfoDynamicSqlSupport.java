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

public final class StorageHistoryInfoDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.546+08:00", comments="Source Table: binlog_storage_history")
    public static final StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.546+08:00", comments="Source field: binlog_storage_history.id")
    public static final SqlColumn<Long> id = storageHistoryInfo.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.547+08:00", comments="Source field: binlog_storage_history.gmt_created")
    public static final SqlColumn<Date> gmtCreated = storageHistoryInfo.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.547+08:00", comments="Source field: binlog_storage_history.gmt_modified")
    public static final SqlColumn<Date> gmtModified = storageHistoryInfo.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.547+08:00", comments="Source field: binlog_storage_history.tso")
    public static final SqlColumn<String> tso = storageHistoryInfo.tso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.547+08:00", comments="Source field: binlog_storage_history.status")
    public static final SqlColumn<Integer> status = storageHistoryInfo.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.547+08:00", comments="Source field: binlog_storage_history.instruction_id")
    public static final SqlColumn<String> instructionId = storageHistoryInfo.instructionId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.547+08:00", comments="Source field: binlog_storage_history.cluster_id")
    public static final SqlColumn<String> clusterId = storageHistoryInfo.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.548+08:00", comments="Source field: binlog_storage_history.group_name")
    public static final SqlColumn<String> groupName = storageHistoryInfo.groupName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.548+08:00", comments="Source field: binlog_storage_history.storage_content")
    public static final SqlColumn<String> storageContent = storageHistoryInfo.storageContent;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.546+08:00", comments="Source Table: binlog_storage_history")
    public static final class StorageHistoryInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> tso = column("tso", JDBCType.VARCHAR);

        public final SqlColumn<Integer> status = column("status", JDBCType.INTEGER);

        public final SqlColumn<String> instructionId = column("instruction_id", JDBCType.VARCHAR);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<String> groupName = column("group_name", JDBCType.VARCHAR);

        public final SqlColumn<String> storageContent = column("storage_content", JDBCType.LONGVARCHAR);

        public StorageHistoryInfo() {
            super("binlog_storage_history");
        }
    }
}