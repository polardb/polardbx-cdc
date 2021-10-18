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

public final class BinlogPhyDdlHistoryDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.678+08:00",
        comments = "Source Table: binlog_phy_ddl_history")
    public static final BinlogPhyDdlHistory binlogPhyDdlHistory = new BinlogPhyDdlHistory();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.679+08:00",
        comments = "Source field: binlog_phy_ddl_history.id")
    public static final SqlColumn<Integer> id = binlogPhyDdlHistory.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.679+08:00",
        comments = "Source field: binlog_phy_ddl_history.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogPhyDdlHistory.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.679+08:00",
        comments = "Source field: binlog_phy_ddl_history.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogPhyDdlHistory.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.679+08:00",
        comments = "Source field: binlog_phy_ddl_history.tso")
    public static final SqlColumn<String> tso = binlogPhyDdlHistory.tso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.679+08:00",
        comments = "Source field: binlog_phy_ddl_history.binlog_file")
    public static final SqlColumn<String> binlogFile = binlogPhyDdlHistory.binlogFile;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.679+08:00",
        comments = "Source field: binlog_phy_ddl_history.pos")
    public static final SqlColumn<Integer> pos = binlogPhyDdlHistory.pos;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.679+08:00",
        comments = "Source field: binlog_phy_ddl_history.storage_inst_id")
    public static final SqlColumn<String> storageInstId = binlogPhyDdlHistory.storageInstId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.679+08:00",
        comments = "Source field: binlog_phy_ddl_history.db_name")
    public static final SqlColumn<String> dbName = binlogPhyDdlHistory.dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.679+08:00",
        comments = "Source field: binlog_phy_ddl_history.extra")
    public static final SqlColumn<String> extra = binlogPhyDdlHistory.extra;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.679+08:00",
        comments = "Source field: binlog_phy_ddl_history.ddl")
    public static final SqlColumn<String> ddl = binlogPhyDdlHistory.ddl;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-12-18T11:31:09.678+08:00",
        comments = "Source Table: binlog_phy_ddl_history")
    public static final class BinlogPhyDdlHistory extends SqlTable {
        public final SqlColumn<Integer> id = column("id", JDBCType.INTEGER);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> tso = column("tso", JDBCType.VARCHAR);

        public final SqlColumn<String> binlogFile = column("binlog_file", JDBCType.VARCHAR);

        public final SqlColumn<Integer> pos = column("pos", JDBCType.INTEGER);

        public final SqlColumn<String> storageInstId = column("storage_inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> dbName = column("db_name", JDBCType.VARCHAR);

        public final SqlColumn<String> extra = column("extra", JDBCType.VARCHAR);

        public final SqlColumn<String> ddl = column("ddl", JDBCType.LONGVARCHAR);

        public BinlogPhyDdlHistory() {
            super("binlog_phy_ddl_history");
        }
    }
}