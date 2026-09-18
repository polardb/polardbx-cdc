/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class NodeInfo {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361352+08:00", comments="Source field: binlog_node_info.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361663+08:00", comments="Source field: binlog_node_info.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361739+08:00", comments="Source field: binlog_node_info.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361805+08:00", comments="Source field: binlog_node_info.cluster_id")
    private String clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361892+08:00", comments="Source field: binlog_node_info.container_id")
    private String containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361959+08:00", comments="Source field: binlog_node_info.ip")
    private String ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362029+08:00", comments="Source field: binlog_node_info.daemon_port")
    private Integer daemonPort;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362093+08:00", comments="Source field: binlog_node_info.available_ports")
    private String availablePorts;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362157+08:00", comments="Source field: binlog_node_info.status")
    private Integer status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.36222+08:00", comments="Source field: binlog_node_info.core")
    private Long core;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362285+08:00", comments="Source field: binlog_node_info.mem")
    private Long mem;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362347+08:00", comments="Source field: binlog_node_info.gmt_heartbeat")
    private Date gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362419+08:00", comments="Source field: binlog_node_info.latest_cursor")
    private String latestCursor;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362486+08:00", comments="Source field: binlog_node_info.role")
    private String role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362549+08:00", comments="Source field: binlog_node_info.cluster_type")
    private String clusterType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362609+08:00", comments="Source field: binlog_node_info.group_name")
    private String groupName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362666+08:00", comments="Source field: binlog_node_info.polarx_inst_id")
    private String polarxInstId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362724+08:00", comments="Source field: binlog_node_info.cluster_role")
    private String clusterRole;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362784+08:00", comments="Source field: binlog_node_info.last_tso_heartbeat")
    private Date lastTsoHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362844+08:00", comments="Source field: binlog_node_info.enable_light_rebalance")
    private Boolean enableLightRebalance;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.360653+08:00", comments="Source Table: binlog_node_info")
    public NodeInfo(Long id, Date gmtCreated, Date gmtModified, String clusterId, String containerId, String ip, Integer daemonPort, String availablePorts, Integer status, Long core, Long mem, Date gmtHeartbeat, String latestCursor, String role, String clusterType, String groupName, String polarxInstId, String clusterRole, Date lastTsoHeartbeat, Boolean enableLightRebalance) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.clusterId = clusterId;
        this.containerId = containerId;
        this.ip = ip;
        this.daemonPort = daemonPort;
        this.availablePorts = availablePorts;
        this.status = status;
        this.core = core;
        this.mem = mem;
        this.gmtHeartbeat = gmtHeartbeat;
        this.latestCursor = latestCursor;
        this.role = role;
        this.clusterType = clusterType;
        this.groupName = groupName;
        this.polarxInstId = polarxInstId;
        this.clusterRole = clusterRole;
        this.lastTsoHeartbeat = lastTsoHeartbeat;
        this.enableLightRebalance = enableLightRebalance;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361193+08:00", comments="Source Table: binlog_node_info")
    public NodeInfo() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361599+08:00", comments="Source field: binlog_node_info.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361637+08:00", comments="Source field: binlog_node_info.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361696+08:00", comments="Source field: binlog_node_info.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361719+08:00", comments="Source field: binlog_node_info.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361761+08:00", comments="Source field: binlog_node_info.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361785+08:00", comments="Source field: binlog_node_info.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361833+08:00", comments="Source field: binlog_node_info.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361869+08:00", comments="Source field: binlog_node_info.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361915+08:00", comments="Source field: binlog_node_info.container_id")
    public String getContainerId() {
        return containerId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.36194+08:00", comments="Source field: binlog_node_info.container_id")
    public void setContainerId(String containerId) {
        this.containerId = containerId == null ? null : containerId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.361981+08:00", comments="Source field: binlog_node_info.ip")
    public String getIp() {
        return ip;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362005+08:00", comments="Source field: binlog_node_info.ip")
    public void setIp(String ip) {
        this.ip = ip == null ? null : ip.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362049+08:00", comments="Source field: binlog_node_info.daemon_port")
    public Integer getDaemonPort() {
        return daemonPort;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362074+08:00", comments="Source field: binlog_node_info.daemon_port")
    public void setDaemonPort(Integer daemonPort) {
        this.daemonPort = daemonPort;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362115+08:00", comments="Source field: binlog_node_info.available_ports")
    public String getAvailablePorts() {
        return availablePorts;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362136+08:00", comments="Source field: binlog_node_info.available_ports")
    public void setAvailablePorts(String availablePorts) {
        this.availablePorts = availablePorts == null ? null : availablePorts.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362179+08:00", comments="Source field: binlog_node_info.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362201+08:00", comments="Source field: binlog_node_info.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362241+08:00", comments="Source field: binlog_node_info.core")
    public Long getCore() {
        return core;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362261+08:00", comments="Source field: binlog_node_info.core")
    public void setCore(Long core) {
        this.core = core;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362305+08:00", comments="Source field: binlog_node_info.mem")
    public Long getMem() {
        return mem;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362327+08:00", comments="Source field: binlog_node_info.mem")
    public void setMem(Long mem) {
        this.mem = mem;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.36237+08:00", comments="Source field: binlog_node_info.gmt_heartbeat")
    public Date getGmtHeartbeat() {
        return gmtHeartbeat;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362398+08:00", comments="Source field: binlog_node_info.gmt_heartbeat")
    public void setGmtHeartbeat(Date gmtHeartbeat) {
        this.gmtHeartbeat = gmtHeartbeat;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362439+08:00", comments="Source field: binlog_node_info.latest_cursor")
    public String getLatestCursor() {
        return latestCursor;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362461+08:00", comments="Source field: binlog_node_info.latest_cursor")
    public void setLatestCursor(String latestCursor) {
        this.latestCursor = latestCursor == null ? null : latestCursor.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362507+08:00", comments="Source field: binlog_node_info.role")
    public String getRole() {
        return role;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362531+08:00", comments="Source field: binlog_node_info.role")
    public void setRole(String role) {
        this.role = role == null ? null : role.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362568+08:00", comments="Source field: binlog_node_info.cluster_type")
    public String getClusterType() {
        return clusterType;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362589+08:00", comments="Source field: binlog_node_info.cluster_type")
    public void setClusterType(String clusterType) {
        this.clusterType = clusterType == null ? null : clusterType.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362628+08:00", comments="Source field: binlog_node_info.group_name")
    public String getGroupName() {
        return groupName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362649+08:00", comments="Source field: binlog_node_info.group_name")
    public void setGroupName(String groupName) {
        this.groupName = groupName == null ? null : groupName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362686+08:00", comments="Source field: binlog_node_info.polarx_inst_id")
    public String getPolarxInstId() {
        return polarxInstId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362706+08:00", comments="Source field: binlog_node_info.polarx_inst_id")
    public void setPolarxInstId(String polarxInstId) {
        this.polarxInstId = polarxInstId == null ? null : polarxInstId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362742+08:00", comments="Source field: binlog_node_info.cluster_role")
    public String getClusterRole() {
        return clusterRole;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362763+08:00", comments="Source field: binlog_node_info.cluster_role")
    public void setClusterRole(String clusterRole) {
        this.clusterRole = clusterRole == null ? null : clusterRole.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362806+08:00", comments="Source field: binlog_node_info.last_tso_heartbeat")
    public Date getLastTsoHeartbeat() {
        return lastTsoHeartbeat;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362825+08:00", comments="Source field: binlog_node_info.last_tso_heartbeat")
    public void setLastTsoHeartbeat(Date lastTsoHeartbeat) {
        this.lastTsoHeartbeat = lastTsoHeartbeat;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362861+08:00", comments="Source field: binlog_node_info.enable_light_rebalance")
    public Boolean getEnableLightRebalance() {
        return enableLightRebalance;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-09-29T12:40:16.362879+08:00", comments="Source field: binlog_node_info.enable_light_rebalance")
    public void setEnableLightRebalance(Boolean enableLightRebalance) {
        this.enableLightRebalance = enableLightRebalance;
    }
}