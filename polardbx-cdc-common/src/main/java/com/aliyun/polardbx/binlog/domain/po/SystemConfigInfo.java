/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import javax.annotation.Generated;
import java.util.Date;

public class SystemConfigInfo {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.607+08:00",
        comments = "Source field: binlog_system_config.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.608+08:00",
        comments = "Source field: binlog_system_config.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.609+08:00",
        comments = "Source field: binlog_system_config.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.609+08:00",
        comments = "Source field: binlog_system_config.config_key")
    private String configKey;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.609+08:00",
        comments = "Source field: binlog_system_config.config_value")
    private String configValue;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.6+08:00",
        comments = "Source Table: binlog_system_config")
    public SystemConfigInfo(Long id, Date gmtCreated, Date gmtModified, String configKey, String configValue) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.configKey = configKey;
        this.configValue = configValue;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.606+08:00",
        comments = "Source Table: binlog_system_config")
    public SystemConfigInfo() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.608+08:00",
        comments = "Source field: binlog_system_config.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.608+08:00",
        comments = "Source field: binlog_system_config.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.608+08:00",
        comments = "Source field: binlog_system_config.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.609+08:00",
        comments = "Source field: binlog_system_config.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.609+08:00",
        comments = "Source field: binlog_system_config.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.609+08:00",
        comments = "Source field: binlog_system_config.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.609+08:00",
        comments = "Source field: binlog_system_config.config_key")
    public String getConfigKey() {
        return configKey;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.609+08:00",
        comments = "Source field: binlog_system_config.config_key")
    public void setConfigKey(String configKey) {
        this.configKey = configKey == null ? null : configKey.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.609+08:00",
        comments = "Source field: binlog_system_config.config_value")
    public String getConfigValue() {
        return configValue;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2021-03-25T16:51:43.609+08:00",
        comments = "Source field: binlog_system_config.config_value")
    public void setConfigValue(String configValue) {
        this.configValue = configValue == null ? null : configValue.trim();
    }
}