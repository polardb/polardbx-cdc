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

public final class BinlogStorageSequenceDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.666+08:00", comments="Source Table: binlog_storage_sequence")
    public static final BinlogStorageSequence binlogStorageSequence = new BinlogStorageSequence();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.667+08:00", comments="Source field: binlog_storage_sequence.id")
    public static final SqlColumn<Long> id = binlogStorageSequence.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.667+08:00", comments="Source field: binlog_storage_sequence.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogStorageSequence.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.667+08:00", comments="Source field: binlog_storage_sequence.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogStorageSequence.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.667+08:00", comments="Source field: binlog_storage_sequence.storage_inst_id")
    public static final SqlColumn<String> storageInstId = binlogStorageSequence.storageInstId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.667+08:00", comments="Source field: binlog_storage_sequence.storage_seq")
    public static final SqlColumn<Long> storageSeq = binlogStorageSequence.storageSeq;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-11-11T18:03:36.667+08:00", comments="Source Table: binlog_storage_sequence")
    public static final class BinlogStorageSequence extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> storageInstId = column("storage_inst_id", JDBCType.VARCHAR);

        public final SqlColumn<Long> storageSeq = column("storage_seq", JDBCType.BIGINT);

        public BinlogStorageSequence() {
            super("binlog_storage_sequence");
        }
    }
}