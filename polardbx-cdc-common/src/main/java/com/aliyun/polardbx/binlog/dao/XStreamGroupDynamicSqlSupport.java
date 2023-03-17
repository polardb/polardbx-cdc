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

public final class XStreamGroupDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    public static final XStreamGroup XStreamGroup = new XStreamGroup();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source field: binlog_x_stream_group.id")
    public static final SqlColumn<Long> id = XStreamGroup.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source field: binlog_x_stream_group.gmt_created")
    public static final SqlColumn<Date> gmtCreated = XStreamGroup.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source field: binlog_x_stream_group.gmt_modified")
    public static final SqlColumn<Date> gmtModified = XStreamGroup.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source field: binlog_x_stream_group.group_name")
    public static final SqlColumn<String> groupName = XStreamGroup.groupName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source field: binlog_x_stream_group.group_desc")
    public static final SqlColumn<String> groupDesc = XStreamGroup.groupDesc;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.212+08:00", comments="Source Table: binlog_x_stream_group")
    public static final class XStreamGroup extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> groupName = column("group_name", JDBCType.VARCHAR);

        public final SqlColumn<String> groupDesc = column("group_desc", JDBCType.VARCHAR);

        public XStreamGroup() {
            super("binlog_x_stream_group");
        }
    }
}