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

public final class ValidationTaskDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.942+08:00", comments="Source Table: validation_task")
    public static final ValidationTask validationTask = new ValidationTask();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.942+08:00", comments="Source field: validation_task.id")
    public static final SqlColumn<Long> id = validationTask.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.942+08:00", comments="Source field: validation_task.external_id")
    public static final SqlColumn<String> externalId = validationTask.externalId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.942+08:00", comments="Source field: validation_task.state_machine_id")
    public static final SqlColumn<String> stateMachineId = validationTask.stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.942+08:00", comments="Source field: validation_task.service_id")
    public static final SqlColumn<String> serviceId = validationTask.serviceId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.942+08:00", comments="Source field: validation_task.task_id")
    public static final SqlColumn<String> taskId = validationTask.taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.type")
    public static final SqlColumn<Integer> type = validationTask.type;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.state")
    public static final SqlColumn<Integer> state = validationTask.state;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.drds_ins_id")
    public static final SqlColumn<String> drdsInsId = validationTask.drdsInsId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.rds_ins_id")
    public static final SqlColumn<String> rdsInsId = validationTask.rdsInsId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.src_logical_db")
    public static final SqlColumn<String> srcLogicalDb = validationTask.srcLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.src_logical_table")
    public static final SqlColumn<String> srcLogicalTable = validationTask.srcLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.src_logical_key_col")
    public static final SqlColumn<String> srcLogicalKeyCol = validationTask.srcLogicalKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.src_phy_db")
    public static final SqlColumn<String> srcPhyDb = validationTask.srcPhyDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.src_phy_table")
    public static final SqlColumn<String> srcPhyTable = validationTask.srcPhyTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.src_phy_key_col")
    public static final SqlColumn<String> srcPhyKeyCol = validationTask.srcPhyKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.polardbx_ins_id")
    public static final SqlColumn<String> polardbxInsId = validationTask.polardbxInsId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.dst_logical_db")
    public static final SqlColumn<String> dstLogicalDb = validationTask.dstLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.dst_logical_table")
    public static final SqlColumn<String> dstLogicalTable = validationTask.dstLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.dst_logical_key_col")
    public static final SqlColumn<String> dstLogicalKeyCol = validationTask.dstLogicalKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.task_range")
    public static final SqlColumn<String> taskRange = validationTask.taskRange;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.deleted")
    public static final SqlColumn<Boolean> deleted = validationTask.deleted;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.create_time")
    public static final SqlColumn<Date> createTime = validationTask.createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.update_time")
    public static final SqlColumn<Date> updateTime = validationTask.updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.config")
    public static final SqlColumn<String> config = validationTask.config;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.943+08:00", comments="Source field: validation_task.stats")
    public static final SqlColumn<String> stats = validationTask.stats;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.942+08:00", comments="Source Table: validation_task")
    public static final class ValidationTask extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<String> externalId = column("external_id", JDBCType.VARCHAR);

        public final SqlColumn<String> stateMachineId = column("state_machine_id", JDBCType.VARCHAR);

        public final SqlColumn<String> serviceId = column("service_id", JDBCType.VARCHAR);

        public final SqlColumn<String> taskId = column("task_id", JDBCType.VARCHAR);

        public final SqlColumn<Integer> type = column("type", JDBCType.INTEGER);

        public final SqlColumn<Integer> state = column("state", JDBCType.INTEGER);

        public final SqlColumn<String> drdsInsId = column("drds_ins_id", JDBCType.VARCHAR);

        public final SqlColumn<String> rdsInsId = column("rds_ins_id", JDBCType.VARCHAR);

        public final SqlColumn<String> srcLogicalDb = column("src_logical_db", JDBCType.VARCHAR);

        public final SqlColumn<String> srcLogicalTable = column("src_logical_table", JDBCType.VARCHAR);

        public final SqlColumn<String> srcLogicalKeyCol = column("src_logical_key_col", JDBCType.VARCHAR);

        public final SqlColumn<String> srcPhyDb = column("src_phy_db", JDBCType.VARCHAR);

        public final SqlColumn<String> srcPhyTable = column("src_phy_table", JDBCType.VARCHAR);

        public final SqlColumn<String> srcPhyKeyCol = column("src_phy_key_col", JDBCType.VARCHAR);

        public final SqlColumn<String> polardbxInsId = column("polardbx_ins_id", JDBCType.VARCHAR);

        public final SqlColumn<String> dstLogicalDb = column("dst_logical_db", JDBCType.VARCHAR);

        public final SqlColumn<String> dstLogicalTable = column("dst_logical_table", JDBCType.VARCHAR);

        public final SqlColumn<String> dstLogicalKeyCol = column("dst_logical_key_col", JDBCType.VARCHAR);

        public final SqlColumn<String> taskRange = column("task_range", JDBCType.VARCHAR);

        public final SqlColumn<Boolean> deleted = column("deleted", JDBCType.BIT);

        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> updateTime = column("update_time", JDBCType.TIMESTAMP);

        public final SqlColumn<String> config = column("config", JDBCType.LONGVARCHAR);

        public final SqlColumn<String> stats = column("stats", JDBCType.LONGVARCHAR);

        public ValidationTask() {
            super("validation_task");
        }
    }
}