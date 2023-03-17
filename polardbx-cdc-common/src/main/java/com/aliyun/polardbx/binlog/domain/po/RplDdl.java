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

public class RplDdl {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.state_machine_id")
    private Long stateMachineId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.service_id")
    private Long serviceId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.task_id")
    private Long taskId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.76+08:00",
        comments = "Source field: rpl_ddl.ddl_tso")
    private String ddlTso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.76+08:00",
        comments = "Source field: rpl_ddl.job_id")
    private Long jobId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.761+08:00",
        comments = "Source field: rpl_ddl.state")
    private Integer state;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.761+08:00",
        comments = "Source field: rpl_ddl.ddl_stmt")
    private String ddlStmt;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.758+08:00",
        comments = "Source Table: rpl_ddl")
    public RplDdl(Long id, Date gmtCreated, Date gmtModified, Long stateMachineId, Long serviceId, Long taskId,
                  String ddlTso, Long jobId, Integer state, String ddlStmt) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.stateMachineId = stateMachineId;
        this.serviceId = serviceId;
        this.taskId = taskId;
        this.ddlTso = ddlTso;
        this.jobId = jobId;
        this.state = state;
        this.ddlStmt = ddlStmt;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source Table: rpl_ddl")
    public RplDdl() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.state_machine_id")
    public Long getStateMachineId() {
        return stateMachineId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.state_machine_id")
    public void setStateMachineId(Long stateMachineId) {
        this.stateMachineId = stateMachineId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.service_id")
    public Long getServiceId() {
        return serviceId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.759+08:00",
        comments = "Source field: rpl_ddl.service_id")
    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.76+08:00",
        comments = "Source field: rpl_ddl.task_id")
    public Long getTaskId() {
        return taskId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.76+08:00",
        comments = "Source field: rpl_ddl.task_id")
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.76+08:00",
        comments = "Source field: rpl_ddl.ddl_tso")
    public String getDdlTso() {
        return ddlTso;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.76+08:00",
        comments = "Source field: rpl_ddl.ddl_tso")
    public void setDdlTso(String ddlTso) {
        this.ddlTso = ddlTso == null ? null : ddlTso.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.76+08:00",
        comments = "Source field: rpl_ddl.job_id")
    public Long getJobId() {
        return jobId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.761+08:00",
        comments = "Source field: rpl_ddl.job_id")
    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.761+08:00",
        comments = "Source field: rpl_ddl.state")
    public Integer getState() {
        return state;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.761+08:00",
        comments = "Source field: rpl_ddl.state")
    public void setState(Integer state) {
        this.state = state;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.761+08:00",
        comments = "Source field: rpl_ddl.ddl_stmt")
    public String getDdlStmt() {
        return ddlStmt;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-12-01T14:20:15.761+08:00",
        comments = "Source field: rpl_ddl.ddl_stmt")
    public void setDdlStmt(String ddlStmt) {
        this.ddlStmt = ddlStmt == null ? null : ddlStmt.trim();
    }
}