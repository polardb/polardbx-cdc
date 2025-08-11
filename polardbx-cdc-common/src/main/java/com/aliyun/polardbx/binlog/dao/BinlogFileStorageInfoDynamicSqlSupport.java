/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class BinlogFileStorageInfoDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.92+08:00", comments="Source Table: binlog_file_storage_info")
    public static final BinlogFileStorageInfo binlogFileStorageInfo = new BinlogFileStorageInfo();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.id")
    public static final SqlColumn<Long> id = binlogFileStorageInfo.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.inst_id")
    public static final SqlColumn<String> instId = binlogFileStorageInfo.instId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.engine")
    public static final SqlColumn<String> engine = binlogFileStorageInfo.engine;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.external_endpoint")
    public static final SqlColumn<String> externalEndpoint = binlogFileStorageInfo.externalEndpoint;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.internal_classic_endpoint")
    public static final SqlColumn<String> internalClassicEndpoint = binlogFileStorageInfo.internalClassicEndpoint;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.internal_vpc_endpoint")
    public static final SqlColumn<String> internalVpcEndpoint = binlogFileStorageInfo.internalVpcEndpoint;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.file_uri")
    public static final SqlColumn<String> fileUri = binlogFileStorageInfo.fileUri;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.access_key_id")
    public static final SqlColumn<String> accessKeyId = binlogFileStorageInfo.accessKeyId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.access_key_secret")
    public static final SqlColumn<String> accessKeySecret = binlogFileStorageInfo.accessKeySecret;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.priority")
    public static final SqlColumn<Long> priority = binlogFileStorageInfo.priority;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.region_id")
    public static final SqlColumn<String> regionId = binlogFileStorageInfo.regionId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.azone_id")
    public static final SqlColumn<String> azoneId = binlogFileStorageInfo.azoneId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.cache_policy")
    public static final SqlColumn<Long> cachePolicy = binlogFileStorageInfo.cachePolicy;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.delete_policy")
    public static final SqlColumn<Long> deletePolicy = binlogFileStorageInfo.deletePolicy;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source field: binlog_file_storage_info.status")
    public static final SqlColumn<Long> status = binlogFileStorageInfo.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.922+08:00", comments="Source field: binlog_file_storage_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogFileStorageInfo.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.922+08:00", comments="Source field: binlog_file_storage_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogFileStorageInfo.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.922+08:00", comments="Source field: binlog_file_storage_info.endpoint_ordinal")
    public static final SqlColumn<Long> endpointOrdinal = binlogFileStorageInfo.endpointOrdinal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.922+08:00", comments="Source field: binlog_file_storage_info.file_system_conf")
    public static final SqlColumn<String> fileSystemConf = binlogFileStorageInfo.fileSystemConf;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-03-13T18:43:38.921+08:00", comments="Source Table: binlog_file_storage_info")
    public static final class BinlogFileStorageInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<String> instId = column("inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> engine = column("engine", JDBCType.VARCHAR);

        public final SqlColumn<String> externalEndpoint = column("external_endpoint", JDBCType.VARCHAR);

        public final SqlColumn<String> internalClassicEndpoint = column("internal_classic_endpoint", JDBCType.VARCHAR);

        public final SqlColumn<String> internalVpcEndpoint = column("internal_vpc_endpoint", JDBCType.VARCHAR);

        public final SqlColumn<String> fileUri = column("file_uri", JDBCType.VARCHAR);

        public final SqlColumn<String> accessKeyId = column("access_key_id", JDBCType.VARCHAR);

        public final SqlColumn<String> accessKeySecret = column("access_key_secret", JDBCType.VARCHAR);

        public final SqlColumn<Long> priority = column("priority", JDBCType.BIGINT);

        public final SqlColumn<String> regionId = column("region_id", JDBCType.VARCHAR);

        public final SqlColumn<String> azoneId = column("azone_id", JDBCType.VARCHAR);

        public final SqlColumn<Long> cachePolicy = column("cache_policy", JDBCType.BIGINT);

        public final SqlColumn<Long> deletePolicy = column("delete_policy", JDBCType.BIGINT);

        public final SqlColumn<Long> status = column("`status`", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> endpointOrdinal = column("endpoint_ordinal", JDBCType.BIGINT);

        public final SqlColumn<String> fileSystemConf = column("file_system_conf", JDBCType.LONGVARCHAR);

        public BinlogFileStorageInfo() {
            super("binlog_file_storage_info");
        }
    }
}