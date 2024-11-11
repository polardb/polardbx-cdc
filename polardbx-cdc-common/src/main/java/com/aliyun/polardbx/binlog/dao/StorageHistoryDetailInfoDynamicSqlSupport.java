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

public final class StorageHistoryDetailInfoDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source Table: binlog_storage_history_detail")
    public static final StorageHistoryDetailInfo storageHistoryDetailInfo = new StorageHistoryDetailInfo();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source field: binlog_storage_history_detail.id")
    public static final SqlColumn<Long> id = storageHistoryDetailInfo.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source field: binlog_storage_history_detail.gmt_created")
    public static final SqlColumn<Date> gmtCreated = storageHistoryDetailInfo.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source field: binlog_storage_history_detail.gmt_modified")
    public static final SqlColumn<Date> gmtModified = storageHistoryDetailInfo.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source field: binlog_storage_history_detail.cluster_id")
    public static final SqlColumn<String> clusterId = storageHistoryDetailInfo.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source field: binlog_storage_history_detail.tso")
    public static final SqlColumn<String> tso = storageHistoryDetailInfo.tso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source field: binlog_storage_history_detail.instruction_id")
    public static final SqlColumn<String> instructionId = storageHistoryDetailInfo.instructionId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source field: binlog_storage_history_detail.stream_name")
    public static final SqlColumn<String> streamName = storageHistoryDetailInfo.streamName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.573+08:00", comments="Source field: binlog_storage_history_detail.status")
    public static final SqlColumn<Integer> status = storageHistoryDetailInfo.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-08T17:00:59.572+08:00", comments="Source Table: binlog_storage_history_detail")
    public static final class StorageHistoryDetailInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<String> tso = column("tso", JDBCType.VARCHAR);

        public final SqlColumn<String> instructionId = column("instruction_id", JDBCType.VARCHAR);

        public final SqlColumn<String> streamName = column("stream_name", JDBCType.VARCHAR);

        public final SqlColumn<Integer> status = column("status", JDBCType.INTEGER);

        public StorageHistoryDetailInfo() {
            super("binlog_storage_history_detail");
        }
    }
}