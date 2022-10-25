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

import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

import javax.annotation.Generated;
import java.sql.JDBCType;
import java.util.Date;

public final class GroupDetailInfoDynamicSqlSupport {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.455+08:00",
        comments = "Source Table: group_detail_info")
    public static final GroupDetailInfo groupDetailInfo = new GroupDetailInfo();

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.455+08:00",
        comments = "Source field: group_detail_info.id")
    public static final SqlColumn<Long> id = groupDetailInfo.id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.455+08:00",
        comments = "Source field: group_detail_info.gmt_created")
    public static final SqlColumn<Date> gmtCreated = groupDetailInfo.gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.455+08:00",
        comments = "Source field: group_detail_info.gmt_modified")
    public static final SqlColumn<Date> gmtModified = groupDetailInfo.gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.455+08:00",
        comments = "Source field: group_detail_info.inst_id")
    public static final SqlColumn<String> instId = groupDetailInfo.instId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.455+08:00",
        comments = "Source field: group_detail_info.db_name")
    public static final SqlColumn<String> dbName = groupDetailInfo.dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.455+08:00",
        comments = "Source field: group_detail_info.group_name")
    public static final SqlColumn<String> groupName = groupDetailInfo.groupName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.456+08:00",
        comments = "Source field: group_detail_info.storage_inst_id")
    public static final SqlColumn<String> storageInstId = groupDetailInfo.storageInstId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.455+08:00",
        comments = "Source Table: group_detail_info")
    public static final class GroupDetailInfo extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> instId = column("inst_id", JDBCType.VARCHAR);

        public final SqlColumn<String> dbName = column("db_name", JDBCType.VARCHAR);

        public final SqlColumn<String> groupName = column("group_name", JDBCType.VARCHAR);

        public final SqlColumn<String> storageInstId = column("storage_inst_id", JDBCType.VARCHAR);

        public GroupDetailInfo() {
            super("group_detail_info");
        }
    }
}