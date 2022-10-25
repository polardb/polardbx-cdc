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

public class RplTask {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.18+08:00",
        comments = "Source field: rpl_task.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.gmt_heartbeat")
    private Date gmtHeartbeat;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.status")
    private Integer status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.service_id")
    private Long serviceId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.183+08:00",
        comments = "Source field: rpl_task.state_machine_id")
    private Long stateMachineId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.183+08:00",
        comments = "Source field: rpl_task.type")
    private Integer type;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.183+08:00",
        comments = "Source field: rpl_task.master_host")
    private String masterHost;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.master_port")
    private Integer masterPort;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.position")
    private String position;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.worker")
    private String worker;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.cluster_id")
    private String clusterId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.185+08:00",
        comments = "Source field: rpl_task.extractor_config")
    private String extractorConfig;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.185+08:00",
        comments = "Source field: rpl_task.pipeline_config")
    private String pipelineConfig;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.185+08:00",
        comments = "Source field: rpl_task.applier_config")
    private String applierConfig;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.186+08:00",
        comments = "Source field: rpl_task.last_error")
    private String lastError;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.186+08:00",
        comments = "Source field: rpl_task.statistic")
    private String statistic;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.186+08:00",
        comments = "Source field: rpl_task.extra")
    private String extra;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.171+08:00",
        comments = "Source Table: rpl_task")
    public RplTask(Long id, Date gmtCreated, Date gmtModified, Date gmtHeartbeat, Integer status, Long serviceId,
                   Long stateMachineId, Integer type, String masterHost, Integer masterPort, String position,
                   String worker, String clusterId, String extractorConfig, String pipelineConfig, String applierConfig,
                   String lastError, String statistic, String extra) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.gmtHeartbeat = gmtHeartbeat;
        this.status = status;
        this.serviceId = serviceId;
        this.stateMachineId = stateMachineId;
        this.type = type;
        this.masterHost = masterHost;
        this.masterPort = masterPort;
        this.position = position;
        this.worker = worker;
        this.clusterId = clusterId;
        this.extractorConfig = extractorConfig;
        this.pipelineConfig = pipelineConfig;
        this.applierConfig = applierConfig;
        this.lastError = lastError;
        this.statistic = statistic;
        this.extra = extra;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.18+08:00",
        comments = "Source Table: rpl_task")
    public RplTask() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.gmt_heartbeat")
    public Date getGmtHeartbeat() {
        return gmtHeartbeat;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.gmt_heartbeat")
    public void setGmtHeartbeat(Date gmtHeartbeat) {
        this.gmtHeartbeat = gmtHeartbeat;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.182+08:00",
        comments = "Source field: rpl_task.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.183+08:00",
        comments = "Source field: rpl_task.service_id")
    public Long getServiceId() {
        return serviceId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.183+08:00",
        comments = "Source field: rpl_task.service_id")
    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.183+08:00",
        comments = "Source field: rpl_task.state_machine_id")
    public Long getStateMachineId() {
        return stateMachineId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.183+08:00",
        comments = "Source field: rpl_task.state_machine_id")
    public void setStateMachineId(Long stateMachineId) {
        this.stateMachineId = stateMachineId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.183+08:00",
        comments = "Source field: rpl_task.type")
    public Integer getType() {
        return type;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.183+08:00",
        comments = "Source field: rpl_task.type")
    public void setType(Integer type) {
        this.type = type;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.183+08:00",
        comments = "Source field: rpl_task.master_host")
    public String getMasterHost() {
        return masterHost;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.master_host")
    public void setMasterHost(String masterHost) {
        this.masterHost = masterHost == null ? null : masterHost.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.master_port")
    public Integer getMasterPort() {
        return masterPort;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.master_port")
    public void setMasterPort(Integer masterPort) {
        this.masterPort = masterPort;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.position")
    public String getPosition() {
        return position;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.position")
    public void setPosition(String position) {
        this.position = position == null ? null : position.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.worker")
    public String getWorker() {
        return worker;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.184+08:00",
        comments = "Source field: rpl_task.worker")
    public void setWorker(String worker) {
        this.worker = worker == null ? null : worker.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.185+08:00",
        comments = "Source field: rpl_task.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.185+08:00",
        comments = "Source field: rpl_task.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.185+08:00",
        comments = "Source field: rpl_task.extractor_config")
    public String getExtractorConfig() {
        return extractorConfig;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.185+08:00",
        comments = "Source field: rpl_task.extractor_config")
    public void setExtractorConfig(String extractorConfig) {
        this.extractorConfig = extractorConfig == null ? null : extractorConfig.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.185+08:00",
        comments = "Source field: rpl_task.pipeline_config")
    public String getPipelineConfig() {
        return pipelineConfig;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.185+08:00",
        comments = "Source field: rpl_task.pipeline_config")
    public void setPipelineConfig(String pipelineConfig) {
        this.pipelineConfig = pipelineConfig == null ? null : pipelineConfig.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.186+08:00",
        comments = "Source field: rpl_task.applier_config")
    public String getApplierConfig() {
        return applierConfig;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.186+08:00",
        comments = "Source field: rpl_task.applier_config")
    public void setApplierConfig(String applierConfig) {
        this.applierConfig = applierConfig == null ? null : applierConfig.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.186+08:00",
        comments = "Source field: rpl_task.last_error")
    public String getLastError() {
        return lastError;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.186+08:00",
        comments = "Source field: rpl_task.last_error")
    public void setLastError(String lastError) {
        this.lastError = lastError == null ? null : lastError.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.186+08:00",
        comments = "Source field: rpl_task.statistic")
    public String getStatistic() {
        return statistic;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.186+08:00",
        comments = "Source field: rpl_task.statistic")
    public void setStatistic(String statistic) {
        this.statistic = statistic == null ? null : statistic.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.186+08:00",
        comments = "Source field: rpl_task.extra")
    public String getExtra() {
        return extra;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-30T17:10:06.187+08:00",
        comments = "Source field: rpl_task.extra")
    public void setExtra(String extra) {
        this.extra = extra == null ? null : extra.trim();
    }
}