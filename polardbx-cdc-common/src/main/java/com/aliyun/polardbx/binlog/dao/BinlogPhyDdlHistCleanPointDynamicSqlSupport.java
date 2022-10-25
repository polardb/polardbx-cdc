/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.dao;

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class BinlogPhyDdlHistCleanPointDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.846+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    public static final BinlogPhyDdlHistCleanPoint binlogPhyDdlHistCleanPoint = new BinlogPhyDdlHistCleanPoint();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.847+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.id")
    public static final SqlColumn<Integer> id = binlogPhyDdlHistCleanPoint.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.847+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.gmt_created")
    public static final SqlColumn<Date> gmtCreated = binlogPhyDdlHistCleanPoint.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.847+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.gmt_modified")
    public static final SqlColumn<Date> gmtModified = binlogPhyDdlHistCleanPoint.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.847+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.tso")
    public static final SqlColumn<String> tso = binlogPhyDdlHistCleanPoint.tso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.847+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.storage_inst_id")
    public static final SqlColumn<String> storageInstId = binlogPhyDdlHistCleanPoint.storageInstId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.847+08:00", comments="Source field: binlog_phy_ddl_hist_clean_point.ext")
    public static final SqlColumn<String> ext = binlogPhyDdlHistCleanPoint.ext;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-05-20T19:57:32.847+08:00", comments="Source Table: binlog_phy_ddl_hist_clean_point")
    public static final class BinlogPhyDdlHistCleanPoint extends SqlTable {
        public final SqlColumn<Integer> id = column("id", JDBCType.INTEGER);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> tso = column("tso", JDBCType.VARCHAR);

        public final SqlColumn<String> storageInstId = column("storage_inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> ext = column("ext", JDBCType.LONGVARCHAR);

        public BinlogPhyDdlHistCleanPoint() {
            super("binlog_phy_ddl_hist_clean_point");
        }
    }
}