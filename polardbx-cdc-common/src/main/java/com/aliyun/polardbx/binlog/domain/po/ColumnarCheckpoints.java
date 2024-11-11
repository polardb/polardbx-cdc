/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class ColumnarCheckpoints {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.303+08:00", comments="Source field: columnar_checkpoints.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.303+08:00", comments="Source field: columnar_checkpoints.logical_schema")
    private String logicalSchema;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.logical_table")
    private String logicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.partition_name")
    private String partitionName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.checkpoint_tso")
    private Long checkpointTso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.checkpoint_type")
    private String checkpointType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.create_time")
    private Date createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.update_time")
    private Date updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.offset")
    private String offset;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.extra")
    private String extra;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.301+08:00", comments="Source Table: columnar_checkpoints")
    public ColumnarCheckpoints(Long id, String logicalSchema, String logicalTable, String partitionName, Long checkpointTso, String checkpointType, Date createTime, Date updateTime, String offset, String extra) {
        this.id = id;
        this.logicalSchema = logicalSchema;
        this.logicalTable = logicalTable;
        this.partitionName = partitionName;
        this.checkpointTso = checkpointTso;
        this.checkpointType = checkpointType;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.offset = offset;
        this.extra = extra;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.303+08:00", comments="Source Table: columnar_checkpoints")
    public ColumnarCheckpoints() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.303+08:00", comments="Source field: columnar_checkpoints.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.303+08:00", comments="Source field: columnar_checkpoints.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.303+08:00", comments="Source field: columnar_checkpoints.logical_schema")
    public String getLogicalSchema() {
        return logicalSchema;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.logical_schema")
    public void setLogicalSchema(String logicalSchema) {
        this.logicalSchema = logicalSchema == null ? null : logicalSchema.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.logical_table")
    public String getLogicalTable() {
        return logicalTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.logical_table")
    public void setLogicalTable(String logicalTable) {
        this.logicalTable = logicalTable == null ? null : logicalTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.partition_name")
    public String getPartitionName() {
        return partitionName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.partition_name")
    public void setPartitionName(String partitionName) {
        this.partitionName = partitionName == null ? null : partitionName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.checkpoint_tso")
    public Long getCheckpointTso() {
        return checkpointTso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.checkpoint_tso")
    public void setCheckpointTso(Long checkpointTso) {
        this.checkpointTso = checkpointTso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.checkpoint_type")
    public String getCheckpointType() {
        return checkpointType;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.checkpoint_type")
    public void setCheckpointType(String checkpointType) {
        this.checkpointType = checkpointType == null ? null : checkpointType.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.create_time")
    public Date getCreateTime() {
        return createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.create_time")
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.update_time")
    public Date getUpdateTime() {
        return updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.update_time")
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.offset")
    public String getOffset() {
        return offset;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.offset")
    public void setOffset(String offset) {
        this.offset = offset == null ? null : offset.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.extra")
    public String getExtra() {
        return extra;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-03-27T15:32:09.304+08:00", comments="Source field: columnar_checkpoints.extra")
    public void setExtra(String extra) {
        this.extra = extra == null ? null : extra.trim();
    }
}