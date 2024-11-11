/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class InstConfig {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.inst_id")
    private String instId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.param_key")
    private String paramKey;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.param_val")
    private String paramVal;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source Table: inst_config")
    public InstConfig(Long id, Date gmtCreated, Date gmtModified, String instId, String paramKey, String paramVal) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.instId = instId;
        this.paramKey = paramKey;
        this.paramVal = paramVal;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source Table: inst_config")
    public InstConfig() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.inst_id")
    public String getInstId() {
        return instId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.inst_id")
    public void setInstId(String instId) {
        this.instId = instId == null ? null : instId.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.param_key")
    public String getParamKey() {
        return paramKey;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.param_key")
    public void setParamKey(String paramKey) {
        this.paramKey = paramKey == null ? null : paramKey.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.param_val")
    public String getParamVal() {
        return paramVal;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2023-07-13T15:39:08.949+08:00", comments="Source field: inst_config.param_val")
    public void setParamVal(String paramVal) {
        this.paramVal = paramVal == null ? null : paramVal.trim();
    }
}