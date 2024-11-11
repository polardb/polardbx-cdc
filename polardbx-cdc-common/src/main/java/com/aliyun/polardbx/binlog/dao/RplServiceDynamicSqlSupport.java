/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class RplServiceDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.932+08:00", comments="Source Table: rpl_service")
    public static final RplService rplService = new RplService();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.933+08:00", comments="Source field: rpl_service.id")
    public static final SqlColumn<Long> id = rplService.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.933+08:00", comments="Source field: rpl_service.gmt_created")
    public static final SqlColumn<Date> gmtCreated = rplService.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.933+08:00", comments="Source field: rpl_service.gmt_modified")
    public static final SqlColumn<Date> gmtModified = rplService.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.933+08:00", comments="Source field: rpl_service.state_machine_id")
    public static final SqlColumn<Long> stateMachineId = rplService.stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.933+08:00", comments="Source field: rpl_service.service_type")
    public static final SqlColumn<String> serviceType = rplService.serviceType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.934+08:00", comments="Source field: rpl_service.state_list")
    public static final SqlColumn<String> stateList = rplService.stateList;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.934+08:00", comments="Source field: rpl_service.channel")
    public static final SqlColumn<String> channel = rplService.channel;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.934+08:00", comments="Source field: rpl_service.status")
    public static final SqlColumn<String> status = rplService.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.933+08:00", comments="Source Table: rpl_service")
    public static final class RplService extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> stateMachineId = column("state_machine_id", JDBCType.BIGINT);

        public final SqlColumn<String> serviceType = column("service_type", JDBCType.VARCHAR);

        public final SqlColumn<String> stateList = column("state_list", JDBCType.VARCHAR);

        public final SqlColumn<String> channel = column("channel", JDBCType.VARCHAR);

        public final SqlColumn<String> status = column("`status`", JDBCType.VARCHAR);

        public RplService() {
            super("rpl_service");
        }
    }
}