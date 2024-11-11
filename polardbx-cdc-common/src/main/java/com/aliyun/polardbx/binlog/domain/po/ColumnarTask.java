/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class ColumnarTask {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.cluster_id")
    private String clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.task_name")
    private String taskName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.ip")
    private String ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.port")
    private Integer port;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.role")
    private String role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.status")
    private Integer status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.gmt_heartbeat")
    private Date gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.container_id")
    private String containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.version")
    private Long version;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source Table: columnar_task")
    public ColumnarTask(Long id, Date gmtCreated, Date gmtModified, String clusterId, String taskName, String ip, Integer port, String role, Integer status, Date gmtHeartbeat, String containerId, Long version) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.clusterId = clusterId;
        this.taskName = taskName;
        this.ip = ip;
        this.port = port;
        this.role = role;
        this.status = status;
        this.gmtHeartbeat = gmtHeartbeat;
        this.containerId = containerId;
        this.version = version;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source Table: columnar_task")
    public ColumnarTask() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.task_name")
    public String getTaskName() {
        return taskName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.task_name")
    public void setTaskName(String taskName) {
        this.taskName = taskName == null ? null : taskName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.ip")
    public String getIp() {
        return ip;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.ip")
    public void setIp(String ip) {
        this.ip = ip == null ? null : ip.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.port")
    public Integer getPort() {
        return port;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.port")
    public void setPort(Integer port) {
        this.port = port;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.role")
    public String getRole() {
        return role;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.role")
    public void setRole(String role) {
        this.role = role == null ? null : role.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.gmt_heartbeat")
    public Date getGmtHeartbeat() {
        return gmtHeartbeat;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.gmt_heartbeat")
    public void setGmtHeartbeat(Date gmtHeartbeat) {
        this.gmtHeartbeat = gmtHeartbeat;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.container_id")
    public String getContainerId() {
        return containerId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.container_id")
    public void setContainerId(String containerId) {
        this.containerId = containerId == null ? null : containerId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.version")
    public Long getVersion() {
        return version;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.241+08:00", comments="Source field: columnar_task.version")
    public void setVersion(Long version) {
        this.version = version;
    }
}