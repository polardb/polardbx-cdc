/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class ValidationTask {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.external_id")
    private String externalId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.state_machine_id")
    private String stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.service_id")
    private String serviceId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.task_id")
    private String taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.type")
    private String type;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.state")
    private String state;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.drds_ins_id")
    private String drdsInsId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.rds_ins_id")
    private String rdsInsId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_logical_db")
    private String srcLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_logical_table")
    private String srcLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_logical_key_col")
    private String srcLogicalKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_phy_db")
    private String srcPhyDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_phy_table")
    private String srcPhyTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_phy_key_col")
    private String srcPhyKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.polardbx_ins_id")
    private String polardbxInsId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.dst_logical_db")
    private String dstLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.dst_logical_table")
    private String dstLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.dst_logical_key_col")
    private String dstLogicalKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.task_range")
    private String taskRange;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.deleted")
    private Boolean deleted;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.create_time")
    private Date createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.update_time")
    private Date updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.config")
    private String config;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.stats")
    private String stats;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source Table: validation_task")
    public ValidationTask(Long id, String externalId, String stateMachineId, String serviceId, String taskId, String type, String state, String drdsInsId, String rdsInsId, String srcLogicalDb, String srcLogicalTable, String srcLogicalKeyCol, String srcPhyDb, String srcPhyTable, String srcPhyKeyCol, String polardbxInsId, String dstLogicalDb, String dstLogicalTable, String dstLogicalKeyCol, String taskRange, Boolean deleted, Date createTime, Date updateTime, String config, String stats) {
        this.id = id;
        this.externalId = externalId;
        this.stateMachineId = stateMachineId;
        this.serviceId = serviceId;
        this.taskId = taskId;
        this.type = type;
        this.state = state;
        this.drdsInsId = drdsInsId;
        this.rdsInsId = rdsInsId;
        this.srcLogicalDb = srcLogicalDb;
        this.srcLogicalTable = srcLogicalTable;
        this.srcLogicalKeyCol = srcLogicalKeyCol;
        this.srcPhyDb = srcPhyDb;
        this.srcPhyTable = srcPhyTable;
        this.srcPhyKeyCol = srcPhyKeyCol;
        this.polardbxInsId = polardbxInsId;
        this.dstLogicalDb = dstLogicalDb;
        this.dstLogicalTable = dstLogicalTable;
        this.dstLogicalKeyCol = dstLogicalKeyCol;
        this.taskRange = taskRange;
        this.deleted = deleted;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.config = config;
        this.stats = stats;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source Table: validation_task")
    public ValidationTask() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.external_id")
    public String getExternalId() {
        return externalId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.external_id")
    public void setExternalId(String externalId) {
        this.externalId = externalId == null ? null : externalId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.state_machine_id")
    public String getStateMachineId() {
        return stateMachineId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.state_machine_id")
    public void setStateMachineId(String stateMachineId) {
        this.stateMachineId = stateMachineId == null ? null : stateMachineId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.service_id")
    public String getServiceId() {
        return serviceId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.service_id")
    public void setServiceId(String serviceId) {
        this.serviceId = serviceId == null ? null : serviceId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.task_id")
    public String getTaskId() {
        return taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.task_id")
    public void setTaskId(String taskId) {
        this.taskId = taskId == null ? null : taskId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.type")
    public String getType() {
        return type;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.type")
    public void setType(String type) {
        this.type = type == null ? null : type.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.state")
    public String getState() {
        return state;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.state")
    public void setState(String state) {
        this.state = state == null ? null : state.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.drds_ins_id")
    public String getDrdsInsId() {
        return drdsInsId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.drds_ins_id")
    public void setDrdsInsId(String drdsInsId) {
        this.drdsInsId = drdsInsId == null ? null : drdsInsId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.rds_ins_id")
    public String getRdsInsId() {
        return rdsInsId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.rds_ins_id")
    public void setRdsInsId(String rdsInsId) {
        this.rdsInsId = rdsInsId == null ? null : rdsInsId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_logical_db")
    public String getSrcLogicalDb() {
        return srcLogicalDb;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_logical_db")
    public void setSrcLogicalDb(String srcLogicalDb) {
        this.srcLogicalDb = srcLogicalDb == null ? null : srcLogicalDb.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_logical_table")
    public String getSrcLogicalTable() {
        return srcLogicalTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_logical_table")
    public void setSrcLogicalTable(String srcLogicalTable) {
        this.srcLogicalTable = srcLogicalTable == null ? null : srcLogicalTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_logical_key_col")
    public String getSrcLogicalKeyCol() {
        return srcLogicalKeyCol;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_logical_key_col")
    public void setSrcLogicalKeyCol(String srcLogicalKeyCol) {
        this.srcLogicalKeyCol = srcLogicalKeyCol == null ? null : srcLogicalKeyCol.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_phy_db")
    public String getSrcPhyDb() {
        return srcPhyDb;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_phy_db")
    public void setSrcPhyDb(String srcPhyDb) {
        this.srcPhyDb = srcPhyDb == null ? null : srcPhyDb.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_phy_table")
    public String getSrcPhyTable() {
        return srcPhyTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.989+08:00", comments="Source field: validation_task.src_phy_table")
    public void setSrcPhyTable(String srcPhyTable) {
        this.srcPhyTable = srcPhyTable == null ? null : srcPhyTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.src_phy_key_col")
    public String getSrcPhyKeyCol() {
        return srcPhyKeyCol;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.src_phy_key_col")
    public void setSrcPhyKeyCol(String srcPhyKeyCol) {
        this.srcPhyKeyCol = srcPhyKeyCol == null ? null : srcPhyKeyCol.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.polardbx_ins_id")
    public String getPolardbxInsId() {
        return polardbxInsId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.polardbx_ins_id")
    public void setPolardbxInsId(String polardbxInsId) {
        this.polardbxInsId = polardbxInsId == null ? null : polardbxInsId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.dst_logical_db")
    public String getDstLogicalDb() {
        return dstLogicalDb;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.dst_logical_db")
    public void setDstLogicalDb(String dstLogicalDb) {
        this.dstLogicalDb = dstLogicalDb == null ? null : dstLogicalDb.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.dst_logical_table")
    public String getDstLogicalTable() {
        return dstLogicalTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.99+08:00", comments="Source field: validation_task.dst_logical_table")
    public void setDstLogicalTable(String dstLogicalTable) {
        this.dstLogicalTable = dstLogicalTable == null ? null : dstLogicalTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.991+08:00", comments="Source field: validation_task.dst_logical_key_col")
    public String getDstLogicalKeyCol() {
        return dstLogicalKeyCol;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.992+08:00", comments="Source field: validation_task.dst_logical_key_col")
    public void setDstLogicalKeyCol(String dstLogicalKeyCol) {
        this.dstLogicalKeyCol = dstLogicalKeyCol == null ? null : dstLogicalKeyCol.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.task_range")
    public String getTaskRange() {
        return taskRange;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.task_range")
    public void setTaskRange(String taskRange) {
        this.taskRange = taskRange == null ? null : taskRange.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.deleted")
    public Boolean getDeleted() {
        return deleted;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.deleted")
    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.create_time")
    public Date getCreateTime() {
        return createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.create_time")
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.update_time")
    public Date getUpdateTime() {
        return updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.update_time")
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.config")
    public String getConfig() {
        return config;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.config")
    public void setConfig(String config) {
        this.config = config == null ? null : config.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.stats")
    public String getStats() {
        return stats;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.993+08:00", comments="Source field: validation_task.stats")
    public void setStats(String stats) {
        this.stats = stats == null ? null : stats.trim();
    }
}