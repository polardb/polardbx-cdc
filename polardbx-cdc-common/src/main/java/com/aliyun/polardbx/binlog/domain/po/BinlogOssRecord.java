/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

import lombok.ToString;

import javax.annotation.Generated;
import java.util.Date;

@ToString
public class BinlogOssRecord {
    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.52+08:00",
        comments = "Source field: binlog_oss_record.id")
    private Integer id;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.gmt_created")
    private Date gmtCreated;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.gmt_modified")
    private Date gmtModified;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.binlog_file")
    private String binlogFile;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.upload_status")
    private Integer uploadStatus;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.purge_status")
    private Integer purgeStatus;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.upload_host")
    private String uploadHost;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.log_begin")
    private Date logBegin;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.log_end")
    private Date logEnd;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.log_size")
    private Long logSize;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.last_tso")
    private String lastTso;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.group_id")
    private String groupId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.523+08:00",
        comments = "Source field: binlog_oss_record.stream_id")
    private String streamId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.523+08:00",
        comments = "Source field: binlog_oss_record.cluster_id")
    private String clusterId;

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.518+08:00",
        comments = "Source Table: binlog_oss_record")
    public BinlogOssRecord(Integer id, Date gmtCreated, Date gmtModified, String binlogFile, Integer uploadStatus,
                           Integer purgeStatus, String uploadHost, Date logBegin, Date logEnd, Long logSize,
                           String lastTso, String groupId, String streamId, String clusterId) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.binlogFile = binlogFile;
        this.uploadStatus = uploadStatus;
        this.purgeStatus = purgeStatus;
        this.uploadHost = uploadHost;
        this.logBegin = logBegin;
        this.logEnd = logEnd;
        this.logSize = logSize;
        this.lastTso = lastTso;
        this.groupId = groupId;
        this.streamId = streamId;
        this.clusterId = clusterId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.519+08:00",
        comments = "Source Table: binlog_oss_record")
    public BinlogOssRecord() {
        super();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.id")
    public Integer getId() {
        return id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.id")
    public void setId(Integer id) {
        this.id = id;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.binlog_file")
    public String getBinlogFile() {
        return binlogFile;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.binlog_file")
    public void setBinlogFile(String binlogFile) {
        this.binlogFile = binlogFile == null ? null : binlogFile.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.521+08:00",
        comments = "Source field: binlog_oss_record.upload_status")
    public Integer getUploadStatus() {
        return uploadStatus;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.upload_status")
    public void setUploadStatus(Integer uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.purge_status")
    public Integer getPurgeStatus() {
        return purgeStatus;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.purge_status")
    public void setPurgeStatus(Integer purgeStatus) {
        this.purgeStatus = purgeStatus;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.upload_host")
    public String getUploadHost() {
        return uploadHost;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.upload_host")
    public void setUploadHost(String uploadHost) {
        this.uploadHost = uploadHost == null ? null : uploadHost.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.log_begin")
    public Date getLogBegin() {
        return logBegin;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.log_begin")
    public void setLogBegin(Date logBegin) {
        this.logBegin = logBegin;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.log_end")
    public Date getLogEnd() {
        return logEnd;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.log_end")
    public void setLogEnd(Date logEnd) {
        this.logEnd = logEnd;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.log_size")
    public Long getLogSize() {
        return logSize;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.log_size")
    public void setLogSize(Long logSize) {
        this.logSize = logSize;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.last_tso")
    public String getLastTso() {
        return lastTso;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.last_tso")
    public void setLastTso(String lastTso) {
        this.lastTso = lastTso == null ? null : lastTso.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.group_id")
    public String getGroupId() {
        return groupId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.522+08:00",
        comments = "Source field: binlog_oss_record.group_id")
    public void setGroupId(String groupId) {
        this.groupId = groupId == null ? null : groupId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.523+08:00",
        comments = "Source field: binlog_oss_record.stream_id")
    public String getStreamId() {
        return streamId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.523+08:00",
        comments = "Source field: binlog_oss_record.stream_id")
    public void setStreamId(String streamId) {
        this.streamId = streamId == null ? null : streamId.trim();
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.523+08:00",
        comments = "Source field: binlog_oss_record.cluster_id")
    public String getClusterId() {
        return clusterId;
    }

    @Generated(value = "org.mybatis.generator.api.MyBatisGenerator", date = "2023-04-25T15:17:32.523+08:00",
        comments = "Source field: binlog_oss_record.cluster_id")
    public void setClusterId(String clusterId) {
        this.clusterId = clusterId == null ? null : clusterId.trim();
    }
}