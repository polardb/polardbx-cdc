/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class BinlogEnvConfigHistory {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.608+08:00",
        comments = "Source field: binlog_env_config_history.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.tso")
    private String tso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.instruction_id")
    private String instructionId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.change_env_content")
    private String changeEnvContent;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.605+08:00",
        comments = "Source Table: binlog_env_config_history")
    public BinlogEnvConfigHistory(Long id, Date gmtCreated, Date gmtModified, String tso, String instructionId,
                                  String changeEnvContent) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.tso = tso;
        this.instructionId = instructionId;
        this.changeEnvContent = changeEnvContent;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.608+08:00",
        comments = "Source Table: binlog_env_config_history")
    public BinlogEnvConfigHistory() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.608+08:00",
        comments = "Source field: binlog_env_config_history.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.tso")
    public String getTso() {
        return tso;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.tso")
    public void setTso(String tso) {
        this.tso = tso == null ? null : tso.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.instruction_id")
    public String getInstructionId() {
        return instructionId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.instruction_id")
    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId == null ? null : instructionId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.change_env_content")
    public String getChangeEnvContent() {
        return changeEnvContent;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-11-24T17:38:28.609+08:00",
        comments = "Source field: binlog_env_config_history.change_env_content")
    public void setChangeEnvContent(String changeEnvContent) {
        this.changeEnvContent = changeEnvContent == null ? null : changeEnvContent.trim();
    }
}