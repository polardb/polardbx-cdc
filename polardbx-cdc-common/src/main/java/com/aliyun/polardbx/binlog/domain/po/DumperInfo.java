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

public class DumperInfo {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.14+08:00", comments="Source field: binlog_dumper_info.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.cluster_id")
    private String clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.task_name")
    private String taskName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.ip")
    private String ip;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.port")
    private Integer port;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.role")
    private String role;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.status")
    private Integer status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.gmt_heartbeat")
    private Date gmtHeartbeat;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.container_id")
    private String containerId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.143+08:00", comments="Source field: binlog_dumper_info.version")
    private Long version;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.136+08:00", comments="Source Table: binlog_dumper_info")
    public DumperInfo(Long id, Date gmtCreated, Date gmtModified, String clusterId, String taskName, String ip, Integer port, String role, Integer status, Date gmtHeartbeat, String containerId, Long version) {
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

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.139+08:00", comments="Source Table: binlog_dumper_info")
    public DumperInfo() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.14+08:00", comments="Source field: binlog_dumper_info.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.task_name")
    public String getTaskName() {
        return taskName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.141+08:00", comments="Source field: binlog_dumper_info.task_name")
    public void setTaskName(String taskName) {
        this.taskName = taskName == null ? null : taskName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.ip")
    public String getIp() {
        return ip;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.ip")
    public void setIp(String ip) {
        this.ip = ip == null ? null : ip.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.port")
    public Integer getPort() {
        return port;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.port")
    public void setPort(Integer port) {
        this.port = port;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.role")
    public String getRole() {
        return role;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.role")
    public void setRole(String role) {
        this.role = role == null ? null : role.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.gmt_heartbeat")
    public Date getGmtHeartbeat() {
        return gmtHeartbeat;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.gmt_heartbeat")
    public void setGmtHeartbeat(Date gmtHeartbeat) {
        this.gmtHeartbeat = gmtHeartbeat;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.142+08:00", comments="Source field: binlog_dumper_info.container_id")
    public String getContainerId() {
        return containerId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.143+08:00", comments="Source field: binlog_dumper_info.container_id")
    public void setContainerId(String containerId) {
        this.containerId = containerId == null ? null : containerId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.143+08:00", comments="Source field: binlog_dumper_info.version")
    public Long getVersion() {
        return version;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-04-11T09:47:17.143+08:00", comments="Source field: binlog_dumper_info.version")
    public void setVersion(Long version) {
        this.version = version;
    }
}