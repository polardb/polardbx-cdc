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

public final class ValidationDiffDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.004+08:00", comments="Source Table: validation_diff")
    public static final ValidationDiff validationDiff = new ValidationDiff();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.009+08:00", comments="Source field: validation_diff.id")
    public static final SqlColumn<Long> id = validationDiff.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.state_machine_id")
    public static final SqlColumn<String> stateMachineId = validationDiff.stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.service_id")
    public static final SqlColumn<String> serviceId = validationDiff.serviceId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.task_id")
    public static final SqlColumn<String> taskId = validationDiff.taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.validation_task_id")
    public static final SqlColumn<String> validationTaskId = validationDiff.validationTaskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.type")
    public static final SqlColumn<String> type = validationDiff.type;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.state")
    public static final SqlColumn<String> state = validationDiff.state;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.src_logical_db")
    public static final SqlColumn<String> srcLogicalDb = validationDiff.srcLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.src_logical_table")
    public static final SqlColumn<String> srcLogicalTable = validationDiff.srcLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.src_logical_key_col")
    public static final SqlColumn<String> srcLogicalKeyCol = validationDiff.srcLogicalKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.src_phy_db")
    public static final SqlColumn<String> srcPhyDb = validationDiff.srcPhyDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.src_phy_table")
    public static final SqlColumn<String> srcPhyTable = validationDiff.srcPhyTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.src_phy_key_col")
    public static final SqlColumn<String> srcPhyKeyCol = validationDiff.srcPhyKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.src_key_col_val")
    public static final SqlColumn<String> srcKeyColVal = validationDiff.srcKeyColVal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.dst_logical_db")
    public static final SqlColumn<String> dstLogicalDb = validationDiff.dstLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.dst_logical_table")
    public static final SqlColumn<String> dstLogicalTable = validationDiff.dstLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.dst_logical_key_col")
    public static final SqlColumn<String> dstLogicalKeyCol = validationDiff.dstLogicalKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.01+08:00", comments="Source field: validation_diff.dst_key_col_val")
    public static final SqlColumn<String> dstKeyColVal = validationDiff.dstKeyColVal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.011+08:00", comments="Source field: validation_diff.deleted")
    public static final SqlColumn<Boolean> deleted = validationDiff.deleted;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.011+08:00", comments="Source field: validation_diff.create_time")
    public static final SqlColumn<Date> createTime = validationDiff.createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.011+08:00", comments="Source field: validation_diff.update_time")
    public static final SqlColumn<Date> updateTime = validationDiff.updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.011+08:00", comments="Source field: validation_diff.diff")
    public static final SqlColumn<String> diff = validationDiff.diff;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:50.008+08:00", comments="Source Table: validation_diff")
    public static final class ValidationDiff extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<String> stateMachineId = column("state_machine_id", JDBCType.VARCHAR);

        public final SqlColumn<String> serviceId = column("service_id", JDBCType.VARCHAR);

        public final SqlColumn<String> taskId = column("task_id", JDBCType.VARCHAR);

        public final SqlColumn<String> validationTaskId = column("validation_task_id", JDBCType.VARCHAR);

        public final SqlColumn<String> type = column("`type`", JDBCType.VARCHAR);

        public final SqlColumn<String> state = column("`state`", JDBCType.VARCHAR);

        public final SqlColumn<String> srcLogicalDb = column("src_logical_db", JDBCType.VARCHAR);

        public final SqlColumn<String> srcLogicalTable = column("src_logical_table", JDBCType.VARCHAR);

        public final SqlColumn<String> srcLogicalKeyCol = column("src_logical_key_col", JDBCType.VARCHAR);

        public final SqlColumn<String> srcPhyDb = column("src_phy_db", JDBCType.VARCHAR);

        public final SqlColumn<String> srcPhyTable = column("src_phy_table", JDBCType.VARCHAR);

        public final SqlColumn<String> srcPhyKeyCol = column("src_phy_key_col", JDBCType.VARCHAR);

        public final SqlColumn<String> srcKeyColVal = column("src_key_col_val", JDBCType.VARCHAR);

        public final SqlColumn<String> dstLogicalDb = column("dst_logical_db", JDBCType.VARCHAR);

        public final SqlColumn<String> dstLogicalTable = column("dst_logical_table", JDBCType.VARCHAR);

        public final SqlColumn<String> dstLogicalKeyCol = column("dst_logical_key_col", JDBCType.VARCHAR);

        public final SqlColumn<String> dstKeyColVal = column("dst_key_col_val", JDBCType.VARCHAR);

        public final SqlColumn<Boolean> deleted = column("deleted", JDBCType.BIT);

        public final SqlColumn<Date> createTime = column("create_time", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> updateTime = column("update_time", JDBCType.TIMESTAMP);

        public final SqlColumn<String> diff = column("diff", JDBCType.LONGVARCHAR);

        public ValidationDiff() {
            super("validation_diff");
        }
    }
}