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

public final class RplStateMachineDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.769+08:00", comments="Source Table: rpl_state_machine")
    public static final RplStateMachine rplStateMachine = new RplStateMachine();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.771+08:00", comments="Source field: rpl_state_machine.id")
    public static final SqlColumn<Long> id = rplStateMachine.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.772+08:00", comments="Source field: rpl_state_machine.gmt_created")
    public static final SqlColumn<Date> gmtCreated = rplStateMachine.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.772+08:00", comments="Source field: rpl_state_machine.gmt_modified")
    public static final SqlColumn<Date> gmtModified = rplStateMachine.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.772+08:00", comments="Source field: rpl_state_machine.type")
    public static final SqlColumn<String> type = rplStateMachine.type;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.772+08:00", comments="Source field: rpl_state_machine.class_name")
    public static final SqlColumn<String> className = rplStateMachine.className;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.772+08:00", comments="Source field: rpl_state_machine.channel")
    public static final SqlColumn<String> channel = rplStateMachine.channel;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.773+08:00", comments="Source field: rpl_state_machine.status")
    public static final SqlColumn<String> status = rplStateMachine.status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.773+08:00", comments="Source field: rpl_state_machine.state")
    public static final SqlColumn<String> state = rplStateMachine.state;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.773+08:00", comments="Source field: rpl_state_machine.cluster_id")
    public static final SqlColumn<String> clusterId = rplStateMachine.clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.773+08:00", comments="Source field: rpl_state_machine.config")
    public static final SqlColumn<String> config = rplStateMachine.config;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.773+08:00", comments="Source field: rpl_state_machine.context")
    public static final SqlColumn<String> context = rplStateMachine.context;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.771+08:00", comments="Source Table: rpl_state_machine")
    public static final class RplStateMachine extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<String> type = column("`type`", JDBCType.VARCHAR);

        public final SqlColumn<String> className = column("class_name", JDBCType.VARCHAR);

        public final SqlColumn<String> channel = column("channel", JDBCType.VARCHAR);

        public final SqlColumn<String> status = column("`status`", JDBCType.VARCHAR);

        public final SqlColumn<String> state = column("`state`", JDBCType.VARCHAR);

        public final SqlColumn<String> clusterId = column("cluster_id", JDBCType.VARCHAR);

        public final SqlColumn<String> config = column("config", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> context = column("context", JDBCType.LONGVARCHAR);

        public RplStateMachine() {
            super("rpl_state_machine");
        }
    }
}