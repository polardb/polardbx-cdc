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

public class RplService {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.856+08:00",
        comments = "Source field: rpl_service.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.857+08:00",
        comments = "Source field: rpl_service.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.857+08:00",
        comments = "Source field: rpl_service.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.state_machine_id")
    private Long stateMachineId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.service_type")
    private Integer serviceType;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.state_list")
    private String stateList;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.channel")
    private String channel;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.status")
    private Integer status;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.853+08:00",
        comments = "Source Table: rpl_service")
    public RplService(Long id, Date gmtCreated, Date gmtModified, Long stateMachineId, Integer serviceType,
                      String stateList, String channel, Integer status) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.stateMachineId = stateMachineId;
        this.serviceType = serviceType;
        this.stateList = stateList;
        this.channel = channel;
        this.status = status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.856+08:00",
        comments = "Source Table: rpl_service")
    public RplService() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.857+08:00",
        comments = "Source field: rpl_service.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.857+08:00",
        comments = "Source field: rpl_service.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.857+08:00",
        comments = "Source field: rpl_service.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.857+08:00",
        comments = "Source field: rpl_service.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.857+08:00",
        comments = "Source field: rpl_service.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.857+08:00",
        comments = "Source field: rpl_service.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.state_machine_id")
    public Long getStateMachineId() {
        return stateMachineId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.state_machine_id")
    public void setStateMachineId(Long stateMachineId) {
        this.stateMachineId = stateMachineId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.service_type")
    public Integer getServiceType() {
        return serviceType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.service_type")
    public void setServiceType(Integer serviceType) {
        this.serviceType = serviceType;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.state_list")
    public String getStateList() {
        return stateList;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.state_list")
    public void setStateList(String stateList) {
        this.stateList = stateList == null ? null : stateList.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.channel")
    public String getChannel() {
        return channel;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.channel")
    public void setChannel(String channel) {
        this.channel = channel == null ? null : channel.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-09-18T17:45:40.858+08:00",
        comments = "Source field: rpl_service.status")
    public void setStatus(Integer status) {
        this.status = status;
    }
}