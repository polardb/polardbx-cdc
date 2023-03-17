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

public final class PolarxCNodeInfoDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.077+08:00",
        comments = "Source Table: node_info")
    public static final PolarxCNodeInfo polarxCNodeInfo = new PolarxCNodeInfo();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.078+08:00",
        comments = "Source field: node_info.id")
    public static final SqlColumn<Long> id = polarxCNodeInfo.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.078+08:00",
        comments = "Source field: node_info.cluster")
    public static final SqlColumn<String> cluster = polarxCNodeInfo.cluster;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.078+08:00",
        comments = "Source field: node_info.inst_id")
    public static final SqlColumn<String> instId = polarxCNodeInfo.instId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.078+08:00",
        comments = "Source field: node_info.nodeid")
    public static final SqlColumn<String> nodeid = polarxCNodeInfo.nodeid;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.079+08:00",
        comments = "Source field: node_info.version")
    public static final SqlColumn<String> version = polarxCNodeInfo.version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.079+08:00",
        comments = "Source field: node_info.ip")
    public static final SqlColumn<String> ip = polarxCNodeInfo.ip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.079+08:00",
        comments = "Source field: node_info.port")
    public static final SqlColumn<Integer> port = polarxCNodeInfo.port;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.079+08:00",
        comments = "Source field: node_info.rpc_port")
    public static final SqlColumn<Long> rpcPort = polarxCNodeInfo.rpcPort;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.079+08:00",
        comments = "Source field: node_info.role")
    public static final SqlColumn<Long> role = polarxCNodeInfo.role;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.079+08:00",
        comments = "Source field: node_info.status")
    public static final SqlColumn<Long> status = polarxCNodeInfo.status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.08+08:00",
        comments = "Source field: node_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = polarxCNodeInfo.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.08+08:00",
        comments = "Source field: node_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = polarxCNodeInfo.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.077+08:00",
        comments = "Source Table: node_info")
    public static final class PolarxCNodeInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<String> cluster = column("cluster", JDBCType.VARCHAR);

        public final SqlColumn<String> instId = column("inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> nodeid = column("nodeid", JDBCType.VARCHAR);

        public final SqlColumn<String> version = column("version", JDBCType.VARCHAR);

        public final SqlColumn<String> ip = column("ip", JDBCType.VARCHAR);

        public final SqlColumn<Integer> port = column("port", JDBCType.INTEGER);

        public final SqlColumn<Long> rpcPort = column("rpc_port", JDBCType.BIGINT);

        public final SqlColumn<Long> role = column("role", JDBCType.BIGINT);

        public final SqlColumn<Long> status = column("status", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public PolarxCNodeInfo() {
            super("node_info");
        }
    }
}