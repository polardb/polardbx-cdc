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

public class ColumnarNodeInfo {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.cluster_id")
    private String clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.container_id")
    private String containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.ip")
    private String ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.daemon_port")
    private Integer daemonPort;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.available_ports")
    private String availablePorts;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.status")
    private Integer status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.core")
    private Long core;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.mem")
    private Long mem;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.gmt_heartbeat")
    private Date gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.latest_cursor")
    private String latestCursor;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.role")
    private String role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.cluster_type")
    private String clusterType;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.group_name")
    private String groupName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source Table: columnar_node_info")
    public ColumnarNodeInfo(Long id, Date gmtCreated, Date gmtModified, String clusterId, String containerId, String ip, Integer daemonPort, String availablePorts, Integer status, Long core, Long mem, Date gmtHeartbeat, String latestCursor, String role, String clusterType, String groupName) {
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
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source Table: columnar_node_info")
    public ColumnarNodeInfo() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.container_id")
    public String getContainerId() {
        return containerId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.container_id")
    public void setContainerId(String containerId) {
        this.containerId = containerId == null ? null : containerId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.ip")
    public String getIp() {
        return ip;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.ip")
    public void setIp(String ip) {
        this.ip = ip == null ? null : ip.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.daemon_port")
    public Integer getDaemonPort() {
        return daemonPort;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.daemon_port")
    public void setDaemonPort(Integer daemonPort) {
        this.daemonPort = daemonPort;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.available_ports")
    public String getAvailablePorts() {
        return availablePorts;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.available_ports")
    public void setAvailablePorts(String availablePorts) {
        this.availablePorts = availablePorts == null ? null : availablePorts.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.core")
    public Long getCore() {
        return core;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.core")
    public void setCore(Long core) {
        this.core = core;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.mem")
    public Long getMem() {
        return mem;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.mem")
    public void setMem(Long mem) {
        this.mem = mem;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.gmt_heartbeat")
    public Date getGmtHeartbeat() {
        return gmtHeartbeat;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.gmt_heartbeat")
    public void setGmtHeartbeat(Date gmtHeartbeat) {
        this.gmtHeartbeat = gmtHeartbeat;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.latest_cursor")
    public String getLatestCursor() {
        return latestCursor;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.latest_cursor")
    public void setLatestCursor(String latestCursor) {
        this.latestCursor = latestCursor == null ? null : latestCursor.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.role")
    public String getRole() {
        return role;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.role")
    public void setRole(String role) {
        this.role = role == null ? null : role.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.cluster_type")
    public String getClusterType() {
        return clusterType;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.cluster_type")
    public void setClusterType(String clusterType) {
        this.clusterType = clusterType == null ? null : clusterType.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.group_name")
    public String getGroupName() {
        return groupName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-09-08T11:47:24.239+08:00", comments="Source field: columnar_node_info.group_name")
    public void setGroupName(String groupName) {
        this.groupName = groupName == null ? null : groupName.trim();
    }
}