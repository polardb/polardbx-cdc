/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class RplFullValidSubTask {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763687+08:00", comments="Source field: rpl_full_valid_sub_task.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763734+08:00", comments="Source field: rpl_full_valid_sub_task.state_machine_id")
    private Long stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763774+08:00", comments="Source field: rpl_full_valid_sub_task.task_id")
    private Long taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76381+08:00", comments="Source field: rpl_full_valid_sub_task.task_stage")
    private String taskStage;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763856+08:00", comments="Source field: rpl_full_valid_sub_task.task_state")
    private String taskState;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763895+08:00", comments="Source field: rpl_full_valid_sub_task.task_type")
    private String taskType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763938+08:00", comments="Source field: rpl_full_valid_sub_task.create_time")
    private Date createTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763978+08:00", comments="Source field: rpl_full_valid_sub_task.update_time")
    private Date updateTime;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764014+08:00", comments="Source field: rpl_full_valid_sub_task.task_config")
    private String taskConfig;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764053+08:00", comments="Source field: rpl_full_valid_sub_task.summary")
    private String summary;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763622+08:00", comments="Source Table: rpl_full_valid_sub_task")
    public RplFullValidSubTask(Long id, Long stateMachineId, Long taskId, String taskStage, String taskState, String taskType, Date createTime, Date updateTime, String taskConfig, String summary) {
        this.id = id;
        this.stateMachineId = stateMachineId;
        this.taskId = taskId;
        this.taskStage = taskStage;
        this.taskState = taskState;
        this.taskType = taskType;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.taskConfig = taskConfig;
        this.summary = summary;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763666+08:00", comments="Source Table: rpl_full_valid_sub_task")
    public RplFullValidSubTask() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763704+08:00", comments="Source field: rpl_full_valid_sub_task.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763719+08:00", comments="Source field: rpl_full_valid_sub_task.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763747+08:00", comments="Source field: rpl_full_valid_sub_task.state_machine_id")
    public Long getStateMachineId() {
        return stateMachineId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763761+08:00", comments="Source field: rpl_full_valid_sub_task.state_machine_id")
    public void setStateMachineId(Long stateMachineId) {
        this.stateMachineId = stateMachineId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763786+08:00", comments="Source field: rpl_full_valid_sub_task.task_id")
    public Long getTaskId() {
        return taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763799+08:00", comments="Source field: rpl_full_valid_sub_task.task_id")
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763823+08:00", comments="Source field: rpl_full_valid_sub_task.task_stage")
    public String getTaskStage() {
        return taskStage;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763843+08:00", comments="Source field: rpl_full_valid_sub_task.task_stage")
    public void setTaskStage(String taskStage) {
        this.taskStage = taskStage == null ? null : taskStage.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763869+08:00", comments="Source field: rpl_full_valid_sub_task.task_state")
    public String getTaskState() {
        return taskState;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763884+08:00", comments="Source field: rpl_full_valid_sub_task.task_state")
    public void setTaskState(String taskState) {
        this.taskState = taskState == null ? null : taskState.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763909+08:00", comments="Source field: rpl_full_valid_sub_task.task_type")
    public String getTaskType() {
        return taskType;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763925+08:00", comments="Source field: rpl_full_valid_sub_task.task_type")
    public void setTaskType(String taskType) {
        this.taskType = taskType == null ? null : taskType.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763951+08:00", comments="Source field: rpl_full_valid_sub_task.create_time")
    public Date getCreateTime() {
        return createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.763966+08:00", comments="Source field: rpl_full_valid_sub_task.create_time")
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76399+08:00", comments="Source field: rpl_full_valid_sub_task.update_time")
    public Date getUpdateTime() {
        return updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764003+08:00", comments="Source field: rpl_full_valid_sub_task.update_time")
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.76403+08:00", comments="Source field: rpl_full_valid_sub_task.task_config")
    public String getTaskConfig() {
        return taskConfig;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764042+08:00", comments="Source field: rpl_full_valid_sub_task.task_config")
    public void setTaskConfig(String taskConfig) {
        this.taskConfig = taskConfig == null ? null : taskConfig.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764065+08:00", comments="Source field: rpl_full_valid_sub_task.summary")
    public String getSummary() {
        return summary;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-11-07T17:06:53.764078+08:00", comments="Source field: rpl_full_valid_sub_task.summary")
    public void setSummary(String summary) {
        this.summary = summary == null ? null : summary.trim();
    }
}