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

public final class CdcSyncPointMetaDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.875441+08:00", comments="Source Table: cdc_sync_point_meta")
    public static final CdcSyncPointMeta cdcSyncPointMeta = new CdcSyncPointMeta();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.875631+08:00", comments="Source field: cdc_sync_point_meta.id")
    public static final SqlColumn<String> id = cdcSyncPointMeta.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.875832+08:00", comments="Source field: cdc_sync_point_meta.participants")
    public static final SqlColumn<Integer> participants = cdcSyncPointMeta.participants;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.875883+08:00", comments="Source field: cdc_sync_point_meta.tso")
    public static final SqlColumn<Long> tso = cdcSyncPointMeta.tso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.875938+08:00", comments="Source field: cdc_sync_point_meta.valid")
    public static final SqlColumn<Integer> valid = cdcSyncPointMeta.valid;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.875992+08:00", comments="Source field: cdc_sync_point_meta.gmt_created")
    public static final SqlColumn<Date> gmtCreated = cdcSyncPointMeta.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.876041+08:00", comments="Source field: cdc_sync_point_meta.gmt_modified")
    public static final SqlColumn<Date> gmtModified = cdcSyncPointMeta.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-15T17:22:34.875554+08:00", comments="Source Table: cdc_sync_point_meta")
    public static final class CdcSyncPointMeta extends SqlTable {
        public final SqlColumn<String> id = column("id", JDBCType.CHAR);

        public final SqlColumn<Integer> participants = column("participants", JDBCType.INTEGER);

        public final SqlColumn<Long> tso = column("tso", JDBCType.BIGINT);

        public final SqlColumn<Integer> valid = column("`valid`", JDBCType.INTEGER);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public CdcSyncPointMeta() {
            super("cdc_sync_point_meta");
        }
    }
}