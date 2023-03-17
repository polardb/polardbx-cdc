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

public final class InstConfigDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.058+08:00", comments="Source Table: inst_config")
    public static final InstConfig instConfig = new InstConfig();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.058+08:00", comments="Source field: inst_config.id")
    public static final SqlColumn<Long> id = instConfig.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.059+08:00", comments="Source field: inst_config.gmt_created")
    public static final SqlColumn<Date> gmtCreated = instConfig.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.059+08:00", comments="Source field: inst_config.gmt_modified")
    public static final SqlColumn<Date> gmtModified = instConfig.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.059+08:00", comments="Source field: inst_config.inst_id")
    public static final SqlColumn<String> instId = instConfig.instId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.059+08:00", comments="Source field: inst_config.param_key")
    public static final SqlColumn<String> paramKey = instConfig.paramKey;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.059+08:00", comments="Source field: inst_config.param_val")
    public static final SqlColumn<String> paramVal = instConfig.paramVal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-09-22T09:50:34.058+08:00", comments="Source Table: inst_config")
    public static final class InstConfig extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> instId = column("inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> paramKey = column("param_key", JDBCType.VARCHAR);

        public final SqlColumn<String> paramVal = column("param_val", JDBCType.VARCHAR);

        public InstConfig() {
            super("inst_config");
        }
    }
}