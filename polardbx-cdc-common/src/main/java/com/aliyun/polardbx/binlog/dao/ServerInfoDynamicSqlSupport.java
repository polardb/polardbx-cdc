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

public final class ServerInfoDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.324+08:00",
        comments = "Source Table: server_info")
    public static final ServerInfo serverInfo = new ServerInfo();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.325+08:00",
        comments = "Source field: server_info.id")
    public static final SqlColumn<Long> id = serverInfo.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.325+08:00",
        comments = "Source field: server_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = serverInfo.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.325+08:00",
        comments = "Source field: server_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = serverInfo.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.325+08:00",
        comments = "Source field: server_info.inst_id")
    public static final SqlColumn<String> instId = serverInfo.instId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.325+08:00",
        comments = "Source field: server_info.inst_type")
    public static final SqlColumn<Integer> instType = serverInfo.instType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.325+08:00",
        comments = "Source field: server_info.ip")
    public static final SqlColumn<String> ip = serverInfo.ip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.325+08:00",
        comments = "Source field: server_info.port")
    public static final SqlColumn<Integer> port = serverInfo.port;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.325+08:00",
        comments = "Source field: server_info.htap_port")
    public static final SqlColumn<Integer> htapPort = serverInfo.htapPort;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.326+08:00",
        comments = "Source field: server_info.mgr_port")
    public static final SqlColumn<Integer> mgrPort = serverInfo.mgrPort;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.326+08:00",
        comments = "Source field: server_info.mpp_port")
    public static final SqlColumn<Integer> mppPort = serverInfo.mppPort;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.326+08:00",
        comments = "Source field: server_info.status")
    public static final SqlColumn<Integer> status = serverInfo.status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.326+08:00",
        comments = "Source field: server_info.region_id")
    public static final SqlColumn<String> regionId = serverInfo.regionId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.326+08:00",
        comments = "Source field: server_info.azone_id")
    public static final SqlColumn<String> azoneId = serverInfo.azoneId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.326+08:00",
        comments = "Source field: server_info.idc_id")
    public static final SqlColumn<String> idcId = serverInfo.idcId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.326+08:00",
        comments = "Source field: server_info.cpu_core")
    public static final SqlColumn<Integer> cpuCore = serverInfo.cpuCore;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.326+08:00",
        comments = "Source field: server_info.mem_size")
    public static final SqlColumn<Integer> memSize = serverInfo.memSize;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.326+08:00",
        comments = "Source field: server_info.extras")
    public static final SqlColumn<String> extras = serverInfo.extras;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-02-18T18:41:30.324+08:00",
        comments = "Source Table: server_info")
    public static final class ServerInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> instId = column("inst_id", JDBCType.VARCHAR);

        public final SqlColumn<Integer> instType = column("inst_type", JDBCType.INTEGER);

        public final SqlColumn<String> ip = column("ip", JDBCType.VARCHAR);

        public final SqlColumn<Integer> port = column("port", JDBCType.INTEGER);

        public final SqlColumn<Integer> htapPort = column("htap_port", JDBCType.INTEGER);

        public final SqlColumn<Integer> mgrPort = column("mgr_port", JDBCType.INTEGER);

        public final SqlColumn<Integer> mppPort = column("mpp_port", JDBCType.INTEGER);

        public final SqlColumn<Integer> status = column("status", JDBCType.INTEGER);

        public final SqlColumn<String> regionId = column("region_id", JDBCType.VARCHAR);

        public final SqlColumn<String> azoneId = column("azone_id", JDBCType.VARCHAR);

        public final SqlColumn<String> idcId = column("idc_id", JDBCType.VARCHAR);

        public final SqlColumn<Integer> cpuCore = column("cpu_core", JDBCType.INTEGER);

        public final SqlColumn<Integer> memSize = column("mem_size", JDBCType.INTEGER);

        public final SqlColumn<String> extras = column("extras", JDBCType.LONGVARCHAR);

        public ServerInfo() {
            super("server_info");
        }
    }
}