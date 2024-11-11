/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class RplFullValidDiff {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765667+08:00", comments="Source field: rpl_full_valid_diff.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76571+08:00", comments="Source field: rpl_full_valid_diff.task_id")
    private Long taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765747+08:00", comments="Source field: rpl_full_valid_diff.src_logical_db")
    private String srcLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76579+08:00", comments="Source field: rpl_full_valid_diff.src_logical_table")
    private String srcLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765826+08:00", comments="Source field: rpl_full_valid_diff.dst_logical_db")
    private String dstLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765863+08:00", comments="Source field: rpl_full_valid_diff.dst_logical_table")
    private String dstLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765898+08:00", comments="Source field: rpl_full_valid_diff.src_key_name")
    private String srcKeyName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765932+08:00", comments="Source field: rpl_full_valid_diff.src_key_val")
    private String srcKeyVal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765968+08:00", comments="Source field: rpl_full_valid_diff.dst_key_name")
    private String dstKeyName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766004+08:00", comments="Source field: rpl_full_valid_diff.dst_key_val")
    private String dstKeyVal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766037+08:00", comments="Source field: rpl_full_valid_diff.error_type")
    private String errorType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766072+08:00", comments="Source field: rpl_full_valid_diff.status")
    private String status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766124+08:00", comments="Source field: rpl_full_valid_diff.create_time")
    private Date createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766162+08:00", comments="Source field: rpl_full_valid_diff.update_time")
    private Date updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765616+08:00", comments="Source Table: rpl_full_valid_diff")
    public RplFullValidDiff(Long id, Long taskId, String srcLogicalDb, String srcLogicalTable, String dstLogicalDb, String dstLogicalTable, String srcKeyName, String srcKeyVal, String dstKeyName, String dstKeyVal, String errorType, String status, Date createTime, Date updateTime) {
        this.id = id;
        this.taskId = taskId;
        this.srcLogicalDb = srcLogicalDb;
        this.srcLogicalTable = srcLogicalTable;
        this.dstLogicalDb = dstLogicalDb;
        this.dstLogicalTable = dstLogicalTable;
        this.srcKeyName = srcKeyName;
        this.srcKeyVal = srcKeyVal;
        this.dstKeyName = dstKeyName;
        this.dstKeyVal = dstKeyVal;
        this.errorType = errorType;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765647+08:00", comments="Source Table: rpl_full_valid_diff")
    public RplFullValidDiff() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765683+08:00", comments="Source field: rpl_full_valid_diff.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765698+08:00", comments="Source field: rpl_full_valid_diff.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765723+08:00", comments="Source field: rpl_full_valid_diff.task_id")
    public Long getTaskId() {
        return taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765736+08:00", comments="Source field: rpl_full_valid_diff.task_id")
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765764+08:00", comments="Source field: rpl_full_valid_diff.src_logical_db")
    public String getSrcLogicalDb() {
        return srcLogicalDb;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765778+08:00", comments="Source field: rpl_full_valid_diff.src_logical_db")
    public void setSrcLogicalDb(String srcLogicalDb) {
        this.srcLogicalDb = srcLogicalDb == null ? null : srcLogicalDb.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765802+08:00", comments="Source field: rpl_full_valid_diff.src_logical_table")
    public String getSrcLogicalTable() {
        return srcLogicalTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765816+08:00", comments="Source field: rpl_full_valid_diff.src_logical_table")
    public void setSrcLogicalTable(String srcLogicalTable) {
        this.srcLogicalTable = srcLogicalTable == null ? null : srcLogicalTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765838+08:00", comments="Source field: rpl_full_valid_diff.dst_logical_db")
    public String getDstLogicalDb() {
        return dstLogicalDb;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765852+08:00", comments="Source field: rpl_full_valid_diff.dst_logical_db")
    public void setDstLogicalDb(String dstLogicalDb) {
        this.dstLogicalDb = dstLogicalDb == null ? null : dstLogicalDb.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765874+08:00", comments="Source field: rpl_full_valid_diff.dst_logical_table")
    public String getDstLogicalTable() {
        return dstLogicalTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765887+08:00", comments="Source field: rpl_full_valid_diff.dst_logical_table")
    public void setDstLogicalTable(String dstLogicalTable) {
        this.dstLogicalTable = dstLogicalTable == null ? null : dstLogicalTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765909+08:00", comments="Source field: rpl_full_valid_diff.src_key_name")
    public String getSrcKeyName() {
        return srcKeyName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765922+08:00", comments="Source field: rpl_full_valid_diff.src_key_name")
    public void setSrcKeyName(String srcKeyName) {
        this.srcKeyName = srcKeyName == null ? null : srcKeyName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765945+08:00", comments="Source field: rpl_full_valid_diff.src_key_val")
    public String getSrcKeyVal() {
        return srcKeyVal;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765957+08:00", comments="Source field: rpl_full_valid_diff.src_key_val")
    public void setSrcKeyVal(String srcKeyVal) {
        this.srcKeyVal = srcKeyVal == null ? null : srcKeyVal.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76598+08:00", comments="Source field: rpl_full_valid_diff.dst_key_name")
    public String getDstKeyName() {
        return dstKeyName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.765993+08:00", comments="Source field: rpl_full_valid_diff.dst_key_name")
    public void setDstKeyName(String dstKeyName) {
        this.dstKeyName = dstKeyName == null ? null : dstKeyName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766015+08:00", comments="Source field: rpl_full_valid_diff.dst_key_val")
    public String getDstKeyVal() {
        return dstKeyVal;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766027+08:00", comments="Source field: rpl_full_valid_diff.dst_key_val")
    public void setDstKeyVal(String dstKeyVal) {
        this.dstKeyVal = dstKeyVal == null ? null : dstKeyVal.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766048+08:00", comments="Source field: rpl_full_valid_diff.error_type")
    public String getErrorType() {
        return errorType;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76606+08:00", comments="Source field: rpl_full_valid_diff.error_type")
    public void setErrorType(String errorType) {
        this.errorType = errorType == null ? null : errorType.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766085+08:00", comments="Source field: rpl_full_valid_diff.status")
    public String getStatus() {
        return status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766111+08:00", comments="Source field: rpl_full_valid_diff.status")
    public void setStatus(String status) {
        this.status = status == null ? null : status.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766137+08:00", comments="Source field: rpl_full_valid_diff.create_time")
    public Date getCreateTime() {
        return createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766151+08:00", comments="Source field: rpl_full_valid_diff.create_time")
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766174+08:00", comments="Source field: rpl_full_valid_diff.update_time")
    public Date getUpdateTime() {
        return updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.766188+08:00", comments="Source field: rpl_full_valid_diff.update_time")
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}