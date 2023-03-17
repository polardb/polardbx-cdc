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

public final class SemiSnapshotInfoDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.124+08:00", comments="Source Table: binlog_semi_snapshot")
    public static final SemiSnapshotInfo semiSnapshotInfo = new SemiSnapshotInfo();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.124+08:00", comments="Source field: binlog_semi_snapshot.id")
    public static final SqlColumn<Long> id = semiSnapshotInfo.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.124+08:00", comments="Source field: binlog_semi_snapshot.gmt_created")
    public static final SqlColumn<Date> gmtCreated = semiSnapshotInfo.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.124+08:00", comments="Source field: binlog_semi_snapshot.gmt_modified")
    public static final SqlColumn<Date> gmtModified = semiSnapshotInfo.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.125+08:00", comments="Source field: binlog_semi_snapshot.tso")
    public static final SqlColumn<String> tso = semiSnapshotInfo.tso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.125+08:00", comments="Source field: binlog_semi_snapshot.storage_inst_id")
    public static final SqlColumn<String> storageInstId = semiSnapshotInfo.storageInstId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-02-14T10:39:59.124+08:00", comments="Source Table: binlog_semi_snapshot")
    public static final class SemiSnapshotInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> tso = column("tso", JDBCType.VARCHAR);

        public final SqlColumn<String> storageInstId = column("storage_inst_id", JDBCType.VARCHAR);

        public SemiSnapshotInfo() {
            super("binlog_semi_snapshot");
        }
    }
}