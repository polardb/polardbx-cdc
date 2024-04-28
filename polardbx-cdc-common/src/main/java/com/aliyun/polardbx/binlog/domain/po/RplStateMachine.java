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

public class RplStateMachine {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.759+08:00", comments="Source field: rpl_state_machine.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.type")
    private String type;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.class_name")
    private String className;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.channel")
    private String channel;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.status")
    private String status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.state")
    private String state;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.cluster_id")
    private String clusterId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.config")
    private String config;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.context")
    private String context;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.75+08:00", comments="Source Table: rpl_state_machine")
    public RplStateMachine(Long id, Date gmtCreated, Date gmtModified, String type, String className, String channel, String status, String state, String clusterId, String config, String context) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.type = type;
        this.className = className;
        this.channel = channel;
        this.status = status;
        this.state = state;
        this.clusterId = clusterId;
        this.config = config;
        this.context = context;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.758+08:00", comments="Source Table: rpl_state_machine")
    public RplStateMachine() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.type")
    public String getType() {
        return type;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.type")
    public void setType(String type) {
        this.type = type == null ? null : type.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.class_name")
    public String getClassName() {
        return className;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.class_name")
    public void setClassName(String className) {
        this.className = className == null ? null : className.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.762+08:00", comments="Source field: rpl_state_machine.channel")
    public String getChannel() {
        return channel;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.channel")
    public void setChannel(String channel) {
        this.channel = channel == null ? null : channel.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.status")
    public String getStatus() {
        return status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.status")
    public void setStatus(String status) {
        this.status = status == null ? null : status.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.state")
    public String getState() {
        return state;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.state")
    public void setState(String state) {
        this.state = state == null ? null : state.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.config")
    public String getConfig() {
        return config;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.config")
    public void setConfig(String config) {
        this.config = config == null ? null : config.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.context")
    public String getContext() {
        return context;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-10-20T14:04:22.763+08:00", comments="Source field: rpl_state_machine.context")
    public void setContext(String context) {
        this.context = context == null ? null : context.trim();
    }
}