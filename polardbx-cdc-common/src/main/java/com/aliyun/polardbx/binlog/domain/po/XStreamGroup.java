/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class XStreamGroup {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.21+08:00", comments="Source field: binlog_x_stream_group.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.group_name")
    private String groupName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.group_desc")
    private String groupDesc;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.21+08:00", comments="Source Table: binlog_x_stream_group")
    public XStreamGroup(Long id, Date gmtCreated, Date gmtModified, String groupName, String groupDesc) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.groupName = groupName;
        this.groupDesc = groupDesc;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.21+08:00", comments="Source Table: binlog_x_stream_group")
    public XStreamGroup() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.21+08:00", comments="Source field: binlog_x_stream_group.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.group_name")
    public String getGroupName() {
        return groupName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.group_name")
    public void setGroupName(String groupName) {
        this.groupName = groupName == null ? null : groupName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.group_desc")
    public String getGroupDesc() {
        return groupDesc;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2022-03-03T17:02:55.211+08:00", comments="Source field: binlog_x_stream_group.group_desc")
    public void setGroupDesc(String groupDesc) {
        this.groupDesc = groupDesc == null ? null : groupDesc.trim();
    }
}