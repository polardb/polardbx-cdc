/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class PolarxCNodeInfo {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.069+08:00",
        comments = "Source field: node_info.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.069+08:00",
        comments = "Source field: node_info.cluster")
    private String cluster;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.inst_id")
    private String instId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.nodeid")
    private String nodeid;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.version")
    private String version;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.ip")
    private String ip;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.port")
    private Integer port;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.rpc_port")
    private Long rpcPort;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.role")
    private Long role;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.status")
    private Long status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.072+08:00",
        comments = "Source field: node_info.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.072+08:00",
        comments = "Source field: node_info.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.064+08:00",
        comments = "Source Table: node_info")
    public PolarxCNodeInfo(Long id, String cluster, String instId, String nodeid, String version, String ip,
                           Integer port, Long rpcPort, Long role, Long status, Date gmtCreated, Date gmtModified) {
        this.id = id;
        this.cluster = cluster;
        this.instId = instId;
        this.nodeid = nodeid;
        this.version = version;
        this.ip = ip;
        this.port = port;
        this.rpcPort = rpcPort;
        this.role = role;
        this.status = status;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.068+08:00",
        comments = "Source Table: node_info")
    public PolarxCNodeInfo() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.069+08:00",
        comments = "Source field: node_info.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.069+08:00",
        comments = "Source field: node_info.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.069+08:00",
        comments = "Source field: node_info.cluster")
    public String getCluster() {
        return cluster;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.cluster")
    public void setCluster(String cluster) {
        this.cluster = cluster == null ? null : cluster.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.inst_id")
    public String getInstId() {
        return instId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.inst_id")
    public void setInstId(String instId) {
        this.instId = instId == null ? null : instId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.nodeid")
    public String getNodeid() {
        return nodeid;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.nodeid")
    public void setNodeid(String nodeid) {
        this.nodeid = nodeid == null ? null : nodeid.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.version")
    public String getVersion() {
        return version;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.version")
    public void setVersion(String version) {
        this.version = version == null ? null : version.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.07+08:00",
        comments = "Source field: node_info.ip")
    public String getIp() {
        return ip;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.ip")
    public void setIp(String ip) {
        this.ip = ip == null ? null : ip.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.port")
    public Integer getPort() {
        return port;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.port")
    public void setPort(Integer port) {
        this.port = port;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.rpc_port")
    public Long getRpcPort() {
        return rpcPort;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.rpc_port")
    public void setRpcPort(Long rpcPort) {
        this.rpcPort = rpcPort;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.role")
    public Long getRole() {
        return role;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.071+08:00",
        comments = "Source field: node_info.role")
    public void setRole(Long role) {
        this.role = role;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.072+08:00",
        comments = "Source field: node_info.status")
    public Long getStatus() {
        return status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.072+08:00",
        comments = "Source field: node_info.status")
    public void setStatus(Long status) {
        this.status = status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.072+08:00",
        comments = "Source field: node_info.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.072+08:00",
        comments = "Source field: node_info.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.072+08:00",
        comments = "Source field: node_info.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-04-19T14:22:59.072+08:00",
        comments = "Source field: node_info.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }
}