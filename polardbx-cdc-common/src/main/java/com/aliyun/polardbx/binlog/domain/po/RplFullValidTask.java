/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class RplFullValidTask {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.752505+08:00", comments="Source field: rpl_full_valid_task.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.752924+08:00", comments="Source field: rpl_full_valid_task.state_machine_id")
    private Long stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.752995+08:00", comments="Source field: rpl_full_valid_task.src_logical_db")
    private String srcLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753087+08:00", comments="Source field: rpl_full_valid_task.src_logical_table")
    private String srcLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753158+08:00", comments="Source field: rpl_full_valid_task.dst_logical_db")
    private String dstLogicalDb;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753229+08:00", comments="Source field: rpl_full_valid_task.dst_logical_table")
    private String dstLogicalTable;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753305+08:00", comments="Source field: rpl_full_valid_task.task_stage")
    private String taskStage;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753373+08:00", comments="Source field: rpl_full_valid_task.task_state")
    private String taskState;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753443+08:00", comments="Source field: rpl_full_valid_task.create_time")
    private Date createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.75352+08:00", comments="Source field: rpl_full_valid_task.update_time")
    private Date updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.751456+08:00", comments="Source Table: rpl_full_valid_task")
    public RplFullValidTask(Long id, Long stateMachineId, String srcLogicalDb, String srcLogicalTable, String dstLogicalDb, String dstLogicalTable, String taskStage, String taskState, Date createTime, Date updateTime) {
        this.id = id;
        this.stateMachineId = stateMachineId;
        this.srcLogicalDb = srcLogicalDb;
        this.srcLogicalTable = srcLogicalTable;
        this.dstLogicalDb = dstLogicalDb;
        this.dstLogicalTable = dstLogicalTable;
        this.taskStage = taskStage;
        this.taskState = taskState;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.752264+08:00", comments="Source Table: rpl_full_valid_task")
    public RplFullValidTask() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.752855+08:00", comments="Source field: rpl_full_valid_task.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.752896+08:00", comments="Source field: rpl_full_valid_task.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.752948+08:00", comments="Source field: rpl_full_valid_task.state_machine_id")
    public Long getStateMachineId() {
        return stateMachineId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.752972+08:00", comments="Source field: rpl_full_valid_task.state_machine_id")
    public void setStateMachineId(Long stateMachineId) {
        this.stateMachineId = stateMachineId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753018+08:00", comments="Source field: rpl_full_valid_task.src_logical_db")
    public String getSrcLogicalDb() {
        return srcLogicalDb;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753065+08:00", comments="Source field: rpl_full_valid_task.src_logical_db")
    public void setSrcLogicalDb(String srcLogicalDb) {
        this.srcLogicalDb = srcLogicalDb == null ? null : srcLogicalDb.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753111+08:00", comments="Source field: rpl_full_valid_task.src_logical_table")
    public String getSrcLogicalTable() {
        return srcLogicalTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753135+08:00", comments="Source field: rpl_full_valid_task.src_logical_table")
    public void setSrcLogicalTable(String srcLogicalTable) {
        this.srcLogicalTable = srcLogicalTable == null ? null : srcLogicalTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.75318+08:00", comments="Source field: rpl_full_valid_task.dst_logical_db")
    public String getDstLogicalDb() {
        return dstLogicalDb;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753208+08:00", comments="Source field: rpl_full_valid_task.dst_logical_db")
    public void setDstLogicalDb(String dstLogicalDb) {
        this.dstLogicalDb = dstLogicalDb == null ? null : dstLogicalDb.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753253+08:00", comments="Source field: rpl_full_valid_task.dst_logical_table")
    public String getDstLogicalTable() {
        return dstLogicalTable;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.75328+08:00", comments="Source field: rpl_full_valid_task.dst_logical_table")
    public void setDstLogicalTable(String dstLogicalTable) {
        this.dstLogicalTable = dstLogicalTable == null ? null : dstLogicalTable.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753326+08:00", comments="Source field: rpl_full_valid_task.task_stage")
    public String getTaskStage() {
        return taskStage;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753353+08:00", comments="Source field: rpl_full_valid_task.task_stage")
    public void setTaskStage(String taskStage) {
        this.taskStage = taskStage == null ? null : taskStage.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753396+08:00", comments="Source field: rpl_full_valid_task.task_state")
    public String getTaskState() {
        return taskState;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.75342+08:00", comments="Source field: rpl_full_valid_task.task_state")
    public void setTaskState(String taskState) {
        this.taskState = taskState == null ? null : taskState.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753471+08:00", comments="Source field: rpl_full_valid_task.create_time")
    public Date getCreateTime() {
        return createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.7535+08:00", comments="Source field: rpl_full_valid_task.create_time")
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753544+08:00", comments="Source field: rpl_full_valid_task.update_time")
    public Date getUpdateTime() {
        return updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.753566+08:00", comments="Source field: rpl_full_valid_task.update_time")
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}