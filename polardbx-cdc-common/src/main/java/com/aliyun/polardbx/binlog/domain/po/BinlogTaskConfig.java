/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import lombok.Builder;

import javax.annotation.Generated;
import java.util.Date;

@Builder
public class BinlogTaskConfig {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259234+08:00",
        comments = "Source field: binlog_task_config.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259584+08:00",
        comments = "Source field: binlog_task_config.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259659+08:00",
        comments = "Source field: binlog_task_config.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259764+08:00",
        comments = "Source field: binlog_task_config.cluster_id")
    private String clusterId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259863+08:00",
        comments = "Source field: binlog_task_config.container_id")
    private String containerId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259937+08:00",
        comments = "Source field: binlog_task_config.task_name")
    private String taskName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260004+08:00",
        comments = "Source field: binlog_task_config.vcpu")
    private Integer vcpu;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260068+08:00",
        comments = "Source field: binlog_task_config.mem")
    private Integer mem;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260157+08:00",
        comments = "Source field: binlog_task_config.ip")
    private String ip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.26024+08:00",
        comments = "Source field: binlog_task_config.port")
    private Integer port;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.26031+08:00",
        comments = "Source field: binlog_task_config.role")
    private String role;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260376+08:00",
        comments = "Source field: binlog_task_config.status")
    private Integer status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260437+08:00",
        comments = "Source field: binlog_task_config.version")
    private Long version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260498+08:00",
        comments = "Source field: binlog_task_config.sub_version")
    private Long subVersion;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260559+08:00",
        comments = "Source field: binlog_task_config.config")
    private String config;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.258369+08:00",
        comments = "Source Table: binlog_task_config")
    public BinlogTaskConfig(Long id, Date gmtCreated, Date gmtModified, String clusterId, String containerId,
                            String taskName, Integer vcpu, Integer mem, String ip, Integer port, String role,
                            Integer status, Long version, Long subVersion, String config) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.clusterId = clusterId;
        this.containerId = containerId;
        this.taskName = taskName;
        this.vcpu = vcpu;
        this.mem = mem;
        this.ip = ip;
        this.port = port;
        this.role = role;
        this.status = status;
        this.version = version;
        this.subVersion = subVersion;
        this.config = config;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259038+08:00",
        comments = "Source Table: binlog_task_config")
    public BinlogTaskConfig() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259517+08:00",
        comments = "Source field: binlog_task_config.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259556+08:00",
        comments = "Source field: binlog_task_config.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259614+08:00",
        comments = "Source field: binlog_task_config.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259638+08:00",
        comments = "Source field: binlog_task_config.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259707+08:00",
        comments = "Source field: binlog_task_config.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259738+08:00",
        comments = "Source field: binlog_task_config.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259791+08:00",
        comments = "Source field: binlog_task_config.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259841+08:00",
        comments = "Source field: binlog_task_config.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259891+08:00",
        comments = "Source field: binlog_task_config.container_id")
    public String getContainerId() {
        return containerId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259917+08:00",
        comments = "Source field: binlog_task_config.container_id")
    public void setContainerId(String containerId) {
        this.containerId = containerId == null ? null : containerId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259958+08:00",
        comments = "Source field: binlog_task_config.task_name")
    public String getTaskName() {
        return taskName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.259984+08:00",
        comments = "Source field: binlog_task_config.task_name")
    public void setTaskName(String taskName) {
        this.taskName = taskName == null ? null : taskName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260024+08:00",
        comments = "Source field: binlog_task_config.vcpu")
    public Integer getVcpu() {
        return vcpu;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260048+08:00",
        comments = "Source field: binlog_task_config.vcpu")
    public void setVcpu(Integer vcpu) {
        this.vcpu = vcpu;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260088+08:00",
        comments = "Source field: binlog_task_config.mem")
    public Integer getMem() {
        return mem;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.26011+08:00",
        comments = "Source field: binlog_task_config.mem")
    public void setMem(Integer mem) {
        this.mem = mem;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.26019+08:00",
        comments = "Source field: binlog_task_config.ip")
    public String getIp() {
        return ip;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260216+08:00",
        comments = "Source field: binlog_task_config.ip")
    public void setIp(String ip) {
        this.ip = ip == null ? null : ip.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260261+08:00",
        comments = "Source field: binlog_task_config.port")
    public Integer getPort() {
        return port;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260282+08:00",
        comments = "Source field: binlog_task_config.port")
    public void setPort(Integer port) {
        this.port = port;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260333+08:00",
        comments = "Source field: binlog_task_config.role")
    public String getRole() {
        return role;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260355+08:00",
        comments = "Source field: binlog_task_config.role")
    public void setRole(String role) {
        this.role = role == null ? null : role.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260396+08:00",
        comments = "Source field: binlog_task_config.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260419+08:00",
        comments = "Source field: binlog_task_config.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260458+08:00",
        comments = "Source field: binlog_task_config.version")
    public Long getVersion() {
        return version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260478+08:00",
        comments = "Source field: binlog_task_config.version")
    public void setVersion(Long version) {
        this.version = version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260518+08:00",
        comments = "Source field: binlog_task_config.sub_version")
    public Long getSubVersion() {
        return subVersion;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260542+08:00",
        comments = "Source field: binlog_task_config.sub_version")
    public void setSubVersion(Long subVersion) {
        this.subVersion = subVersion;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260579+08:00",
        comments = "Source field: binlog_task_config.config")
    public String getConfig() {
        return config;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-07-04T11:08:01.260598+08:00",
        comments = "Source field: binlog_task_config.config")
    public void setConfig(String config) {
        this.config = config == null ? null : config.trim();
    }
}