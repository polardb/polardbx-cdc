/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import javax.annotation.Generated;
import java.util.Date;

public class GroupDetailInfo {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.453+08:00",
        comments = "Source field: group_detail_info.id")
    private Long id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.inst_id")
    private String instId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.db_name")
    private String dbName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.group_name")
    private String groupName;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.storage_inst_id")
    private String storageInstId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.453+08:00",
        comments = "Source Table: group_detail_info")
    public GroupDetailInfo(Long id, Date gmtCreated, Date gmtModified, String instId, String dbName, String groupName,
                           String storageInstId) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.instId = instId;
        this.dbName = dbName;
        this.groupName = groupName;
        this.storageInstId = storageInstId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.453+08:00",
        comments = "Source Table: group_detail_info")
    public GroupDetailInfo() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.453+08:00",
        comments = "Source field: group_detail_info.id")
    public Long getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.inst_id")
    public String getInstId() {
        return instId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.inst_id")
    public void setInstId(String instId) {
        this.instId = instId == null ? null : instId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.db_name")
    public String getDbName() {
        return dbName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.db_name")
    public void setDbName(String dbName) {
        this.dbName = dbName == null ? null : dbName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.group_name")
    public String getGroupName() {
        return groupName;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.group_name")
    public void setGroupName(String groupName) {
        this.groupName = groupName == null ? null : groupName.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.storage_inst_id")
    public String getStorageInstId() {
        return storageInstId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2020-11-20T14:55:42.454+08:00",
        comments = "Source field: group_detail_info.storage_inst_id")
    public void setStorageInstId(String storageInstId) {
        this.storageInstId = storageInstId == null ? null : storageInstId.trim();
    }
}