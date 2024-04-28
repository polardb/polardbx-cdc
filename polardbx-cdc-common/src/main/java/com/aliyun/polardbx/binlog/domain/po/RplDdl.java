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
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.555+08:00",
        comments = "Source field: rpl_ddl_main.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.556+08:00",
        comments = "Source field: rpl_ddl_main.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.fsm_id")
    private Long fsmId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.ddl_tso")
    private String ddlTso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.service_id")
    private Long serviceId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.token")
    private String token;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.state")
    private String state;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.async_flag")
    private Boolean asyncFlag;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.async_state")
    private String asyncState;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.schema_name")
    private String schemaName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.table_name")
    private String tableName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.559+08:00",
        comments = "Source field: rpl_ddl_main.task_id")
    private Long taskId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.559+08:00",
        comments = "Source field: rpl_ddl_main.parallel_seq")
    private Integer parallelSeq;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.559+08:00",
        comments = "Source field: rpl_ddl_main.ddl_stmt")
    private String ddlStmt;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.551+08:00",
        comments = "Source Table: rpl_ddl_main")
    public RplDdl(Long id, Date gmtCreated, Date gmtModified, Long fsmId, String ddlTso, Long serviceId, String token,
                  String state, Boolean asyncFlag, String asyncState, String schemaName, String tableName, Long taskId,
                  Integer parallelSeq, String ddlStmt) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.fsmId = fsmId;
        this.ddlTso = ddlTso;
        this.serviceId = serviceId;
        this.token = token;
        this.state = state;
        this.asyncFlag = asyncFlag;
        this.asyncState = asyncState;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.taskId = taskId;
        this.parallelSeq = parallelSeq;
        this.ddlStmt = ddlStmt;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.555+08:00",
        comments = "Source Table: rpl_ddl_main")
    public RplDdl() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.556+08:00",
        comments = "Source field: rpl_ddl_main.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.556+08:00",
        comments = "Source field: rpl_ddl_main.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.fsm_id")
    public Long getFsmId() {
        return fsmId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.fsm_id")
    public void setFsmId(Long fsmId) {
        this.fsmId = fsmId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.ddl_tso")
    public String getDdlTso() {
        return ddlTso;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.ddl_tso")
    public void setDdlTso(String ddlTso) {
        this.ddlTso = ddlTso == null ? null : ddlTso.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.service_id")
    public Long getServiceId() {
        return serviceId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.service_id")
    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.557+08:00",
        comments = "Source field: rpl_ddl_main.token")
    public String getToken() {
        return token;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.token")
    public void setToken(String token) {
        this.token = token == null ? null : token.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.state")
    public String getState() {
        return state;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.state")
    public void setState(String state) {
        this.state = state == null ? null : state.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.async_flag")
    public Boolean getAsyncFlag() {
        return asyncFlag;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.async_flag")
    public void setAsyncFlag(Boolean asyncFlag) {
        this.asyncFlag = asyncFlag;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.async_state")
    public String getAsyncState() {
        return asyncState;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.async_state")
    public void setAsyncState(String asyncState) {
        this.asyncState = asyncState == null ? null : asyncState.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.schema_name")
    public String getSchemaName() {
        return schemaName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.schema_name")
    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName == null ? null : schemaName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.table_name")
    public String getTableName() {
        return tableName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.558+08:00",
        comments = "Source field: rpl_ddl_main.table_name")
    public void setTableName(String tableName) {
        this.tableName = tableName == null ? null : tableName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.559+08:00",
        comments = "Source field: rpl_ddl_main.task_id")
    public Long getTaskId() {
        return taskId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.559+08:00",
        comments = "Source field: rpl_ddl_main.task_id")
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.559+08:00",
        comments = "Source field: rpl_ddl_main.parallel_seq")
    public Integer getParallelSeq() {
        return parallelSeq;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.559+08:00",
        comments = "Source field: rpl_ddl_main.parallel_seq")
    public void setParallelSeq(Integer parallelSeq) {
        this.parallelSeq = parallelSeq;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.559+08:00",
        comments = "Source field: rpl_ddl_main.ddl_stmt")
    public String getDdlStmt() {
        return ddlStmt;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2024-03-20T17:19:45.559+08:00",
        comments = "Source field: rpl_ddl_main.ddl_stmt")
    public void setDdlStmt(String ddlStmt) {
        this.ddlStmt = ddlStmt == null ? null : ddlStmt.trim();
    }
}