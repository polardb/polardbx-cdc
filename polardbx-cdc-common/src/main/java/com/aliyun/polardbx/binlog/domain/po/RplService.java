/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class RplService {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.925+08:00", comments="Source field: rpl_service.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.state_machine_id")
    private Long stateMachineId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.service_type")
    private String serviceType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.state_list")
    private String stateList;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.channel")
    private String channel;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.status")
    private String status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.921+08:00", comments="Source Table: rpl_service")
    public RplService(Long id, Date gmtCreated, Date gmtModified, Long stateMachineId, String serviceType, String stateList, String channel, String status) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.stateMachineId = stateMachineId;
        this.serviceType = serviceType;
        this.stateList = stateList;
        this.channel = channel;
        this.status = status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.925+08:00", comments="Source Table: rpl_service")
    public RplService() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.926+08:00", comments="Source field: rpl_service.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.state_machine_id")
    public Long getStateMachineId() {
        return stateMachineId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.state_machine_id")
    public void setStateMachineId(Long stateMachineId) {
        this.stateMachineId = stateMachineId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.service_type")
    public String getServiceType() {
        return serviceType;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.service_type")
    public void setServiceType(String serviceType) {
        this.serviceType = serviceType == null ? null : serviceType.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.state_list")
    public String getStateList() {
        return stateList;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.state_list")
    public void setStateList(String stateList) {
        this.stateList = stateList == null ? null : stateList.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.channel")
    public String getChannel() {
        return channel;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.channel")
    public void setChannel(String channel) {
        this.channel = channel == null ? null : channel.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.status")
    public String getStatus() {
        return status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-15T14:24:49.927+08:00", comments="Source field: rpl_service.status")
    public void setStatus(String status) {
        this.status = status == null ? null : status.trim();
    }
}