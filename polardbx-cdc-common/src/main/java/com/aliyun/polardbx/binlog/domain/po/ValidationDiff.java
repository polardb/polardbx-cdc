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
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class ValidationDiff {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.state_machine_id")
    private String stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.service_id")
    private String serviceId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.task_id")
    private String taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.validation_task_id")
    private String validationTaskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.type")
    private Integer type;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.state")
    private Integer state;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.src_logical_db")
    private String srcLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.src_logical_table")
    private String srcLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.src_logical_key_col")
    private String srcLogicalKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.src_phy_db")
    private String srcPhyDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_phy_table")
    private String srcPhyTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_phy_key_col")
    private String srcPhyKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_key_col_val")
    private String srcKeyColVal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_logical_db")
    private String dstLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_logical_table")
    private String dstLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_logical_key_col")
    private String dstLogicalKeyCol;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_key_col_val")
    private String dstKeyColVal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.deleted")
    private Boolean deleted;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.create_time")
    private Date createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.update_time")
    private Date updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.diff")
    private String diff;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source Table: validation_diff")
    public ValidationDiff(Long id, String stateMachineId, String serviceId, String taskId, String validationTaskId, Integer type, Integer state, String srcLogicalDb, String srcLogicalTable, String srcLogicalKeyCol, String srcPhyDb, String srcPhyTable, String srcPhyKeyCol, String srcKeyColVal, String dstLogicalDb, String dstLogicalTable, String dstLogicalKeyCol, String dstKeyColVal, Boolean deleted, Date createTime, Date updateTime, String diff) {
        this.id = id;
        this.stateMachineId = stateMachineId;
        this.serviceId = serviceId;
        this.taskId = taskId;
        this.validationTaskId = validationTaskId;
        this.type = type;
        this.state = state;
        this.srcLogicalDb = srcLogicalDb;
        this.srcLogicalTable = srcLogicalTable;
        this.srcLogicalKeyCol = srcLogicalKeyCol;
        this.srcPhyDb = srcPhyDb;
        this.srcPhyTable = srcPhyTable;
        this.srcPhyKeyCol = srcPhyKeyCol;
        this.srcKeyColVal = srcKeyColVal;
        this.dstLogicalDb = dstLogicalDb;
        this.dstLogicalTable = dstLogicalTable;
        this.dstLogicalKeyCol = dstLogicalKeyCol;
        this.dstKeyColVal = dstKeyColVal;
        this.deleted = deleted;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.diff = diff;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source Table: validation_diff")
    public ValidationDiff() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.state_machine_id")
    public String getStateMachineId() {
        return stateMachineId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.state_machine_id")
    public void setStateMachineId(String stateMachineId) {
        this.stateMachineId = stateMachineId == null ? null : stateMachineId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.service_id")
    public String getServiceId() {
        return serviceId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.service_id")
    public void setServiceId(String serviceId) {
        this.serviceId = serviceId == null ? null : serviceId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.task_id")
    public String getTaskId() {
        return taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.task_id")
    public void setTaskId(String taskId) {
        this.taskId = taskId == null ? null : taskId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.validation_task_id")
    public String getValidationTaskId() {
        return validationTaskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.validation_task_id")
    public void setValidationTaskId(String validationTaskId) {
        this.validationTaskId = validationTaskId == null ? null : validationTaskId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.type")
    public Integer getType() {
        return type;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.type")
    public void setType(Integer type) {
        this.type = type;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.state")
    public Integer getState() {
        return state;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.state")
    public void setState(Integer state) {
        this.state = state;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.src_logical_db")
    public String getSrcLogicalDb() {
        return srcLogicalDb;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.src_logical_db")
    public void setSrcLogicalDb(String srcLogicalDb) {
        this.srcLogicalDb = srcLogicalDb == null ? null : srcLogicalDb.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.src_logical_table")
    public String getSrcLogicalTable() {
        return srcLogicalTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.src_logical_table")
    public void setSrcLogicalTable(String srcLogicalTable) {
        this.srcLogicalTable = srcLogicalTable == null ? null : srcLogicalTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.src_logical_key_col")
    public String getSrcLogicalKeyCol() {
        return srcLogicalKeyCol;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.945+08:00", comments="Source field: validation_diff.src_logical_key_col")
    public void setSrcLogicalKeyCol(String srcLogicalKeyCol) {
        this.srcLogicalKeyCol = srcLogicalKeyCol == null ? null : srcLogicalKeyCol.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_phy_db")
    public String getSrcPhyDb() {
        return srcPhyDb;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_phy_db")
    public void setSrcPhyDb(String srcPhyDb) {
        this.srcPhyDb = srcPhyDb == null ? null : srcPhyDb.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_phy_table")
    public String getSrcPhyTable() {
        return srcPhyTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_phy_table")
    public void setSrcPhyTable(String srcPhyTable) {
        this.srcPhyTable = srcPhyTable == null ? null : srcPhyTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_phy_key_col")
    public String getSrcPhyKeyCol() {
        return srcPhyKeyCol;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_phy_key_col")
    public void setSrcPhyKeyCol(String srcPhyKeyCol) {
        this.srcPhyKeyCol = srcPhyKeyCol == null ? null : srcPhyKeyCol.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_key_col_val")
    public String getSrcKeyColVal() {
        return srcKeyColVal;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.src_key_col_val")
    public void setSrcKeyColVal(String srcKeyColVal) {
        this.srcKeyColVal = srcKeyColVal == null ? null : srcKeyColVal.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_logical_db")
    public String getDstLogicalDb() {
        return dstLogicalDb;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_logical_db")
    public void setDstLogicalDb(String dstLogicalDb) {
        this.dstLogicalDb = dstLogicalDb == null ? null : dstLogicalDb.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_logical_table")
    public String getDstLogicalTable() {
        return dstLogicalTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_logical_table")
    public void setDstLogicalTable(String dstLogicalTable) {
        this.dstLogicalTable = dstLogicalTable == null ? null : dstLogicalTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_logical_key_col")
    public String getDstLogicalKeyCol() {
        return dstLogicalKeyCol;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_logical_key_col")
    public void setDstLogicalKeyCol(String dstLogicalKeyCol) {
        this.dstLogicalKeyCol = dstLogicalKeyCol == null ? null : dstLogicalKeyCol.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_key_col_val")
    public String getDstKeyColVal() {
        return dstKeyColVal;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.dst_key_col_val")
    public void setDstKeyColVal(String dstKeyColVal) {
        this.dstKeyColVal = dstKeyColVal == null ? null : dstKeyColVal.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.deleted")
    public Boolean getDeleted() {
        return deleted;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.deleted")
    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.create_time")
    public Date getCreateTime() {
        return createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.create_time")
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.update_time")
    public Date getUpdateTime() {
        return updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.update_time")
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.diff")
    public String getDiff() {
        return diff;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.946+08:00", comments="Source field: validation_diff.diff")
    public void setDiff(String diff) {
        this.diff = diff == null ? null : diff.trim();
    }
}