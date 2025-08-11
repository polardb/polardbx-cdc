/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class XStream {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.006+08:00", comments="Source field: binlog_x_stream.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.006+08:00", comments="Source field: binlog_x_stream.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.006+08:00", comments="Source field: binlog_x_stream.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.stream_name")
    private String streamName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.stream_desc")
    private String streamDesc;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.group_name")
    private String groupName;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.expected_storage_tso")
    private String expectedStorageTso;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.latest_cursor")
    private String latestCursor;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.status")
    private Integer status;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.endpoint")
    private String endpoint;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.005+08:00", comments="Source Table: binlog_x_stream")
    public XStream(Long id, Date gmtCreated, Date gmtModified, String streamName, String streamDesc, String groupName, String expectedStorageTso, String latestCursor, Integer status, String endpoint) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.streamName = streamName;
        this.streamDesc = streamDesc;
        this.groupName = groupName;
        this.expectedStorageTso = expectedStorageTso;
        this.latestCursor = latestCursor;
        this.status = status;
        this.endpoint = endpoint;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.006+08:00", comments="Source Table: binlog_x_stream")
    public XStream() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.006+08:00", comments="Source field: binlog_x_stream.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.006+08:00", comments="Source field: binlog_x_stream.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.006+08:00", comments="Source field: binlog_x_stream.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.006+08:00", comments="Source field: binlog_x_stream.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.stream_name")
    public String getStreamName() {
        return streamName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.stream_name")
    public void setStreamName(String streamName) {
        this.streamName = streamName == null ? null : streamName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.stream_desc")
    public String getStreamDesc() {
        return streamDesc;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.stream_desc")
    public void setStreamDesc(String streamDesc) {
        this.streamDesc = streamDesc == null ? null : streamDesc.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.group_name")
    public String getGroupName() {
        return groupName;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.group_name")
    public void setGroupName(String groupName) {
        this.groupName = groupName == null ? null : groupName.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.expected_storage_tso")
    public String getExpectedStorageTso() {
        return expectedStorageTso;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.expected_storage_tso")
    public void setExpectedStorageTso(String expectedStorageTso) {
        this.expectedStorageTso = expectedStorageTso == null ? null : expectedStorageTso.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.latest_cursor")
    public String getLatestCursor() {
        return latestCursor;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.latest_cursor")
    public void setLatestCursor(String latestCursor) {
        this.latestCursor = latestCursor == null ? null : latestCursor.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.status")
    public Integer getStatus() {
        return status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.endpoint")
    public String getEndpoint() {
        return endpoint;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2025-02-08T12:30:20.007+08:00", comments="Source field: binlog_x_stream.endpoint")
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint == null ? null : endpoint.trim();
    }
}