/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class BinlogTaskInfo {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.192+08:00",
        comments = "Source field: binlog_task_info.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.192+08:00",
        comments = "Source field: binlog_task_info.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.192+08:00",
        comments = "Source field: binlog_task_info.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.cluster_id")
    private String clusterId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.task_name")
    private String taskName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.ip")
    private String ip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.port")
    private Integer port;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.role")
    private String role;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.status")
    private Integer status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.gmt_heartbeat")
    private Date gmtHeartbeat;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.container_id")
    private String containerId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.version")
    private Long version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.polarx_inst_id")
    private String polarxInstId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.sub_version")
    private Long subVersion;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.enable_light_rebalance")
    private Boolean enableLightRebalance;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.sources_list")
    private String sourcesList;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.ext")
    private String ext;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.191+08:00",
        comments = "Source Table: binlog_task_info")
    public BinlogTaskInfo(Long id, Date gmtCreated, Date gmtModified, String clusterId, String taskName, String ip,
                          Integer port, String role, Integer status, Date gmtHeartbeat, String containerId,
                          Long version, String polarxInstId, Long subVersion, Boolean enableLightRebalance,
                          String sourcesList, String ext) {
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
        this.polarxInstId = polarxInstId;
        this.subVersion = subVersion;
        this.enableLightRebalance = enableLightRebalance;
        this.sourcesList = sourcesList;
        this.ext = ext;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.192+08:00",
        comments = "Source Table: binlog_task_info")
    public BinlogTaskInfo() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.192+08:00",
        comments = "Source field: binlog_task_info.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.192+08:00",
        comments = "Source field: binlog_task_info.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.192+08:00",
        comments = "Source field: binlog_task_info.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.192+08:00",
        comments = "Source field: binlog_task_info.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.task_name")
    public String getTaskName() {
        return taskName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.task_name")
    public void setTaskName(String taskName) {
        this.taskName = taskName == null ? null : taskName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.ip")
    public String getIp() {
        return ip;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.ip")
    public void setIp(String ip) {
        this.ip = ip == null ? null : ip.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.port")
    public Integer getPort() {
        return port;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.port")
    public void setPort(Integer port) {
        this.port = port;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.role")
    public String getRole() {
        return role;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.role")
    public void setRole(String role) {
        this.role = role == null ? null : role.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.gmt_heartbeat")
    public Date getGmtHeartbeat() {
        return gmtHeartbeat;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.193+08:00",
        comments = "Source field: binlog_task_info.gmt_heartbeat")
    public void setGmtHeartbeat(Date gmtHeartbeat) {
        this.gmtHeartbeat = gmtHeartbeat;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.container_id")
    public String getContainerId() {
        return containerId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.container_id")
    public void setContainerId(String containerId) {
        this.containerId = containerId == null ? null : containerId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.version")
    public Long getVersion() {
        return version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.version")
    public void setVersion(Long version) {
        this.version = version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.polarx_inst_id")
    public String getPolarxInstId() {
        return polarxInstId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.polarx_inst_id")
    public void setPolarxInstId(String polarxInstId) {
        this.polarxInstId = polarxInstId == null ? null : polarxInstId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.sub_version")
    public Long getSubVersion() {
        return subVersion;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.sub_version")
    public void setSubVersion(Long subVersion) {
        this.subVersion = subVersion;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.enable_light_rebalance")
    public Boolean getEnableLightRebalance() {
        return enableLightRebalance;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.enable_light_rebalance")
    public void setEnableLightRebalance(Boolean enableLightRebalance) {
        this.enableLightRebalance = enableLightRebalance;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.sources_list")
    public String getSourcesList() {
        return sourcesList;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.sources_list")
    public void setSourcesList(String sourcesList) {
        this.sourcesList = sourcesList == null ? null : sourcesList.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.ext")
    public String getExt() {
        return ext;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2025-12-01T17:53:05.194+08:00",
        comments = "Source field: binlog_task_info.ext")
    public void setExt(String ext) {
        this.ext = ext == null ? null : ext.trim();
    }
}