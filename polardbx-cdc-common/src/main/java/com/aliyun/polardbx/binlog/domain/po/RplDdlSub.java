/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class RplDdlSub {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.fsm_id")
    private Long fsmId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.ddl_tso")
    private String ddlTso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.task_id")
    private Long taskId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.service_id")
    private Long serviceId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.state")
    private String state;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.291+08:00",
        comments = "Source field: rpl_ddl_sub.schema_name")
    private String schemaName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.291+08:00",
        comments = "Source field: rpl_ddl_sub.parallel_seq")
    private Integer parallelSeq;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source Table: rpl_ddl_sub")
    public RplDdlSub(Long id, Date gmtCreated, Date gmtModified, Long fsmId, String ddlTso, Long taskId, Long serviceId,
                     String state, String schemaName, Integer parallelSeq) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.fsmId = fsmId;
        this.ddlTso = ddlTso;
        this.taskId = taskId;
        this.serviceId = serviceId;
        this.state = state;
        this.schemaName = schemaName;
        this.parallelSeq = parallelSeq;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source Table: rpl_ddl_sub")
    public RplDdlSub() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.fsm_id")
    public Long getFsmId() {
        return fsmId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.fsm_id")
    public void setFsmId(Long fsmId) {
        this.fsmId = fsmId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.ddl_tso")
    public String getDdlTso() {
        return ddlTso;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.ddl_tso")
    public void setDdlTso(String ddlTso) {
        this.ddlTso = ddlTso == null ? null : ddlTso.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.task_id")
    public Long getTaskId() {
        return taskId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.task_id")
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.service_id")
    public Long getServiceId() {
        return serviceId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.service_id")
    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.state")
    public String getState() {
        return state;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.29+08:00",
        comments = "Source field: rpl_ddl_sub.state")
    public void setState(String state) {
        this.state = state == null ? null : state.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.291+08:00",
        comments = "Source field: rpl_ddl_sub.schema_name")
    public String getSchemaName() {
        return schemaName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.291+08:00",
        comments = "Source field: rpl_ddl_sub.schema_name")
    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName == null ? null : schemaName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.291+08:00",
        comments = "Source field: rpl_ddl_sub.parallel_seq")
    public Integer getParallelSeq() {
        return parallelSeq;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T16:52:46.291+08:00",
        comments = "Source field: rpl_ddl_sub.parallel_seq")
    public void setParallelSeq(Integer parallelSeq) {
        this.parallelSeq = parallelSeq;
    }
}