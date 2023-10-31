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

public class RplDdlSub {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.fsm_id")
    private Long fsmId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.ddl_tso")
    private String ddlTso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.task_id")
    private Long taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.service_id")
    private Long serviceId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.954+08:00", comments="Source field: rpl_ddl_sub.state")
    private Integer state;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source Table: rpl_ddl_sub")
    public RplDdlSub(Long id, Date gmtCreated, Date gmtModified, Long fsmId, String ddlTso, Long taskId, Long serviceId, Integer state) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.fsmId = fsmId;
        this.ddlTso = ddlTso;
        this.taskId = taskId;
        this.serviceId = serviceId;
        this.state = state;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source Table: rpl_ddl_sub")
    public RplDdlSub() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.fsm_id")
    public Long getFsmId() {
        return fsmId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.fsm_id")
    public void setFsmId(Long fsmId) {
        this.fsmId = fsmId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.ddl_tso")
    public String getDdlTso() {
        return ddlTso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.ddl_tso")
    public void setDdlTso(String ddlTso) {
        this.ddlTso = ddlTso == null ? null : ddlTso.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.task_id")
    public Long getTaskId() {
        return taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.task_id")
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.953+08:00", comments="Source field: rpl_ddl_sub.service_id")
    public Long getServiceId() {
        return serviceId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.954+08:00", comments="Source field: rpl_ddl_sub.service_id")
    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.954+08:00", comments="Source field: rpl_ddl_sub.state")
    public Integer getState() {
        return state;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.954+08:00", comments="Source field: rpl_ddl_sub.state")
    public void setState(Integer state) {
        this.state = state;
    }
}