/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class RplDbFullPosition {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.state_machine_id")
    private Long stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.service_id")
    private Long serviceId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.task_id")
    private Long taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.full_table_name")
    private String fullTableName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.total_count")
    private Long totalCount;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.finished_count")
    private Long finishedCount;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.finished")
    private Integer finished;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.position")
    private String position;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.end_position")
    private String endPosition;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source Table: rpl_db_full_position")
    public RplDbFullPosition(Long id, Date gmtCreated, Date gmtModified, Long stateMachineId, Long serviceId, Long taskId, String fullTableName, Long totalCount, Long finishedCount, Integer finished, String position, String endPosition) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.stateMachineId = stateMachineId;
        this.serviceId = serviceId;
        this.taskId = taskId;
        this.fullTableName = fullTableName;
        this.totalCount = totalCount;
        this.finishedCount = finishedCount;
        this.finished = finished;
        this.position = position;
        this.endPosition = endPosition;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source Table: rpl_db_full_position")
    public RplDbFullPosition() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.state_machine_id")
    public Long getStateMachineId() {
        return stateMachineId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.state_machine_id")
    public void setStateMachineId(Long stateMachineId) {
        this.stateMachineId = stateMachineId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.service_id")
    public Long getServiceId() {
        return serviceId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.service_id")
    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.task_id")
    public Long getTaskId() {
        return taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.task_id")
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.full_table_name")
    public String getFullTableName() {
        return fullTableName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.full_table_name")
    public void setFullTableName(String fullTableName) {
        this.fullTableName = fullTableName == null ? null : fullTableName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.total_count")
    public Long getTotalCount() {
        return totalCount;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.937+08:00", comments="Source field: rpl_db_full_position.total_count")
    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.finished_count")
    public Long getFinishedCount() {
        return finishedCount;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.finished_count")
    public void setFinishedCount(Long finishedCount) {
        this.finishedCount = finishedCount;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.finished")
    public Integer getFinished() {
        return finished;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.finished")
    public void setFinished(Integer finished) {
        this.finished = finished;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.position")
    public String getPosition() {
        return position;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.position")
    public void setPosition(String position) {
        this.position = position == null ? null : position.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.end_position")
    public String getEndPosition() {
        return endPosition;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.938+08:00", comments="Source field: rpl_db_full_position.end_position")
    public void setEndPosition(String endPosition) {
        this.endPosition = endPosition == null ? null : endPosition.trim();
    }
}