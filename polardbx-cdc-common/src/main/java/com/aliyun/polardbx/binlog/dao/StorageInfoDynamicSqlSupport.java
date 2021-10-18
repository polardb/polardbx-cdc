/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.dao;

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

import javax.annotation.Generated;
import java.sql.JDBCType;
import java.util.Date;

public final class StorageInfoDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.416+08:00",
        comments = "Source Table: storage_info")
    public static final StorageInfo storageInfo = new StorageInfo();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.416+08:00",
        comments = "Source field: storage_info.id")
    public static final SqlColumn<Long> id = storageInfo.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.416+08:00",
        comments = "Source field: storage_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = storageInfo.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = storageInfo.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.inst_id")
    public static final SqlColumn<String> instId = storageInfo.instId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.storage_inst_id")
    public static final SqlColumn<String> storageInstId = storageInfo.storageInstId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.storage_master_inst_id")
    public static final SqlColumn<String> storageMasterInstId = storageInfo.storageMasterInstId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.ip")
    public static final SqlColumn<String> ip = storageInfo.ip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.port")
    public static final SqlColumn<Integer> port = storageInfo.port;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.xport")
    public static final SqlColumn<Integer> xport = storageInfo.xport;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.user")
    public static final SqlColumn<String> user = storageInfo.user;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.storage_type")
    public static final SqlColumn<Integer> storageType = storageInfo.storageType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.inst_kind")
    public static final SqlColumn<Integer> instKind = storageInfo.instKind;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.status")
    public static final SqlColumn<Integer> status = storageInfo.status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.region_id")
    public static final SqlColumn<String> regionId = storageInfo.regionId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.azone_id")
    public static final SqlColumn<String> azoneId = storageInfo.azoneId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.idc_id")
    public static final SqlColumn<String> idcId = storageInfo.idcId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.max_conn")
    public static final SqlColumn<Integer> maxConn = storageInfo.maxConn;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.417+08:00",
        comments = "Source field: storage_info.cpu_core")
    public static final SqlColumn<Integer> cpuCore = storageInfo.cpuCore;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.418+08:00",
        comments = "Source field: storage_info.mem_size")
    public static final SqlColumn<Integer> memSize = storageInfo.memSize;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.418+08:00",
        comments = "Source field: storage_info.is_vip")
    public static final SqlColumn<Integer> isVip = storageInfo.isVip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.418+08:00",
        comments = "Source field: storage_info.passwd_enc")
    public static final SqlColumn<String> passwdEnc = storageInfo.passwdEnc;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.418+08:00",
        comments = "Source field: storage_info.extras")
    public static final SqlColumn<String> extras = storageInfo.extras;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.416+08:00",
        comments = "Source Table: storage_info")
    public static final class StorageInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> instId = column("inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> storageInstId = column("storage_inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> storageMasterInstId = column("storage_master_inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> ip = column("ip", JDBCType.VARCHAR);

        public final SqlColumn<Integer> port = column("port", JDBCType.INTEGER);

        public final SqlColumn<Integer> xport = column("xport", JDBCType.INTEGER);

        public final SqlColumn<String> user = column("user", JDBCType.VARCHAR);

        public final SqlColumn<Integer> storageType = column("storage_type", JDBCType.INTEGER);

        public final SqlColumn<Integer> instKind = column("inst_kind", JDBCType.INTEGER);

        public final SqlColumn<Integer> status = column("status", JDBCType.INTEGER);

        public final SqlColumn<String> regionId = column("region_id", JDBCType.VARCHAR);

        public final SqlColumn<String> azoneId = column("azone_id", JDBCType.VARCHAR);

        public final SqlColumn<String> idcId = column("idc_id", JDBCType.VARCHAR);

        public final SqlColumn<Integer> maxConn = column("max_conn", JDBCType.INTEGER);

        public final SqlColumn<Integer> cpuCore = column("cpu_core", JDBCType.INTEGER);

        public final SqlColumn<Integer> memSize = column("mem_size", JDBCType.INTEGER);

        public final SqlColumn<Integer> isVip = column("is_vip", JDBCType.INTEGER);

        public final SqlColumn<String> passwdEnc = column("passwd_enc", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> extras = column("extras", JDBCType.LONGVARCHAR);

        public StorageInfo() {
            super("storage_info");
        }
    }
}